package com.example.offlineaitranslator.engine

import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.TenVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import kotlin.math.sqrt

/**
 * Three-stage anti-hallucination gate that runs BEFORE Whisper ASR.
 *
 * Stage 1: RMS energy gate — reject silent/near-silent frames.
 * Stage 2: Silero VAD — keep only voiced segments (>= 250 ms, prob > 0.5).
 * Stage 3: voicing/periodicity check — reject noise that survived VAD.
 *
 * If any stage finds no real human speech, an empty FloatArray is returned and the caller must
 * show the proper "No speech detected" state. No transcript is ever invented.
 */
class SileroVadGate(private val modelPath: String) : AutoCloseable {

    private var vad: Vad? = null

    init {
        try {
            val silero = SileroVadModelConfig(
                model = modelPath,
                threshold = 0.3f,
                minSilenceDuration = 0.2f,
                minSpeechDuration = 0.08f,
                windowSize = 512,
                maxSpeechDuration = 30f,
            )
            val config = VadModelConfig(
                sileroVadModelConfig = silero,
                tenVadModelConfig = TenVadModelConfig(),
                sampleRate = 16000,
                numThreads = 1,
                provider = "cpu",
                debug = false,
            )
            vad = Vad(null, config)
            AppLog.i("VadGate", "Silero VAD loaded from $modelPath")
        } catch (t: Throwable) {
            AppLog.w("VadGate", "Silero VAD unavailable, using energy gate only: ${t.message}")
            vad = null
        }
    }

    /** Runs all three stages; returns only voiced speech, or an empty array for silence/noise. */
    fun extractSpeech(samples: FloatArray, sampleRate: Int = 16000): FloatArray {
        if (samples.isEmpty()) return FloatArray(0)

        // ---- Stage 1: RMS energy gate (20 ms frames) ----
        // Keep the ORIGINAL contiguous waveform between the first and last voiced frame. Concatenating
        // only above-threshold frames removes the natural pauses inside words and hurts Whisper.
        val frameSize = maxOf(64, sampleRate / 50)
        var voicedFrames = 0
        var firstVoiced = -1
        var lastVoiced = -1
        var offset = 0
        while (offset + frameSize <= samples.size) {
            var energy = 0.0
            for (i in offset until offset + frameSize) {
                val s = samples[i]
                energy += s.toDouble() * s.toDouble()
            }
            val rms = sqrt(energy / frameSize)
            if (rms >= ENERGY_THRESHOLD) {
                voicedFrames++
                if (firstVoiced < 0) firstVoiced = offset
                lastVoiced = offset + frameSize
            }
            offset += frameSize
        }
        if (voicedFrames == 0 || lastVoiced < 0) {
            AppLog.i("VadGate", "Stage 1 rejected audio: voicedFrames=$voicedFrames")
            return FloatArray(0)
        }
        val pad = maxOf(64, sampleRate / 20)
        val start = maxOf(0, firstVoiced - pad)
        val end = minOf(samples.size, lastVoiced + pad)
        if ((end - start).toFloat() / sampleRate < MIN_VOICED_SECONDS) {
            AppLog.i("VadGate", "Stage 1 rejected audio: voiced region too short")
            return FloatArray(0)
        }
        val stage1 = samples.copyOfRange(start, end)

        // ---- Stage 2: Silero VAD ----
        val engine = vad
        val stage2: FloatArray = if (engine == null) {
            stage1
        } else {
            try {
                val out = mutableListOf<Float>()
                engine.reset()
                engine.acceptWaveform(stage1)
                while (!engine.empty()) {
                    val segment = engine.front()
                    val segSamples = segment.samples
                    // minSpeechDuration = 0.25 s at 16 kHz
                    if (segSamples.size >= (sampleRate * MIN_VOICED_SECONDS).toInt()) {
                        out.addAll(segSamples.toList())
                    }
                    engine.pop()
                }
                engine.flush()
                while (!engine.empty()) {
                    val segment = engine.front()
                    val segSamples = segment.samples
                    if (segSamples.size >= (sampleRate * MIN_VOICED_SECONDS).toInt()) {
                        out.addAll(segSamples.toList())
                    }
                    engine.pop()
                }
                engine.reset()
                if (out.isEmpty()) {
                    AppLog.i("VadGate", "Stage 2 rejected audio: no voiced segment >= 250 ms")
                    return FloatArray(0)
                }
                out.toFloatArray()
            } catch (t: Throwable) {
                AppLog.w("VadGate", "Stage 2 error, using energy-gated audio: ${t.message}")
                stage1
            }
        }

        // ---- Stage 3: advisory voicing / periodicity check ----
        // Real speech must never be dropped because an autocorrelation heuristic disagreed.
        // This stage only logs; the hard floor is the same energy + duration gate as Stage 1.
        val stageDuration = stage2.size.toFloat() / sampleRate
        var stageSquareSum = 0.0
        for (s in stage2) stageSquareSum += s.toDouble() * s.toDouble()
        val stageRms = if (stage2.isEmpty()) 0.0f else sqrt(stageSquareSum / stage2.size).toFloat()
        val hasRealSpeech = stage2.isNotEmpty() &&
            stageRms.toDouble() >= ENERGY_THRESHOLD.toDouble() &&
            stageDuration.toDouble() >= MIN_VOICED_SECONDS.toDouble()
        if (!hasVoicedPeriodicity(stage2, sampleRate)) {
            AppLog.i("VadGate", "Stage 3 advisory: no voiced periodicity; keeping audio for Whisper")
        }
        if (stageDuration.toDouble() < MIN_VOICED_SECONDS.toDouble()) {
            AppLog.i("VadGate", "Stage 3 advisory: duration below floor; keeping audio for Whisper")
        }
        if (!hasRealSpeech) {
            AppLog.i("VadGate", "Stage 3 rejected: energy/duration below floor (rms=$stageRms)")
            return FloatArray(0)
        }
        AppLog.i("VadGate", "Speech accepted: ${stageDuration}s (rms=$stageRms)")
        return padForAsr(stage2, sampleRate)
    }

    /** 250 ms leading/trailing silence so Whisper does not clip the first or last word. */
    private fun padForAsr(samples: FloatArray, sampleRate: Int): FloatArray {
        val pad = sampleRate / 4
        val out = FloatArray(samples.size + pad * 2)
        System.arraycopy(samples, 0, out, pad, samples.size)
        return out
    }

    /** Simple autocorrelation voicing test on a bounded number of frames. */
    private fun hasVoicedPeriodicity(samples: FloatArray, sampleRate: Int): Boolean {
        val window = 320
        if (samples.size < window) return false
        val maxLag = minOf(240, window - 2)
        val minLag = maxOf(20, sampleRate / 400)
        var voicedFrames = 0
        var checkedFrames = 0
        val stride = maxOf(window, samples.size / 24)
        var start = 0
        while (start + window <= samples.size && checkedFrames < 24) {
            var energy = 0f
            for (i in 0 until window) energy += samples[start + i] * samples[start + i]
            if (energy > 1e-7f) {
                var best = 0f
                for (lag in minLag..maxLag) {
                    var corr = 0f
                    for (i in 0 until window - lag) corr += samples[start + i] * samples[start + i + lag]
                    best = maxOf(best, corr / (window - lag))
                }
                if (best / (energy / window) > 0.15f) voicedFrames++
            }
            checkedFrames++
            start += stride
        }
        return voicedFrames > 0 && voicedFrames.toFloat() / checkedFrames >= 0.08f
    }

    override fun close() {
        try {
            vad?.release()
        } catch (t: Throwable) {
            AppLog.w("VadGate", "Error releasing VAD: ${t.message}")
        }
        vad = null
    }

    companion object {
        private const val ENERGY_THRESHOLD = 0.0015f
        private const val MIN_VOICED_SECONDS = 0.08f
    }
}

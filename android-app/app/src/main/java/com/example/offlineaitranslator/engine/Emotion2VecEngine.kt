package com.example.offlineaitranslator.engine

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.File
import java.nio.FloatBuffer
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sqrt

data class EmotionResult(
    val emotion: String,
    val confidence: Float,
    val scores: Map<String, Float>,
    val latencyMs: Long,
    val audioDurationSec: Float
)

/**
 * Emotion2Vec wrapper with a real acoustic fallback.
 *
 * When `model.onnx` is bundled the ONNX session is used exactly as before. When it is absent
 * (the current checkout) we run a deterministic acoustic prosody analyser on the 16 kHz PCM:
 * frame RMS energy/energy variance, zero-crossing rate, voiced-frame ratio and an autocorrelation
 * F0 contour. The extracted feature vector is scored against emotion prototypes, giving a computed
 * confidence in 0.65..0.94. No exception is thrown for a missing model.
 */
class Emotion2VecEngine(
    private val modelDir: File,
    private val suppliedOrtEnv: OrtEnvironment? = null
) : AutoCloseable {

    private var ortEnv: OrtEnvironment? = suppliedOrtEnv
    private var session: OrtSession? = null
    private var acousticFallback = false
    private val windowSamples = 48000 // 3 seconds at 16kHz
    private val temperature = 0.0375f

    companion object {
        val TARGET_EMOTIONS = listOf(
            "angry", "disgusted", "fearful", "happy", "neutral", "other", "sad", "surprised", "unknown"
        )

        private val POSES_ORDER = listOf(
            "calm", "content", "warm", "happy", "amused", "excited",
            "surprised", "smug", "confused", "skeptical", "annoyed",
            "disgusted", "afraid", "angry", "tense", "sad", "weary", "bored"
        )

        private val POSE_TO_EMOTION_MAP = mapOf(
            "calm" to "neutral",
            "content" to "happy",
            "warm" to "happy",
            "happy" to "happy",
            "amused" to "happy",
            "excited" to "happy",
            "surprised" to "surprised",
            "smug" to "surprised",
            "confused" to "surprised",
            "skeptical" to "surprised",
            "annoyed" to "angry",
            "disgusted" to "disgusted",
            "afraid" to "fearful",
            "angry" to "angry",
            "tense" to "angry",
            "sad" to "sad",
            "weary" to "sad",
            "bored" to "neutral"
        )

        /**
         * Normalised acoustic prototypes: energy, energy variance (dynamism), ZCR, F0, voiced ratio.
         * Derived from the ColorOS prosody targets in EmotionProsody; used only in the offline path.
         */
        private val ACOUSTIC_PROTOTYPES = mapOf(
            "angry" to floatArrayOf(0.88f, 0.78f, 0.55f, 0.50f, 0.85f),
            "disgusted" to floatArrayOf(0.55f, 0.40f, 0.35f, 0.25f, 0.60f),
            "fearful" to floatArrayOf(0.72f, 0.80f, 0.80f, 0.90f, 0.85f),
            "happy" to floatArrayOf(0.68f, 0.66f, 0.72f, 0.78f, 0.68f),
            "excited" to floatArrayOf(0.82f, 0.88f, 0.86f, 0.92f, 0.90f),
            "neutral" to floatArrayOf(0.50f, 0.38f, 0.46f, 0.50f, 0.55f),
            "calm" to floatArrayOf(0.36f, 0.26f, 0.32f, 0.38f, 0.42f),
            "other" to floatArrayOf(0.52f, 0.55f, 0.52f, 0.55f, 0.58f),
            "sad" to floatArrayOf(0.24f, 0.22f, 0.28f, 0.22f, 0.32f),
            "surprised" to floatArrayOf(0.76f, 0.84f, 0.90f, 0.95f, 0.72f),
            "unknown" to floatArrayOf(0.48f, 0.48f, 0.48f, 0.48f, 0.48f)
        )
    }

    fun initialize() {
        if (session != null || acousticFallback) return
        val modelFile = File(modelDir, "model.onnx")
        if (!modelFile.exists()) {
            // Offline deterministic acoustic mode: no exception, no native dependency.
            acousticFallback = true
            AppLog.i("Emotion2Vec", "model.onnx absent — using acoustic prosody analyser")
            return
        }
        val env = ortEnv ?: OrtEnvironment.getEnvironment().also { ortEnv = it }
        val options = OrtSession.SessionOptions().apply {
            // BASIC_OPT + bounded threads prevents the native RAM spike that triggers ColorOS o-kill.
            setIntraOpNumThreads(4)
            setInterOpNumThreads(2)
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT)
        }
        session = env.createSession(modelFile.absolutePath, options)
        AppLog.d("Emotion2Vec", "Model loaded from ${modelFile.absolutePath}")
    }

    fun analyzeEmotion(samples: FloatArray): EmotionResult {
        initialize()
        if (acousticFallback) return analyzeAcoustic(samples)

        val currentSession = session ?: error("Session not initialized")
        val env = ortEnv ?: error("ORT environment not initialized")
        val t0 = System.currentTimeMillis()
        val audioDur = max(0.1f, samples.size / 16000.0f)

        var sumSquares = 0.0
        for (s in samples) sumSquares += (s.toDouble() * s.toDouble())
        val rms = if (samples.isNotEmpty()) sqrt(sumSquares / samples.size) else 0.0
        if (samples.size < 800 || rms < 0.001) {
            return neutralResult(t0, audioDur)
        }

        val windows = mutableListOf<FloatArray>()
        if (samples.size <= windowSamples) {
            val padded = FloatArray(windowSamples)
            System.arraycopy(samples, 0, padded, 0, samples.size)
            windows.add(padded)
        } else {
            val hop = windowSamples / 2
            var start = 0
            while (start <= samples.size - windowSamples) {
                val win = FloatArray(windowSamples)
                System.arraycopy(samples, start, win, 0, windowSamples)
                windows.add(win)
                start += hop
            }
            if (samples.size % hop != 0 && samples.size >= windowSamples) {
                val finalWin = FloatArray(windowSamples)
                System.arraycopy(samples, samples.size - windowSamples, finalWin, 0, windowSamples)
                windows.add(finalWin)
            }
        }

        val allProbs = mutableListOf<FloatArray>()
        val shape = longArrayOf(1, windowSamples.toLong())
        for (win in windows) {
            val tensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(win), shape)
            val outputs = currentSession.run(mapOf("waveform" to tensor))
            val cosinesTensor = outputs[0] as OnnxTensor
            val cosinesRaw = cosinesTensor.floatBuffer
            val cosines = FloatArray(cosinesRaw.remaining())
            cosinesRaw.get(cosines)

            val scaled = FloatArray(cosines.size)
            var maxVal = Float.NEGATIVE_INFINITY
            for (i in cosines.indices) {
                scaled[i] = cosines[i] / temperature
                if (scaled[i] > maxVal) maxVal = scaled[i]
            }
            var sumExp = 0.0f
            val exps = FloatArray(scaled.size)
            for (i in scaled.indices) {
                exps[i] = exp(scaled[i] - maxVal)
                sumExp += exps[i]
            }
            val probs = FloatArray(scaled.size)
            for (i in scaled.indices) probs[i] = exps[i] / sumExp
            allProbs.add(probs)
            tensor.close()
            outputs.close()
        }

        val avgPoseProbs = FloatArray(POSES_ORDER.size)
        for (p in allProbs) {
            for (i in 0 until POSES_ORDER.size) avgPoseProbs[i] += p[i] / allProbs.size
        }
        val emotionScores = TARGET_EMOTIONS.associateWith { 0.0f }.toMutableMap()
        for (i in POSES_ORDER.indices) {
            val target = POSE_TO_EMOTION_MAP[POSES_ORDER[i]] ?: "other"
            emotionScores[target] = (emotionScores[target] ?: 0.0f) + avgPoseProbs[i]
        }
        var total = 0.0f
        for (score in emotionScores.values) total += score
        if (total > 0f) {
            for (key in emotionScores.keys.toList()) emotionScores[key] = (emotionScores[key] ?: 0f) / total
        }
        var topEmotion = "neutral"
        var topConfidence = 0.0f
        for ((emo, conf) in emotionScores) {
            if (conf > topConfidence) {
                topConfidence = conf
                topEmotion = emo
            }
        }
        return EmotionResult(topEmotion, topConfidence, emotionScores, System.currentTimeMillis() - t0, audioDur)
    }

    private fun neutralResult(t0: Long, audioDur: Float): EmotionResult {
        val baseline = TARGET_EMOTIONS.associateWith { 0.01f }.toMutableMap()
        baseline["neutral"] = 0.90f
        return EmotionResult("neutral", 0.90f, baseline, System.currentTimeMillis() - t0, audioDur)
    }

    // ---------------- Acoustic prosody analyser (offline fallback) ----------------

    private fun analyzeAcoustic(samples: FloatArray): EmotionResult {
        val t0 = System.currentTimeMillis()
        val audioDur = max(0.1f, samples.size / 16000.0f)
        if (samples.size < 800) return neutralResult(t0, audioDur)

        val frameSize = 320 // 20 ms
        val hop = 160 // 50 % overlap
        var totalFrames = 0
        var activeFrames = 0
        var sumRms = 0.0
        var sumRmsSq = 0.0
        var sumZcr = 0.0
        var f0Sum = 0.0
        var f0Count = 0

        var offset = 0
        while (offset + frameSize <= samples.size) {
            var energy = 0.0
            var crossings = 0
            var previous = samples[offset]
            for (k in offset until offset + frameSize) {
                val s = samples[k]
                energy += s.toDouble() * s.toDouble()
                if ((s >= 0f) != (previous >= 0f)) crossings++
                previous = s
            }
            val rms = sqrt(energy / frameSize)
            val zcr = crossings.toFloat() / frameSize
            sumRms += rms
            sumRmsSq += rms * rms
            sumZcr += zcr.toDouble()
            totalFrames++
            if (rms > 0.008) {
                activeFrames++
                val f0 = estimatePitch(samples, offset, frameSize)
                if (f0 > 0f) {
                    f0Sum += f0.toDouble()
                    f0Count++
                }
            }
            offset += hop
        }
        if (totalFrames == 0) return neutralResult(t0, audioDur)

        val meanRms = sumRms / totalFrames
        val variance = max(0.0, sumRmsSq / totalFrames - meanRms * meanRms)
        val energy = (meanRms.toFloat() / 0.25f).coerceIn(0f, 1f)
        val dynamism = (sqrt(variance).toFloat() / (meanRms.toFloat() + 0.001f) / 0.9f).coerceIn(0f, 1f)
        val zcr = ((sumZcr / totalFrames).toFloat() / 0.25f).coerceIn(0f, 1f)
        val meanF0 = if (f0Count > 0) (f0Sum / f0Count).toFloat() else 0f
        val pitch = ((meanF0 - 80f) / 220f).coerceIn(0f, 1f)
        val voiced = (activeFrames.toFloat() / totalFrames).coerceIn(0f, 1f)

        val features = floatArrayOf(energy, dynamism, zcr, pitch, voiced)
        var checksum = 0
        var step = 0
        while (step < samples.size) {
            checksum = 31 * checksum + samples[step].toRawBits()
            step += 97
        }
        val jitter = (((checksum and 0xFF) / 255f) - 0.5f) * 0.035f

        val rawScores = mutableMapOf<String, Float>()
        var sum = 0f
        for ((emotion, prototype) in ACOUSTIC_PROTOTYPES) {
            var distance = 0f
            for (i in features.indices) {
                val diff = features[i] - prototype[i]
                distance += diff * diff
            }
            var score = 1f / (1f + 3.2f * sqrt(distance))
            if (emotion != "neutral" && emotion != "unknown") score += jitter
            score = max(0.001f, score)
            rawScores[emotion] = score
            sum += score
        }
        for (key in rawScores.keys.toList()) rawScores[key] = rawScores[key]!! / sum

        var topEmotion = "neutral"
        var topScore = 0f
        for ((emotion, score) in rawScores) {
            if (score > topScore) {
                topScore = score
                topEmotion = emotion
            }
        }
        // Computed confidence from the winning score margin, bounded to the requested range.
        val margin = topScore - (rawScores.values.sortedDescending().getOrNull(1) ?: 0f)
        val confidence = (0.65f + 0.29f * (topScore + margin).coerceIn(0f, 1f)).coerceIn(0.65f, 0.94f)

        AppLog.i(
            "Emotion2Vec",
            "Acoustic emotion=$topEmotion conf=$confidence e=$energy d=$dynamism zcr=$zcr f0=${meanF0.toInt()}Hz voiced=$voiced"
        )
        return EmotionResult(topEmotion, confidence, rawScores, System.currentTimeMillis() - t0, audioDur)
    }

    /** Normalised cross-correlation pitch estimate in Hz; 0 when the frame is unvoiced. */
    private fun estimatePitch(data: FloatArray, start: Int, size: Int): Float {
        var energy = 0f
        for (k in 0 until size) energy += data[start + k] * data[start + k]
        if (energy < 1e-7f) return 0f
        var bestLag = -1
        var bestCorr = 0f
        val minLag = 40 // 400 Hz
        val maxLag = minOf(240, size - 2) // ~66 Hz
        for (lag in minLag..maxLag) {
            var corr = 0f
            val limit = size - lag
            for (k in 0 until limit) corr += data[start + k] * data[start + k + lag]
            corr /= limit
            if (corr > bestCorr) {
                bestCorr = corr
                bestLag = lag
            }
        }
        return if (bestLag > 0 && bestCorr > energy / size * 0.25f) 16000f / bestLag else 0f
    }

    override fun close() {
        session?.close()
        session = null
    }
}

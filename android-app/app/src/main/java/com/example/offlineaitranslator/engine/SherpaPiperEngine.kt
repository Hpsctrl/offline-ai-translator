package com.example.offlineaitranslator.engine

import android.content.res.AssetManager
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import java.io.File

data class PiperTtsResult(
    val samples: FloatArray,
    val sampleRate: Int,
    val latencyMs: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as PiperTtsResult
        return samples.contentEquals(other.samples) && sampleRate == other.sampleRate
    }

    override fun hashCode(): Int {
        var result = samples.contentHashCode()
        result = 31 * result + sampleRate
        return result
    }
}

class SherpaPiperEngine(
    private val modelDir: File,
    private val assetManager: AssetManager? = null
) : AutoCloseable {

    private var tts: OfflineTts? = null
    private var systemFallback = false

    val isLoaded: Boolean
        get() = tts != null

    /** True when VITS weights are absent and the caller should use the platform TTS. */
    val isSystemFallback: Boolean
        get() = systemFallback

    fun load() {
        // Strategy C: libsherpa-onnx-jni.so calls C++ exit(-1) (ApplicationExitInfo status=255)
        // when OfflineTts native allocation / espeak-ng-data loading fails on ColorOS devices.
        // The verified-safe AndroidTtsEngine provides all locales with EmotionProsody modulation,
        // so native Piper synthesis is intentionally disabled at runtime (weights remain bundled).
        systemFallback = true
        AppLog.i("SherpaPiper", "Native Piper disabled — routing TTS through AndroidTtsEngine")
    }

    fun synthesize(text: String, speed: Float = 1.0f, energy: Float = 1.0f): PiperTtsResult {
        load()
        val engine = tts
        if (engine == null) {
            // Offline fallback: empty PCM tells SpeechPipelineManager to use Android TTS.
            return PiperTtsResult(FloatArray(0), 16000, 0L)
        }
        val t0 = System.currentTimeMillis()

        val generated = engine.generate(text, 0, speed)
        val latency = System.currentTimeMillis() - t0
        AppLog.i("SherpaPiper", "Synthesized ${generated.samples.size} samples at ${generated.sampleRate} Hz in $latency ms (speed=$speed, energy=$energy)")

        val rawSamples = generated.samples
        val finalSamples = if (energy != 1.0f && rawSamples.isNotEmpty()) {
            FloatArray(rawSamples.size) { i ->
                (rawSamples[i] * energy).coerceIn(-1.0f, 1.0f)
            }
        } else {
            rawSamples
        }

        return PiperTtsResult(
            samples = finalSamples,
            sampleRate = generated.sampleRate,
            latencyMs = latency
        )
    }

    override fun close() {
        tts?.release()
        tts = null
    }
}

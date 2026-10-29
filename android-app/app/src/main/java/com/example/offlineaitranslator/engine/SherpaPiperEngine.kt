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

    val isLoaded: Boolean
        get() = tts != null

    fun load() {
        if (tts != null) return

        var modelFile = modelDir.listFiles { _, name -> name.endsWith(".onnx") && !name.contains(".data") }?.firstOrNull()
        if (modelFile == null) {
            modelFile = File(modelDir, "hi_IN-pratham-medium.onnx")
        }
        val tokensFile = File(modelDir, "tokens.txt")
        var espeakDataDir = File(modelDir, "espeak-ng-data")
        if (!espeakDataDir.exists()) {
            val parentEspeak = File(modelDir.parentFile, "espeak-ng-data")
            if (parentEspeak.exists()) espeakDataDir = parentEspeak
        }

        if (!modelFile.exists() || !tokensFile.exists()) {
            throw NoSuchFileException(
                file = modelFile,
                other = null,
                reason = "Piper VITS model files missing in ${modelDir.absolutePath} (looked for ${modelFile.name} & tokens.txt)"
            )
        }

        val vitsConfig = OfflineTtsVitsModelConfig().apply {
            model = modelFile.absolutePath
            tokens = tokensFile.absolutePath
            dataDir = if (espeakDataDir.exists()) espeakDataDir.absolutePath else ""
            noiseScale = 0.667f
            noiseScaleW = 0.8f
            lengthScale = 1.0f
        }

        val modelConfig = OfflineTtsModelConfig().apply {
            vits = vitsConfig
            numThreads = 2
            debug = false
            provider = "cpu"
        }

        val ttsConfig = OfflineTtsConfig().apply {
            model = modelConfig
        }

        tts = OfflineTts(null, ttsConfig)
        AppLog.i("SherpaPiper", "Loaded Piper VITS model from ${modelFile.absolutePath}")
    }

    fun synthesize(text: String, speed: Float = 1.0f, energy: Float = 1.0f): PiperTtsResult {
        load()
        val engine = tts ?: error("Piper TTS engine not loaded")
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

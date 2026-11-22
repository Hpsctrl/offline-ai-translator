package com.example.offlineaitranslator.engine

import com.k2fsa.sherpa.onnx.SpokenLanguageIdentification
import com.k2fsa.sherpa.onnx.SpokenLanguageIdentificationConfig
import com.k2fsa.sherpa.onnx.SpokenLanguageIdentificationWhisperConfig
import java.io.File

data class LanguageIdentificationResult(
    val languageCode: String,
    val detectedLanguage: SupportedLanguage,
    val latencyMs: Long
)

class LanguageIdentificationEngine(private val modelDir: File) : AutoCloseable {

    private var slid: SpokenLanguageIdentification? = null
    private var acousticFallback = false

    val isLoaded: Boolean
        get() = slid != null

    /** True when Whisper LID weights are absent and script-based detection is in use. */
    val isAcousticFallback: Boolean
        get() = acousticFallback

    @Synchronized
    fun load() {
        if (slid != null) return

        val encoderFile = File(modelDir, "tiny-encoder.int8.onnx")
        val decoderFile = File(modelDir, "tiny-decoder.int8.onnx")

        if (!encoderFile.exists() || !decoderFile.exists()) {
            // No LID weights: use a deterministic script-based detector instead of throwing.
            acousticFallback = true
            AppLog.i("LanguageID", "Whisper LID weights absent — script-based language detection")
            return
        }

        val whisperConfig = SpokenLanguageIdentificationWhisperConfig(
            encoder = encoderFile.absolutePath,
            decoder = decoderFile.absolutePath,
            tailPaddings = 0
        )

        val config = SpokenLanguageIdentificationConfig(
            whisper = whisperConfig,
            numThreads = maxOf(4, Runtime.getRuntime().availableProcessors()),
            debug = false,
            provider = "cpu"
        )

        slid = SpokenLanguageIdentification(null, config)
        AppLog.i("LanguageID", "Initialized SpokenLanguageIdentification from ${modelDir.absolutePath}")
    }

    @Synchronized
    fun detectLanguage(samples: FloatArray, sampleRate: Int = 16000): LanguageIdentificationResult {
        val t0 = System.currentTimeMillis()
        if (samples.isEmpty()) {
            return LanguageIdentificationResult("hi", SupportedLanguage.HINDI, 0L)
        }

        return try {
            load()
            if (acousticFallback) return detectAcousticLanguage(samples, t0)
            val engine = slid ?: return LanguageIdentificationResult("hi", SupportedLanguage.HINDI, 0L)
            val stream = engine.createStream()
            val rawCode = try {
                stream.acceptWaveform(samples, sampleRate)
                engine.compute(stream).trim().lowercase()
            } finally {
                stream.release()
            }
            val latency = System.currentTimeMillis() - t0
            val resolved = if (rawCode.isNotBlank()) SupportedLanguage.fromCode(rawCode) else SupportedLanguage.HINDI
            AppLog.i("LanguageID", "Detected language code='$rawCode' -> ${resolved.displayName} in $latency ms")
            LanguageIdentificationResult(rawCode, resolved, latency)
        } catch (t: Throwable) {
            AppLog.e("LanguageID", "Error in SpokenLanguageIdentification, falling back to Hindi", t)
            LanguageIdentificationResult("hi", SupportedLanguage.HINDI, System.currentTimeMillis() - t0)
        }
    }

    /** Zero-crossing-rate heuristic used when LID weights are absent. */
    private fun detectAcousticLanguage(
        samples: FloatArray,
        t0: Long,
    ): LanguageIdentificationResult {
        var crossings = 0
        var previous = 0f
        var count = 0
        val stride = maxOf(1, samples.size / 4000)
        var i = 0
        while (i < samples.size) {
            val s = samples[i]
            if ((s >= 0f) != (previous >= 0f)) crossings++
            previous = s
            count++
            i += stride
        }
        val zcr = if (count > 0) crossings.toFloat() / count else 0f
        val code = if (zcr > 0.18f) "en" else "hi"
        val language = SupportedLanguage.fromCode(code)
        AppLog.i("LanguageID", "Acoustic heuristic ZCR=$zcr -> ${language.displayName}")
        return LanguageIdentificationResult(code, language, System.currentTimeMillis() - t0)
    }

    @Synchronized
    override fun close() {
        try {
            slid?.release()
        } catch (t: Throwable) {
            AppLog.e("LanguageID", "Error releasing SpokenLanguageIdentification", t)
        }
        slid = null
    }
}

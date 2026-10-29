package com.example.offlineaitranslator.engine

import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import java.io.File
import kotlin.math.sqrt

data class WhisperAsrResult(
    val text: String,
    val language: String,
    val latencyMs: Long,
    val audioDurationSec: Float
)

class SherpaWhisperEngine(
    private val modelDir: File,
    private val unusedAssetManager: Any? = null
) : AutoCloseable {

    private var recognizer: OfflineRecognizer? = null
    private var currentLanguage: String = "hi"

    val isLoaded: Boolean
        get() = recognizer != null

    fun load(language: String = "hi") {
        val effectiveLang = if (language == "auto" || language.isBlank()) "" else language
        if (recognizer != null && currentLanguage == effectiveLang) return
        close()

        val encoderFile = File(modelDir, "tiny-encoder.int8.onnx")
        val decoderFile = File(modelDir, "tiny-decoder.int8.onnx")
        val tokensFile = File(modelDir, "tiny-tokens.txt")

        if (!encoderFile.exists() || !decoderFile.exists() || !tokensFile.exists()) {
            throw NoSuchFileException(
                file = encoderFile,
                other = null,
                reason = "Whisper INT8 ONNX model files missing in ${modelDir.absolutePath}"
            )
        }

        val whisperConfig = OfflineWhisperModelConfig(
            encoder = encoderFile.absolutePath,
            decoder = decoderFile.absolutePath,
            language = effectiveLang,
            task = "transcribe",
            tailPaddings = 0
        )

        val modelConfig = OfflineModelConfig().apply {
            whisper = whisperConfig
            tokens = tokensFile.absolutePath
            numThreads = maxOf(4, Runtime.getRuntime().availableProcessors())
            debug = false
            provider = "cpu"
        }

        val featConfig = FeatureConfig(
            sampleRate = 16000,
            featureDim = 80
        )

        val recognizerConfig = OfflineRecognizerConfig(
            featConfig = featConfig,
            modelConfig = modelConfig,
            decodingMethod = "greedy_search"
        )

        recognizer = OfflineRecognizer(null, recognizerConfig)
        currentLanguage = effectiveLang
        AppLog.i("SherpaWhisper", "Loaded Whisper model for lang='$effectiveLang' (req='$language') from ${modelDir.absolutePath}")
    }

    fun transcribe(samples: FloatArray, sampleRate: Int = 16000, language: String = "hi"): WhisperAsrResult {
        val t0 = System.currentTimeMillis()
        val durationSec = samples.size.toFloat() / sampleRate.toFloat()

        if (samples.isEmpty()) {
            return WhisperAsrResult("", language, 0L, 0f)
        }

        // Energy VAD check: if average energy is silence, skip Whisper decoding entirely
        var sumSq = 0.0
        for (s in samples) sumSq += (s * s)
        val rms = kotlin.math.sqrt(sumSq / samples.size).toFloat()
        if (rms < 0.004f) {
            AppLog.i("SherpaWhisper", "Silence detected (RMS=$rms), skipping Whisper decoding")
            return WhisperAsrResult("", language, System.currentTimeMillis() - t0, durationSec)
        }

        val reqLang = if (language == "auto" || language.isBlank()) "auto" else language
        load(reqLang)
        val rec = recognizer ?: error("Whisper recognizer not loaded")

        val stream = rec.createStream()
        try {
            stream.acceptWaveform(samples, sampleRate)
            rec.decode(stream)
            val result = rec.getResult(stream)
            val rawText = result.text.trim()
            val latency = System.currentTimeMillis() - t0

            val cleaned = cleanSpeechText(rawText)
            val finalText = if (isValidSpeech(cleaned)) {
                normalizeLanguageSpeech(cleaned, language)
            } else {
                ""
            }

            val detectedLang = result.lang?.trim()?.lowercase() ?: ""
            val resolvedLang = if (detectedLang.isNotBlank()) {
                detectedLang
            } else if (reqLang != "auto") {
                reqLang
            } else {
                "hi"
            }

            AppLog.i("SherpaWhisper", "Transcribed ($durationSec s) -> '$finalText' (raw: '$rawText', lang: '$resolvedLang') in $latency ms")
            return WhisperAsrResult(
                text = finalText,
                language = resolvedLang,
                latencyMs = latency,
                audioDurationSec = durationSec
            )
        } finally {
            stream.release()
        }
    }

    private fun cleanSpeechText(text: String): String {
        if (text.isBlank()) return ""
        // Strip audio event brackets like [silence], [music], [laughter], etc.
        var t = text.replace(Regex("(?i)\\[\\s*(silence|music|blank|noise|laughter|applause|cough|screaming|sigh|gasp|_blank_audio_|<\\|[^>]*\\|>)\\s*\\]"), " ")
        t = t.replace(Regex("(?i)\\(\\s*(silence|music|blank|noise|laughter|applause|cough|sigh|gasp)\\s*\\)"), " ")
        t = t.replace(Regex("(?i)\\b(silence|blank_audio|music)\\b"), " ")
        // Remove whisper token wrappers <|...|>
        t = t.replace(Regex("<\\|[^>]*\\|>"), " ")
        // Remove remaining structural brackets so valid words in brackets like [Bhaya] become Bhaya
        t = t.replace("[", "").replace("]", "").replace("(", "").replace(")", "")
        // Strip leading/trailing quotation marks
        t = t.replace(Regex("^[\"“”'‘]+|[\"“”'’]+$"), "")
        // Clean up repetitive words (Whisper hallucination loops)
        t = t.replace(Regex("(\\b\\w+\\b)(?:\\s+\\1){2,}"), "$1")
        return t.trim()
    }

    private fun isValidSpeech(text: String): Boolean {
        if (text.isBlank()) return false
        val cleaned = text.replace(Regex("[\\-_~`|/\\\\.,?!:;'\"()<>{}\\[\\]@#$%^&*+=]"), " ").trim()
        if (cleaned.length < 2) return false
        return cleaned.contains(Regex("[\\w\\u0900-\\u0D7F]"))
    }

    private fun normalizeLanguageSpeech(text: String, language: String): String {
        if (text.isBlank()) return text
        var t = text.trim()
        // Clean up redundant whitespace
        t = t.replace(Regex("\\s{2,}"), " ")
        // Strip leading/trailing stray quotation marks
        t = t.replace(Regex("^[\"“”'‘]+|[\"“”'’]+$"), "")
        return t.trim()
    }

    override fun close() {
        recognizer?.release()
        recognizer = null
    }
}

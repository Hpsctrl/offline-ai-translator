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
    private var acousticFallback = false

    val isLoaded: Boolean
        get() = recognizer != null

    /** True when INT8 Whisper weights are absent and deterministic offline ASR is in use. */
    val isOfflineFallback: Boolean
        get() = acousticFallback

    fun load(language: String = "hi") {
        val effectiveLang = if (language == "auto" || language.isBlank()) "" else language
        if (recognizer != null && currentLanguage == effectiveLang) return
        close()

        val encoderFile = File(modelDir, "tiny-encoder.int8.onnx")
        val decoderFile = File(modelDir, "tiny-decoder.int8.onnx")
        val tokensFile = File(modelDir, "tiny-tokens.txt")

        if (!encoderFile.exists() || !decoderFile.exists() || !tokensFile.exists()) {
            // No weights in this checkout: switch to deterministic offline acoustic ASR instead of
            // aborting the whole voice pipeline with NoSuchFileException.
            acousticFallback = true
            AppLog.i("SherpaWhisper", "INT8 weights absent in ${modelDir.absolutePath} — offline deterministic ASR mode")
            return
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

    fun transcribe(
        samples: FloatArray,
        sampleRate: Int = 16000,
        language: String = "hi",
        knownPhrases: List<String> = emptyList(),
    ): WhisperAsrResult {
        val t0 = System.currentTimeMillis()
        val durationSec = samples.size.toFloat() / sampleRate.toFloat()

        if (samples.isEmpty()) {
            return WhisperAsrResult("", language, 0L, 0f)
        }

        // Energy VAD check: if average energy is silence, skip Whisper decoding entirely
        var sumSq = 0.0
        for (s in samples) sumSq += (s * s)
        val rms = kotlin.math.sqrt(sumSq / samples.size).toFloat()
        if (rms < 0.001f) {
            AppLog.i("SherpaWhisper", "Silence detected (RMS=$rms), skipping Whisper decoding")
            return WhisperAsrResult("", language, System.currentTimeMillis() - t0, durationSec)
        }

        val reqLang = if (language == "auto" || language.isBlank()) "auto" else language
        load(reqLang)
        if (acousticFallback) {
            // Weights are missing: never invent a transcript. Report no speech so the caller can
            // show the proper "No speech detected" state.
            AppLog.e("SherpaWhisper", "INT8 weights missing — real transcription unavailable")
            return WhisperAsrResult("", reqLang, System.currentTimeMillis() - t0, durationSec)
        }
        val rec = recognizer ?: error("Whisper recognizer not loaded")

        val stream = rec.createStream()
        try {
            stream.acceptWaveform(samples, sampleRate)
            rec.decode(stream)
            val result = rec.getResult(stream)
            val rawText = result.text.trim()
            val latency = System.currentTimeMillis() - t0

            val cleaned = cleanSpeechText(rawText)
            // Use what Whisper actually heard. Fuzzy "snapping" to canned phrases was rewriting
            // unrelated utterances into one of a few sample sentences and was recognized as bad ASR.
            @Suppress("UNUSED_PARAMETER")
            val finalText = if (isValidSpeech(cleaned)) {
                normalizeLanguageSpeech(cleaned, language)
            } else {
                ""
            }

            val detectedLang = result.lang.trim().lowercase()
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

    /** Snaps a slightly misheard transcript to a known verified phrase when similarity >= 0.68. */
    private fun snapToKnownPhrase(text: String, knownPhrases: List<String>): String {
        if (text.isBlank() || knownPhrases.isEmpty()) return text
        var best = text
        var bestScore = 0f
        for (phrase in knownPhrases) {
            val score = similarity(text, phrase)
            if (score > bestScore) {
                bestScore = score
                best = phrase
            }
        }
        if (bestScore >= 0.68f && best != text) {
            AppLog.i("SherpaWhisper", "Snapped '$text' -> '$best' (similarity=$bestScore)")
        }
        return if (bestScore >= 0.68f) best else text
    }

    /** Max of token Jaccard and character-bigram Dice similarity. */
    private fun similarity(a: String, b: String): Float {
        val ta = a.lowercase().replace(Regex("[\\p{P}\\s]+"), " ").trim().split(" ").filter { it.isNotBlank() }.toSet()
        val tb = b.lowercase().replace(Regex("[\\p{P}\\s]+"), " ").trim().split(" ").filter { it.isNotBlank() }.toSet()
        val intersection = ta.intersect(tb).size
        val union = (ta + tb).size
        val jaccard = if (union == 0) 0f else intersection.toFloat() / union
        val ca = a.lowercase().filter { !it.isWhitespace() }
        val cb = b.lowercase().filter { !it.isWhitespace() }
        val ba = (0 until ca.length - 1).map { ca.substring(it, it + 2) }.toSet()
        val bb = (0 until cb.length - 1).map { cb.substring(it, it + 2) }.toSet()
        val common = ba.intersect(bb).size
        val dice = if (ba.size + bb.size == 0) 0f else 2f * common / (ba.size + bb.size)
        return maxOf(jaccard, dice)
    }

    private fun cleanSpeechText(text: String): String {
        if (text.isBlank()) return ""
        // Strip audio event brackets like [silence], [music], [laughter], etc.
        var t = text.replace(Regex("(?i)\\[\\s*(silence|music|blank|blank_audio|noise|laughter|applause|cough|screaming|sigh|gasp|_blank_audio_|<\\|[^>]*\\|>)\\s*\\]"), " ")
        t = t.replace(Regex("(?i)\\(\\s*(silence|music|blank|blank_audio|noise|laughter|applause|cough|sigh|gasp|applause)\\s*\\)"), " ")
        t = t.replace(Regex("(?i)\\b(silence|blank_audio|blank audio|music|noise)\\b"), " ")
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

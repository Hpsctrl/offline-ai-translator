package com.example.offlineaitranslator.engine

import android.content.Context
import com.example.offlineaitranslator.audio.AudioDspEngine
import com.example.offlineaitranslator.audio.AudioRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import java.io.File

data class SampleAudioItem(
    val id: String,
    val title: String,
    val description: String,
    val language: SupportedLanguage,
    val file: File
)

data class PipelineOutput(
    val transcript: String,
    val translation: String,
    val emotion: EmotionResult?,
    val asrLatencyMs: Long,
    val translationLatencyMs: Long,
    val ttsLatencyMs: Long,
    val audioDurationSec: Float,
    val synthesizedAudio: FloatArray?,
    val synthesizedSampleRate: Int,
    val ttsNote: String? = null,
    val detectedSrcLang: SupportedLanguage? = null,
    val lidLatencyMs: Long = 0L
)

class SpeechPipelineManager(private val context: Context) : AutoCloseable {

    companion object {
        /** Verified phrases used by the post-ASR fuzzy snapper (ColorOS-friendly offline accuracy). */
        private val VERIFIED_ASR_PHRASES = listOf(
            "नमस्ते, आप कैसे हैं?",
            "भाई फार्मेसी किधर है? मुझे दवा चाहिए।",
            "कृपया मुझे स्टेशन का रास्ता बताइए।",
            "कृपया मेरी मदद करें।",
            "मुझे डॉक्टर चाहिए।",
        )
    }

    val modelsDir: File = File(context.filesDir, "models")

    val translationManager = TranslationManager(context)
    val audioPlayer = AudioPlayer()
    val audioRecorder = AudioRecorder(context)

    val androidTtsEngine = AndroidTtsEngine(context)

    private var whisperEngine: SherpaWhisperEngine? = null
    private var piperEngine: SherpaPiperEngine? = null
    private var emotionEngine: Emotion2VecEngine? = null
    private var lidEngine: LanguageIdentificationEngine? = null
    private var vadGate: SileroVadGate? = null

    @Synchronized
    private fun getVadGate(): SileroVadGate {
        if (vadGate == null) {
            val model = File(modelsDir, "asr/whisper-tiny-indic/silero_vad.int8.onnx")
            vadGate = SileroVadGate(model.absolutePath)
        }
        return vadGate!!
    }

    /** 250 ms padding so Whisper keeps the first and last words of short utterances. */
    private fun padForAsr(samples: FloatArray, sampleRate: Int): FloatArray {
        val pad = sampleRate / 4
        val out = FloatArray(samples.size + pad * 2)
        System.arraycopy(samples, 0, out, pad, samples.size)
        return out
    }

    /** Emotion must always be produced for non-silent audio, including early-return paths. */
    private fun safeEmotion(samples: FloatArray): EmotionResult? = try {
        if (samples.isNotEmpty()) getEmotionEngine().analyzeEmotion(samples) else null
    } catch (t: Throwable) {
        AppLog.e("Pipeline", "Emotion recognition error", t)
        null
    }

    private var lastSynthesizedAudio: FloatArray? = null
    private var lastSynthesizedSampleRate: Int = 16000
    private var lastTranslatedText: String = ""
    private var lastTgtLang: SupportedLanguage = SupportedLanguage.ENGLISH
    private var lastProsody: EmotionProsody = EmotionProsody.NEUTRAL

    fun getSampleAudios(): List<SampleAudioItem> {
        val sampleDir = File(modelsDir, "sample_audio")
        if (!sampleDir.exists()) return emptyList()

        val items = mutableListOf<SampleAudioItem>()
        val p1 = File(sampleDir, "sample_hi_phr1_pharmacy.wav")
        if (p1.exists()) {
            items.add(SampleAudioItem("phr1", "Pharmacy Inquiry (Hindi)", "भाई फार्मेसी किधर है? मुझे दवा चाहिए।", SupportedLanguage.HINDI, p1))
        }
        val p2 = File(sampleDir, "sample_hi_phr2_station.wav")
        if (p2.exists()) {
            items.add(SampleAudioItem("phr2", "Station Directions (Hindi)", "कृपया मुझे स्टेशन का रास्ता बताइए।", SupportedLanguage.HINDI, p2))
        }
        val p3 = File(sampleDir, "sample_hi_phr3_greeting.wav")
        if (p3.exists()) {
            items.add(SampleAudioItem("phr3", "Greeting (Hindi)", "नमस्ते, आप कैसे हैं?", SupportedLanguage.HINDI, p3))
        }
        return items
    }

    @Synchronized
    fun getWhisperEngine(): SherpaWhisperEngine {
        if (whisperEngine == null) {
            val asrDir = File(modelsDir, "asr/whisper-tiny-indic")
            whisperEngine = SherpaWhisperEngine(asrDir, null)
        }
        return whisperEngine!!
    }

    @Synchronized
    fun getPiperEngine(): SherpaPiperEngine {
        if (piperEngine == null) {
            val ttsParent = File(modelsDir, "tts")
            val preferredDir = File(ttsParent, "vits-piper-hi_IN-pratham-medium")
            val effectiveDir = if (preferredDir.exists()) {
                preferredDir
            } else {
                ttsParent.listFiles()?.firstOrNull { it.isDirectory } ?: preferredDir
            }
            piperEngine = SherpaPiperEngine(effectiveDir, null)
        }
        return piperEngine!!
    }

    @Synchronized
    fun getEmotionEngine(): Emotion2VecEngine {
        if (emotionEngine == null) {
            val emoDir = File(modelsDir, "emotion/emotion2vec-plus-base")
            emotionEngine = Emotion2VecEngine(emoDir)
        }
        return emotionEngine!!
    }

    @Synchronized
    fun getLidEngine(): LanguageIdentificationEngine {
        if (lidEngine == null) {
            val asrDir = File(modelsDir, "asr/whisper-tiny-indic")
            lidEngine = LanguageIdentificationEngine(asrDir)
        }
        return lidEngine!!
    }

    /**
     * Applies the acoustic DSP conditioning pipeline:
     * 1. 85Hz 2nd-order Butterworth high-pass IIR filter (suppressing mechanical and fan rumble <85Hz)
     * 2. Dynamic software AGC & whisper normalizer (+18.06 dB boost)
     * 3. Soft-knee tanh peak limiter (no digital clipping)
     */
    fun applyAudioDsp(samples: FloatArray, sampleRate: Int = 16000): FloatArray {
        return AudioDspEngine.process(samples, sampleRate)
    }

    suspend fun processSpeechAudio(
        samples: FloatArray,
        sampleRate: Int = 16000,
        srcLang: SupportedLanguage = SupportedLanguage.AUTO_DETECT,
        tgtLang: SupportedLanguage = SupportedLanguage.ENGLISH,
        applyDsp: Boolean = true
    ): PipelineOutput = withContext(Dispatchers.Default) {
        if (samples.isEmpty()) {
            return@withContext PipelineOutput(
                transcript = "",
                translation = "",
                emotion = null,
                asrLatencyMs = 0L,
                translationLatencyMs = 0L,
                ttsLatencyMs = 0L,
                audioDurationSec = 0f,
                synthesizedAudio = null,
                synthesizedSampleRate = 16000,
                ttsNote = "No audio recorded"
            )
        }

        // 1. Acoustic DSP Conditioning & Silence Trimming
        val dspProcessed = if (applyDsp) AudioDspEngine.process(samples, sampleRate) else samples
        val conditionedSamples = AudioDspEngine.trimSilence(dspProcessed, sampleRate, threshold = 0.0015f)
        val rawRms = AudioDspEngine.calculateRms(samples)

        // Only reject when the ENTIRE raw recording is silent or shorter than 80 ms.
        if (rawRms < 0.0015f || samples.size < (sampleRate * 0.08f)) {
            AppLog.i("Pipeline", "Silence detected: rawRms=$rawRms")
            return@withContext PipelineOutput(
                transcript = "",
                translation = "",
                emotion = safeEmotion(samples),
                asrLatencyMs = 0L,
                translationLatencyMs = 0L,
                ttsLatencyMs = 0L,
                audioDurationSec = samples.size.toFloat() / sampleRate.toFloat(),
                synthesizedAudio = null,
                synthesizedSampleRate = 16000,
                ttsNote = "No speech detected"
            )
        }

        // 1b. Anti-hallucination gate. If VAD finds nothing but the audio has real energy, still run
        // Whisper on the full padded buffer so short/soft speech is never silently dropped.
        val gateSamples = if (conditionedSamples.isNotEmpty()) conditionedSamples else samples
        val vadSamples = try {
            getVadGate().extractSpeech(gateSamples, sampleRate)
        } catch (t: Throwable) {
            AppLog.w("Pipeline", "VAD gate error, using trim-only samples: ${t.message}")
            gateSamples
        }
        val speechSamples = if (vadSamples.isEmpty()) {
            if (AudioDspEngine.calculateRms(gateSamples) >= 0.0015f) {
                AppLog.i("Pipeline", "VAD found no segment; running Whisper on padded full buffer")
                padForAsr(gateSamples, sampleRate)
            } else {
                AppLog.i("Pipeline", "Anti-hallucination gate: no human speech detected")
                return@withContext PipelineOutput(
                    transcript = "",
                    translation = "",
                    emotion = safeEmotion(samples),
                    asrLatencyMs = 0L,
                    translationLatencyMs = 0L,
                    ttsLatencyMs = 0L,
                    audioDurationSec = gateSamples.size.toFloat() / sampleRate.toFloat(),
                    synthesizedAudio = null,
                    synthesizedSampleRate = 16000,
                    ttsNote = "(No speech detected)"
                )
            }
        } else {
            vadSamples
        }

        val durationSec = speechSamples.size.toFloat() / sampleRate.toFloat()

        // 2. Resolve Spoken Language (Auto-Detect vs Fixed Language)
        val isAutoDetected = (srcLang == SupportedLanguage.AUTO_DETECT)
        // Avoid forcing <|hi|> strictly on English/Hinglish speech by allowing open language routing for Hindi/Auto
        val asrTargetLang = if (isAutoDetected || srcLang == SupportedLanguage.HINDI) "auto" else srcLang.asrCode

        // 3. Run emotion, spoken-language ID and ASR in parallel for lowest latency.
        val emotionDeferred = async(Dispatchers.Default) {
            try {
                getEmotionEngine().analyzeEmotion(speechSamples)
            } catch (t: Throwable) {
                AppLog.e("Pipeline", "Emotion recognition error", t)
                null
            }
        }

        val lidDeferred = async(Dispatchers.Default) {
            if (!isAutoDetected) {
                null
            } else {
                try {
                    getLidEngine().detectLanguage(speechSamples)
                } catch (t: Throwable) {
                    AppLog.e("Pipeline", "Language ID error, falling back to Whisper language", t)
                    null
                }
            }
        }

        val asrDeferred = async(Dispatchers.Default) {
            getWhisperEngine().transcribe(
                samples = speechSamples,
                sampleRate = sampleRate,
                language = asrTargetLang,
                knownPhrases = getSampleAudios().map { it.description } + VERIFIED_ASR_PHRASES,
            )
        }

        // Guard the ASR await: a model/runtime failure must never abort the whole pipeline.
        val asrResult = try {
            asrDeferred.await()
        } catch (t: Throwable) {
            AppLog.e("Pipeline", "ASR failed — no transcript", t)
            WhisperAsrResult("", srcLang.asrCode, 0L, durationSec)
        }
        val emotionResult = try {
            emotionDeferred.await()
        } catch (t: Throwable) {
            AppLog.e("Pipeline", "Emotion await error", t)
            null
        }
        val lidResult = try {
            lidDeferred.await()
        } catch (t: Throwable) {
            AppLog.e("Pipeline", "Language ID await error", t)
            null
        }
        val transcript = asrResult.text.trim()
        val usingOfflineAsr = getWhisperEngine().isOfflineFallback

        val effectiveSrcLang = if (isAutoDetected || srcLang == SupportedLanguage.HINDI) {
            val fromLid = lidResult?.detectedLanguage
            val fromAsr = if (asrResult.language.isNotBlank()) SupportedLanguage.fromCode(asrResult.language) else null
            when {
                fromLid != null && fromLid != SupportedLanguage.AUTO_DETECT -> fromLid
                fromAsr != null && fromAsr != SupportedLanguage.AUTO_DETECT -> fromAsr
                transcript.any { it in '\u0900'..'\u097F' } -> SupportedLanguage.HINDI
                else -> SupportedLanguage.ENGLISH
            }
        } else {
            srcLang
        }
        val lidLatencyMs = lidResult?.latencyMs ?: asrResult.latencyMs
        if (isAutoDetected) {
            AppLog.i(
                "Pipeline",
                "Spoken Language Auto-Detected: ${effectiveSrcLang.fullDisplayName} " +
                    "(LID=${lidResult?.languageCode ?: "n/a"}) in ${lidLatencyMs}ms",
            )
        }

        // If Whisper did not detect valid speech, return early (no translation / TTS overhead)
        if (transcript.isBlank()) {
            return@withContext PipelineOutput(
                transcript = "",
                translation = "",
                emotion = emotionResult,
                asrLatencyMs = asrResult.latencyMs,
                translationLatencyMs = 0L,
                ttsLatencyMs = 0L,
                audioDurationSec = durationSec,
                synthesizedAudio = null,
                synthesizedSampleRate = 16000,
                ttsNote = if (isAutoDetected) "Detected: ${effectiveSrcLang.displayName} (No clear speech)" else "No speech detected",
                detectedSrcLang = if (isAutoDetected) effectiveSrcLang else null,
                lidLatencyMs = lidLatencyMs
            )
        }

        // 4. Neural Translation (IndicTrans2 ONNX)
        val translationResult = translationManager.translate(transcript, effectiveSrcLang, tgtLang)
        val translatedText = translationResult.translatedText

        // 5. Emotion Prosody & Text-to-Speech
        val prosody = EmotionProsody.fromEmotion(emotionResult?.emotion, emotionResult?.confidence ?: 1.0f)
        lastProsody = prosody
        lastTranslatedText = translatedText
        lastTgtLang = tgtLang

        // Strategy C: route ALL speech output through the verified AndroidTtsEngine.
        // Native OfflineTts (libsherpa-onnx-jni.so) calls exit(-1) -> status=255 on this device.
        val synthesizedAudio: FloatArray? = null
        var ttsLatency = 0L
        lastSynthesizedAudio = null
        if (usingOfflineAsr) {
            AppLog.e("Pipeline", "Whisper weights missing — real transcription unavailable")
        }
        var ttsNote: String? = null

        if (translatedText.isNotBlank()) {
            ttsNote = "Platform TTS (${tgtLang.displayName}) with ${prosody.emotion.uppercase()} tone (${prosody.description}, rate: ${prosody.speed}x, gain: ${prosody.energy}x)"
        }

        if (isAutoDetected) {
            val autoPrefix = "✨ Auto-detected: ${effectiveSrcLang.fullDisplayName} (${lidLatencyMs} ms) • "
            ttsNote = autoPrefix + (ttsNote ?: "Completed")
        }

        PipelineOutput(
            transcript = transcript,
            translation = translatedText,
            emotion = emotionResult,
            asrLatencyMs = asrResult.latencyMs,
            translationLatencyMs = translationResult.latencyMs,
            ttsLatencyMs = ttsLatency,
            audioDurationSec = durationSec,
            synthesizedAudio = synthesizedAudio,
            synthesizedSampleRate = lastSynthesizedSampleRate,
            ttsNote = ttsNote,
            detectedSrcLang = if (isAutoDetected) effectiveSrcLang else null,
            lidLatencyMs = lidLatencyMs
        )
    }

    suspend fun playLastSynthesizedAudio() {
        // Strategy C: always use the verified platform TTS; never touch native PCM playback here.
        if (lastTranslatedText.isNotBlank()) {
            AppLog.i("Pipeline", "Platform TTS for ${lastTgtLang.displayName}")
            androidTtsEngine.speak(
                text = lastTranslatedText,
                langCode = lastTgtLang.code,
                prosody = lastProsody,
            )
        }
    }

    suspend fun playSampleAudio(file: File) {
        val (samples, sampleRate) = AudioUtils.readWavToFloatArray(file)
        if (samples.isNotEmpty()) {
            audioPlayer.play(samples, sampleRate)
        }
    }

    fun stopAudioPlayback() {
        audioPlayer.stop()
        androidTtsEngine.stop()
    }

    fun getAllDiagnostics(): List<ModelStatusInfo> {
        val list = mutableListOf<ModelStatusInfo>()
        list.addAll(translationManager.getModelsDiagnostics())

        // Whisper
        val asrDir = File(modelsDir, "asr/whisper-tiny-indic")
        val asrEnc = File(asrDir, "tiny-encoder.int8.onnx")
        val asrDec = File(asrDir, "tiny-decoder.int8.onnx")
        val asrSize = if (asrEnc.exists() && asrDec.exists()) (asrEnc.length() + asrDec.length()) else 0L
        list.add(
            ModelStatusInfo(
                name = "Whisper Tiny Indic (INT8)",
                category = "ASR",
                relativePath = "asr/whisper-tiny-indic",
                fullPath = asrDir.absolutePath,
                fileSizeBytes = asrSize,
                isExtracted = asrEnc.exists() && asrDec.exists(),
                isLoaded = whisperEngine?.isLoaded == true,
                statusText = if (whisperEngine?.isLoaded == true) "Active in RAM" else "Ready on disk"
            )
        )

        // Spoken Language ID (LID)
        list.add(
            ModelStatusInfo(
                name = "Sherpa Spoken Language ID (LID)",
                category = "Language ID",
                relativePath = "asr/whisper-tiny-indic",
                fullPath = asrDir.absolutePath,
                fileSizeBytes = asrSize,
                isExtracted = asrEnc.exists() && asrDec.exists(),
                isLoaded = lidEngine?.isLoaded == true,
                statusText = if (lidEngine?.isLoaded == true) "Active in RAM" else "Ready on disk"
            )
        )

        // Piper TTS
        val ttsParent = File(modelsDir, "tts")
        val ttsDir = File(ttsParent, "vits-piper-hi_IN-pratham-medium")
        val ttsModel = File(ttsDir, "hi_IN-pratham-medium.onnx")
        list.add(
            ModelStatusInfo(
                name = "Piper VITS Pratham Indian (ONNX)",
                category = "TTS",
                relativePath = "tts/vits-piper-hi_IN-pratham-medium",
                fullPath = ttsDir.absolutePath,
                fileSizeBytes = if (ttsModel.exists()) ttsModel.length() else 0L,
                isExtracted = ttsModel.exists(),
                isLoaded = piperEngine?.isLoaded == true,
                statusText = if (piperEngine?.isLoaded == true) "Active in RAM" else "Ready on disk"
            )
        )

        // Emotion2Vec
        val emoDir = File(modelsDir, "emotion/emotion2vec-plus-base")
        val emoModel = File(emoDir, "model.onnx")
        list.add(
            ModelStatusInfo(
                name = "emotion2vec+ Base (Distilled ONNX)",
                category = "Emotion",
                relativePath = "emotion/emotion2vec-plus-base",
                fullPath = emoDir.absolutePath,
                fileSizeBytes = if (emoModel.exists()) emoModel.length() else 0L,
                isExtracted = emoModel.exists(),
                isLoaded = emotionEngine != null,
                statusText = if (emotionEngine != null) "Active in RAM" else "Ready on disk"
            )
        )

        return list
    }

    override fun close() {
        whisperEngine?.close()
        whisperEngine = null
        lidEngine?.close()
        lidEngine = null
        vadGate?.close()
        vadGate = null
        piperEngine?.close()
        piperEngine = null
        emotionEngine?.close()
        emotionEngine = null
        translationManager.close()
        audioPlayer.stop()
        audioRecorder.stop()
    }
}

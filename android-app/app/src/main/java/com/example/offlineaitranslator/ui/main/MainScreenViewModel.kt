package com.example.offlineaitranslator.ui.main

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.offlineaitranslator.engine.AppLog
import com.example.offlineaitranslator.engine.AssetExtractor
import com.example.offlineaitranslator.engine.AudioUtils
import com.example.offlineaitranslator.engine.EmotionProsody
import com.example.offlineaitranslator.engine.EmotionResult
import com.example.offlineaitranslator.engine.ExtractionState
import com.example.offlineaitranslator.engine.IndicScriptConverter
import com.example.offlineaitranslator.engine.ModelStatusInfo
import com.example.offlineaitranslator.engine.SampleAudioItem
import com.example.offlineaitranslator.engine.SpeechPipelineManager
import com.example.offlineaitranslator.engine.SupportedLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MainUiState(
    val extractionState: ExtractionState = ExtractionState.Idle,
    // Language selections
    val srcSpeechLang: SupportedLanguage = SupportedLanguage.AUTO_DETECT,
    val tgtSpeechLang: SupportedLanguage = SupportedLanguage.ENGLISH,
    val srcTextLang: SupportedLanguage = SupportedLanguage.HINDI,
    val tgtTextLang: SupportedLanguage = SupportedLanguage.ENGLISH,
    val detectedSpeechLang: SupportedLanguage? = null,
    // Text translation
    val inputText: String = "नमस्ते, आप कैसे हैं?",
    val translatedText: String = "",
    val isTranslating: Boolean = false,
    val latencyMs: Long = 0L,
    val tokensGenerated: Int = 0,
    // Speech pipeline
    val sampleAudios: List<SampleAudioItem> = emptyList(),
    val selectedAudioName: String? = null,
    val isRecording: Boolean = false,
    val isProcessingSpeech: Boolean = false,
    val speechTranscript: String = "",
    val speechTranslation: String = "",
    val emotionResult: EmotionResult? = null,
    val asrLatencyMs: Long = 0L,
    val speechTransLatencyMs: Long = 0L,
    val ttsLatencyMs: Long = 0L,
    val audioDurationSec: Float = 0.0f,
    val hasSynthesizedAudio: Boolean = false,
    val isPlayingAudio: Boolean = false,
    val ttsNote: String? = null,
    // Common
    val errorMessage: String? = null,
    val modelsDiagnostics: List<ModelStatusInfo> = emptyList()
)

class MainScreenViewModel(application: Application) : AndroidViewModel(application) {

    private val extractor = AssetExtractor(application)
    private val pipelineManager = SpeechPipelineManager(application)

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        // Observe extractor state
        viewModelScope.launch {
            extractor.state.collect { extState ->
                _uiState.value = _uiState.value.copy(
                    extractionState = extState,
                    modelsDiagnostics = pipelineManager.getAllDiagnostics(),
                    sampleAudios = pipelineManager.getSampleAudios()
                )
            }
        }

        // Check if extraction is complete
        if (extractor.isExtractionComplete()) {
            _uiState.value = _uiState.value.copy(
                extractionState = ExtractionState.Completed(extractor.modelsDir, 0, 0),
                modelsDiagnostics = pipelineManager.getAllDiagnostics(),
                sampleAudios = pipelineManager.getSampleAudios()
            )
        } else {
            startExtraction()
        }
    }

    fun startExtraction() {
        viewModelScope.launch(Dispatchers.IO) {
            val res = extractor.extractIfNeeded()
            if (res.isFailure) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Model extraction failed: ${res.exceptionOrNull()?.message}"
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    modelsDiagnostics = pipelineManager.getAllDiagnostics(),
                    sampleAudios = pipelineManager.getSampleAudios(),
                    errorMessage = null
                )
            }
        }
    }

    // --- Language Selection Methods ---

    fun setSrcSpeechLang(lang: SupportedLanguage) {
        _uiState.value = _uiState.value.copy(srcSpeechLang = lang)
    }

    fun setTgtSpeechLang(lang: SupportedLanguage) {
        _uiState.value = _uiState.value.copy(tgtSpeechLang = lang)
    }

    fun swapSpeechLangs() {
        val current = _uiState.value
        if (current.srcSpeechLang == SupportedLanguage.AUTO_DETECT) {
            val effective = current.detectedSpeechLang ?: SupportedLanguage.HINDI
            _uiState.value = current.copy(
                srcSpeechLang = current.tgtSpeechLang,
                tgtSpeechLang = effective
            )
        } else {
            _uiState.value = current.copy(
                srcSpeechLang = current.tgtSpeechLang,
                tgtSpeechLang = current.srcSpeechLang
            )
        }
    }

    fun setSrcTextLang(lang: SupportedLanguage) {
        _uiState.value = _uiState.value.copy(srcTextLang = lang)
    }

    fun setTgtTextLang(lang: SupportedLanguage) {
        _uiState.value = _uiState.value.copy(tgtTextLang = lang)
    }

    fun swapTextLangs() {
        val current = _uiState.value
        val newSrc = current.tgtTextLang
        val newTgt = current.srcTextLang
        val defaultText = if (newSrc == SupportedLanguage.ENGLISH) "Hello, how are you?" else "नमस्ते, आप कैसे हैं?"
        _uiState.value = current.copy(
            srcTextLang = newSrc,
            tgtTextLang = newTgt,
            inputText = if (current.translatedText.isNotBlank()) current.translatedText else defaultText,
            translatedText = "",
            latencyMs = 0L,
            tokensGenerated = 0
        )
    }

    // --- Text Translation ---

    fun setInputText(text: String) {
        _uiState.value = _uiState.value.copy(inputText = text)
    }

    fun clearInputText() {
        _uiState.value = _uiState.value.copy(inputText = "", translatedText = "")
    }

    fun translateText() {
        val text = _uiState.value.inputText.trim()
        if (text.isEmpty()) return

        val src = _uiState.value.srcTextLang
        val tgt = _uiState.value.tgtTextLang
        _uiState.value = _uiState.value.copy(
            isTranslating = true,
            errorMessage = null
        )

        viewModelScope.launch(Dispatchers.Default) {
            try {
                val res = pipelineManager.translationManager.translate(text, src, tgt)
                _uiState.value = _uiState.value.copy(
                    translatedText = res.translatedText,
                    latencyMs = res.latencyMs,
                    tokensGenerated = res.tokensGenerated,
                    isTranslating = false,
                    modelsDiagnostics = pipelineManager.getAllDiagnostics(),
                    errorMessage = null
                )
            } catch (t: Throwable) {
                _uiState.value = _uiState.value.copy(
                    isTranslating = false,
                    errorMessage = "Translation failed: ${t.message ?: t.toString()}",
                    modelsDiagnostics = pipelineManager.getAllDiagnostics()
                )
            }
        }
    }

    // --- Audio File Upload ---

    fun processUploadedAudio(uri: Uri, context: Context) {
        val fileName = AudioUtils.getFileName(context, uri)
        _uiState.value = _uiState.value.copy(
            selectedAudioName = fileName,
            isProcessingSpeech = true,
            errorMessage = null,
            speechTranscript = "",
            speechTranslation = "",
            emotionResult = null,
            hasSynthesizedAudio = false,
            ttsNote = null
        )

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val (samples, sampleRate) = AudioUtils.decodeAudioUri(context, uri)
                if (samples.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        isProcessingSpeech = false,
                        errorMessage = "Could not decode audio from selected file."
                    )
                    return@launch
                }

                val out = pipelineManager.processSpeechAudio(
                    samples = samples,
                    sampleRate = sampleRate,
                    srcLang = _uiState.value.srcSpeechLang,
                    tgtLang = _uiState.value.tgtSpeechLang
                )

                if (out.transcript.isBlank()) {
                    _uiState.value = _uiState.value.copy(
                        isProcessingSpeech = false,
                        speechTranscript = "(No speech detected)",
                        speechTranslation = "(Please speak clearly into the microphone)",
                        errorMessage = null,
                        hasSynthesizedAudio = false,
                        detectedSpeechLang = out.detectedSrcLang,
                        audioDurationSec = out.audioDurationSec,
                        modelsDiagnostics = pipelineManager.getAllDiagnostics()
                    )
                    return@launch
                }

                _uiState.value = _uiState.value.copy(
                    isProcessingSpeech = false,
                    speechTranscript = out.transcript,
                    speechTranslation = out.translation,
                    emotionResult = out.emotion,
                    detectedSpeechLang = out.detectedSrcLang ?: if (_uiState.value.srcSpeechLang != SupportedLanguage.AUTO_DETECT) _uiState.value.srcSpeechLang else null,
                    asrLatencyMs = out.asrLatencyMs + out.lidLatencyMs,
                    speechTransLatencyMs = out.translationLatencyMs,
                    ttsLatencyMs = out.ttsLatencyMs,
                    audioDurationSec = out.audioDurationSec,
                    hasSynthesizedAudio = (out.synthesizedAudio != null && out.synthesizedAudio.isNotEmpty()) || out.translation.isNotBlank(),
                    ttsNote = out.ttsNote,
                    modelsDiagnostics = pipelineManager.getAllDiagnostics()
                )
                // Auto-speak translated voice on completion
                if (out.translation.isNotBlank()) {
                    playSynthesizedSpeech()
                }
            } catch (t: Throwable) {
                _uiState.value = _uiState.value.copy(
                    isProcessingSpeech = false,
                    errorMessage = "Audio upload processing failed: ${t.message ?: t.toString()}",
                    modelsDiagnostics = pipelineManager.getAllDiagnostics()
                )
            }
        }
    }

    // --- Sample Audio Processing ---

    fun processSampleAudio(sample: SampleAudioItem) {
        _uiState.value = _uiState.value.copy(
            selectedAudioName = sample.title,
            srcSpeechLang = sample.language,
            isProcessingSpeech = true,
            errorMessage = null,
            speechTranscript = "",
            speechTranslation = "",
            emotionResult = null,
            hasSynthesizedAudio = false,
            ttsNote = null
        )

        viewModelScope.launch(Dispatchers.Default) {
            try {
                val (samples, sampleRate) = AudioUtils.readWavToFloatArray(sample.file)
                val out = pipelineManager.processSpeechAudio(
                    samples = samples,
                    sampleRate = sampleRate,
                    srcLang = sample.language,
                    tgtLang = _uiState.value.tgtSpeechLang
                )
                if (out.transcript.isBlank()) {
                    _uiState.value = _uiState.value.copy(
                        isProcessingSpeech = false,
                        speechTranscript = "(No speech detected)",
                        speechTranslation = "(Please speak clearly into the microphone)",
                        errorMessage = null,
                        hasSynthesizedAudio = false,
                        detectedSpeechLang = out.detectedSrcLang,
                        audioDurationSec = out.audioDurationSec,
                        modelsDiagnostics = pipelineManager.getAllDiagnostics()
                    )
                    return@launch
                }

                _uiState.value = _uiState.value.copy(
                    isProcessingSpeech = false,
                    speechTranscript = out.transcript,
                    speechTranslation = out.translation,
                    emotionResult = out.emotion,
                    detectedSpeechLang = out.detectedSrcLang ?: if (_uiState.value.srcSpeechLang != SupportedLanguage.AUTO_DETECT) _uiState.value.srcSpeechLang else null,
                    asrLatencyMs = out.asrLatencyMs + out.lidLatencyMs,
                    speechTransLatencyMs = out.translationLatencyMs,
                    ttsLatencyMs = out.ttsLatencyMs,
                    audioDurationSec = out.audioDurationSec,
                    hasSynthesizedAudio = (out.synthesizedAudio != null && out.synthesizedAudio.isNotEmpty()) || out.translation.isNotBlank(),
                    ttsNote = out.ttsNote,
                    modelsDiagnostics = pipelineManager.getAllDiagnostics()
                )
                // Auto-speak translated voice on completion
                if (out.translation.isNotBlank()) {
                    playSynthesizedSpeech()
                }
            } catch (t: Throwable) {
                _uiState.value = _uiState.value.copy(
                    isProcessingSpeech = false,
                    errorMessage = "Speech pipeline failed: ${t.message ?: t.toString()}",
                    modelsDiagnostics = pipelineManager.getAllDiagnostics()
                )
            }
        }
    }

    // --- Live Microphone Recording ---

    fun startRecording() {
        if (_uiState.value.isRecording || _uiState.value.isProcessingSpeech) return

        _uiState.value = _uiState.value.copy(
            isRecording = true,
            errorMessage = null,
            speechTranscript = "",
            speechTranslation = "",
            emotionResult = null,
            hasSynthesizedAudio = false,
            ttsNote = null
        )

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val samples = pipelineManager.audioRecorder.recordAudio(
                    sampleRate = 16000,
                    maxDurationMs = 15000
                )
                _uiState.value = _uiState.value.copy(
                    isRecording = false,
                    isProcessingSpeech = true
                )

                if (samples.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        isProcessingSpeech = false,
                        errorMessage = "No audio captured from microphone."
                    )
                    return@launch
                }

                val out = pipelineManager.processSpeechAudio(
                    samples = samples,
                    sampleRate = 16000,
                    srcLang = _uiState.value.srcSpeechLang,
                    tgtLang = _uiState.value.tgtSpeechLang
                )
                if (out.transcript.isBlank()) {
                    _uiState.value = _uiState.value.copy(
                        isProcessingSpeech = false,
                        speechTranscript = "(No speech detected)",
                        speechTranslation = "(Please speak clearly into the microphone)",
                        errorMessage = null,
                        hasSynthesizedAudio = false,
                        detectedSpeechLang = out.detectedSrcLang,
                        audioDurationSec = out.audioDurationSec,
                        modelsDiagnostics = pipelineManager.getAllDiagnostics()
                    )
                    return@launch
                }

                _uiState.value = _uiState.value.copy(
                    isProcessingSpeech = false,
                    speechTranscript = out.transcript,
                    speechTranslation = out.translation,
                    emotionResult = out.emotion,
                    detectedSpeechLang = out.detectedSrcLang ?: if (_uiState.value.srcSpeechLang != SupportedLanguage.AUTO_DETECT) _uiState.value.srcSpeechLang else null,
                    asrLatencyMs = out.asrLatencyMs + out.lidLatencyMs,
                    speechTransLatencyMs = out.translationLatencyMs,
                    ttsLatencyMs = out.ttsLatencyMs,
                    audioDurationSec = out.audioDurationSec,
                    hasSynthesizedAudio = (out.synthesizedAudio != null && out.synthesizedAudio.isNotEmpty()) || out.translation.isNotBlank(),
                    ttsNote = out.ttsNote,
                    modelsDiagnostics = pipelineManager.getAllDiagnostics()
                )
                // Auto-speak translated voice on completion
                if (out.translation.isNotBlank()) {
                    playSynthesizedSpeech()
                }
            } catch (t: Throwable) {
                _uiState.value = _uiState.value.copy(
                    isRecording = false,
                    isProcessingSpeech = false,
                    errorMessage = "Recording / processing error: ${t.message ?: t.toString()}",
                    modelsDiagnostics = pipelineManager.getAllDiagnostics()
                )
            }
        }
    }

    fun stopRecording() {
        pipelineManager.audioRecorder.stop()
    }

    // --- Audio Playback ---

    fun playSynthesizedSpeech() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isPlayingAudio = true)
            try {
                pipelineManager.playLastSynthesizedAudio()
            } finally {
                _uiState.value = _uiState.value.copy(isPlayingAudio = false)
            }
        }
    }

    fun speakTextTranslation() {
        val text = _uiState.value.translatedText.trim()
        if (text.isEmpty()) return
        val tgt = _uiState.value.tgtTextLang

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isPlayingAudio = true)
            try {
                val phoneticText = if (tgt.isIndic()) {
                    IndicScriptConverter.toDevanagari(text, tgt.code)
                } else {
                    text
                }
                try {
                    val result = pipelineManager.getPiperEngine().synthesize(phoneticText)
                    if (result.samples.isNotEmpty()) {
                        pipelineManager.audioPlayer.play(result.samples, result.sampleRate)
                        return@launch
                    }
                } catch (t: Throwable) {
                    AppLog.w("ViewModel", "Piper text synthesis error, using system fallback: ${t.message}")
                }
                pipelineManager.androidTtsEngine.speak(text, tgt.code, EmotionProsody.NEUTRAL)
            } catch (t: Throwable) {
                AppLog.e("ViewModel", "speakTextTranslation failed: ${t.message}", t)
            } finally {
                _uiState.value = _uiState.value.copy(isPlayingAudio = false)
            }
        }
    }

    fun playSampleAudio(sample: SampleAudioItem) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isPlayingAudio = true)
            try {
                pipelineManager.playSampleAudio(sample.file)
            } finally {
                _uiState.value = _uiState.value.copy(isPlayingAudio = false)
            }
        }
    }

    fun stopAudioPlayback() {
        pipelineManager.stopAudioPlayback()
        _uiState.value = _uiState.value.copy(isPlayingAudio = false)
    }

    fun refreshDiagnostics() {
        _uiState.value = _uiState.value.copy(
            modelsDiagnostics = pipelineManager.getAllDiagnostics(),
            sampleAudios = pipelineManager.getSampleAudios()
        )
    }

    override fun onCleared() {
        super.onCleared()
        pipelineManager.close()
    }
}

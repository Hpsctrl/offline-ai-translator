package com.example.offlineaitranslator.engine

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Offline Text-To-Speech engine using Android's built-in platform TTS.
 * Supports all Indic languages installed on Samsung/Android devices (Telugu, Hindi,
 * Tamil, Bengali, Marathi, Gujarati, Kannada, Malayalam, etc.) with emotion modulation!
 */
class AndroidTtsEngine(private val context: Context) {

    private var tts: TextToSpeech? = null
    private var isInitialized = false

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            isInitialized = (status == TextToSpeech.SUCCESS)
            if (isInitialized) {
                AppLog.i("AndroidTTS", "Android platform TTS initialized successfully.")
            } else {
                AppLog.e("AndroidTTS", "Failed to initialize Android platform TTS, status=$status")
            }
        }
    }

    /**
     * Speaks text using Android's offline system TTS with prosody modulated by detected emotion.
     */
    suspend fun speak(
        text: String,
        langCode: String,
        prosody: EmotionProsody
    ): Boolean = suspendCancellableCoroutine { continuation ->
        val engine = tts
        if (engine == null || !isInitialized) {
            AppLog.w("AndroidTTS", "TTS not initialized yet")
            continuation.resume(false)
            return@suspendCancellableCoroutine
        }

        val locale = getLocaleForLang(langCode)
        val langResult = engine.setLanguage(locale)
        if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            AppLog.w("AndroidTTS", "Language $locale is not supported or missing data on this device")
        }

        // Apply Emotion prosody modulation
        engine.setSpeechRate(prosody.speed)
        engine.setPitch(prosody.pitch)

        val utteranceId = "utt_${System.currentTimeMillis()}"
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                if (continuation.isActive) continuation.resume(true)
            }
            override fun onError(utteranceId: String?) {
                if (continuation.isActive) continuation.resume(false)
            }
        })

        val res = engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        if (res != TextToSpeech.SUCCESS && continuation.isActive) {
            continuation.resume(false)
        }
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }

    private fun getLocaleForLang(langCode: String): Locale {
        val clean = langCode.lowercase()
        return when {
            clean.startsWith("hin") || clean == "hi" -> Locale("hi", "IN")
            clean.startsWith("eng") || clean == "en" -> Locale.US
            clean.startsWith("tel") || clean == "te" -> Locale("te", "IN")
            clean.startsWith("tam") || clean == "ta" -> Locale("ta", "IN")
            clean.startsWith("ben") || clean == "bn" || clean.startsWith("asm") || clean == "as" -> Locale("bn", "IN")
            clean.startsWith("mar") || clean == "mr" -> Locale("mr", "IN")
            clean.startsWith("guj") || clean == "gu" -> Locale("gu", "IN")
            clean.startsWith("kan") || clean == "kn" -> Locale("kn", "IN")
            clean.startsWith("mal") || clean == "ml" -> Locale("ml", "IN")
            clean.startsWith("pan") || clean == "pa" -> Locale("pa", "IN")
            clean.startsWith("ory") || clean == "or" -> Locale("or", "IN")
            clean.startsWith("urd") || clean == "ur" -> Locale("ur", "IN")
            clean.startsWith("jpn") || clean == "ja" -> Locale.JAPANESE
            clean.startsWith("spa") || clean == "es" -> Locale("es", "ES")
            clean.startsWith("zho") || clean == "zh" -> Locale.CHINESE
            clean.startsWith("fra") || clean == "fr" -> Locale.FRENCH
            clean.startsWith("deu") || clean == "de" -> Locale.GERMAN
            clean.startsWith("rus") || clean == "ru" -> Locale("ru", "RU")
            clean.startsWith("ara") || clean == "ar" -> Locale("ar", "SA")
            clean.startsWith("kor") || clean == "ko" -> Locale.KOREAN
            clean.startsWith("por") || clean == "pt" -> Locale("pt", "BR")
            clean.startsWith("ita") || clean == "it" -> Locale.ITALIAN
            else -> {
                val prefix = if (clean.contains("_")) clean.substringBefore("_") else clean
                Locale(prefix.take(2))
            }
        }
    }
}

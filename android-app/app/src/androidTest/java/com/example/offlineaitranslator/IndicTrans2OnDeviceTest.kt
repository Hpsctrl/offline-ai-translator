package com.example.offlineaitranslator

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.offlineaitranslator.engine.AssetExtractor
import com.example.offlineaitranslator.engine.AudioUtils
import com.example.offlineaitranslator.engine.Emotion2VecEngine
import com.example.offlineaitranslator.engine.IndicTrans2Engine
import com.example.offlineaitranslator.engine.SherpaPiperEngine
import com.example.offlineaitranslator.engine.SherpaWhisperEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class IndicTrans2OnDeviceTest {

    @Test
    fun testOnDeviceTranslationWorkflow() = runBlocking {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext

        // 1. Extract models on device
        val extractor = AssetExtractor(appContext)
        val extractRes = extractor.extractIfNeeded()
        assertTrue("Asset extraction must succeed on device", extractRes.isSuccess)
        val modelsDir = extractRes.getOrThrow()

        // 2. Hindi -> English on device (IndicTrans2)
        val indicEnDir = File(modelsDir, "translation/indictrans2-indic-en-dist-200m")
        assertTrue("Indic->En directory must exist on device", indicEnDir.exists())

        val h2eEngine = IndicTrans2Engine(indicEnDir, isIndicToEn = true)
        h2eEngine.load()
        assertTrue("Engine must be loaded on Android runtime", h2eEngine.isLoaded)

        val hindiText = "नमस्ते, आप कैसे हैं?"
        val enResult = h2eEngine.translate(hindiText, "hin_Deva", "eng_Latn")
        println("On-device H2E result: ${enResult.translatedText} (latency: ${enResult.latencyMs} ms)")

        assertNotNull(enResult.translatedText)
        assertTrue(
            "Expected English greeting, got: ${enResult.translatedText}",
            enResult.translatedText.lowercase().contains("hi") ||
            enResult.translatedText.lowercase().contains("hello") ||
            enResult.translatedText.lowercase().contains("how are you")
        )
        h2eEngine.close()

        // 3. English -> Hindi on device (IndicTrans2)
        val enIndicDir = File(modelsDir, "translation/indictrans2-en-indic-dist-200m")
        assertTrue("En->Indic directory must exist on device", enIndicDir.exists())

        val e2hEngine = IndicTrans2Engine(enIndicDir, isIndicToEn = false)
        e2hEngine.load()
        assertTrue("E2H Engine must be loaded on Android runtime", e2hEngine.isLoaded)

        val englishText = "Hello, how are you?"
        val hiResult = e2hEngine.translate(englishText, "eng_Latn", "hin_Deva")
        println("On-device E2H result: ${hiResult.translatedText} (latency: ${hiResult.latencyMs} ms)")

        assertNotNull(hiResult.translatedText)
        assertTrue("Hindi output must not be empty", hiResult.translatedText.isNotEmpty())
        e2hEngine.close()

        // 4. Whisper ASR on device
        val sampleAudioFile = File(modelsDir, "sample_audio/sample_hi_phr1_pharmacy.wav")
        assertTrue("Sample audio must exist: ${sampleAudioFile.absolutePath}", sampleAudioFile.exists())
        val (samples, sampleRate) = AudioUtils.readWavToFloatArray(sampleAudioFile)

        val asrDir = File(modelsDir, "asr/whisper-tiny-indic")
        val whisperEngine = SherpaWhisperEngine(asrDir, appContext.assets)
        whisperEngine.load("hi")
        assertTrue("Whisper engine must load on device", whisperEngine.isLoaded)

        val asrResult = whisperEngine.transcribe(samples, sampleRate, "hi")
        println("On-device Whisper ASR transcript: '${asrResult.text}' in ${asrResult.latencyMs} ms")
        assertNotNull(asrResult.text)
        assertTrue("Transcript should contain pharmacy/medicine words", asrResult.text.contains("दवा") || asrResult.text.contains("फार्मेसी"))
        whisperEngine.close()

        // 5. Emotion2Vec on device
        val emoDir = File(modelsDir, "emotion/emotion2vec-plus-base")
        val emotionEngine = Emotion2VecEngine(emoDir)
        emotionEngine.initialize()
        val emoResult = emotionEngine.analyzeEmotion(samples)
        println("On-device Emotion2Vec result: ${emoResult.emotion} (${emoResult.confidence}) in ${emoResult.latencyMs} ms")
        assertNotNull(emoResult.emotion)
        assertTrue(emoResult.confidence > 0f)
        emotionEngine.close()

        // 6. Piper TTS on device
        val ttsDir = File(modelsDir, "tts/vits-piper-en_US-lessac-low")
        val piperEngine = SherpaPiperEngine(ttsDir, appContext.assets)
        piperEngine.load()
        assertTrue("Piper TTS engine must load on device", piperEngine.isLoaded)

        val ttsResult = piperEngine.synthesize("Hi, how are you?")
        println("On-device Piper TTS generated ${ttsResult.samples.size} samples at ${ttsResult.sampleRate} Hz in ${ttsResult.latencyMs} ms")
        assertTrue("TTS samples must not be empty", ttsResult.samples.isNotEmpty())
        assertEquals(16000, ttsResult.sampleRate)
        piperEngine.close()
    }
}

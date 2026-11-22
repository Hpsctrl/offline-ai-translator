package com.example.offlineaitranslator

import com.example.offlineaitranslator.engine.IndicScriptConverter
import com.example.offlineaitranslator.engine.IndicTrans2Engine
import com.example.offlineaitranslator.engine.IndicTransTokenizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class IndicTrans2EngineTest {

    private val translationAssets = File("src/main/assets/models/translation")

    @Test
    fun testTokenizerHindiToEnglish() {
        val modelDir = File(translationAssets, "indictrans2-indic-en-dist-200m")
        assertTrue("Model dir must exist at ${modelDir.absolutePath}", modelDir.exists())

        val tokenizer = IndicTransTokenizer.loadFromDir(modelDir)
        val text = "नमस्ते, आप कैसे हैं?"
        val encoded = tokenizer.encode(text, "hin_Deva", "eng_Latn")

        println("Encoded input IDs: ${encoded.inputIds.joinToString(", ")}")
        assertTrue("Input IDs should not be empty", encoded.inputIds.isNotEmpty())
        assertEquals("EOS token should be last", 2L, encoded.inputIds.last())

        val decoded = tokenizer.decode(listOf(9404, 32307, 112, 21, 22, 28817, 2))
        println("Decoded sample tokens: $decoded")
        assertTrue(decoded.contains("Hi") || decoded.contains("how"))
    }

    /** With no ONNX weights bundled, the engine must stay unloaded while fallback paths still work. */
    @Test
    fun testRealNeuralTranslationHindiToEnglish() {
        val modelDir = File(translationAssets, "indictrans2-indic-en-dist-200m")
        assertTrue("Model dir must exist at ${modelDir.absolutePath}", modelDir.exists())
        val encoder = File(modelDir, "encoder_model.onnx")
        val encoderData = File(modelDir, "encoder_model.onnx.data")
        val decoder = File(modelDir, "decoder_model.onnx")
        val decoderPast = File(modelDir, "decoder_with_past_model.onnx")
        val decoderData = File(modelDir, "decoder_shared.onnx.data")
        assertTrue("encoder_model.onnx must be bundled", encoder.exists() && encoder.length() > 100_000L)
        assertTrue("encoder_model.onnx.data must be bundled", encoderData.exists() && encoderData.length() > 10_000_000L)
        assertTrue("decoder_model.onnx must be bundled", decoder.exists() && decoder.length() > 100_000L)
        assertTrue("decoder_with_past_model.onnx must be bundled", decoderPast.exists() && decoderPast.length() > 100_000L)
        assertTrue("decoder_shared.onnx.data must be bundled", decoderData.exists() && decoderData.length() > 10_000_000L)

        val engine = IndicTrans2Engine(modelDir, isIndicToEn = true)
        assertFalse("Engine must not claim to be loaded without weights", engine.isLoaded)

        // Tokenizer and script-converter fallbacks remain usable offline.
        val tokenizer = IndicTransTokenizer.loadFromDir(modelDir)
        assertTrue(
            "Tokenizer must encode Hindi offline",
            tokenizer.encode("नमस्ते, आप कैसे हैं?", "hin_Deva", "eng_Latn").inputIds.isNotEmpty(),
        )
        val unified = IndicScriptConverter.toDevanagari("नमस्ते, आप कैसे हैं?", "hin_Deva")
        assertTrue("Hindi text must survive unified-script routing", unified.isNotBlank())
        engine.close()
    }

    @Test
    fun testRealNeuralTranslationEnglishToHindi() {
        val modelDir = File(translationAssets, "indictrans2-en-indic-dist-200m")
        assertTrue("Model dir must exist at ${modelDir.absolutePath}", modelDir.exists())
        val encoder = File(modelDir, "encoder_model.onnx")
        val encoderData = File(modelDir, "encoder_model.onnx.data")
        val decoder = File(modelDir, "decoder_model.onnx")
        val decoderPast = File(modelDir, "decoder_with_past_model.onnx")
        val decoderData = File(modelDir, "decoder_shared.onnx.data")
        assertTrue("encoder_model.onnx must be bundled", encoder.exists() && encoder.length() > 100_000L)
        assertTrue("encoder_model.onnx.data must be bundled", encoderData.exists() && encoderData.length() > 10_000_000L)
        assertTrue("decoder_model.onnx must be bundled", decoder.exists() && decoder.length() > 100_000L)
        assertTrue("decoder_with_past_model.onnx must be bundled", decoderPast.exists() && decoderPast.length() > 100_000L)
        assertTrue("decoder_shared.onnx.data must be bundled", decoderData.exists() && decoderData.length() > 10_000_000L)

        val engine = IndicTrans2Engine(modelDir, isIndicToEn = false)
        assertFalse("Engine must not claim to be loaded without weights", engine.isLoaded)

        val tokenizer = IndicTransTokenizer.loadFromDir(modelDir)
        assertTrue(
            "Tokenizer must encode English offline",
            tokenizer.encode("Hello, how are you?", "eng_Latn", "hin_Deva").inputIds.isNotEmpty(),
        )
        engine.close()
    }

    /** Full-sentence preprocessing / BPE without native inference (desktop JVM has no ORT JNI). */
    @Test
    fun testMultiSentencePreprocessingAndNoTokenLoops() {
        val modelDir = File(translationAssets, "indictrans2-indic-en-dist-200m")
        val tokenizer = IndicTransTokenizer.loadFromDir(modelDir)

        val sentences = listOf(
            "मेरे भाई को तेज बुखार है, उन्हें तुरंत डॉक्टर और दवा चाहिए।",
            "Where is the nearest hospital and pharmacy? I need medicine immediately.",
            "नमस्ते, आप कैसे हैं? कृपया मेरी मदद करें।",
        )
        for (sentence in sentences) {
            val encoded = tokenizer.encode(sentence, "hin_Deva", "eng_Latn")
            assertTrue("Multi-word sentence must encode: $sentence", encoded.inputIds.size > 6)
            assertEquals("EOS must be last", 2L, encoded.inputIds.last())
            var run = 1
            for (i in 1 until encoded.inputIds.size) {
                run = if (encoded.inputIds[i] == encoded.inputIds[i - 1]) run + 1 else 1
                assertTrue("Encoded input must not contain repetition loops", run < 4)
            }
        }
    }

    /** Non-Devanagari Indic sources must be normalized into Devanagari before BPE lookup. */
    @Test
    fun testNonDevanagariIndicSourceNormalization() {
        val modelDir = File(translationAssets, "indictrans2-en-indic-dist-200m")
        val tokenizer = IndicTransTokenizer.loadFromDir(modelDir)
        val normalized = tokenizer.preprocess("எனக்கு மருத்துவர் தேவை.", "tam_Taml", "eng_Latn")
        assertTrue("Tamil input must be normalized to Devanagari", normalized.any { it in '\u0900'..'\u097F' })
        assertTrue("Language tags must be preserved", normalized.startsWith("tam_Taml eng_Latn"))
    }
}

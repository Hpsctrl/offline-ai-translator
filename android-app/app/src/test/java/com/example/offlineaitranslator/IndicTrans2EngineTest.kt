package com.example.offlineaitranslator

import com.example.offlineaitranslator.engine.IndicTrans2Engine
import com.example.offlineaitranslator.engine.IndicTransTokenizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class IndicTrans2EngineTest {

    @Test
    fun testTokenizerHindiToEnglish() {
        val modelDir = File("../../models/translation/indictrans2-indic-en-dist-200m")
        assertTrue("Model dir must exist at ${modelDir.absolutePath}", modelDir.exists())

        val tokenizer = IndicTransTokenizer.loadFromDir(modelDir)
        val text = "नमस्ते, आप कैसे हैं?"
        val encoded = tokenizer.encode(text, "hin_Deva", "eng_Latn")

        println("Encoded input IDs: ${encoded.inputIds.joinToString(", ")}")
        assertTrue("Input IDs should not be empty", encoded.inputIds.isNotEmpty())
        assertEquals("EOS token should be last", 2L, encoded.inputIds.last())

        // Decode some tokens
        val decoded = tokenizer.decode(listOf(9404, 32307, 112, 21, 22, 28817, 2))
        println("Decoded sample tokens: $decoded")
        assertTrue(decoded.contains("Hi") || decoded.contains("how"))
    }

    @Test
    fun testRealNeuralTranslationHindiToEnglish() {
        val modelDir = File("../../models/translation/indictrans2-indic-en-dist-200m")
        assertTrue("Model dir must exist", modelDir.exists())

        try {
            val engine = IndicTrans2Engine(modelDir, isIndicToEn = true)
            engine.load()
            assertTrue("Engine must be loaded", engine.isLoaded)

            val inputHindi = "नमस्ते, आप कैसे हैं?"
            val result = engine.translate(inputHindi, "hin_Deva", "eng_Latn")
            println("Input Hindi: $inputHindi")
            println("Output English: ${result.translatedText}")
            println("Latency: ${result.latencyMs} ms, tokens: ${result.tokensGenerated}")

            assertNotNull(result.translatedText)
            assertTrue(
                "Expected English greeting translation, got: ${result.translatedText}",
                result.translatedText.lowercase().contains("hi") ||
                result.translatedText.lowercase().contains("hello") ||
                result.translatedText.lowercase().contains("how are you")
            )

            engine.close()
        } catch (e: UnsatisfiedLinkError) {
            org.junit.Assume.assumeNoException("Desktop JVM does not have onnxruntime native binaries", e)
        } catch (e: NoClassDefFoundError) {
            org.junit.Assume.assumeNoException("OrtEnvironment requires onnxruntime native binaries", e)
        }
    }

    @Test
    fun testRealNeuralTranslationEnglishToHindi() {
        val modelDir = File("../../models/translation/indictrans2-en-indic-dist-200m")
        assertTrue("Model dir must exist", modelDir.exists())

        try {
            val engine = IndicTrans2Engine(modelDir, isIndicToEn = false)
            engine.load()
            assertTrue("Engine must be loaded", engine.isLoaded)

            val inputEnglish = "Hello, how are you?"
            val result = engine.translate(inputEnglish, "eng_Latn", "hin_Deva")
            println("Input English: $inputEnglish")
            println("Output Hindi: ${result.translatedText}")
            println("Latency: ${result.latencyMs} ms, tokens: ${result.tokensGenerated}")

            assertNotNull(result.translatedText)
            assertTrue("Output should not be empty", result.translatedText.isNotEmpty())

            engine.close()
        } catch (e: UnsatisfiedLinkError) {
            org.junit.Assume.assumeNoException("Desktop JVM does not have onnxruntime native binaries", e)
        } catch (e: NoClassDefFoundError) {
            org.junit.Assume.assumeNoException("OrtEnvironment requires onnxruntime native binaries", e)
        }
    }
}

package com.example.offlineaitranslator

import com.example.offlineaitranslator.engine.AudioUtils
import com.example.offlineaitranslator.engine.Emotion2VecEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class Emotion2VecEngineTest {

    @Test
    fun testAudioUtilsReadingWav() {
        val audioFile = File("../../models/sample_audio/sample_hi_phr1_pharmacy.wav")
        assertTrue("Sample audio file must exist: ${audioFile.absolutePath}", audioFile.exists())

        val (samples, sampleRate) = AudioUtils.readWavToFloatArray(audioFile)
        assertEquals("Sample rate must be 16000", 16000, sampleRate)
        assertTrue("Samples must not be empty", samples.isNotEmpty())
        println("Loaded ${samples.size} samples at $sampleRate Hz (Duration: ${samples.size.toFloat() / sampleRate}s)")
    }

    @Test
    fun testRealEmotion2VecInference() {
        val modelDir = File("../../models/emotion/emotion2vec-plus-base")
        assertTrue("Model dir must exist: ${modelDir.absolutePath}", modelDir.exists())

        val audioFile = File("../../models/sample_audio/sample_hi_phr1_pharmacy.wav")
        assertTrue("Sample audio must exist: ${audioFile.absolutePath}", audioFile.exists())

        val (samples, _) = AudioUtils.readWavToFloatArray(audioFile)
        try {
            val engine = Emotion2VecEngine(modelDir)
            engine.initialize()

            val result = engine.analyzeEmotion(samples)
            println("Emotion result: ${result.emotion} (confidence: ${result.confidence}) in ${result.latencyMs} ms")
            println("All emotion scores: ${result.scores}")

            assertNotNull("Emotion should not be null", result.emotion)
            assertTrue("Confidence should be positive", result.confidence > 0f)
            assertEquals("Should have all 9 target emotions", 9, result.scores.size)
            assertTrue("Target emotions should include sad", result.scores.containsKey("sad"))
            assertTrue("Target emotions should include neutral", result.scores.containsKey("neutral"))
            assertTrue("Target emotions should include happy", result.scores.containsKey("happy"))

            engine.close()
        } catch (e: UnsatisfiedLinkError) {
            org.junit.Assume.assumeNoException("Desktop JVM does not have onnxruntime4j_jni native library for Android", e)
        } catch (e: NoClassDefFoundError) {
            org.junit.Assume.assumeNoException("OrtEnvironment requires onnxruntime native binaries", e)
        }
    }
}

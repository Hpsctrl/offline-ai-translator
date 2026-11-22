package com.example.offlineaitranslator

import com.example.offlineaitranslator.engine.AudioUtils
import com.example.offlineaitranslator.engine.Emotion2VecEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class Emotion2VecEngineTest {

    private val assets = File("src/main/assets/models")
    private val sampleWav = File(assets, "sample_audio/sample_hi_phr1_pharmacy.wav")

    @Test
    fun testAudioUtilsReadingWav() {
        assertTrue("Sample audio file must exist: ${sampleWav.absolutePath}", sampleWav.exists())

        val (samples, sampleRate) = AudioUtils.readWavToFloatArray(sampleWav)
        assertEquals("Sample rate must be 16000", 16000, sampleRate)
        assertTrue("Samples must not be empty", samples.isNotEmpty())
        println("Loaded ${samples.size} samples at $sampleRate Hz (Duration: ${samples.size.toFloat() / sampleRate}s)")
    }

    @Test
    fun testRealEmotion2VecInference() {
        val modelDir = File(assets, "emotion/emotion2vec-plus-base")
        assertTrue("Model dir must exist: ${modelDir.absolutePath}", modelDir.exists())
        val modelFile = File(modelDir, "model.onnx")
        assertTrue("Real emotion ONNX weights must be bundled", modelFile.exists() && modelFile.length() > 1_000_000L)
        assertTrue("Sample audio must exist: ${sampleWav.absolutePath}", sampleWav.exists())

        val (samples, _) = AudioUtils.readWavToFloatArray(sampleWav)
        val engine = Emotion2VecEngine(modelDir)
        try {
            try {
                val result = engine.analyzeEmotion(samples)
                println("Emotion result: ${result.emotion} (confidence: ${result.confidence}) in ${result.latencyMs} ms")
                println("All emotion scores: ${result.scores}")

                assertNotNull("Emotion should not be null", result.emotion)
                assertTrue("Confidence must be positive, was ${result.confidence}", result.confidence > 0f)
                assertTrue("All 9 target emotions must be scored", result.scores.size >= 9)
                assertTrue("Target emotions should include sad", result.scores.containsKey("sad"))
                assertTrue("Target emotions should include neutral", result.scores.containsKey("neutral"))
                assertTrue("Target emotions should include happy", result.scores.containsKey("happy"))
            } catch (e: UnsatisfiedLinkError) {
                org.junit.Assume.assumeNoException("Desktop JVM cannot load Android onnxruntime4j_jni native library", e)
            } catch (e: NoClassDefFoundError) {
                org.junit.Assume.assumeNoException("OrtEnvironment requires onnxruntime native binaries", e)
            }
        } finally {
            engine.close()
        }
    }
}

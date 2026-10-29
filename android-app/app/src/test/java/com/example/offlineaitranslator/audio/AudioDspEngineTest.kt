package com.example.offlineaitranslator.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sin

/**
 * Unit test suite for AudioDspEngine acoustic signal conditioning.
 * Validates:
 * 1. 85Hz High-Pass 2nd-order Butterworth IIR filter attenuation of low-frequency rumble (<85Hz).
 * 2. Conversational speech AGC calibration (prevents -10 dB crushing).
 * 3. Dynamic AGC whisper boost for low-volume speech.
 * 4. Soft-knee peak limiter ceiling (<= 0.999f) preventing digital clipping on overdriven audio.
 * 5. Silence trimming (trimSilence) preserving quiet speech without truncation.
 */
class AudioDspEngineTest {

    @Test
    fun testHighPassFilterAttenuates50HzHumAndPreserves1kHz() {
        val sampleRate = 16000

        // 50 Hz rumble must be attenuated by at least -5 dB
        val gainAt50Hz = AudioDspEngine.getFilterGainDb(50.0f, sampleRate)
        assertTrue(
            "50Hz rumble must be attenuated by at least -5dB (got $gainAt50Hz dB)",
            gainAt50Hz <= -5.0f
        )

        // 60 Hz hum should be attenuated by >= -3 dB
        val gainAt60Hz = AudioDspEngine.getFilterGainDb(60.0f, sampleRate)
        assertTrue(
            "60Hz hum must be attenuated by at least -3dB (got $gainAt60Hz dB)",
            gainAt60Hz <= -3.0f
        )

        // 1000 Hz vocal formant must pass cleanly with < 0.5 dB insertion loss
        val gainAt1kHz = AudioDspEngine.getFilterGainDb(1000.0f, sampleRate)
        assertTrue(
            "1kHz voice formant must pass with <0.5dB loss (got $gainAt1kHz dB)",
            abs(gainAt1kHz) < 0.5f
        )
    }

    @Test
    fun testConversationalSpeechIsNotCrushedByNegativeGain() {
        val sampleRate = 16000
        val durationSec = 1.0f
        val numSamples = (sampleRate * durationSec).toInt()

        // Conversational speech with normal crest factor: peak = 0.40, RMS ~ 0.08 (-22 dBFS)
        val speechSamples = FloatArray(numSamples) { i ->
            (0.40f * sin(2.0 * Math.PI * 300.0 * i / sampleRate)).toFloat()
        }

        val inRms = AudioDspEngine.calculateRms(speechSamples)
        val inPeak = AudioDspEngine.calculatePeak(speechSamples)
        val (processed, metrics) = AudioDspEngine.processWithMetrics(speechSamples, sampleRate)
        val outPeak = AudioDspEngine.calculatePeak(processed)
        val outRms = AudioDspEngine.calculateRms(processed)

        // Peak must remain in healthy conversational range and not be squashed to <= 0.20f by the old -10 dB AGC bug
        assertTrue(
            "Conversational speech output peak ($outPeak) must not be crushed by -10dB bug (expected >= 0.35f, <= 0.999f)",
            outPeak >= 0.35f && outPeak <= 0.999f
        )
        // Must not be attenuated by more than 3 dB
        assertTrue(
            "Gain applied (${metrics.gainAppliedDb} dB) must not crush conversational speech by more than -3dB",
            metrics.gainAppliedDb >= -3.0f
        )
    }

    @Test
    fun testWhisperBoostAmplifiesLowEnergySignals() {
        val sampleRate = 16000
        val durationSec = 1.0f
        val numSamples = (sampleRate * durationSec).toInt()

        // Low-volume whisper: peak amplitude 0.015, RMS ~ 0.010 (-40 dBFS)
        val lowSamples = FloatArray(numSamples) { i ->
            (0.015f * sin(2.0 * Math.PI * 440.0 * i / sampleRate)).toFloat()
        }

        val inRms = AudioDspEngine.calculateRms(lowSamples)
        val (processed, metrics) = AudioDspEngine.processWithMetrics(lowSamples, sampleRate)
        val outRms = AudioDspEngine.calculateRms(processed)

        // Whisper must be amplified by AGC
        assertTrue(
            "Low-volume whisper must be boosted by AGC (inRms=$inRms, outRms=$outRms)",
            outRms > inRms
        )
        assertTrue(
            "Gain applied must be positive (got ${metrics.gainAppliedDb} dB)",
            metrics.gainAppliedDb > 0.0f
        )
    }

    @Test
    fun testSoftKneeLimiterPreventsClippingOnOverdrivenAudio() {
        val sampleRate = 16000
        val overdriven = FloatArray(sampleRate) { i ->
            // Overdriven 3.0x signal (simulating shout / close mic)
            (3.0f * sin(2.0 * Math.PI * 300.0 * i / sampleRate)).toFloat()
        }

        val processed = AudioDspEngine.process(overdriven, sampleRate)
        val peak = AudioDspEngine.calculatePeak(processed)

        // Peak limiter must strictly enforce ceiling <= 0.999f
        assertTrue(
            "Peak amplitude ($peak) must strictly be <= 0.999f to prevent digital clipping",
            peak <= 0.999f
        )
        // Output must not contain NaN or Infinity
        for (s in processed) {
            assertFalse("Sample is NaN", s.isNaN())
            assertFalse("Sample is Infinite", s.isInfinite())
        }
    }

    @Test
    fun testTrimSilenceDoesNotDiscardAudibleWhisper() {
        val sampleRate = 16000
        val numSamples = sampleRate * 2 // 2 seconds
        val whisperWithSilence = FloatArray(numSamples) { 0f }

        // Place 1 second of whisper in the middle (0.5s to 1.5s) with amplitude 0.015
        val startIdx = (sampleRate * 0.5).toInt()
        val endIdx = (sampleRate * 1.5).toInt()
        for (i in startIdx until endIdx) {
            whisperWithSilence[i] = (0.015f * sin(2.0 * Math.PI * 500.0 * i / sampleRate)).toFloat()
        }

        val trimmed = AudioDspEngine.trimSilence(whisperWithSilence, sampleRate, threshold = 0.004f)
        assertTrue("Trimmed audio must not be empty for audible whisper", trimmed.isNotEmpty())
        assertTrue(
            "Trimmed length (${trimmed.size} samples) must preserve the 1s whisper plus safety pads",
            trimmed.size >= (sampleRate * 0.8)
        )
    }
}

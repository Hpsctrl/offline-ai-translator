package com.example.offlineaitranslator.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Adversarial Stress Test Suite for AudioDspEngine.
 *
 * Exercises the acoustic conditioning pipeline under hostile and edge-case conditions:
 * 1. Conversational Speech Calibration: Peak remains in healthy range (0.50f–0.70f steady-state, <= 0.999f transient) and is NOT squashed by -10 dB.
 * 2. Low-Volume Whisper & Trimming: Positive AGC boost without squashing into silence, trimSilence preserves soft syllables.
 * 3. Overdriven Audio & Limiter: Peak limiter strictly enforces ceiling <= 0.999f with ZERO NaN and ZERO Infinity.
 * 4. 85Hz High-Pass Filter: Attenuation >= 6 dB at 60Hz hum, preservation of speech >= 250Hz.
 * 5. Noise Gate Crossfading: Smooth crossfade transitions with zero clicking or flutter.
 */
class AudioDspAdversarialStressTest {

    private val sampleRate = 16000

    @Test
    fun testConversationalSpeechCalibrationAndRange() {
        // Conversational speech: peak = 0.40f, 300Hz harmonic tone (duration 1.0s)
        val numSamples = sampleRate * 1
        val speechSamples = FloatArray(numSamples) { i ->
            (0.40f * sin(2.0 * Math.PI * 300.0 * i / sampleRate)).toFloat()
        }

        val inRms = AudioDspEngine.calculateRms(speechSamples)
        val inPeak = AudioDspEngine.calculatePeak(speechSamples)
        assertTrue("Input peak should be ~0.40f", inPeak in 0.38f..0.42f)
        assertTrue("Input RMS should be conversational", inRms in 0.05f..0.35f)

        val (processed, metrics) = AudioDspEngine.processWithMetrics(speechSamples, sampleRate)
        val outPeak = AudioDspEngine.calculatePeak(processed)
        val outRms = AudioDspEngine.calculateRms(processed)

        // 1. Overall output peak must be bounded by limiter ceiling <= 0.999f and >= 0.50f
        assertTrue(
            "Conversational speech output peak ($outPeak) must be >= 0.50f",
            outPeak >= 0.50f
        )
        assertTrue(
            "Conversational speech output peak ($outPeak) must be <= 0.999f",
            outPeak <= 0.999f
        )

        // 2. In steady state (after initial 100ms attack), peak must be in healthy range [0.50f, 0.70f]
        val steadyStateSamples = processed.copyOfRange(1600, numSamples)
        val steadyStatePeak = AudioDspEngine.calculatePeak(steadyStateSamples)
        assertTrue(
            "Steady-state conversational peak ($steadyStatePeak) must remain in healthy range [0.50f, 0.70f]",
            steadyStatePeak in 0.50f..0.70f
        )

        // 3. Must not be squashed by negative gain (the legacy -10 dB bug)
        assertTrue(
            "Gain applied (${metrics.gainAppliedDb} dB) must be >= -3.0 dB, not crushed by -10 dB bug",
            metrics.gainAppliedDb >= -3.0f
        )
    }

    @Test
    fun testLowVolumeWhisperSpeechAgcBoostAndTrimming() {
        val numSamples = sampleRate * 2 // 2.0 seconds
        val whisperSamples = FloatArray(numSamples) { i ->
            val t = i.toDouble() / sampleRate
            (0.015 * sin(2.0 * Math.PI * 500.0 * t)).toFloat()
        }

        val inRms = AudioDspEngine.calculateRms(whisperSamples)
        val (processed, metrics) = AudioDspEngine.processWithMetrics(whisperSamples, sampleRate)
        val outRms = AudioDspEngine.calculateRms(processed)

        // 1. Whisper must receive positive AGC amplification
        assertTrue(
            "Whisper speech must be boosted: inRms=$inRms, outRms=$outRms",
            outRms > inRms
        )
        assertTrue(
            "Gain applied must be positive: got ${metrics.gainAppliedDb} dB",
            metrics.gainAppliedDb > 0.0f
        )
        assertTrue(
            "Whisper speech must not be squashed into silence (outRms >= 0.02f)",
            outRms >= 0.02f
        )

        // 2. trimSilence must preserve audible whisper without truncation
        val paddedWhisper = FloatArray(sampleRate * 3) { 0.0f }
        // Place 1.5s whisper in middle (0.8s to 2.3s)
        val startIdx = (sampleRate * 0.8).toInt()
        val endIdx = (sampleRate * 2.3).toInt()
        for (i in startIdx until endIdx) {
            val t = i.toDouble() / sampleRate
            paddedWhisper[i] = (0.012 * sin(2.0 * Math.PI * 600.0 * t)).toFloat()
        }

        val trimmed = AudioDspEngine.trimSilence(paddedWhisper, sampleRate, threshold = 0.003f)
        assertTrue("Trimmed whisper audio must not be empty", trimmed.isNotEmpty())
        assertTrue(
            "Trimmed whisper audio size (${trimmed.size}) must preserve the 1.5s speech burst",
            trimmed.size >= (sampleRate * 1.4)
        )
    }

    @Test
    fun testOverdrivenAudioPeakLimiterEnforcesCeiling() {
        // Heavily overdriven input: 3.5x to 4.0x full-scale amplitude
        val numSamples = sampleRate * 1 // 1.0 second
        val overdriven = FloatArray(numSamples) { i ->
            val t = i.toDouble() / sampleRate
            (3.8 * sin(2.0 * Math.PI * 300.0 * t)).toFloat()
        }

        val processed = AudioDspEngine.process(overdriven, sampleRate)
        val peak = AudioDspEngine.calculatePeak(processed)

        // Limiter ceiling strictly <= 0.999f
        assertTrue(
            "Peak amplitude ($peak) must strictly be <= 0.999f on heavily overdriven audio",
            peak <= 0.999f
        )

        // Zero NaN, Zero Infinity
        for (s in processed) {
            assertFalse("Output sample is NaN", s.isNaN())
            assertFalse("Output sample is Infinite", s.isInfinite())
        }

        // Test injected extreme impulses (+10.0f, -10.0f)
        val impulseAudio = FloatArray(sampleRate) { 0.0f }
        impulseAudio[100] = 10.0f
        impulseAudio[500] = -10.0f
        impulseAudio[2000] = 5.0f

        val impulseOut = AudioDspEngine.process(impulseAudio, sampleRate)
        val impulsePeak = AudioDspEngine.calculatePeak(impulseOut)
        assertTrue(
            "Impulse output peak ($impulsePeak) must be <= 0.999f",
            impulsePeak <= 0.999f
        )
        for (s in impulseOut) {
            assertFalse("Impulse response produced NaN", s.isNaN())
            assertFalse("Impulse response produced Inf", s.isInfinite())
        }
    }

    @Test
    fun test85HzHighPassFilterAttenuationAndSpeechPreservation() {
        // 1. Theoretical filter gain via frequency response formula
        val gain60Hz = AudioDspEngine.getFilterGainDb(60.0f, sampleRate)
        val gain50Hz = AudioDspEngine.getFilterGainDb(50.0f, sampleRate)
        val gain250Hz = AudioDspEngine.getFilterGainDb(250.0f, sampleRate)
        val gain1000Hz = AudioDspEngine.getFilterGainDb(1000.0f, sampleRate)

        assertTrue(
            "60Hz hum theoretical attenuation must be >= 6.0 dB (got $gain60Hz dB)",
            gain60Hz <= -6.0f
        )
        assertTrue(
            "50Hz rumble theoretical attenuation must be >= 8.0 dB (got $gain50Hz dB)",
            gain50Hz <= -8.0f
        )
        assertTrue(
            "250Hz voice frequency insertion loss must be < 0.5 dB (got $gain250Hz dB)",
            abs(gain250Hz) < 0.5f
        )
        assertTrue(
            "1000Hz voice formant insertion loss must be < 0.1 dB (got $gain1000Hz dB)",
            abs(gain1000Hz) < 0.1f
        )

        // 2. Time-domain IIR filter empirical verification on 60Hz sine wave
        val numSamples = sampleRate * 1
        val hum60Samples = FloatArray(numSamples) { i ->
            (0.30 * sin(2.0 * Math.PI * 60.0 * i / sampleRate)).toFloat()
        }
        val filteredHum = AudioDspEngine.applyHighPassFilter(hum60Samples)

        // Skip transient (first 1000 samples)
        val steadyIn = hum60Samples.copyOfRange(1000, numSamples)
        val steadyOut = filteredHum.copyOfRange(1000, numSamples)
        val inRms = AudioDspEngine.calculateRms(steadyIn)
        val outRms = AudioDspEngine.calculateRms(steadyOut)
        val empiricalGain60Db = 20.0f * kotlin.math.log10(outRms / inRms)

        assertTrue(
            "Empirical 60Hz IIR filter attenuation must be >= 6.0 dB (got $empiricalGain60Db dB)",
            empiricalGain60Db <= -6.0f
        )
    }

    @Test
    fun testNoiseGateSmoothCrossfadeTransitions() {
        val numSamples = sampleRate * 2
        val stepSignal = FloatArray(numSamples) { 0.0f }
        // Step from silence to speech at 0.5s (sample 8000)
        for (i in (sampleRate * 0.5).toInt() until (sampleRate * 1.5).toInt()) {
            stepSignal[i] = (0.25 * sin(2.0 * Math.PI * 400.0 * i / sampleRate)).toFloat()
        }

        val processed = AudioDspEngine.process(stepSignal, sampleRate)

        // Verify continuity around the transition boundary
        var maxDiff = 0.0f
        for (i in 7950 until 8100) {
            val diff = abs(processed[i + 1] - processed[i])
            if (diff > maxDiff) maxDiff = diff
        }

        assertTrue(
            "Noise gate step transition must be smooth (max diff = $maxDiff, expected < 0.45f)",
            maxDiff < 0.45f
        )
    }
}

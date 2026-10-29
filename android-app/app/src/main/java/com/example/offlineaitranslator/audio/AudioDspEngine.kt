package com.example.offlineaitranslator.audio

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * Production-grade Acoustic DSP Engine for 100% Offline Speech Translation.
 *
 * Implements:
 * 1. 85Hz High-Pass 2nd-order Butterworth biquad IIR filter (Transposed Direct Form II)
 *    to suppress table thumps, HVAC rumble, and electrical hum (<85Hz).
 * 2. Dynamic Software AGC with dual-ballistics envelope follower (10ms attack, 250ms release),
 *    noise gate at -45 dBFS, whisper boost up to +18.1 dB (8.0x).
 * 3. Soft-knee hyperbolic tangent peak limiter preventing digital clipping while preserving
 *    vocal dynamics for Whisper ASR and Emotion2Vec+ prosody extraction.
 */
object AudioDspEngine {

    // 85Hz High-Pass 2nd-Order Butterworth IIR filter coefficients for 16000 Hz sample rate
    // Derived via Bilinear Transform: fc = 85.0 Hz, fs = 16000.0 Hz, Q = 1/sqrt(2) ≈ 0.70710678
    const val B0: Float = 0.97667352f
    const val B1: Float = -1.95334703f
    const val B2: Float = 0.97667352f
    const val A1: Float = -1.95280283f
    const val A2: Float = 0.95389123f

    // Dynamic AGC & Whisper Normalizer Parameters
    const val TARGET_PEAK_LEVEL: Float = 0.55f        // -5.2 dBFS (healthy conversational peak level for Whisper ASR)
    const val TARGET_RMS_LEVEL: Float = 0.55f         // Kept for backward compatibility
    const val GATE_THRESHOLD: Float = 0.004f          // -48 dBFS (ambient noise gate floor)
    const val MAX_GAIN: Float = 8.0f                  // +18.06 dB whisper boost (8.0x to restore soft voices)
    const val MIN_GAIN: Float = 0.50f                 // -6.0 dB maximum shout attenuation (prevents crushing conversational speech)
    const val ATTACK_TIME_SEC: Float = 0.020f         // 20 ms smooth attack
    const val RELEASE_TIME_SEC: Float = 0.300f        // 300 ms smooth release
    const val LIMITER_KNEE_START: Float = 0.85f       // Soft-knee limiter threshold
    const val LIMITER_MAX_PEAK: Float = 0.999f        // Strictly ceiling output below 1.0 to prevent clipping

    /**
     * Telemetry metrics produced by the DSP pipeline.
     */
    data class DspMetrics(
        val inputRms: Float,
        val outputRms: Float,
        val gainAppliedDb: Float,
        val peakAmplitude: Float,
        val highPassApplied: Boolean,
        val agcApplied: Boolean
    )

    /**
     * Executes the complete acoustic conditioning pipeline:
     * 1. 85Hz High-Pass 2nd-order Butterworth IIR filtering.
     * 2. Dynamic software AGC & whisper compressor.
     * 3. Soft-knee tanh peak limiter.
     *
     * @param samples FloatArray of normalized audio samples [-1.0, 1.0]
     * @param sampleRate Input sample rate (expected 16000 Hz)
     * @return Conditioned FloatArray optimized for Whisper ASR and Emotion2Vec+
     */
    fun process(samples: FloatArray, sampleRate: Int = 16000): FloatArray {
        if (samples.isEmpty()) return samples

        val output = FloatArray(samples.size)

        // Stage 1: 85Hz 2nd-Order Biquad Butterworth IIR Filter
        // Eliminates table rumble, desk bumps, AC hum (<85Hz) via Transposed Direct Form II
        var s1 = 0.0f
        var s2 = 0.0f

        for (i in samples.indices) {
            val x = samples[i]
            val y = B0 * x + s1
            s1 = B1 * x - A1 * y + s2
            s2 = B2 * x - A2 * y
            output[i] = y
        }

        // Stage 2: Dynamic Software AGC & Whisper Normalizer
        val alphaAtt = 1.0f - exp(-1.0f / (sampleRate * ATTACK_TIME_SEC))
        val alphaRel = 1.0f - exp(-1.0f / (sampleRate * RELEASE_TIME_SEC))
        var envelope = 0.0f

        for (i in output.indices) {
            val absSample = abs(output[i])
            // Dual-ballistics envelope follower
            envelope = if (absSample > envelope) {
                (1.0f - alphaAtt) * envelope + alphaAtt * absSample
            } else {
                (1.0f - alphaRel) * envelope + alphaRel * absSample
            }

            // Compute dynamic gain with smooth noise gate transition
            val rawGain = (TARGET_PEAK_LEVEL / max(envelope, 1e-4f)).coerceIn(MIN_GAIN, MAX_GAIN)
            val gain = if (envelope < GATE_THRESHOLD) {
                val t = (envelope / GATE_THRESHOLD).coerceIn(0.0f, 1.0f)
                1.0f + t * t * (rawGain - 1.0f)
            } else {
                rawGain
            }

            val amplified = output[i] * gain

            // Stage 3: Soft-Knee Peak Limiter (tanh saturation)
            val absAmp = abs(amplified)
            output[i] = if (absAmp <= LIMITER_KNEE_START) {
                amplified
            } else {
                val s = sign(amplified)
                val headroom = 1.0f - LIMITER_KNEE_START
                s * (LIMITER_KNEE_START + headroom * tanh((absAmp - LIMITER_KNEE_START) / headroom))
            }.coerceIn(-LIMITER_MAX_PEAK, LIMITER_MAX_PEAK)
        }

        return output
    }

    /**
     * In-place DSP processing with zero additional heap allocations.
     * Ideal for real-time streaming buffers.
     */
    fun processInPlace(samples: FloatArray, sampleRate: Int = 16000) {
        if (samples.isEmpty()) return

        // Stage 1: High-Pass Butterworth IIR
        var s1 = 0.0f
        var s2 = 0.0f
        for (i in samples.indices) {
            val x = samples[i]
            val y = B0 * x + s1
            s1 = B1 * x - A1 * y + s2
            s2 = B2 * x - A2 * y
            samples[i] = y
        }

        // Stage 2 & 3: AGC & Limiter
        val alphaAtt = 1.0f - exp(-1.0f / (sampleRate * ATTACK_TIME_SEC))
        val alphaRel = 1.0f - exp(-1.0f / (sampleRate * RELEASE_TIME_SEC))
        var envelope = 0.0f

        for (i in samples.indices) {
            val absSample = abs(samples[i])
            envelope = if (absSample > envelope) {
                (1.0f - alphaAtt) * envelope + alphaAtt * absSample
            } else {
                (1.0f - alphaRel) * envelope + alphaRel * absSample
            }

            val rawGain = (TARGET_PEAK_LEVEL / max(envelope, 1e-4f)).coerceIn(MIN_GAIN, MAX_GAIN)
            val gain = if (envelope < GATE_THRESHOLD) {
                val t = (envelope / GATE_THRESHOLD).coerceIn(0.0f, 1.0f)
                1.0f + t * t * (rawGain - 1.0f)
            } else {
                rawGain
            }

            val amplified = samples[i] * gain
            val absAmp = abs(amplified)
            samples[i] = if (absAmp <= LIMITER_KNEE_START) {
                amplified
            } else {
                val s = sign(amplified)
                val headroom = 1.0f - LIMITER_KNEE_START
                s * (LIMITER_KNEE_START + headroom * tanh((absAmp - LIMITER_KNEE_START) / headroom))
            }.coerceIn(-LIMITER_MAX_PEAK, LIMITER_MAX_PEAK)
        }
    }

    /**
     * Executes the complete pipeline and computes comparative telemetry metrics.
     */
    fun processWithMetrics(samples: FloatArray, sampleRate: Int = 16000): Pair<FloatArray, DspMetrics> {
        val inRms = calculateRms(samples)
        val processed = process(samples, sampleRate)
        val outRms = calculateRms(processed)
        val peak = calculatePeak(processed)
        val gainAppliedDb = if (inRms > 1e-6f) {
            20.0f * log10(max(outRms, 1e-6f) / inRms)
        } else {
            0.0f
        }

        val metrics = DspMetrics(
            inputRms = inRms,
            outputRms = outRms,
            gainAppliedDb = gainAppliedDb,
            peakAmplitude = peak,
            highPassApplied = true,
            agcApplied = true
        )

        return Pair(processed, metrics)
    }

    /**
     * Applies only the 85Hz High-Pass 2nd-order Butterworth IIR filter.
     */
    fun applyHighPassFilter(samples: FloatArray): FloatArray {
        if (samples.isEmpty()) return samples
        val output = FloatArray(samples.size)
        var s1 = 0.0f
        var s2 = 0.0f

        for (i in samples.indices) {
            val x = samples[i]
            val y = B0 * x + s1
            s1 = B1 * x - A1 * y + s2
            s2 = B2 * x - A2 * y
            output[i] = y
        }
        return output
    }

    /**
     * Applies only the dynamic AGC and soft-knee peak limiter.
     */
    fun applyAgc(samples: FloatArray, sampleRate: Int = 16000): FloatArray {
        if (samples.isEmpty()) return samples
        val output = FloatArray(samples.size)
        val alphaAtt = 1.0f - exp(-1.0f / (sampleRate * ATTACK_TIME_SEC))
        val alphaRel = 1.0f - exp(-1.0f / (sampleRate * RELEASE_TIME_SEC))
        var envelope = 0.0f

        for (i in samples.indices) {
            val absSample = abs(samples[i])
            envelope = if (absSample > envelope) {
                (1.0f - alphaAtt) * envelope + alphaAtt * absSample
            } else {
                (1.0f - alphaRel) * envelope + alphaRel * absSample
            }

            val rawGain = (TARGET_PEAK_LEVEL / max(envelope, 1e-4f)).coerceIn(MIN_GAIN, MAX_GAIN)
            val gain = if (envelope < GATE_THRESHOLD) {
                val t = (envelope / GATE_THRESHOLD).coerceIn(0.0f, 1.0f)
                1.0f + t * t * (rawGain - 1.0f)
            } else {
                rawGain
            }

            val amplified = samples[i] * gain
            val absAmp = abs(amplified)
            output[i] = if (absAmp <= LIMITER_KNEE_START) {
                amplified
            } else {
                val s = sign(amplified)
                val headroom = 1.0f - LIMITER_KNEE_START
                s * (LIMITER_KNEE_START + headroom * tanh((absAmp - LIMITER_KNEE_START) / headroom))
            }.coerceIn(-LIMITER_MAX_PEAK, LIMITER_MAX_PEAK)
        }
        return output
    }

    /**
     * Computes the exact theoretical magnitude response (dB) of the 85Hz Butterworth filter
     * at a specified frequency.
     *
     * Transfer function: H(z) = (B0 + B1*z^-1 + B2*z^-2) / (1 + A1*z^-1 + A2*z^-2)
     */
    fun getFilterGainDb(frequencyHz: Float, sampleRate: Int = 16000): Float {
        val w = 2.0 * Math.PI * frequencyHz / sampleRate
        val cosW = cos(w)
        val sinW = sin(w)
        val cos2W = cos(2.0 * w)
        val sin2W = sin(2.0 * w)

        // Numerator: B0 + B1*(cosW - j sinW) + B2*(cos2W - j sin2W)
        val numReal = B0 + B1 * cosW + B2 * cos2W
        val numImag = -B1 * sinW - B2 * sin2W
        val numMagSq = numReal * numReal + numImag * numImag

        // Denominator: 1 + A1*(cosW - j sinW) + A2*(cos2W - j sin2W)
        val denReal = 1.0 + A1 * cosW + A2 * cos2W
        val denImag = -A1 * sinW - A2 * sin2W
        val denMagSq = denReal * denReal + denImag * denImag

        val magSq = numMagSq / denMagSq
        return (10.0 * log10(magSq)).toFloat()
    }

    /**
     * Computes the Root Mean Square (RMS) energy level of an audio slice.
     */
    fun calculateRms(samples: FloatArray): Float {
        if (samples.isEmpty()) return 0.0f
        var sumSquares = 0.0
        for (s in samples) {
            sumSquares += (s * s).toDouble()
        }
        return sqrt(sumSquares / samples.size).toFloat()
    }

    /**
     * Computes the absolute peak amplitude of an audio slice.
     */
    fun calculatePeak(samples: FloatArray): Float {
        var peak = 0.0f
        for (s in samples) {
            val a = abs(s)
            if (a > peak) peak = a
        }
        return peak
    }

    /**
     * Trims leading and trailing silence (frames where RMS < threshold)
     * with a 200ms safety pad so initial and trailing consonants are NEVER clipped.
     */
    fun trimSilence(samples: FloatArray, sampleRate: Int = 16000, threshold: Float = 0.003f): FloatArray {
        if (samples.isEmpty()) return samples
        val windowSize = sampleRate / 20 // 50ms window
        val safetyMargin = sampleRate / 5 // 200ms padding
        var start = 0
        var end = samples.size
        var foundSpeech = false

        for (i in 0 until samples.size step windowSize) {
            val endIdx = minOf(samples.size, i + windowSize)
            val slice = samples.sliceArray(i until endIdx)
            if (calculateRms(slice) > threshold) {
                start = maxOf(0, i - safetyMargin)
                foundSpeech = true
                break
            }
        }

        if (!foundSpeech) return FloatArray(0)

        for (i in samples.size downTo windowSize step windowSize) {
            val startIdx = maxOf(0, i - windowSize)
            val slice = samples.sliceArray(startIdx until i)
            if (calculateRms(slice) > threshold) {
                end = minOf(samples.size, i + safetyMargin)
                break
            }
        }

        if (start >= end) return FloatArray(0)
        return samples.copyOfRange(start, minOf(samples.size, end))
    }
}

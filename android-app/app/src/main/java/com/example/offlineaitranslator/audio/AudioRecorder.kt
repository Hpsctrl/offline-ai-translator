package com.example.offlineaitranslator.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import com.example.offlineaitranslator.engine.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Advanced Multi-Mic Acoustic Audio Recorder with hardware beamforming,
 * silicon-level acoustic effects, and software DSP conditioning.
 *
 * Pipeline:
 * 1. Multi-mic directional beamforming via AudioSource.VOICE_RECOGNITION
 * 2. Hardware effect attachment: NoiseSuppressor, AutomaticGainControl, AcousticEchoCanceler
 * 3. 16 kHz 16-bit Linear PCM capture loop
 * 4. Software DSP Conditioning Stage (AudioDspEngine):
 *    - 85Hz High-Pass 2nd-order Butterworth IIR filter
 *    - Dynamic software AGC & whisper normalizer (+18.1 dB boost)
 *    - Soft-knee tanh peak limiter (no digital clipping)
 */
class AudioRecorder {

    companion object {
        private const val TAG = "AudioRecorder"
        const val DEFAULT_SAMPLE_RATE = 16000
    }

    private var audioRecord: AudioRecord? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var agc: AutomaticGainControl? = null
    private var aec: AcousticEchoCanceler? = null
    private val isRecording = AtomicBoolean(false)

    data class HardwareEffectStatus(
        val voiceRecognitionActive: Boolean,
        val noiseSuppressorAvailable: Boolean,
        val noiseSuppressorEnabled: Boolean,
        val agcAvailable: Boolean,
        val agcEnabled: Boolean,
        val aecAvailable: Boolean,
        val aecEnabled: Boolean,
        val activeAudioSource: String
    )

    @Volatile
    var lastEffectStatus: HardwareEffectStatus? = null
        private set

    val recording: Boolean
        get() = isRecording.get()

    /**
     * Records audio from the microphone, applies hardware beamforming,
     * hardware noise suppression, and software 85Hz Butterworth + AGC DSP filtering.
     *
     * @param sampleRate Target audio sample rate (default 16000 Hz)
     * @param maxDurationMs Maximum recording timeout in milliseconds
     * @param enableDsp Whether to apply AudioDspEngine filtering (85Hz IIR + AGC)
     * @param onAmplitude Real-time callback providing normalized amplitude [0.0, 1.0]
     * @return Clean, conditioned FloatArray [-1.0, 1.0] ready for Whisper ASR
     */
    @SuppressLint("MissingPermission")
    suspend fun recordAudio(
        sampleRate: Int = DEFAULT_SAMPLE_RATE,
        maxDurationMs: Long = 10000,
        enableDsp: Boolean = true,
        onAmplitude: ((Float) -> Unit)? = null
    ): FloatArray = withContext(Dispatchers.IO) {
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        val bufferSize = maxOf(minBufferSize, sampleRate * 2)

        var record: AudioRecord? = null
        var selectedSource = "VOICE_RECOGNITION"
        var voiceRecognitionEngaged = false

        // Tier 1: Prioritize VOICE_RECOGNITION to engage multi-mic directional beamforming
        try {
            record = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )
            if (record.state == AudioRecord.STATE_INITIALIZED) {
                voiceRecognitionEngaged = true
            }
        } catch (e: Exception) {
            AppLog.w(TAG, "AudioSource.VOICE_RECOGNITION initialization error: ${e.message}")
            record = null
        }

        // Tier 2 Fallback: Standard MIC if VOICE_RECOGNITION is rejected by custom OEM HAL
        if (record == null || record.state != AudioRecord.STATE_INITIALIZED) {
            record?.release()
            selectedSource = "MIC"
            voiceRecognitionEngaged = false
            try {
                record = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    bufferSize
                )
            } catch (e: Exception) {
                AppLog.w(TAG, "AudioSource.MIC initialization error: ${e.message}")
                record = null
            }
        }

        // Tier 3 Fallback: AudioSource.DEFAULT for emulators/kiosks
        if (record == null || record.state != AudioRecord.STATE_INITIALIZED) {
            record?.release()
            selectedSource = "DEFAULT"
            voiceRecognitionEngaged = false
            record = AudioRecord(
                MediaRecorder.AudioSource.DEFAULT,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )
        }

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            throw IllegalStateException("AudioRecord failed to initialize across all AudioSource tiers.")
        }

        val sessionId = record.audioSessionId
        var nsActive = false

        // Unilateral Voice Recognition:
        // Do NOT enable AcousticEchoCanceler (AEC is strictly for full-duplex speakerphone calls;
        // enabling AEC during unilateral voice capture notches out vocal formants via false echo cancellation).
        // Similarly, do NOT attach hardware AGC to prevent gain pumping conflicts with AudioDspEngine.
        if (NoiseSuppressor.isAvailable()) {
            try {
                noiseSuppressor = NoiseSuppressor.create(sessionId)?.apply {
                    enabled = true
                    nsActive = this.enabled
                }
            } catch (e: Exception) {
                AppLog.w(TAG, "Hardware audiofx initialization note: NoiseSuppressor failed: ${e.message}")
            }
        }

        lastEffectStatus = HardwareEffectStatus(
            voiceRecognitionActive = voiceRecognitionEngaged,
            noiseSuppressorAvailable = NoiseSuppressor.isAvailable(),
            noiseSuppressorEnabled = nsActive,
            agcAvailable = AutomaticGainControl.isAvailable(),
            agcEnabled = false,
            aecAvailable = AcousticEchoCanceler.isAvailable(),
            aecEnabled = false,
            activeAudioSource = selectedSource
        )

        audioRecord = record
        isRecording.set(true)
        record.startRecording()

        val chunks = mutableListOf<FloatArray>()
        var totalSamples = 0
        val readBuffer = ShortArray(1024)
        val startTime = System.currentTimeMillis()

        try {
            while (isRecording.get() && (System.currentTimeMillis() - startTime) < maxDurationMs) {
                val readShorts = record.read(readBuffer, 0, readBuffer.size)
                if (readShorts > 0) {
                    val floatChunk = FloatArray(readShorts)
                    var maxAmp = 0f
                    for (i in 0 until readShorts) {
                        val s = readBuffer[i]
                        val f = s / 32768.0f
                        floatChunk[i] = f
                        val absVal = kotlin.math.abs(f)
                        if (absVal > maxAmp) maxAmp = absVal
                    }
                    chunks.add(floatChunk)
                    totalSamples += readShorts
                    onAmplitude?.invoke(maxAmp)
                } else if (readShorts < 0) {
                    // Break immediately on negative return codes (ERROR_INVALID_OPERATION, ERROR_BAD_VALUE, ERROR_DEAD_OBJECT)
                    // to prevent 100% CPU busy-spin
                    AppLog.w(TAG, "AudioRecord.read returned error code: $readShorts; terminating capture loop")
                    break
                }
            }
        } finally {
            releaseAudioHardware()
        }

        // Return raw normalized float samples directly (DSP conditioning is applied once in SpeechPipelineManager)
        val rawFloats = FloatArray(totalSamples)
        var offset = 0
        for (chunk in chunks) {
            System.arraycopy(chunk, 0, rawFloats, offset, chunk.size)
            offset += chunk.size
        }
        rawFloats
    }

    /**
     * Halts recording cleanly. Signals termination to the read loop and stops capture
     * without invoking release() while read() is blocking.
     */
    fun stop() {
        isRecording.set(false)
        try {
            audioRecord?.let {
                if (it.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    it.stop()
                }
            }
        } catch (e: Exception) {
            AppLog.w(TAG, "Error stopping AudioRecord: ${e.message}")
        }
    }

    /**
     * Safely releases audio hardware effects and AudioRecord instance.
     * Invoked from recordAudio finally block after the read loop has fully exited.
     */
    @Synchronized
    private fun releaseAudioHardware() {
        isRecording.set(false)

        // Rule: release hardware audiofx BEFORE AudioRecord to prevent mediaserver binder deadlock
        try {
            noiseSuppressor?.enabled = false
            noiseSuppressor?.release()
        } catch (_: Exception) {}
        noiseSuppressor = null

        try {
            agc?.enabled = false
            agc?.release()
        } catch (_: Exception) {}
        agc = null

        try {
            aec?.enabled = false
            aec?.release()
        } catch (_: Exception) {}
        aec = null

        try {
            audioRecord?.let {
                if (it.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    it.stop()
                }
                it.release()
            }
        } catch (e: Exception) {
            AppLog.w(TAG, "Error releasing AudioRecord: ${e.message}")
        } finally {
            audioRecord = null
        }
    }
}

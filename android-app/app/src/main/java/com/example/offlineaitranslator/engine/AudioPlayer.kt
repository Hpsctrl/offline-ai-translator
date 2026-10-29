package com.example.offlineaitranslator.engine

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AudioPlayer {
    private var currentTrack: AudioTrack? = null
    @Volatile
    private var isPlaying = false

    suspend fun play(samples: FloatArray, sampleRate: Int) = withContext(Dispatchers.IO) {
        stop()
        if (samples.isEmpty()) return@withContext

        val minBufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT
        )

        val bufferSize = maxOf(minBufferSize, samples.size * 4)

        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        val format = AudioFormat.Builder()
            .setSampleRate(sampleRate)
            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()

        val track = AudioTrack(
            attributes,
            format,
            bufferSize,
            AudioTrack.MODE_STATIC,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        )

        currentTrack = track
        isPlaying = true

        track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
        track.play()

        val durationMs = ((samples.size.toFloat() / sampleRate) * 1000).toLong()
        val startTime = System.currentTimeMillis()
        while (isPlaying && (System.currentTimeMillis() - startTime) < durationMs + 300) {
            Thread.sleep(50)
        }
        stop()
    }

    fun stop() {
        isPlaying = false
        try {
            currentTrack?.let {
                if (it.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    it.stop()
                }
                it.release()
            }
        } catch (e: Exception) {
            AppLog.w("AudioPlayer", "Error stopping track: ${e.message}")
        } finally {
            currentTrack = null
        }
    }
}

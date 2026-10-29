package com.example.offlineaitranslator.engine

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.provider.OpenableColumns
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

object AudioUtils {

    private const val TAG = "AudioUtils"
    const val TARGET_SAMPLE_RATE = 16000

    /**
     * Reads a 16-bit mono WAV file or stream and returns normalized FloatArray [-1.0, 1.0].
     */
    fun readWavToFloatArray(file: File): Pair<FloatArray, Int> {
        val bytes = file.readBytes()
        return readWavToFloatArray(bytes)
    }

    fun readWavToFloatArray(bytes: ByteArray): Pair<FloatArray, Int> {
        if (bytes.size < 44) {
            return Pair(FloatArray(0), TARGET_SAMPLE_RATE)
        }

        // Parse RIFF WAV header
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val riff = String(bytes, 0, 4)
        val wave = String(bytes, 8, 4)
        if (riff != "RIFF" || wave != "WAVE") {
            // Fallback: assume raw 16-bit PCM at 16000 Hz
            return Pair(pcm16ToFloatArray(bytes), TARGET_SAMPLE_RATE)
        }

        var offset = 12
        var sampleRate = TARGET_SAMPLE_RATE
        var channels = 1
        var bitsPerSample = 16
        var dataOffset = 44
        var dataSize = bytes.size - 44

        while (offset + 8 <= bytes.size) {
            val chunkId = String(bytes, offset, 4)
            val chunkSize = buffer.getInt(offset + 4)
            offset += 8
            if (chunkId == "fmt ") {
                channels = buffer.getShort(offset + 2).toInt().coerceAtLeast(1)
                sampleRate = buffer.getInt(offset + 4)
                bitsPerSample = buffer.getShort(offset + 14).toInt().coerceAtLeast(8)
                offset += chunkSize
            } else if (chunkId == "data") {
                dataOffset = offset
                dataSize = chunkSize.coerceAtMost(bytes.size - dataOffset)
                break
            } else {
                offset += chunkSize
            }
        }

        val bytesPerSample = bitsPerSample / 8
        val totalRawSamples = dataSize / bytesPerSample
        val numMonoSamples = totalRawSamples / channels
        val samples = FloatArray(numMonoSamples)
        val shortBuffer = ByteBuffer.wrap(bytes, dataOffset, dataSize).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()

        for (i in 0 until numMonoSamples) {
            val idx = i * channels
            if (idx < shortBuffer.limit()) {
                val sampleShort = shortBuffer.get(idx)
                samples[i] = sampleShort / 32768.0f
            }
        }

        val finalSamples = if (sampleRate != TARGET_SAMPLE_RATE && sampleRate > 0) {
            resample(samples, sampleRate, TARGET_SAMPLE_RATE)
        } else {
            samples
        }

        return Pair(finalSamples, TARGET_SAMPLE_RATE)
    }

    /**
     * Decodes any audio format (WAV, MP3, M4A, AAC, OGG, FLAC) from a content Uri into a 16kHz FloatArray.
     */
    fun decodeAudioUri(context: Context, uri: Uri): Pair<FloatArray, Int> {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return Pair(FloatArray(0), TARGET_SAMPLE_RATE)
            val bytes = inputStream.use { it.readBytes() }

            if (bytes.size >= 12 && String(bytes, 0, 4) == "RIFF" && String(bytes, 8, 4) == "WAVE") {
                readWavToFloatArray(bytes)
            } else {
                decodeCompressedAudio(context, uri)
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to decode audio URI: $uri", e)
            Pair(FloatArray(0), TARGET_SAMPLE_RATE)
        }
    }

    /**
     * Uses Android MediaExtractor + MediaCodec to decode compressed audio into raw PCM float samples.
     */
    private fun decodeCompressedAudio(context: Context, uri: Uri): Pair<FloatArray, Int> {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)
            var audioTrackIndex = -1
            var format: MediaFormat? = null

            for (i in 0 until extractor.trackCount) {
                val trackFormat = extractor.getTrackFormat(i)
                val mime = trackFormat.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    format = trackFormat
                    break
                }
            }

            if (audioTrackIndex == -1 || format == null) {
                return Pair(FloatArray(0), TARGET_SAMPLE_RATE)
            }

            extractor.selectTrack(audioTrackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
            val sampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            } else 44100
            val channelCount = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            } else 1

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val pcmStream = ByteArrayOutputStream()
            val bufferInfo = MediaCodec.BufferInfo()
            var isEos = false

            while (!isEos) {
                val inputIndex = codec.dequeueInputBuffer(10000L)
                if (inputIndex >= 0) {
                    val inputBuffer = codec.getInputBuffer(inputIndex)
                    if (inputBuffer != null) {
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            isEos = true
                        } else {
                            codec.queueInputBuffer(inputIndex, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                var outputIndex = codec.dequeueOutputBuffer(bufferInfo, 10000L)
                while (outputIndex >= 0) {
                    val outputBuffer = codec.getOutputBuffer(outputIndex)
                    if (outputBuffer != null && bufferInfo.size > 0) {
                        val chunk = ByteArray(bufferInfo.size)
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.get(chunk, 0, bufferInfo.size)
                        pcmStream.write(chunk)
                    }
                    codec.releaseOutputBuffer(outputIndex, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        isEos = true
                        break
                    }
                    outputIndex = codec.dequeueOutputBuffer(bufferInfo, 0L)
                }
            }

            val pcmBytes = pcmStream.toByteArray()
            val rawFloats = pcm16ToFloatArrayWithChannels(pcmBytes, channelCount)
            val finalSamples = if (sampleRate != TARGET_SAMPLE_RATE && sampleRate > 0) {
                resample(rawFloats, sampleRate, TARGET_SAMPLE_RATE)
            } else {
                rawFloats
            }

            return Pair(finalSamples, TARGET_SAMPLE_RATE)
        } finally {
            codec?.stop()
            codec?.release()
            extractor.release()
        }
    }

    /**
     * Converts multi-channel 16-bit PCM bytes to mono FloatArray.
     */
    fun pcm16ToFloatArrayWithChannels(pcmBytes: ByteArray, channels: Int): FloatArray {
        val ch = channels.coerceAtLeast(1)
        val numShorts = pcmBytes.size / 2
        val numMono = numShorts / ch
        val samples = FloatArray(numMono)
        val shortBuffer = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        for (i in 0 until numMono) {
            val idx = i * ch
            if (idx < shortBuffer.limit()) {
                samples[i] = shortBuffer.get(idx) / 32768.0f
            }
        }
        return samples
    }

    fun pcm16ToFloatArray(pcmBytes: ByteArray): FloatArray {
        val numSamples = pcmBytes.size / 2
        val floatSamples = FloatArray(numSamples)
        val buffer = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        for (i in 0 until numSamples) {
            floatSamples[i] = buffer.get(i) / 32768.0f
        }
        return floatSamples
    }

    fun floatArrayToPcm16(floatSamples: FloatArray): ByteArray {
        val pcmBytes = ByteArray(floatSamples.size * 2)
        val buffer = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN)
        for (sample in floatSamples) {
            val clamped = sample.coerceIn(-1.0f, 1.0f)
            val shortVal = (clamped * 32767.0f).toInt().toShort()
            buffer.putShort(shortVal)
        }
        return pcmBytes
    }

    /**
     * Resamples float array using linear interpolation.
     */
    fun resample(input: FloatArray, inRate: Int, outRate: Int): FloatArray {
        if (inRate == outRate || input.isEmpty()) return input
        val ratio = inRate.toDouble() / outRate.toDouble()
        val outLen = (input.size / ratio).toInt()
        val output = FloatArray(outLen)
        for (i in 0 until outLen) {
            val inIndex = i * ratio
            val index0 = inIndex.toInt()
            val frac = (inIndex - index0).toFloat()
            val s0 = input.getOrElse(index0) { 0f }
            val s1 = input.getOrElse(index0 + 1) { s0 }
            output[i] = s0 + frac * (s1 - s0)
        }
        return output
    }

    fun getFileName(context: Context, uri: Uri): String {
        var name = "audio_file"
        try {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        name = it.getString(nameIndex) ?: "audio_file"
                    }
                }
            }
        } catch (_: Exception) {}
        return name
    }
}

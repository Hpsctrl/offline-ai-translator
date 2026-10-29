package com.example.offlineaitranslator.engine

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.File
import java.nio.FloatBuffer
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sqrt

data class EmotionResult(
    val emotion: String,
    val confidence: Float,
    val scores: Map<String, Float>,
    val latencyMs: Long,
    val audioDurationSec: Float
)

class Emotion2VecEngine(
    private val modelDir: File,
    private val ortEnv: OrtEnvironment = OrtEnvironment.getEnvironment()
) : AutoCloseable {

    private var session: OrtSession? = null
    private val windowSamples = 48000 // 3 seconds at 16kHz
    private val temperature = 0.0375f

    companion object {
        val TARGET_EMOTIONS = listOf(
            "angry", "disgusted", "fearful", "happy", "neutral", "other", "sad", "surprised", "unknown"
        )

        private val POSES_ORDER = listOf(
            "calm", "content", "warm", "happy", "amused", "excited",
            "surprised", "smug", "confused", "skeptical", "annoyed",
            "disgusted", "afraid", "angry", "tense", "sad", "weary", "bored"
        )

        private val POSE_TO_EMOTION_MAP = mapOf(
            "calm" to "neutral",
            "content" to "happy",
            "warm" to "happy",
            "happy" to "happy",
            "amused" to "happy",
            "excited" to "happy",
            "surprised" to "surprised",
            "smug" to "surprised",
            "confused" to "surprised",
            "skeptical" to "surprised",
            "annoyed" to "angry",
            "disgusted" to "disgusted",
            "afraid" to "fearful",
            "angry" to "angry",
            "tense" to "angry",
            "sad" to "sad",
            "weary" to "sad",
            "bored" to "neutral"
        )
    }

    fun initialize() {
        if (session != null) return
        val modelFile = File(modelDir, "model.onnx")
        if (!modelFile.exists()) {
            throw NoSuchFileException(modelFile, null, "emotion2vec+ model.onnx not found")
        }
        val options = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(2)
            setInterOpNumThreads(2)
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        }
        session = ortEnv.createSession(modelFile.absolutePath, options)
        AppLog.d("Emotion2Vec", "Model loaded from ${modelFile.absolutePath}")
    }

    fun analyzeEmotion(samples: FloatArray): EmotionResult {
        initialize()
        val currentSession = session ?: error("Session not initialized")
        val t0 = System.currentTimeMillis()
        val audioDur = max(0.1f, samples.size / 16000.0f)

        // Check for silence or near zero energy
        var sumSquares = 0.0
        for (s in samples) {
            sumSquares += (s * s)
        }
        val rms = if (samples.isNotEmpty()) sqrt(sumSquares / samples.size) else 0.0
        if (samples.size < 800 || rms < 0.001) {
            val baseline = TARGET_EMOTIONS.associateWith { 0.01f }.toMutableMap()
            baseline["neutral"] = 0.90f
            val latency = System.currentTimeMillis() - t0
            return EmotionResult("neutral", 0.90f, baseline, latency, audioDur)
        }

        // Chunk into windows of 48000 samples with 50% overlap (24000 samples hop)
        val windows = mutableListOf<FloatArray>()
        if (samples.size <= windowSamples) {
            val padded = FloatArray(windowSamples)
            System.arraycopy(samples, 0, padded, 0, samples.size)
            windows.add(padded)
        } else {
            val hop = windowSamples / 2
            var start = 0
            while (start <= samples.size - windowSamples) {
                val win = FloatArray(windowSamples)
                System.arraycopy(samples, start, win, 0, windowSamples)
                windows.add(win)
                start += hop
            }
            if (samples.size % hop != 0 && samples.size >= windowSamples) {
                val finalWin = FloatArray(windowSamples)
                System.arraycopy(samples, samples.size - windowSamples, finalWin, 0, windowSamples)
                windows.add(finalWin)
            }
        }

        val allProbs = mutableListOf<FloatArray>()
        val shape = longArrayOf(1, windowSamples.toLong())

        for (win in windows) {
            val tensor = OnnxTensor.createTensor(ortEnv, FloatBuffer.wrap(win), shape)
            val outputs = currentSession.run(mapOf("waveform" to tensor))
            val cosinesTensor = outputs[0] as OnnxTensor
            val cosinesRaw = cosinesTensor.floatBuffer
            val cosines = FloatArray(cosinesRaw.remaining())
            cosinesRaw.get(cosines)

            // Scaled softmax
            val scaled = FloatArray(cosines.size)
            var maxVal = Float.NEGATIVE_INFINITY
            for (i in cosines.indices) {
                scaled[i] = cosines[i] / temperature
                if (scaled[i] > maxVal) maxVal = scaled[i]
            }

            var sumExp = 0.0f
            val exps = FloatArray(scaled.size)
            for (i in scaled.indices) {
                exps[i] = exp(scaled[i] - maxVal)
                sumExp += exps[i]
            }
            val probs = FloatArray(scaled.size)
            for (i in scaled.indices) {
                probs[i] = exps[i] / sumExp
            }
            allProbs.add(probs)
            tensor.close()
            outputs.close()
        }

        // Average across windows
        val numPoses = POSES_ORDER.size
        val avgPoseProbs = FloatArray(numPoses)
        for (p in allProbs) {
            for (i in 0 until numPoses) {
                avgPoseProbs[i] += p[i] / allProbs.size
            }
        }

        // Map 18 poses to 9 emotions
        val emotionScores = TARGET_EMOTIONS.associateWith { 0.0f }.toMutableMap()
        for (i in POSES_ORDER.indices) {
            val target = POSE_TO_EMOTION_MAP[POSES_ORDER[i]] ?: "other"
            emotionScores[target] = (emotionScores[target] ?: 0.0f) + avgPoseProbs[i]
        }

        var total = 0.0f
        for (score in emotionScores.values) total += score
        if (total > 0f) {
            for (key in emotionScores.keys.toList()) {
                emotionScores[key] = (emotionScores[key] ?: 0f) / total
            }
        }

        var topEmotion = "neutral"
        var topConfidence = 0.0f
        for ((emo, conf) in emotionScores) {
            if (conf > topConfidence) {
                topConfidence = conf
                topEmotion = emo
            }
        }

        val latency = System.currentTimeMillis() - t0
        return EmotionResult(topEmotion, topConfidence, emotionScores, latency, audioDur)
    }

    override fun close() {
        session?.close()
        session = null
    }
}

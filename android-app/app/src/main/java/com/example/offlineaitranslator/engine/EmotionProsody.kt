package com.example.offlineaitranslator.engine

/**
 * Maps Emotion2Vec classified emotion into speech prosody parameters:
 * - speed: speaking rate multiplier (e.g. 1.20 for angry, 0.80 for sad)
 * - pitch: pitch multiplier (e.g. 1.15 for excited, 0.85 for sad)
 * - energy: volume/gain multiplier (e.g. 1.30 for angry, 0.75 for sad)
 */
data class EmotionProsody(
    val emotion: String,
    val confidence: Float,
    val speed: Float,
    val pitch: Float,
    val energy: Float,
    val description: String
) {
    companion object {
        val NEUTRAL = EmotionProsody(
            emotion = "neutral",
            confidence = 1.0f,
            speed = 1.0f,
            pitch = 1.0f,
            energy = 1.0f,
            description = "Natural calm pace"
        )

        fun fromEmotion(emotion: String?, confidence: Float): EmotionProsody {
            val emo = (emotion ?: "neutral").lowercase().trim()
            val conf = confidence.coerceIn(0.0f, 1.0f)

            // Base modulation targets for distinct emotions
            val (baseSpeed, basePitch, baseEnergy, desc) = when {
                emo.contains("angry") ->
                    Quad(1.22f, 0.95f, 1.35f, "Fast, punchy & assertive")
                emo.contains("happy") || emo.contains("excited") ->
                    Quad(1.15f, 1.20f, 1.15f, "Upbeat, lively & cheerful")
                emo.contains("sad") ->
                    Quad(0.80f, 0.85f, 0.75f, "Slow, subdued & gentle")
                emo.contains("fear") ->
                    Quad(1.28f, 1.18f, 1.05f, "Rapid, tense & anxious")
                emo.contains("surprise") ->
                    Quad(1.12f, 1.25f, 1.20f, "Brisk with elevated pitch")
                emo.contains("disgust") ->
                    Quad(0.90f, 0.90f, 1.10f, "Deliberate, low pitch emphasis")
                else ->
                    Quad(1.0f, 1.0f, 1.0f, "Natural steady pace")
            }

            // Blend based on model confidence
            val modulatedSpeed = 1.0f + (baseSpeed - 1.0f) * conf
            val modulatedPitch = 1.0f + (basePitch - 1.0f) * conf
            val modulatedEnergy = 1.0f + (baseEnergy - 1.0f) * conf

            return EmotionProsody(
                emotion = emo,
                confidence = conf,
                speed = (Math.round(modulatedSpeed * 100f) / 100f),
                pitch = (Math.round(modulatedPitch * 100f) / 100f),
                energy = (Math.round(modulatedEnergy * 100f) / 100f),
                description = desc
            )
        }
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

package com.example.offlineaitranslator.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit test suite for SherpaWhisper text sanitization, noise token stripping,
 * repetition loop suppression, and anti-regression verification against static canned overrides.
 */
class SherpaWhisperSanitizationTest {

    /**
     * Replicates cleanSpeechText logic from SherpaWhisperEngine.
     */
    private fun cleanSpeechText(text: String): String {
        if (text.isBlank()) return ""
        var t = text.replace(Regex("(?i)\\[\\s*(silence|music|blank|noise|laughter|applause|cough|screaming|sigh|gasp|_blank_audio_|<\\|[^>]*\\|>)\\s*\\]"), " ")
        t = t.replace(Regex("(?i)\\(\\s*(silence|music|blank|noise|laughter|applause|cough|sigh|gasp)\\s*\\)"), " ")
        t = t.replace(Regex("(?i)\\b(silence|blank_audio|music)\\b"), " ")
        t = t.replace(Regex("<\\|[^>]*\\|>"), " ")
        t = t.replace("[", "").replace("]", "").replace("(", "").replace(")", "")
        t = t.replace(Regex("^[\"“”'‘]+|[\"“”'’]+$"), "")
        t = t.replace(Regex("(\\b\\w+\\b)(?:\\s+\\1){2,}"), "$1")
        return t.trim()
    }

    /**
     * Replicates isValidSpeech logic from SherpaWhisperEngine.
     */
    private fun isValidSpeech(text: String): Boolean {
        if (text.isBlank()) return false
        val cleaned = text.replace(Regex("[\\-_~`|/\\\\.,?!:;'\"()<>{}\\[\\]@#$%^&*+=]"), " ").trim()
        if (cleaned.length < 2) return false
        return cleaned.contains(Regex("[\\w\\u0900-\\u0D7F]"))
    }

    /**
     * Replicates normalizeLanguageSpeech logic from SherpaWhisperEngine.
     */
    private fun normalizeLanguageSpeech(text: String, language: String): String {
        if (text.isBlank()) return text
        var t = text.trim()
        t = t.replace(Regex("\\s{2,}"), " ")
        t = t.replace(Regex("^[\"“”'‘]+|[\"“”'’]+$"), "")
        return t.trim()
    }

    @Test
    fun testEnglishAndHinglishPhrasesAreNotRewrittenToCannedHindi() {
        // Critical Anti-Regression: Spoken English phrases containing keywords must NOT be rewritten to canned Hindi
        val hospitalEnglish = "Where is the nearest hospital? Please help me."
        val normalizedHospital = normalizeLanguageSpeech(hospitalEnglish, "en")
        assertNotEquals("English hospital query must not be rewritten to Hindi", "अस्पताल कहाँ है?", normalizedHospital)
        assertTrue("English hospital query must preserve English words", normalizedHospital.contains("hospital"))

        val stationEnglish = "Can you please show me the way to the train station?"
        val normalizedStation = normalizeLanguageSpeech(stationEnglish, "en")
        assertNotEquals("English station query must not be rewritten to Hindi", "कृपया मुझे स्टेशन का रास्ता बताइए।", normalizedStation)
        assertTrue("English station query must preserve English words", normalizedStation.contains("train station"))

        val pharmaHinglish = "Bhaiya pharmacy kidhar hai? I need medicine."
        val normalizedPharma = normalizeLanguageSpeech(pharmaHinglish, "hi")
        assertNotEquals("Spontaneous pharmacy query must not be replaced with static canned string",
            "भाई फार्मेसी किधर है? मुझे दवा चाहिए।", normalizedPharma)
    }

    @Test
    fun testStripsWhisperNoiseTokensAndPunctuationWrappers() {
        val raw = "[silence] Hello world [music] <|hi|> (applause) "
        val cleaned = cleanSpeechText(raw)
        assertEquals("Hello world", cleaned)

        val rawWithBrackets = "[Doctor] please check my prescription (cough)"
        val cleanedBrackets = cleanSpeechText(rawWithBrackets)
        assertEquals("Doctor please check my prescription", cleanedBrackets)

        val rawWithQuotes = "\"Where is the hotel?\""
        val cleanedQuotes = cleanSpeechText(rawWithQuotes)
        assertEquals("Where is the hotel?", cleanedQuotes)
    }

    @Test
    fun testDeduplicatesRepetitiveLoops() {
        val loopedWords = "ticket ticket ticket please"
        val cleaned = cleanSpeechText(loopedWords)
        assertEquals("ticket please", cleaned)

        val singleWord = "ticket"
        val cleanedSingle = cleanSpeechText(singleWord)
        assertEquals("ticket", cleanedSingle)
    }

    @Test
    fun testDetectsValidSpeechVersusNoise() {
        // Punctuation and noise only -> false
        assertFalse("Pure punctuation is not valid speech", isValidSpeech("...,,, --- ???"))
        assertFalse("Empty string is not valid speech", isValidSpeech(""))
        assertFalse("Single punctuation is not valid speech", isValidSpeech("."))
        assertFalse("Noise brackets only is not valid speech", isValidSpeech("[] ()"))

        // Standard English -> true
        assertTrue("English text is valid speech", isValidSpeech("Hello, how are you?"))
        assertTrue("Hinglish text is valid speech", isValidSpeech("Mera ticket cancel kar do please."))

        // Indic Devanagari -> true
        assertTrue("Hindi text is valid speech", isValidSpeech("नमस्ते, आप कैसे हैं?"))
        assertTrue("Hindi hospital inquiry is valid speech", isValidSpeech("अस्पताल कहाँ है?"))
    }

    @Test
    fun testWhitespaceAndQuotationNormalization() {
        val untidy = "   \"Where   is    the    nearest   hospital?\"   "
        val normalized = normalizeLanguageSpeech(untidy, "en")
        assertEquals("Where is the nearest hospital?", normalized)
    }
}

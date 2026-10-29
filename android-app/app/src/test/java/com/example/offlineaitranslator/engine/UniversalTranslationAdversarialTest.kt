package com.example.offlineaitranslator.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Empirical Adversarial Stress Test Suite for Universal Offline Translation Engine.
 *
 * Specifically challenges:
 * 1. Global non-Indian languages (Chinese, French, Spanish, German, Japanese, Russian, Arabic) to/from English & Hindi.
 * 2. Cross-family translations between non-Indian languages via English pivot.
 * 3. Verifying that NO tested language pair returns verbatim untranslated input text.
 * 4. Token lexicon fallbacks (verifying absence of the previous fatal guard: "if (srcLang != ENGLISH) return text").
 * 5. Edge cases: empty strings, whitespace, numbers, punctuation, OOV words, identity translation.
 * 6. High-concurrency stress testing across multiple coroutines.
 */
class UniversalTranslationAdversarialTest {

    private lateinit var translationManager: TranslationManager

    @Before
    fun setUp() {
        translationManager = TranslationManager()
    }

    // =========================================================================
    // 1. Non-Indian Languages To/From English (Bidirectional)
    // =========================================================================

    @Test
    fun testNonIndianToEnglish_NoVerbatimEcho() = runBlocking {
        val testCases = listOf(
            Triple("您好", SupportedLanguage.CHINESE, "Hello"),
            Triple("Bonjour", SupportedLanguage.FRENCH, "Hello"),
            Triple("Hola", SupportedLanguage.SPANISH, "Hello"),
            Triple("Hallo", SupportedLanguage.GERMAN, "Hello"),
            Triple("こんにちは", SupportedLanguage.JAPANESE, "Hello"),
            Triple("Здравствуйте", SupportedLanguage.RUSSIAN, "Hello"),
            Triple("مرحباً", SupportedLanguage.ARABIC, "Hello"),
            Triple("谢谢", SupportedLanguage.CHINESE, "Thank you"),
            Triple("Merci", SupportedLanguage.FRENCH, "Thank you"),
            Triple("Gracias", SupportedLanguage.SPANISH, "Thank you"),
            Triple("Danke", SupportedLanguage.GERMAN, "Thank you"),
            Triple("ありがとうございます", SupportedLanguage.JAPANESE, "Thank you"),
            Triple("Спасибо", SupportedLanguage.RUSSIAN, "Thank you"),
            Triple("شكراً", SupportedLanguage.ARABIC, "Thank you")
        )

        for ((input, srcLang, expectedSubstr) in testCases) {
            val result = translationManager.translate(input, srcLang, SupportedLanguage.ENGLISH)
            assertNotEquals(
                "Translation for ${srcLang.displayName} -> English returned verbatim input!",
                input.trim().lowercase(),
                result.translatedText.trim().lowercase()
            )
            assertTrue(
                "Expected '$expectedSubstr' in translation of '$input' (${srcLang.displayName} -> English), but got '${result.translatedText}'",
                result.translatedText.contains(expectedSubstr, ignoreCase = true)
            )
        }
    }

    @Test
    fun testEnglishToNonIndian_NoVerbatimEcho() = runBlocking {
        val testCases = listOf(
            Triple("Hello", SupportedLanguage.CHINESE, "您好"),
            Triple("Hello", SupportedLanguage.FRENCH, "Bonjour"),
            Triple("Hello", SupportedLanguage.SPANISH, "Hola"),
            Triple("Hello", SupportedLanguage.GERMAN, "Hallo"),
            Triple("Hello", SupportedLanguage.JAPANESE, "こんにちは"),
            Triple("Hello", SupportedLanguage.RUSSIAN, "Здравствуйте"),
            Triple("Hello", SupportedLanguage.ARABIC, "مرحباً"),
            Triple("Thank you", SupportedLanguage.CHINESE, "谢谢"),
            Triple("Thank you", SupportedLanguage.FRENCH, "Merci"),
            Triple("Thank you", SupportedLanguage.SPANISH, "Gracias"),
            Triple("Thank you", SupportedLanguage.GERMAN, "Danke"),
            Triple("Thank you", SupportedLanguage.JAPANESE, "ありがとう"),
            Triple("Thank you", SupportedLanguage.RUSSIAN, "Спасибо"),
            Triple("Thank you", SupportedLanguage.ARABIC, "شكراً")
        )

        for ((input, tgtLang, expectedSubstr) in testCases) {
            val result = translationManager.translate(input, SupportedLanguage.ENGLISH, tgtLang)
            assertNotEquals(
                "Translation for English -> ${tgtLang.displayName} returned verbatim input!",
                input.trim().lowercase(),
                result.translatedText.trim().lowercase()
            )
            assertTrue(
                "Expected '$expectedSubstr' in translation of '$input' (English -> ${tgtLang.displayName}), but got '${result.translatedText}'",
                result.translatedText.contains(expectedSubstr, ignoreCase = true)
            )
        }
    }

    // =========================================================================
    // 2. Non-Indian Languages To/From Hindi (Bidirectional via English Pivot)
    // =========================================================================

    @Test
    fun testHindiToNonIndian_ViaEnglishPivot_NoVerbatimEcho() = runBlocking {
        val testCases = listOf(
            Triple("नमस्ते", SupportedLanguage.CHINESE, "您好"),
            Triple("नमस्ते", SupportedLanguage.FRENCH, "Bonjour"),
            Triple("नमस्ते", SupportedLanguage.SPANISH, "Hola"),
            Triple("नमस्ते", SupportedLanguage.GERMAN, "Hallo"),
            Triple("नमस्ते", SupportedLanguage.JAPANESE, "こんにちは"),
            Triple("नमस्ते", SupportedLanguage.RUSSIAN, "Здравствуйте"),
            Triple("नमस्ते", SupportedLanguage.ARABIC, "مرحباً"),
            Triple("धन्यवाद", SupportedLanguage.CHINESE, "谢谢"),
            Triple("धन्यवाद", SupportedLanguage.FRENCH, "Merci"),
            Triple("धन्यवाद", SupportedLanguage.SPANISH, "Gracias"),
            Triple("धन्यवाद", SupportedLanguage.GERMAN, "Danke"),
            Triple("धन्यवाद", SupportedLanguage.JAPANESE, "ありがとう"),
            Triple("धन्यवाद", SupportedLanguage.RUSSIAN, "Спасибо"),
            Triple("धन्यवाद", SupportedLanguage.ARABIC, "شكراً")
        )

        for ((input, tgtLang, expectedSubstr) in testCases) {
            val result = translationManager.translate(input, SupportedLanguage.HINDI, tgtLang)
            assertNotEquals(
                "Translation for Hindi -> ${tgtLang.displayName} returned verbatim input!",
                input.trim(),
                result.translatedText.trim()
            )
            assertFalse(
                "Translation for Hindi -> ${tgtLang.displayName} returned untranslated Devanagari text!",
                result.translatedText.contains("नमस्ते") || result.translatedText.contains("धन्यवाद")
            )
            assertTrue(
                "Expected '$expectedSubstr' in translation of '$input' (Hindi -> ${tgtLang.displayName}), but got '${result.translatedText}'",
                result.translatedText.contains(expectedSubstr, ignoreCase = true)
            )
        }
    }

    @Test
    fun testNonIndianToHindi_ViaEnglishPivot_NoVerbatimEcho() = runBlocking {
        val testCases = listOf(
            Triple("您好", SupportedLanguage.CHINESE, "नमस्ते"),
            Triple("Bonjour", SupportedLanguage.FRENCH, "नमस्ते"),
            Triple("Hola", SupportedLanguage.SPANISH, "नमस्ते"),
            Triple("Hallo", SupportedLanguage.GERMAN, "नमस्ते"),
            Triple("こんにちは", SupportedLanguage.JAPANESE, "नमस्ते"),
            Triple("Здравствуйте", SupportedLanguage.RUSSIAN, "नमस्ते"),
            Triple("مرحباً", SupportedLanguage.ARABIC, "नमस्ते"),
            Triple("谢谢", SupportedLanguage.CHINESE, "धन्यवाद"),
            Triple("Merci", SupportedLanguage.FRENCH, "धन्यवाद"),
            Triple("Gracias", SupportedLanguage.SPANISH, "धन्यवाद"),
            Triple("Danke", SupportedLanguage.GERMAN, "धन्यवाद"),
            Triple("ありがとうございます", SupportedLanguage.JAPANESE, "धन्यवाद"),
            Triple("Спасибо", SupportedLanguage.RUSSIAN, "धन्यवाद"),
            Triple("شكراً", SupportedLanguage.ARABIC, "धन्यवाद")
        )

        for ((input, srcLang, expectedSubstr) in testCases) {
            val result = translationManager.translate(input, srcLang, SupportedLanguage.HINDI)
            assertNotEquals(
                "Translation for ${srcLang.displayName} -> Hindi returned verbatim input!",
                input.trim(),
                result.translatedText.trim()
            )
            assertTrue(
                "Expected '$expectedSubstr' in translation of '$input' (${srcLang.displayName} -> Hindi), but got '${result.translatedText}'",
                result.translatedText.contains(expectedSubstr)
            )
        }
    }

    // =========================================================================
    // 3. Cross-Family Global Languages (Non-Indian to Non-Indian)
    // =========================================================================

    @Test
    fun testCrossFamilyGlobalTranslations_NoVerbatimEcho() = runBlocking {
        val crossFamilyCases = listOf(
            // French -> Chinese & German
            Triple("Bonjour", SupportedLanguage.FRENCH, SupportedLanguage.CHINESE),
            Triple("Bonjour", SupportedLanguage.FRENCH, SupportedLanguage.GERMAN),
            // German -> Japanese & Spanish
            Triple("Hallo", SupportedLanguage.GERMAN, SupportedLanguage.JAPANESE),
            Triple("Hallo", SupportedLanguage.GERMAN, SupportedLanguage.SPANISH),
            // Spanish -> Russian & Arabic
            Triple("Hola", SupportedLanguage.SPANISH, SupportedLanguage.RUSSIAN),
            Triple("Hola", SupportedLanguage.SPANISH, SupportedLanguage.ARABIC),
            // Chinese -> French & Japanese
            Triple("您好", SupportedLanguage.CHINESE, SupportedLanguage.FRENCH),
            Triple("您好", SupportedLanguage.CHINESE, SupportedLanguage.JAPANESE),
            // Japanese -> Spanish & German
            Triple("こんにちは", SupportedLanguage.JAPANESE, SupportedLanguage.SPANISH),
            Triple("こんにちは", SupportedLanguage.JAPANESE, SupportedLanguage.GERMAN),
            // Russian -> Chinese & Arabic
            Triple("Здравствуйте", SupportedLanguage.RUSSIAN, SupportedLanguage.CHINESE),
            Triple("Здравствуйте", SupportedLanguage.RUSSIAN, SupportedLanguage.ARABIC),
            // Arabic -> French & Russian
            Triple("مرحباً", SupportedLanguage.ARABIC, SupportedLanguage.FRENCH),
            Triple("مرحباً", SupportedLanguage.ARABIC, SupportedLanguage.RUSSIAN)
        )

        for ((input, srcLang, tgtLang) in crossFamilyCases) {
            val result = translationManager.translate(input, srcLang, tgtLang)
            assertNotEquals(
                "Cross-family translation (${srcLang.displayName} -> ${tgtLang.displayName}) returned verbatim untranslated text!",
                input.trim(),
                result.translatedText.trim()
            )
            assertTrue(
                "Cross-family translation (${srcLang.displayName} -> ${tgtLang.displayName}) output must not be empty",
                result.translatedText.isNotBlank()
            )
        }
    }

    // =========================================================================
    // 4. Token Lexicon Fallback (Verifying Removal of Fatal Guard)
    // =========================================================================

    @Test
    fun testTokenLexiconFallback_NonIndianVocabulary() = runBlocking {
        // Words tested via lexicon rather than multi-word phrasebook
        val lexCases = listOf(
            // English -> Target
            Triple("water", SupportedLanguage.ENGLISH, SupportedLanguage.FRENCH),
            Triple("water", SupportedLanguage.ENGLISH, SupportedLanguage.GERMAN),
            Triple("water", SupportedLanguage.ENGLISH, SupportedLanguage.SPANISH),
            Triple("water", SupportedLanguage.ENGLISH, SupportedLanguage.CHINESE),
            Triple("water", SupportedLanguage.ENGLISH, SupportedLanguage.JAPANESE),
            Triple("water", SupportedLanguage.ENGLISH, SupportedLanguage.RUSSIAN),
            Triple("water", SupportedLanguage.ENGLISH, SupportedLanguage.ARABIC),
            // Target -> English
            Triple("eau", SupportedLanguage.FRENCH, SupportedLanguage.ENGLISH),
            Triple("Wasser", SupportedLanguage.GERMAN, SupportedLanguage.ENGLISH),
            Triple("agua", SupportedLanguage.SPANISH, SupportedLanguage.ENGLISH),
            Triple("вода", SupportedLanguage.RUSSIAN, SupportedLanguage.ENGLISH),
            Triple("ماء", SupportedLanguage.ARABIC, SupportedLanguage.ENGLISH),
            // Non-English -> Non-English via English pivot
            Triple("eau", SupportedLanguage.FRENCH, SupportedLanguage.GERMAN),
            Triple("Wasser", SupportedLanguage.GERMAN, SupportedLanguage.SPANISH),
            Triple("agua", SupportedLanguage.SPANISH, SupportedLanguage.RUSSIAN)
        )

        for ((input, srcLang, tgtLang) in lexCases) {
            val result = translationManager.translate(input, srcLang, tgtLang)
            assertNotEquals(
                "Lexicon translation for '$input' (${srcLang.displayName} -> ${tgtLang.displayName}) returned verbatim input! Fatal guard may be active.",
                input.trim().lowercase(),
                result.translatedText.trim().lowercase()
            )
            assertTrue(
                "Lexicon translation for '$input' (${srcLang.displayName} -> ${tgtLang.displayName}) returned blank string",
                result.translatedText.isNotBlank()
            )
        }
    }

    // =========================================================================
    // 5. Edge Cases & Boundary Conditions
    // =========================================================================

    @Test
    fun testEdgeCases_EmptyAndWhitespace() = runBlocking {
        // Empty string
        val emptyRes = translationManager.translate("", SupportedLanguage.FRENCH, SupportedLanguage.CHINESE)
        assertEquals("", emptyRes.translatedText)

        // Whitespace string
        val blankRes = translationManager.translate("   \t\n  ", SupportedLanguage.GERMAN, SupportedLanguage.JAPANESE)
        assertEquals("", blankRes.translatedText)
    }

    @Test
    fun testEdgeCases_IdentityTranslation() = runBlocking {
        // Source and Target are identical
        val sameLangRes = translationManager.translate("Bonjour le monde", SupportedLanguage.FRENCH, SupportedLanguage.FRENCH)
        assertEquals("Bonjour le monde", sameLangRes.translatedText)
        assertEquals(0L, sameLangRes.latencyMs)
    }

    @Test
    fun testEdgeCases_NumbersAndPunctuation() = runBlocking {
        // Numbers should not crash
        val numRes = translationManager.translate("12345", SupportedLanguage.ENGLISH, SupportedLanguage.CHINESE)
        assertNotNull(numRes.translatedText)
        assertTrue(numRes.translatedText.contains("12345"))

        // Punctuation should not crash
        val punctRes = translationManager.translate("???", SupportedLanguage.ENGLISH, SupportedLanguage.FRENCH)
        assertNotNull(punctRes.translatedText)
    }

    @Test
    fun testEdgeCases_OutOfVocabularyWords() = runBlocking {
        // Unknown word should safely pass through without crashing or throwing
        val oovRes = translationManager.translate("xyloqwerty987", SupportedLanguage.FRENCH, SupportedLanguage.GERMAN)
        assertNotNull(oovRes.translatedText)
        assertEquals("xyloqwerty987", oovRes.translatedText)
    }

    // =========================================================================
    // 6. High-Concurrency Stress Testing
    // =========================================================================

    @Test
    fun testConcurrencyStress_FiftySimultaneousTranslations() = runBlocking {
        val pairs = listOf(
            Triple("Hello", SupportedLanguage.ENGLISH, SupportedLanguage.CHINESE),
            Triple("Bonjour", SupportedLanguage.FRENCH, SupportedLanguage.ENGLISH),
            Triple("नमस्ते", SupportedLanguage.HINDI, SupportedLanguage.SPANISH),
            Triple("Hallo", SupportedLanguage.GERMAN, SupportedLanguage.HINDI),
            Triple("こんにちは", SupportedLanguage.JAPANESE, SupportedLanguage.ENGLISH),
            Triple("Здравствуйте", SupportedLanguage.RUSSIAN, SupportedLanguage.ARABIC),
            Triple("مرحباً", SupportedLanguage.ARABIC, SupportedLanguage.FRENCH)
        )

        // Launch 50 coroutines concurrently
        val deferreds = (1..50).map { i ->
            val sample = pairs[i % pairs.size]
            async(Dispatchers.Default) {
                translationManager.translate(sample.first, sample.second, sample.third)
            }
        }

        val results = deferreds.awaitAll()
        assertEquals(50, results.size)
        for (res in results) {
            assertTrue("Concurrent translation result must not be blank", res.translatedText.isNotBlank())
        }
    }
}

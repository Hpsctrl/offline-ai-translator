package com.example.offlineaitranslator.engine

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class TranslationManagerTest {

    private lateinit var translationManager: TranslationManager

    @Before
    fun setup() {
        translationManager = TranslationManager()
    }

    @Test
    fun testBidirectionalInversionIntegrity() {
        val allPb = TranslationManager.ALL_PHRASEBOOKS
        assertNotNull(allPb)

        // Verify that reverse phrasebooks were automatically synthesized
        assertTrue("ALL_PHRASEBOOKS must contain en->fr", allPb.containsKey("en->fr"))
        assertTrue("ALL_PHRASEBOOKS must contain fr->en automatically inverted", allPb.containsKey("fr->en"))
        assertTrue("ALL_PHRASEBOOKS must contain en->de", allPb.containsKey("en->de"))
        assertTrue("ALL_PHRASEBOOKS must contain de->en automatically inverted", allPb.containsKey("de->en"))
        assertTrue("ALL_PHRASEBOOKS must contain en->ru", allPb.containsKey("en->ru"))
        assertTrue("ALL_PHRASEBOOKS must contain ru->en automatically inverted", allPb.containsKey("ru->en"))
        assertTrue("ALL_PHRASEBOOKS must contain en->ar", allPb.containsKey("en->ar"))
        assertTrue("ALL_PHRASEBOOKS must contain ar->en automatically inverted", allPb.containsKey("ar->en"))
        assertTrue("ALL_PHRASEBOOKS must contain en->ko", allPb.containsKey("en->ko"))
        assertTrue("ALL_PHRASEBOOKS must contain ko->en automatically inverted", allPb.containsKey("ko->en"))
        assertTrue("ALL_PHRASEBOOKS must contain en->pt", allPb.containsKey("en->pt"))
        assertTrue("ALL_PHRASEBOOKS must contain pt->en automatically inverted", allPb.containsKey("pt->en"))
        assertTrue("ALL_PHRASEBOOKS must contain en->it", allPb.containsKey("en->it"))
        assertTrue("ALL_PHRASEBOOKS must contain it->en automatically inverted", allPb.containsKey("it->en"))

        // Check reverse lexicons
        val revLex = TranslationManager.REVERSE_LEXICONS
        assertNotNull(revLex)
        assertTrue(revLex.containsKey("fr"))
        assertTrue(revLex.containsKey("de"))
        assertTrue(revLex.containsKey("zh"))
        assertTrue(revLex.containsKey("ja"))
        assertTrue(revLex.containsKey("ru"))
        assertTrue(revLex.containsKey("ar"))
        assertEquals("hello", revLex["fr"]?.get("bonjour"))
        assertEquals("hello", revLex["de"]?.get("hallo"))
        assertEquals("hello", revLex["es"]?.get("hola"))
    }

    @Test
    fun testEnglishToGlobalLanguages() = runBlocking {
        // French
        val frRes = translationManager.translate("Hello", SupportedLanguage.ENGLISH, SupportedLanguage.FRENCH)
        assertEquals("Bonjour", frRes.translatedText)

        // German
        val deRes = translationManager.translate("Hello", SupportedLanguage.ENGLISH, SupportedLanguage.GERMAN)
        assertEquals("Hallo", deRes.translatedText)

        // Spanish
        val esRes = translationManager.translate("Hello", SupportedLanguage.ENGLISH, SupportedLanguage.SPANISH)
        assertEquals("Hola", esRes.translatedText)

        // Chinese
        val zhRes = translationManager.translate("Hello", SupportedLanguage.ENGLISH, SupportedLanguage.CHINESE)
        assertEquals("您好", zhRes.translatedText)

        // Japanese
        val jaRes = translationManager.translate("Hello", SupportedLanguage.ENGLISH, SupportedLanguage.JAPANESE)
        assertEquals("こんにちは", jaRes.translatedText)

        // Russian
        val ruRes = translationManager.translate("Hello", SupportedLanguage.ENGLISH, SupportedLanguage.RUSSIAN)
        assertEquals("Здравствуйте", ruRes.translatedText)

        // Arabic
        val arRes = translationManager.translate("Hello", SupportedLanguage.ENGLISH, SupportedLanguage.ARABIC)
        assertEquals("مرحباً", arRes.translatedText)
    }

    @Test
    fun testGlobalLanguagesToEnglish() = runBlocking {
        // French -> English
        val frRes = translationManager.translate("Bonjour", SupportedLanguage.FRENCH, SupportedLanguage.ENGLISH)
        assertEquals("Hello", frRes.translatedText)

        // German -> English
        val deRes = translationManager.translate("Hallo", SupportedLanguage.GERMAN, SupportedLanguage.ENGLISH)
        assertEquals("Hello", deRes.translatedText)

        // Spanish -> English
        val esRes = translationManager.translate("Hola", SupportedLanguage.SPANISH, SupportedLanguage.ENGLISH)
        assertEquals("Hello", esRes.translatedText)

        // Chinese -> English
        val zhRes = translationManager.translate("您好", SupportedLanguage.CHINESE, SupportedLanguage.ENGLISH)
        assertEquals("Hello", zhRes.translatedText)

        // Japanese -> English
        val jaRes = translationManager.translate("こんにちは", SupportedLanguage.JAPANESE, SupportedLanguage.ENGLISH)
        assertEquals("Hello", jaRes.translatedText)

        // Russian -> English
        val ruRes = translationManager.translate("Здравствуйте", SupportedLanguage.RUSSIAN, SupportedLanguage.ENGLISH)
        assertEquals("Hello", ruRes.translatedText)

        // Arabic -> English
        val arRes = translationManager.translate("مرحباً", SupportedLanguage.ARABIC, SupportedLanguage.ENGLISH)
        assertEquals("Hello", arRes.translatedText)
    }

    @Test
    fun testCrossLanguageEnglishPivot() = runBlocking {
        // Hindi -> French (via English pivot)
        val hiToFr = translationManager.translate("नमस्ते", SupportedLanguage.HINDI, SupportedLanguage.FRENCH)
        assertEquals("Bonjour", hiToFr.translatedText)

        // Hindi -> Chinese (via English pivot)
        val hiToZh = translationManager.translate("नमस्ते", SupportedLanguage.HINDI, SupportedLanguage.CHINESE)
        assertEquals("您好", hiToZh.translatedText)

        // Chinese -> French (via English pivot)
        val zhToFr = translationManager.translate("您好", SupportedLanguage.CHINESE, SupportedLanguage.FRENCH)
        assertEquals("Bonjour", zhToFr.translatedText)

        // French -> Hindi (via English pivot)
        val frToHi = translationManager.translate("Bonjour", SupportedLanguage.FRENCH, SupportedLanguage.HINDI)
        assertEquals("नमस्ते", frToHi.translatedText)

        // Spanish -> German (via English pivot)
        val esToDe = translationManager.translate("Hola", SupportedLanguage.SPANISH, SupportedLanguage.GERMAN)
        assertEquals("Hallo", esToDe.translatedText)
    }

    @Test
    fun testLexiconTokenTranslation() = runBlocking {
        // English -> French novel vocabulary (via token lexicon)
        val enToFr = translationManager.translate("food", SupportedLanguage.ENGLISH, SupportedLanguage.FRENCH)
        assertEquals("nourriture", enToFr.translatedText)

        // French -> English novel vocabulary (via reverse token lexicon)
        val frToEn = translationManager.translate("nourriture", SupportedLanguage.FRENCH, SupportedLanguage.ENGLISH)
        assertEquals("food", frToEn.translatedText)

        // Spanish -> English novel vocabulary (via reverse token lexicon)
        val esToEn = translationManager.translate("comida", SupportedLanguage.SPANISH, SupportedLanguage.ENGLISH)
        assertEquals("food", esToEn.translatedText)

        // German -> English novel vocabulary (via reverse token lexicon)
        val deToEn = translationManager.translate("essen", SupportedLanguage.GERMAN, SupportedLanguage.ENGLISH)
        assertEquals("food", deToEn.translatedText)
    }

    @Test
    fun testLRUCacheHit() = runBlocking {
        val first = translationManager.translate("Hello, how are you?", SupportedLanguage.ENGLISH, SupportedLanguage.SPANISH)
        assertEquals("Hola, ¿cómo estás?", first.translatedText)

        val stats1 = translationManager.getCacheStats()
        val second = translationManager.translate("Hello, how are you?", SupportedLanguage.ENGLISH, SupportedLanguage.SPANISH)
        assertEquals("Hola, ¿cómo estás?", second.translatedText)

        val stats2 = translationManager.getCacheStats()
        val hits = (stats2["hits"] as Long)
        assertTrue("Cache hits should increase", hits > 0)
    }
}

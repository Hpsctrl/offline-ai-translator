package com.example.offlineaitranslator.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupportedLanguageBetaTest {

    @Test
    fun fullTierIndicAndGlobalFlags() {
        listOf(
            SupportedLanguage.ENGLISH, SupportedLanguage.HINDI, SupportedLanguage.BENGALI,
            SupportedLanguage.TAMIL, SupportedLanguage.TELUGU, SupportedLanguage.MARATHI,
            SupportedLanguage.GUJARATI, SupportedLanguage.KANNADA, SupportedLanguage.MALAYALAM,
            SupportedLanguage.PUNJABI, SupportedLanguage.URDU, SupportedLanguage.ODIA,
            SupportedLanguage.NEPALI,
        ).forEach {
            assertFalse("${it.displayName} must not be BETA", it.isBeta)
            assertFalse("${it.displayName} voice must not be BETA", it.isVoiceBeta)
        }
    }

    @Test
    fun lowResourceIndicAreVoiceBetaOnly() {
        listOf(
            SupportedLanguage.ASSAMESE, SupportedLanguage.MAITHILI, SupportedLanguage.SANSKRIT,
            SupportedLanguage.SINDHI, SupportedLanguage.KASHMIRI, SupportedLanguage.KONKANI,
            SupportedLanguage.DOGRI, SupportedLanguage.MANIPURI, SupportedLanguage.SANTALI,
            SupportedLanguage.BODO,
        ).forEach {
            assertFalse("${it.displayName} text must not be BETA", it.isBeta)
            assertTrue("${it.displayName} voice must be BETA", it.isVoiceBeta)
        }
    }

    @Test
    fun globalLanguagesAreBeta() {
        listOf(
            SupportedLanguage.SPANISH, SupportedLanguage.FRENCH, SupportedLanguage.GERMAN,
            SupportedLanguage.CHINESE, SupportedLanguage.JAPANESE, SupportedLanguage.RUSSIAN,
            SupportedLanguage.ARABIC, SupportedLanguage.KOREAN, SupportedLanguage.PORTUGUESE,
            SupportedLanguage.ITALIAN,
        ).forEach {
            assertTrue("${it.displayName} must be BETA", it.isBeta)
            assertTrue("${it.displayName} voice must be BETA", it.isVoiceBeta)
        }
    }
}

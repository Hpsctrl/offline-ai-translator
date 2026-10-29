package com.example.offlineaitranslator.ui.main

import com.example.offlineaitranslator.engine.SupportedLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MainScreenViewModelTest {

    @Test
    fun testMainUiStateDefaults() {
        val state = MainUiState()
        assertEquals(SupportedLanguage.AUTO_DETECT, state.srcSpeechLang)
        assertEquals(SupportedLanguage.ENGLISH, state.tgtSpeechLang)
        assertNotNull(state.inputText)
        assertEquals("", state.translatedText)
        assertEquals(0L, state.latencyMs)
    }
}

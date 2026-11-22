package com.example.offlineaitranslator.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GrammarCorrectionEngineTest {

    @Test
    fun removesFillersAndStutters() {
        val cleaned = GrammarCorrectionEngine.clean("Uh, I need need medicine, you know.")
        assertFalse("Filler 'uh' must be removed: $cleaned", cleaned.contains("Uh", true))
        assertFalse("Filler 'you know' must be removed: $cleaned", cleaned.contains("you know", true))
        assertFalse("Immediate stutter must be collapsed: $cleaned", cleaned.contains("need need", true))
        assertTrue("Cleaned text must end with punctuation: $cleaned", cleaned.endsWith(".") || cleaned.endsWith("?") || cleaned.endsWith("!"))
    }

    @Test
    fun isIdempotentAndHandlesHindiFiller() {
        val once = GrammarCorrectionEngine.clean("मतलब मुझे दवा चाहिए")
        val twice = GrammarCorrectionEngine.clean(once)
        assertEquals("Grammar cleaning must be idempotent", once, twice)
        assertFalse("Hindi filler must be removed: $once", once.contains("मतलब"))
    }
}

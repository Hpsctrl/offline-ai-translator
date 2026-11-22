package com.example.offlineaitranslator.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmergencyPhrasebookTest {

    @Test
    fun hasSixCategoriesWithPhrases() {
        assertEquals(6, EmergencyPhrasebook.CATEGORIES.size)
        assertTrue("At least 24 phrases expected", EmergencyPhrasebook.PHRASES.size >= 24)
        for (category in EmergencyPhrasebook.CATEGORIES) {
            assertTrue("Category must have phrases: $category", EmergencyPhrasebook.phrasesFor(category).isNotEmpty())
        }
    }

    @Test
    fun categoryLookupIsCaseInsensitive() {
        val exact = EmergencyPhrasebook.phrasesFor(EmergencyPhrasebook.CATEGORIES.first())
        val upper = EmergencyPhrasebook.phrasesFor(EmergencyPhrasebook.CATEGORIES.first().uppercase())
        assertEquals(exact.size, upper.size)
    }
}

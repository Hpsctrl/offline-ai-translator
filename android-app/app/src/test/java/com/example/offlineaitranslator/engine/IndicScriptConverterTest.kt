package com.example.offlineaitranslator.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class IndicScriptConverterTest {

    @Test
    fun testDevanagariToTelugu() {
        val dev = "नन्नु ताकवद्दु"
        val tel = IndicScriptConverter.fromDevanagari(dev, "tel_Telu")
        assertEquals("నన్ను తాకవద్దు", tel)
    }

    @Test
    fun testTeluguToDevanagari() {
        val tel = "నన్ను తాకవద్దు"
        val dev = IndicScriptConverter.toDevanagari(tel, "tel_Telu")
        assertEquals("नन्नु ताकवद्दु", dev)
    }

    @Test
    fun testDevanagariToBengali() {
        val dev = "नन्नु ताकवद्दु"
        val ben = IndicScriptConverter.fromDevanagari(dev, "ben_Beng")
        assertEquals("নন্নু তাক঵দ্দু", ben)
    }

    @Test
    fun testDevanagariToTamil() {
        val dev = "नन्नु ताकवद्दु"
        val tam = IndicScriptConverter.fromDevanagari(dev, "tam_Taml")
        assertEquals("நந்நு தாகவத்து", tam)
    }

    @Test
    fun testDevanagariToKannada() {
        val dev = "नन्नु ताकवद्दु"
        val kan = IndicScriptConverter.fromDevanagari(dev, "kan_Knda")
        assertEquals("ನನ್ನು ತಾಕವದ್ದು", kan)
    }

    @Test
    fun testDevanagariToMalayalam() {
        val dev = "नन्नु ताकवद्दु"
        val mal = IndicScriptConverter.fromDevanagari(dev, "mal_Mlym")
        assertEquals("നന്നു താകവദ്ദു", mal)
    }

    @Test
    fun testDevanagariToGujarati() {
        val dev = "नन्नु ताकवद्दु"
        val guj = IndicScriptConverter.fromDevanagari(dev, "guj_Gujr")
        assertEquals("નન્નુ તાકવદ્દુ", guj)
    }
}

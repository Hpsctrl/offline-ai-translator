package com.example.offlineaitranslator.ui.main

import androidx.compose.ui.graphics.Color
import com.example.offlineaitranslator.theme.CoUiShapes
import com.example.offlineaitranslator.theme.coUiDarkColorScheme
import com.example.offlineaitranslator.theme.coUiLightColorScheme
import com.example.offlineaitranslator.theme.coUiTextStyles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.max
import kotlin.math.min

/**
 * UI integrity tests for the vendored COUI (ColorOS 17) design system.
 * These replace the retired truck-art oracle tests and the stale version assertions.
 */
class ArtisticIndianUiStressTest {

    private fun luminance(color: Color): Double {
        fun channelLum(c: Float): Double =
            if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        return 0.2126 * channelLum(color.red) +
            0.7152 * channelLum(color.green) +
            0.0722 * channelLum(color.blue)
    }

    private fun contrastRatio(c1: Color, c2: Color): Double {
        val l1 = luminance(c1)
        val l2 = luminance(c2)
        return (max(l1, l2) + 0.05) / (min(l1, l2) + 0.05)
    }

    // --------------------------------------------------------------------------
    // 1. COUI TOKEN INTEGRITY & CONTRAST ORACLE
    // --------------------------------------------------------------------------

    @Test
    fun testCoUiPaletteTokensDefinedAndDistinct() {
        val light = coUiLightColorScheme()
        val dark = coUiDarkColorScheme()

        assertNotEquals("Primary and error must differ", light.primary, light.error)
        assertNotEquals("Light and dark backgrounds must differ", light.background, dark.background)
        assertNotEquals("Light and dark cards must differ", light.card, dark.card)
        assertFalse("Light scheme must not be dark", light.isDark)
        assertTrue("Dark scheme must be dark", dark.isDark)

        assertEquals("COUI light accent must be #0080FF", Color(0xFF0080FF), light.primary)
        assertEquals("COUI dark accent must be #1A8CFF", Color(0xFF1A8CFF), dark.primary)
        assertEquals("COUI light page background must be white", Color.White, light.background)
        assertEquals("COUI dark page background must be black", Color.Black, dark.background)
    }

    @Test
    fun testCoUiContrastOnCoreSurfaces() {
        val light = coUiLightColorScheme()
        val dark = coUiDarkColorScheme()

        val bodyOnLight = contrastRatio(light.labelPrimary, light.background)
        assertTrue("Light body text contrast was $bodyOnLight (expected >= 7.0)", bodyOnLight >= 7.0)

        val whiteOnPrimary = contrastRatio(Color.White, light.primary)
        assertTrue("White on COUI primary was $whiteOnPrimary (expected >= 3.0)", whiteOnPrimary >= 3.0)

        val bodyOnDark = contrastRatio(dark.labelPrimary, dark.background)
        assertTrue("Dark body text contrast was $bodyOnDark (expected >= 7.0)", bodyOnDark >= 7.0)
    }

    @Test
    fun testCoUiTypographyScaleIsOrdered() {
        val type = coUiTextStyles()
        assertTrue(type.displayS.fontSize.value > type.headlineS.fontSize.value)
        assertTrue(type.headlineS.fontSize.value > type.bodyXL.fontSize.value)
        assertTrue(type.bodyXL.fontSize.value > type.bodyXS.fontSize.value)
        assertTrue(type.buttonL.fontSize.value > type.buttonM.fontSize.value)
    }

    @Test
    fun testCoUiShapesAreDefined() {
        assertNotEquals("Card and small shapes must differ", CoUiShapes.card, CoUiShapes.small)
        assertNotEquals("Button and card shapes must differ", CoUiShapes.button, CoUiShapes.card)
    }

    // --------------------------------------------------------------------------
    // 2. VERSIONING & SOURCE-LEVEL WIRING PARITY
    // --------------------------------------------------------------------------

    @Test
    fun testVersionParityAcrossBuildGradleAndSource() {
        val projectRoot = File(".").canonicalFile
        val buildGradleFile = File(projectRoot, "build.gradle.kts")
        assertTrue("build.gradle.kts must exist at ${buildGradleFile.absolutePath}", buildGradleFile.exists())

        val content = buildGradleFile.readText()
        assertTrue("build.gradle.kts must declare versionCode = 10", content.contains("versionCode = 10"))
        assertTrue("build.gradle.kts must declare versionName = \"2.0\"", content.contains("versionName = \"2.0\""))

        val mainScreenFile = File(projectRoot, "src/main/java/com/example/offlineaitranslator/ui/main/MainScreen.kt")
        assertTrue("MainScreen.kt must exist", mainScreenFile.exists())
        val mainScreenContent = mainScreenFile.readText()
        assertTrue(
            "MainScreen.kt must display the version badge through CoUiTopBar",
            mainScreenContent.contains("R.string.ui_version"),
        )
        val strings = File(projectRoot, "src/main/res/values/strings.xml")
        assertTrue("values/strings.xml must define ui_version", strings.readText().contains("name=\"ui_version\""))
        val stringsZh = File(projectRoot, "src/main/res/values-zh/strings.xml")
        assertTrue("values-zh/strings.xml must exist (bilingual resources)", stringsZh.exists())
    }

    @Test
    fun testLegacyTruckArtComponentsRetired() {
        val projectRoot = File(".").canonicalFile
        val legacyFile = File(projectRoot, "src/main/java/com/example/offlineaitranslator/ui/main/TruckArtComponents.kt")
        assertTrue("TruckArtComponents.kt must still exist", legacyFile.exists())
        val content = legacyFile.readText()
        assertTrue("Legacy components must be deprecated", content.contains("@Deprecated"))
        assertFalse("Legacy palette must be gone", content.contains("MarigoldYellow"))
        assertFalse("Legacy hardcoded colours must be gone", content.contains("Color(0xFF"))
    }

    @Test
    fun testCouiComponentsAreUsedByMainScreen() {
        val projectRoot = File(".").canonicalFile
        val source = File(projectRoot, "src/main/java/com/example/offlineaitranslator/ui/main/MainScreen.kt").readText()
        val expected = listOf(
            "CoUiScaffold",
            "CoUiTabBar",
            "CoUiLanguageSelector",
            "CoUiTextField",
            "CoUiProgressBanner",
            "CoUiEmotionBadge",
            "CoUiWaveform",
            "CoUiCardListGroup",
            "CoUiListRow",
        )
        expected.forEach { component ->
            assertTrue("MainScreen.kt must use $component", source.contains(component))
        }
    }

    @Test
    fun testAllViewModelStateAndHandlersRemainConnected() {
        val projectRoot = File(".").canonicalFile
        val mainScreen = File(projectRoot, "src/main/java/com/example/offlineaitranslator/ui/main/MainScreen.kt").readText()
        val adapters = File(projectRoot, "src/main/java/com/example/offlineaitranslator/ui/coui/CoUiStateAdapters.kt").readText()
        val combined = mainScreen + adapters

        val stateFields = listOf(
            "extractionState", "srcSpeechLang", "tgtSpeechLang", "srcTextLang", "tgtTextLang",
            "detectedSpeechLang", "inputText", "translatedText", "isTranslating", "latencyMs",
            "tokensGenerated", "sampleAudios", "selectedAudioName", "isRecording", "isProcessingSpeech",
            "speechTranscript", "speechTranslation", "emotionResult", "asrLatencyMs",
            "speechTransLatencyMs", "ttsLatencyMs", "audioDurationSec", "hasSynthesizedAudio",
            "isPlayingAudio", "ttsNote", "errorMessage", "modelsDiagnostics",
        )
        stateFields.forEach { field ->
            assertTrue("State field '$field' must stay connected", combined.contains(field))
        }

        val handlers = listOf(
            "startExtraction", "startRecording", "stopRecording", "processUploadedAudio",
            "processSampleAudio", "playSampleAudio", "playSynthesizedSpeech", "stopAudioPlayback",
            "speakTextTranslation", "translateText", "setInputText", "clearInputText",
            "setSrcSpeechLang", "setTgtSpeechLang", "swapSpeechLangs", "setSrcTextLang",
            "setTgtTextLang", "swapTextLangs", "refreshDiagnostics",
        )
        handlers.forEach { handler ->
            assertTrue("Handler '$handler' must stay wired", mainScreen.contains("viewModel.$handler"))
        }
    }

    // --------------------------------------------------------------------------
    // 3. THEME INTEGRITY & dynamicColor = false ENFORCEMENT
    // --------------------------------------------------------------------------

    @Test
    fun testDynamicColorDefaultIsExplicitlyFalse() {
        val projectRoot = File(".").canonicalFile
        val themeFile = File(projectRoot, "src/main/java/com/example/offlineaitranslator/theme/Theme.kt")
        assertTrue("Theme.kt must exist", themeFile.exists())
        val content = themeFile.readText()
        assertTrue("dynamicColor must explicitly default to false", content.contains("dynamicColor: Boolean = false"))

        val mainActivityFile = File(projectRoot, "src/main/java/com/example/offlineaitranslator/MainActivity.kt")
        assertTrue("MainActivity.kt must exist", mainActivityFile.exists())
        val mainActivityContent = mainActivityFile.readText()
        assertTrue("MainActivity must use OfflineAITranslatorTheme", mainActivityContent.contains("OfflineAITranslatorTheme"))
        assertFalse("MainActivity must NOT pass dynamicColor = true", mainActivityContent.contains("dynamicColor = true"))
    }
}

package com.example.offlineaitranslator.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Instrumented UI test for MainScreen. */
class MainScreenTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  @Before
  fun setup() {
    composeTestRule.setContent {
      MainScreen(
        onItemClick = {},
        microphoneGranted = true,
        onRequestMicrophone = {}
      )
    }
  }

  @Test
  fun testOfflineUiElementsExist() {
    composeTestRule.onNodeWithText("  100% OFFLINE • ZERO NETWORK PERMISSIONS").assertExists()
    composeTestRule.onNodeWithText("Offline Speech Pipeline (ASR + Emotion + MT + TTS)").assertExists()
  }
}

package com.example.offlineaitranslator

import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.offlineaitranslator.ui.main.MainScreen

@Composable
fun MainNavigation(microphoneGranted: Boolean, onRequestMicrophone: () -> Unit) {
  val backStack = rememberNavBackStack(Main)

  NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    entryProvider =
      entryProvider {
        entry<Main> {
          MainScreen(
            onItemClick = { navKey -> backStack.add(navKey) },
            microphoneGranted = microphoneGranted,
            onRequestMicrophone = onRequestMicrophone,
            modifier = Modifier.safeDrawingPadding()
          )
        }
      },
  )
}

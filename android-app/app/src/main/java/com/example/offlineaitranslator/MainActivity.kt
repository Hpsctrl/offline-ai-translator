package com.example.offlineaitranslator

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.offlineaitranslator.theme.CoUiTheme
import com.example.offlineaitranslator.theme.OfflineAITranslatorTheme
import com.example.offlineaitranslator.ui.coui.CouiRefreshRate

class MainActivity : ComponentActivity() {
  // Activity-owned Compose state: the permission callback only writes this value, so the single
  // setContent composition recomposes on grant instead of installing a second composition.
  private val microphoneGranted = mutableStateOf(false)

  private val microphonePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
    microphoneGranted.value = granted
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    CouiRefreshRate.apply(window)
    microphoneGranted.value = ContextCompat.checkSelfPermission(
      this,
      Manifest.permission.RECORD_AUDIO,
    ) == PackageManager.PERMISSION_GRANTED
    setContent {
      OfflineAITranslatorTheme {
        // COUI pages paint their own `backgroundWithCard` surface; no Material Surface wrapper.
        Box(modifier = Modifier.fillMaxSize().background(CoUiTheme.colors.backgroundWithCard)) {
          MainNavigation(microphoneGranted = microphoneGranted.value, onRequestMicrophone = {
            microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
          })
        }
      }
    }
  }
}

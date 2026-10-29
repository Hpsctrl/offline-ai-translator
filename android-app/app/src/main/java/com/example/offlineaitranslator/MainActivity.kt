package com.example.offlineaitranslator

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.offlineaitranslator.theme.OfflineAITranslatorTheme

class MainActivity : ComponentActivity() {
  private var microphoneGranted = false
  private val microphonePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
    microphoneGranted = granted
    showApp()
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    microphoneGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    showApp()
  }

  private fun showApp() {
    setContent {
      OfflineAITranslatorTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
          MainNavigation(microphoneGranted = microphoneGranted, onRequestMicrophone = {
            microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
          })
        }
      }
    }
  }
}

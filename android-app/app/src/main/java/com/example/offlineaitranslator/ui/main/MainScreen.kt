package com.example.offlineaitranslator.ui.main

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import com.example.offlineaitranslator.engine.EmotionResult
import com.example.offlineaitranslator.engine.ExtractionState
import com.example.offlineaitranslator.engine.SampleAudioItem
import com.example.offlineaitranslator.engine.SupportedLanguage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onItemClick: (NavKey) -> Unit,
    microphoneGranted: Boolean,
    onRequestMicrophone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MainScreenViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val context = LocalContext.current

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.processUploadedAudio(uri, context)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // --- Clean Modern Header ---
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(Color(0xFF10B981), CircleShape)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "AI Voice & Text Translator",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Offline Mode • 33 Languages",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                ) {
                    Text(
                        text = "v1.6",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }
        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

        // --- Clean Tabs ---
        TabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            divider = {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), thickness = 1.dp)
            }
        ) {
            Tab(
                selected = selectedTabIndex == 0,
                onClick = { selectedTabIndex = 0 },
                text = {
                    Text(
                        "🎙️ Voice",
                        fontWeight = if (selectedTabIndex == 0) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 13.sp
                    )
                }
            )
            Tab(
                selected = selectedTabIndex == 1,
                onClick = { selectedTabIndex = 1 },
                text = {
                    Text(
                        "💬 Text",
                        fontWeight = if (selectedTabIndex == 1) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 13.sp
                    )
                }
            )
            Tab(
                selected = selectedTabIndex == 2,
                onClick = { selectedTabIndex = 2 },
                text = {
                    Text(
                        "⚙️ Models",
                        fontWeight = if (selectedTabIndex == 2) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 13.sp
                    )
                }
            )
        }

        // --- Setup Progress Banner (Only shown once on first launch) ---
        when (val ext = uiState.extractionState) {
            is ExtractionState.Extracting -> {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f))
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val pct = (ext.progress * 100).toInt()
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Preparing AI models ($pct%)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        LinearProgressIndicator(
                            progress = { ext.progress },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                        )
                        Text(
                            "Loading: ${ext.currentFile} (${ext.filesExtracted}/${ext.totalFiles})",
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            is ExtractionState.Error -> {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f))
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Model Setup Error", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                        Text(ext.message, fontSize = 12.sp)
                        Button(onClick = { viewModel.startExtraction() }, shape = RoundedCornerShape(8.dp)) {
                            Text("Retry")
                        }
                    }
                }
            }
            else -> {}
        }

        // --- Tab Contents ---
        Box(modifier = Modifier.fillMaxSize()) {
            when (selectedTabIndex) {
                0 -> VoiceAndAudioTab(
                    uiState = uiState,
                    microphoneGranted = microphoneGranted,
                    onRequestMicrophone = onRequestMicrophone,
                    onPickAudio = { audioPickerLauncher.launch("audio/*") },
                    onStartRecording = { viewModel.startRecording() },
                    onStopRecording = { viewModel.stopRecording() },
                    onSelectSample = { viewModel.processSampleAudio(it) },
                    onPlaySynthesized = { viewModel.playSynthesizedSpeech() },
                    onSrcLangChanged = { viewModel.setSrcSpeechLang(it) },
                    onTgtLangChanged = { viewModel.setTgtSpeechLang(it) },
                    onSwapLangs = { viewModel.swapSpeechLangs() }
                )
                1 -> TextTranslationTab(
                    uiState = uiState,
                    onInputChanged = { viewModel.setInputText(it) },
                    onTranslate = { viewModel.translateText() },
                    onClear = { viewModel.clearInputText() },
                    onSpeakText = { viewModel.speakTextTranslation() },
                    onSrcLangChanged = { viewModel.setSrcTextLang(it) },
                    onTgtLangChanged = { viewModel.setTgtTextLang(it) },
                    onSwapLangs = { viewModel.swapTextLangs() }
                )
                2 -> DiagnosticsTab(
                    uiState = uiState,
                    onRefresh = { viewModel.refreshDiagnostics() }
                )
            }
        }
    }
}

@Composable
private fun VoiceAndAudioTab(
    uiState: MainUiState,
    microphoneGranted: Boolean,
    onRequestMicrophone: () -> Unit,
    onPickAudio: () -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onSelectSample: (SampleAudioItem) -> Unit,
    onPlaySynthesized: () -> Unit,
    onSrcLangChanged: (SupportedLanguage) -> Unit,
    onTgtLangChanged: (SupportedLanguage) -> Unit,
    onSwapLangs: () -> Unit
) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        LanguageSelectorBar(
            srcLang = uiState.srcSpeechLang,
            tgtLang = uiState.tgtSpeechLang,
            onSrcLangSelected = onSrcLangChanged,
            onTgtLangSelected = onTgtLangChanged,
            onSwap = onSwapLangs,
            isSpeechMode = true
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Voice Translation",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Tap the microphone to speak or choose a sample",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (!microphoneGranted) {
                    Button(
                        onClick = onRequestMicrophone,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("🎙️ Grant Microphone Access", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                } else {
                    TactileVintageMicButton(
                        isRecording = uiState.isRecording,
                        isProcessing = uiState.isProcessingSpeech,
                        onClick = {
                            if (uiState.isRecording) {
                                onStopRecording()
                            } else {
                                onStartRecording()
                            }
                        },
                        languageLabel = uiState.srcSpeechLang.displayName
                    )

                    if (uiState.isRecording) {
                        AudioWaveformVisualizer(isRecording = true)
                    }
                }

                OutlinedButton(
                    onClick = onPickAudio,
                    enabled = !uiState.isRecording && !uiState.isProcessingSpeech,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                ) {
                    Text("📁 Choose Audio File (WAV, MP3, M4A)", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                }

                if (uiState.sampleAudios.isNotEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            "Or test with sample audio:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            uiState.sampleAudios.forEach { sample ->
                                FilterChip(
                                    selected = uiState.selectedAudioName == sample.title,
                                    onClick = { onSelectSample(sample) },
                                    label = { Text(sample.title, fontSize = 12.sp) },
                                    shape = RoundedCornerShape(10.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(visible = uiState.isProcessingSpeech) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.5.dp)
                    Column {
                        Text("Translating speech...", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(
                            "Generating translation locally",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }

        if (uiState.speechTranscript.isNotBlank() || uiState.speechTranslation.isNotBlank() || uiState.emotionResult != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = uiState.selectedAudioName ?: "Recognized Output",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        uiState.emotionResult?.let { emo ->
                            EmotionBadge(emotion = emo)
                        }
                    }

                    HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val label = if (uiState.srcSpeechLang == SupportedLanguage.AUTO_DETECT && uiState.detectedSpeechLang != null) {
                                    "Speech Input (${uiState.detectedSpeechLang.displayName}):"
                                } else {
                                    "Speech Input (${uiState.srcSpeechLang.displayName}):"
                                }
                                Text(
                                    text = label,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                if (uiState.srcSpeechLang == SupportedLanguage.AUTO_DETECT && uiState.detectedSpeechLang != null) {
                                    Spacer(Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                                    ) {
                                        Text(
                                            text = "✨ ${uiState.detectedSpeechLang.displayName}",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            if (uiState.asrLatencyMs > 0) {
                                Text("${uiState.asrLatencyMs} ms", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                        val srcRtl = uiState.srcSpeechLang.isRtl
                        val srcLayoutDir = if (srcRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
                        CompositionLocalProvider(LocalLayoutDirection provides srcLayoutDir) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = if (uiState.speechTranscript.isEmpty()) "(No speech detected)" else uiState.speechTranscript,
                                    fontSize = 15.sp,
                                    lineHeight = 21.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Translation (${uiState.tgtSpeechLang.displayName}):",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            if (uiState.speechTransLatencyMs > 0) {
                                Text("${uiState.speechTransLatencyMs} ms", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                        val tgtRtl = uiState.tgtSpeechLang.isRtl
                        val tgtLayoutDir = if (tgtRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
                        CompositionLocalProvider(LocalLayoutDirection provides tgtLayoutDir) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                                    .border(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = if (uiState.speechTranslation.isEmpty()) "(No translation generated)" else uiState.speechTranslation,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    lineHeight = 22.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    if (uiState.hasSynthesizedAudio || uiState.speechTranslation.isNotBlank()) {
                        Button(
                            onClick = onPlaySynthesized,
                            enabled = !uiState.isPlayingAudio,
                            modifier = Modifier.fillMaxWidth().height(46.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Text(
                                if (uiState.isPlayingAudio) "🔊 Speaking..." else "🔊 Play Audio",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }

                    uiState.ttsNote?.let { note ->
                        Text(
                            text = note,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        OutlinedButton(
                            onClick = {
                                val textToCopy = "Original: ${uiState.speechTranscript}\nTranslation: ${uiState.speechTranslation}"
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("AI Translation", textToCopy))
                                Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                        ) {
                            Text("📋 Copy", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        uiState.errorMessage?.let { err ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f))
            ) {
                Text(err, modifier = Modifier.padding(14.dp), color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun TextTranslationTab(
    uiState: MainUiState,
    onInputChanged: (String) -> Unit,
    onTranslate: () -> Unit,
    onClear: () -> Unit,
    onSpeakText: () -> Unit,
    onSrcLangChanged: (SupportedLanguage) -> Unit,
    onTgtLangChanged: (SupportedLanguage) -> Unit,
    onSwapLangs: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        LanguageSelectorBar(
            srcLang = uiState.srcTextLang,
            tgtLang = uiState.tgtTextLang,
            onSrcLangSelected = onSrcLangChanged,
            onTgtLangSelected = onTgtLangChanged,
            onSwap = onSwapLangs,
            isSpeechMode = false
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val srcRtl = uiState.srcTextLang.isRtl
                val srcLayoutDir = if (srcRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
                CompositionLocalProvider(LocalLayoutDirection provides srcLayoutDir) {
                    OutlinedTextField(
                        value = uiState.inputText,
                        onValueChange = onInputChanged,
                        label = { Text("Enter text to translate (${uiState.srcTextLang.displayName})") },
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onClear,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    ) {
                        Text("Clear", fontSize = 12.sp)
                    }

                    Button(
                        onClick = onTranslate,
                        enabled = uiState.inputText.isNotBlank() && !uiState.isTranslating,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(42.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        if (uiState.isTranslating) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                            Spacer(Modifier.width(8.dp))
                            Text("Translating...")
                        } else {
                            Text("Translate", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        if (uiState.translatedText.isNotBlank()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f))
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${uiState.tgtTextLang.displayName} Translation:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        if (uiState.latencyMs > 0) {
                            Text("${uiState.latencyMs} ms", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                        }
                    }

                    val tgtRtl = uiState.tgtTextLang.isRtl
                    val tgtLayoutDir = if (tgtRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
                    CompositionLocalProvider(LocalLayoutDirection provides tgtLayoutDir) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                                .border(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = uiState.translatedText,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                lineHeight = 22.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = onSpeakText,
                            enabled = !uiState.isPlayingAudio && uiState.translatedText.isNotBlank(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Text(if (uiState.isPlayingAudio) "🔊 Speaking..." else "🔊 Play Audio", fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Translation", uiState.translatedText))
                                Toast.makeText(context, "Copied translation", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                        ) {
                            Text("📋 Copy", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticsTab(
    uiState: MainUiState,
    onRefresh: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Offline AI Engine",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp
                )
                Text(
                    "Speech recognition, translation, and neural voices run 100% locally on your phone. No internet connection is needed or used.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 17.sp
                )
            }
        }

        Text("Installed Models & Engines:", fontWeight = FontWeight.Bold, fontSize = 14.sp)

        uiState.modelsDiagnostics.forEach { model ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(model.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (model.isLoaded) Color(0xFF10B981) else Color(0xFF0284C7))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(model.statusText, fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                    Text(
                        "Category: ${model.category} • Size: ${model.fileSizeBytes / (1024 * 1024)} MB",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Button(
            onClick = onRefresh,
            modifier = Modifier.fillMaxWidth().height(46.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Text("🔄 Refresh Status", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        }
    }
}

@Composable
fun LanguageSelectorBar(
    srcLang: SupportedLanguage,
    tgtLang: SupportedLanguage,
    onSrcLangSelected: (SupportedLanguage) -> Unit,
    onTgtLangSelected: (SupportedLanguage) -> Unit,
    onSwap: () -> Unit,
    isSpeechMode: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            LanguageDropdownButton(
                selectedLanguage = srcLang,
                availableLanguages = if (isSpeechMode) SupportedLanguage.speechSourceLanguages else SupportedLanguage.realLanguages,
                onLanguageSelected = onSrcLangSelected,
                modifier = Modifier.weight(1f)
            )

            IconButton(
                onClick = onSwap,
                modifier = Modifier.size(36.dp)
            ) {
                Text("⇄", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }

            LanguageDropdownButton(
                selectedLanguage = tgtLang,
                availableLanguages = SupportedLanguage.realLanguages,
                onLanguageSelected = onTgtLangSelected,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun LanguageDropdownButton(
    selectedLanguage: SupportedLanguage,
    availableLanguages: List<SupportedLanguage>,
    onLanguageSelected: (SupportedLanguage) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .clickable { expanded = true }
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = selectedLanguage.displayName,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text("▾", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            availableLanguages.forEach { lang ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(lang.displayName, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            if (lang.displayName != lang.nativeName) {
                                Text(lang.nativeName, fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    },
                    onClick = {
                        onLanguageSelected(lang)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun EmotionBadge(emotion: EmotionResult, modifier: Modifier = Modifier) {
    val (emoji, bgColor, textColor) = when (emotion.emotion.lowercase()) {
        "happy" -> Triple("😊", Color(0xFFDCFCE7), Color(0xFF166534))
        "sad" -> Triple("😔", Color(0xFFE0F2FE), Color(0xFF075985))
        "angry" -> Triple("😠", Color(0xFFFEE2E2), Color(0xFF991B1B))
        "fearful" -> Triple("😨", Color(0xFFFEF3C7), Color(0xFF92400E))
        "surprised" -> Triple("😲", Color(0xFFF3E8FF), Color(0xFF6B21A8))
        "disgusted" -> Triple("🤢", Color(0xFFF5F5F4), Color(0xFF57534E))
        "neutral" -> Triple("😐", Color(0xFFF1F5F9), Color(0xFF334155))
        else -> Triple("🙂", Color(0xFFF1F5F9), Color(0xFF334155))
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        border = BorderStroke(1.dp, textColor.copy(alpha = 0.2f))
    ) {
        Text(
            text = "$emoji ${emotion.emotion.uppercase()} (${(emotion.confidence * 100).toInt()}%)",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = textColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
fun AudioWaveformVisualizer(
    isRecording: Boolean,
    modifier: Modifier = Modifier
) {
    val barCount = 18
    val staticHeights = listOf(0.25f, 0.45f, 0.70f, 0.35f, 0.85f, 0.60f, 0.95f, 0.50f, 0.75f, 0.40f, 0.90f, 0.65f, 0.30f, 0.80f, 0.55f, 0.70f, 0.35f, 0.20f)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until barCount) {
            val barHeightFraction = if (isRecording) staticHeights[i % staticHeights.size] else 0.15f
            val barColor = if (isRecording) Color(0xFFEF4444) else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 2.dp)
                    .fillMaxHeight(barHeightFraction)
                    .background(barColor, RoundedCornerShape(2.dp))
            )
        }
    }
}

package com.example.offlineaitranslator.ui.main

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import com.example.offlineaitranslator.R
import com.example.offlineaitranslator.engine.EmergencyPhrasebook
import com.example.offlineaitranslator.engine.ExtractionState
import com.example.offlineaitranslator.engine.OcrSample
import com.example.offlineaitranslator.engine.OfflineOcrEngine
import com.example.offlineaitranslator.engine.SampleAudioItem
import com.example.offlineaitranslator.engine.SupportedLanguage
import com.example.offlineaitranslator.theme.CoUiTheme
import com.example.offlineaitranslator.ui.coui.CoUiMotion
import com.example.offlineaitranslator.ui.coui.CoUiBadge
import com.example.offlineaitranslator.ui.coui.CoUiButton
import com.example.offlineaitranslator.ui.coui.CoUiCard
import com.example.offlineaitranslator.ui.coui.CoUiCardListGroup
import com.example.offlineaitranslator.ui.coui.CoUiEmotionBadge
import com.example.offlineaitranslator.ui.coui.CoUiErrorBanner
import com.example.offlineaitranslator.ui.coui.CoUiCircularProgress
import com.example.offlineaitranslator.ui.coui.CoUiIcon
import com.example.offlineaitranslator.ui.coui.CoUiIconButton
import com.example.offlineaitranslator.ui.coui.CoUiIconKind
import com.example.offlineaitranslator.ui.coui.CoUiInfoBanner
import com.example.offlineaitranslator.ui.coui.CoUiLanguageSelector
import com.example.offlineaitranslator.ui.coui.CoUiListRow
import com.example.offlineaitranslator.ui.coui.CoUiLocalizedTextPanel
import com.example.offlineaitranslator.ui.coui.CoUiMicButton
import com.example.offlineaitranslator.ui.coui.CoUiOutlinedButton
import com.example.offlineaitranslator.ui.coui.CoUiProgressBanner
import com.example.offlineaitranslator.ui.coui.CoUiScaffold
import com.example.offlineaitranslator.ui.coui.CoUiSectionTitle
import com.example.offlineaitranslator.ui.coui.CoUiStatusChip
import com.example.offlineaitranslator.ui.coui.CoUiTabBar
import com.example.offlineaitranslator.ui.coui.CoUiTextField
import com.example.offlineaitranslator.ui.coui.CoUiTone
import com.example.offlineaitranslator.ui.coui.CoUiText
import com.example.offlineaitranslator.ui.coui.CoUiWaveform
import com.example.offlineaitranslator.ui.coui.couiOverscroll
import com.example.offlineaitranslator.ui.coui.allTranslationLanguages
import com.example.offlineaitranslator.ui.coui.detectedEmotion
import com.example.offlineaitranslator.ui.coui.extractionProgress
import com.example.offlineaitranslator.ui.coui.extractionStatusText
import com.example.offlineaitranslator.ui.coui.isExtractingModels

@Composable
private fun localizedError(message: String?): String = when (message) {
    "ui_ocr_no_text" -> stringResource(R.string.ui_ocr_no_text)
    else -> message ?: ""
}

/**
 * COUI-styled main screen.
 *
 * Signature is unchanged so `Navigation.kt` keeps compiling. Every property of [MainUiState] and every
 * event handler of [MainScreenViewModel] remains connected; only the visual layer was replaced.
 */
@Composable
fun MainScreen(
    onItemClick: (NavKey) -> Unit,
    microphoneGranted: Boolean,
    onRequestMicrophone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MainScreenViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    // Each tab owns its scroll position: a tab can never inherit another tab's mid-scroll offset,
    // and the collapsed top bar derives from whichever tab is currently visible.
    val voiceScrollState = rememberScrollState()
    val textScrollState = rememberScrollState()
    val ocrScrollState = rememberScrollState()
    val sosScrollState = rememberScrollState()
    val historyScrollState = rememberScrollState()
    val modelsScrollState = rememberScrollState()
    val activeScrollState = when (selectedTabIndex) {
        0 -> voiceScrollState
        1 -> textScrollState
        2 -> ocrScrollState
        3 -> sosScrollState
        4 -> historyScrollState
        else -> modelsScrollState
    }

    // When a speech result finishes, glide the Voice tab down to the newly populated result card.
    LaunchedEffect(uiState.isProcessingSpeech) {
        if (!uiState.isProcessingSpeech && uiState.speechTranscript.isNotBlank()) {
            voiceScrollState.animateScrollTo(voiceScrollState.maxValue)
        }
    }

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) {
            viewModel.processUploadedAudio(uri, context)
        }
    }

    CoUiScaffold(
        title = stringResource(R.string.ui_app_title),
        modifier = modifier.fillMaxSize(),
        subtitle = stringResource(R.string.ui_offline_subtitle),
        privacyBadge = stringResource(R.string.ui_privacy_badge),
        version = stringResource(R.string.ui_version),
        scrollState = activeScrollState,
    ) {
        // First-launch model extraction progress / errors.
        if (uiState.isExtractingModels && uiState.extractionState is ExtractionState.Extracting) {
            CoUiProgressBanner(
                progress = uiState.extractionProgress,
                title = stringResource(R.string.ui_preparing_models) +
                    " ${(uiState.extractionProgress * 100).toInt()}%",
                detail = uiState.extractionStatusText(),
            )
        }
        if (uiState.extractionState is ExtractionState.Error) {
            CoUiErrorBanner(
                message = stringResource(R.string.ui_model_setup_error, uiState.extractionStatusText()),
                onRetry = { viewModel.startExtraction() },
            )
        }

        CoUiTabBar(
            items = listOf(
                stringResource(R.string.ui_tab_voice),
                stringResource(R.string.ui_tab_text),
                stringResource(R.string.ui_tab_ocr),
                stringResource(R.string.ui_tab_sos),
                stringResource(R.string.ui_tab_history),
                stringResource(R.string.ui_tab_models),
            ),
            selectedIndex = selectedTabIndex,
            onTabSelected = { selectedTabIndex = it },
            icons = listOf(
                CoUiIconKind.MIC,
                CoUiIconKind.CHECK,
                CoUiIconKind.FOLDER,
                CoUiIconKind.CLOSE,
                CoUiIconKind.REFRESH,
                CoUiIconKind.NEURAL,
            ),
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .couiOverscroll(activeScrollState),
        ) {
            AnimatedContent(
                targetState = selectedTabIndex,
                transitionSpec = {
                    (
                        fadeIn(animationSpec = tween(220, easing = CoUiMotion.CouiEase)) +
                            slideInHorizontally(
                                animationSpec = tween(280, easing = CoUiMotion.CouiMoveEase),
                                initialOffsetX = { it / 10 },
                            ) +
                            scaleIn(
                                animationSpec = tween(280, easing = CoUiMotion.CouiMoveEase),
                                initialScale = 0.97f,
                            )
                        ) togetherWith (
                        fadeOut(animationSpec = tween(140, easing = CoUiMotion.CouiInEase)) +
                            slideOutHorizontally(
                                animationSpec = tween(180, easing = CoUiMotion.CouiInEase),
                                targetOffsetX = { -it / 14 },
                            ) +
                            scaleOut(
                                animationSpec = tween(180, easing = CoUiMotion.CouiInEase),
                                targetScale = 0.985f,
                            )
                        )
                },
                label = "coui-tab-content",
            ) { tabIndex ->
                when (tabIndex) {
                    0 -> VoiceTab(
                        uiState = uiState,
                        scrollState = voiceScrollState,
                        microphoneGranted = microphoneGranted,
                        onRequestMicrophone = onRequestMicrophone,
                        onPickAudio = { audioPickerLauncher.launch("audio/*") },
                        onStartRecording = { viewModel.startRecording() },
                        onStopRecording = { viewModel.stopRecording() },
                        onSelectSample = { viewModel.processSampleAudio(it) },
                        onPlaySample = { viewModel.playSampleAudio(it) },
                        onPlaySynthesized = { viewModel.playSynthesizedSpeech() },
                        onStopPlayback = { viewModel.stopAudioPlayback() },
                        onSrcLangChanged = { viewModel.setSrcSpeechLang(it) },
                        onTgtLangChanged = { viewModel.setTgtSpeechLang(it) },
                        onSwapLangs = { viewModel.swapSpeechLangs() },
                        onGrammarToggle = { viewModel.setGrammarCorrectionEnabled(it) },
                    )
                    1 -> TextTab(
                        uiState = uiState,
                        scrollState = textScrollState,
                        onInputChanged = { viewModel.setInputText(it) },
                        onTranslate = { viewModel.translateText() },
                        onClear = { viewModel.clearInputText() },
                        onSpeakText = { viewModel.speakTextTranslation() },
                        onStopPlayback = { viewModel.stopAudioPlayback() },
                        onSrcLangChanged = { viewModel.setSrcTextLang(it) },
                        onTgtLangChanged = { viewModel.setTgtTextLang(it) },
                        onSwapLangs = { viewModel.swapTextLangs() },
                        onGrammarToggle = { viewModel.setGrammarCorrectionEnabled(it) },
                    )
                    2 -> OcrTab(
                        uiState = uiState,
                        scrollState = ocrScrollState,
                        onOcrInputChanged = { viewModel.setOcrInputText(it) },
                        onLoadSample = { viewModel.loadOcrSample(it) },
                        onProcessOcrImage = { uri -> viewModel.processOcrImage(uri, context) },
                        onCleanGrammar = { viewModel.cleanGrammar(it, "OCR") },
                        onTranslateOcr = { viewModel.translateOcrText() },
                        onSpeakOcr = {
                            viewModel.speakPhrase(uiState.ocrTranslatedText, uiState.tgtTextLang)
                        },
                    )
                    3 -> SosTab(
                        uiState = uiState,
                        scrollState = sosScrollState,
                        onSelectCategory = { viewModel.setSelectedEmergencyCategory(it) },
                        onSpeak = { viewModel.speakPhrase(it, SupportedLanguage.ENGLISH) },
                        onFlashcard = { viewModel.showFlashcard(it) },
                        onDismissFlashcard = { viewModel.dismissFlashcard() },
                    )
                    4 -> HistoryTab(
                        uiState = uiState,
                        scrollState = historyScrollState,
                        onFilterChanged = { viewModel.setHistoryFilter(it) },
                        onReplay = { text, code ->
                            viewModel.speakPhrase(text, SupportedLanguage.fromCode(code))
                        },
                        onTogglePin = { viewModel.toggleHistoryPin(it) },
                        onClear = { viewModel.clearHistory() },
                    )
                    5 -> ModelsTab(
                        uiState = uiState,
                        scrollState = modelsScrollState,
                        onRefresh = { viewModel.refreshDiagnostics() },
                    )
                }
            }
        }
    }
    // `onItemClick` is intentionally unused: Navigation.kt declares a single destination.
}

/**
 * Shared grammar toggle + cleaned-text preview, used by both the Voice and Text tabs.
 */
@Composable
private fun GrammarCorrectionRows(
    enabled: Boolean,
    cleanedText: String,
    onToggle: (Boolean) -> Unit,
) {
    CoUiCardListGroup(
        items = listOf(
            @Composable {
                CoUiListRow(
                    title = stringResource(R.string.ui_grammar_correction),
                    modifier = Modifier.clickable { onToggle(!enabled) },
                    endActions = {
                        CoUiBadge(
                            text = stringResource(if (enabled) R.string.ui_on else R.string.ui_off),
                            tone = if (enabled) CoUiTone.SUCCESS else CoUiTone.PRIMARY,
                        )
                    },
                )
            },
            @Composable {
                CoUiListRow(
                    title = stringResource(R.string.ui_grammar_cleaned),
                    summary = cleanedText.ifBlank { null },
                )
            },
        ),
    )
}

/** Decode an image Uri into a downsampled bitmap so the preview never allocates a full-size image. */
private fun decodeDownsampledBitmap(context: Context, uri: Uri): Bitmap? {
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val maxDimension = 1024
        var sampleSize = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sampleSize > maxDimension) {
            sampleSize *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        }
    } catch (t: Throwable) {
        null
    }
}

@Composable
private fun VoiceTab(
    uiState: MainUiState,
    scrollState: ScrollState,
    microphoneGranted: Boolean,
    onRequestMicrophone: () -> Unit,
    onPickAudio: () -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onSelectSample: (SampleAudioItem) -> Unit,
    onPlaySample: (SampleAudioItem) -> Unit,
    onPlaySynthesized: () -> Unit,
    onStopPlayback: () -> Unit,
    onSrcLangChanged: (SupportedLanguage) -> Unit,
    onTgtLangChanged: (SupportedLanguage) -> Unit,
    onSwapLangs: () -> Unit,
    onGrammarToggle: (Boolean) -> Unit,
) {
    val context = LocalContext.current

    // MainScreenViewModel emits fixed English placeholders for no-speech results. Resolve those
    // sentinels to localized COUI copy here so the Voice tab is bilingual without changing the
    // ViewModel's public contract. Sentinel resources stay identical in every locale on purpose.
    val noSpeechText = stringResource(R.string.ui_no_speech)
    val speakClearlyText = stringResource(R.string.ui_please_speak_clearly)
    val noSpeechSentinel = stringResource(R.string.ui_no_speech_sentinel)
    val speakClearlySentinel = stringResource(R.string.ui_speak_clearly_sentinel)
    val localizedSpeechText: (String) -> String = { text ->
        when (text) {
            noSpeechSentinel -> noSpeechText
            speakClearlySentinel -> speakClearlyText
            else -> text
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CoUiLanguageSelector(
            srcLang = uiState.srcSpeechLang,
            tgtLang = uiState.tgtSpeechLang,
            sourceLanguages = SupportedLanguage.speechSourceLanguages,
            targetLanguages = SupportedLanguage.allTranslationLanguages,
            onSrcSelected = onSrcLangChanged,
            onTgtSelected = onTgtLangChanged,
            onSwap = onSwapLangs,
        )

        if (uiState.speechTranscript.isNotBlank()) {
            GrammarCorrectionRows(
                enabled = uiState.grammarCorrectionEnabled,
                cleanedText = uiState.voiceGrammarCleanedText,
                onToggle = onGrammarToggle,
            )
        }

        CoUiCard {
            CoUiSectionTitle(text = stringResource(R.string.ui_voice_translation))
            CoUiText(
                text = stringResource(R.string.ui_voice_hint),
                style = CoUiTheme.textStyles.bodyXS,
                color = CoUiTheme.colors.labelSecondary,
            )
            Spacer(Modifier.height(12.dp))

            if (!microphoneGranted) {
                CoUiButton(
                    text = stringResource(R.string.ui_grant_mic),
                    onClick = onRequestMicrophone,
                    modifier = Modifier.fillMaxWidth(),
                    icon = CoUiIconKind.MIC,
                )
            } else {
                CoUiMicButton(
                    isRecording = uiState.isRecording,
                    isProcessing = uiState.isProcessingSpeech,
                    languageLabel = uiState.srcSpeechLang.displayName,
                    onClick = {
                        if (uiState.isRecording) onStopRecording() else onStartRecording()
                    },
                )
                if (uiState.isRecording) {
                    Spacer(Modifier.height(8.dp))
                    CoUiWaveform(isRecording = true)
                }
            }

            Spacer(Modifier.height(8.dp))
            CoUiOutlinedButton(
                text = stringResource(R.string.ui_choose_audio),
                onClick = onPickAudio,
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isRecording && !uiState.isProcessingSpeech,
                icon = CoUiIconKind.FOLDER,
            )
            CoUiText(
                text = stringResource(R.string.ui_audio_formats),
                style = CoUiTheme.textStyles.bodyXS,
                color = CoUiTheme.colors.labelTertiary,
            )

            if (uiState.sampleAudios.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                CoUiSectionTitle(text = stringResource(R.string.ui_sample_audio))
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    uiState.sampleAudios.forEach { sample ->
                        CoUiBadge(
                            text = sample.title,
                            tone = if (uiState.selectedAudioName == sample.title) CoUiTone.PRIMARY else CoUiTone.NEUTRAL,
                            modifier = Modifier.clickable(
                                enabled = !uiState.isRecording && !uiState.isProcessingSpeech,
                                onClick = { onSelectSample(sample) },
                            ),
                        )
                        CoUiIconButton(
                            icon = CoUiIconKind.PLAY,
                            contentDescription = stringResource(R.string.ui_play_sample, sample.title),
                            onClick = { onPlaySample(sample) },
                            enabled = !uiState.isPlayingAudio,
                        )
                    }
                }
            }
        }

        AnimatedVisibility(visible = uiState.isProcessingSpeech) {
            CoUiCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CoUiCircularProgress(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.5.dp,
                        color = CoUiTheme.colors.primaryText,
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        CoUiText(
                            text = stringResource(R.string.ui_translating_speech),
                            style = CoUiTheme.textStyles.bodyM,
                            color = CoUiTheme.colors.labelPrimary,
                        )
                        CoUiText(
                            text = stringResource(R.string.ui_generating_local),
                            style = CoUiTheme.textStyles.bodyXS,
                            color = CoUiTheme.colors.labelSecondary,
                        )
                    }
                }
            }
        }

        if (uiState.speechTranscript.isNotBlank() ||
            uiState.speechTranslation.isNotBlank() ||
            uiState.detectedEmotion != null
        ) {
            CoUiCard {
                Column(modifier = Modifier.fillMaxWidth()) {
                    CoUiText(
                        text = uiState.selectedAudioName ?: stringResource(R.string.ui_recognized_output),
                        style = CoUiTheme.textStyles.headlineXS,
                        color = CoUiTheme.colors.labelPrimary,
                    )
                    Spacer(Modifier.height(6.dp))
                    CoUiEmotionBadge(emotion = uiState.detectedEmotion)
                }

                Spacer(Modifier.height(10.dp))

                // Source transcript + detected language.
                val effectiveSrc = if (
                    uiState.srcSpeechLang == SupportedLanguage.AUTO_DETECT &&
                    uiState.detectedSpeechLang != null
                ) {
                    uiState.detectedSpeechLang
                } else {
                    uiState.srcSpeechLang
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    CoUiText(
                        text = stringResource(R.string.ui_speech_input, effectiveSrc.displayName),
                        style = CoUiTheme.textStyles.bodyS,
                        color = CoUiTheme.colors.primaryText,
                    )
                    if (uiState.asrLatencyMs > 0) {
                        CoUiText(
                            text = stringResource(R.string.ui_ms, uiState.asrLatencyMs),
                            style = CoUiTheme.textStyles.bodyXS,
                            color = CoUiTheme.colors.labelTertiary,
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                CoUiLocalizedTextPanel(
                    text = if (uiState.speechTranscript.isEmpty()) {
                        noSpeechText
                    } else {
                        localizedSpeechText(uiState.speechTranscript)
                    },
                    rtl = effectiveSrc.isRtl,
                    tone = CoUiTone.NEUTRAL,
                )
                if (uiState.audioDurationSec > 0f) {
                    Spacer(Modifier.height(4.dp))
                    CoUiText(
                        text = stringResource(
                            R.string.ui_audio_duration,
                            "%.1f".format(uiState.audioDurationSec),
                        ),
                        style = CoUiTheme.textStyles.bodyXS,
                        color = CoUiTheme.colors.labelTertiary,
                    )
                }

                Spacer(Modifier.height(10.dp))

                // Translated text.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    CoUiText(
                        text = stringResource(R.string.ui_translation_label, uiState.tgtSpeechLang.displayName),
                        style = CoUiTheme.textStyles.bodyS,
                        color = CoUiTheme.colors.primaryText,
                    )
                    if (uiState.speechTransLatencyMs > 0) {
                        CoUiText(
                            text = stringResource(R.string.ui_ms, uiState.speechTransLatencyMs),
                            style = CoUiTheme.textStyles.bodyXS,
                            color = CoUiTheme.colors.labelTertiary,
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                CoUiLocalizedTextPanel(
                    text = if (uiState.speechTranslation.isEmpty()) {
                        stringResource(R.string.ui_no_translation)
                    } else {
                        localizedSpeechText(uiState.speechTranslation)
                    },
                    rtl = uiState.tgtSpeechLang.isRtl,
                    tone = CoUiTone.PRIMARY,
                )

                if (uiState.hasSynthesizedAudio || uiState.speechTranslation.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CoUiButton(
                            text = stringResource(
                                if (uiState.isPlayingAudio) R.string.ui_speaking else R.string.ui_play_audio,
                            ),
                            onClick = onPlaySynthesized,
                            modifier = Modifier.weight(1f),
                            enabled = !uiState.isPlayingAudio,
                            icon = CoUiIconKind.PLAY,
                        )
                        if (uiState.isPlayingAudio) {
                            CoUiOutlinedButton(
                                text = stringResource(R.string.ui_stop),
                                onClick = onStopPlayback,
                                icon = CoUiIconKind.STOP,
                            )
                        }
                    }
                    if (uiState.ttsLatencyMs > 0) {
                        CoUiText(
                            text = stringResource(R.string.ui_tts_latency, uiState.ttsLatencyMs),
                            style = CoUiTheme.textStyles.bodyXS,
                            color = CoUiTheme.colors.labelTertiary,
                        )
                    }
                }

                val copyText = stringResource(
                    R.string.ui_copy_text,
                    uiState.speechTranscript,
                    uiState.speechTranslation,
                )
                val copyLabel = stringResource(R.string.ui_copy_label_ai)
                val copiedToast = stringResource(R.string.ui_copied_clipboard)
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    CoUiOutlinedButton(
                        text = stringResource(R.string.ui_copy),
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText(copyLabel, copyText))
                            Toast.makeText(context, copiedToast, Toast.LENGTH_SHORT).show()
                        },
                        icon = CoUiIconKind.COPY,
                    )
                }
            }
        }

        uiState.ttsNote?.let { note ->
            CoUiInfoBanner(text = stringResource(R.string.ui_tts_note, note))
        }
        uiState.errorMessage?.let { error -> CoUiErrorBanner(message = localizedError(error)) }
    }
}

@Composable
private fun TextTab(
    uiState: MainUiState,
    scrollState: ScrollState,
    onInputChanged: (String) -> Unit,
    onTranslate: () -> Unit,
    onClear: () -> Unit,
    onSpeakText: () -> Unit,
    onStopPlayback: () -> Unit,
    onSrcLangChanged: (SupportedLanguage) -> Unit,
    onTgtLangChanged: (SupportedLanguage) -> Unit,
    onSwapLangs: () -> Unit,
    onGrammarToggle: (Boolean) -> Unit,
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CoUiLanguageSelector(
            srcLang = uiState.srcTextLang,
            tgtLang = uiState.tgtTextLang,
            sourceLanguages = SupportedLanguage.allTranslationLanguages,
            targetLanguages = SupportedLanguage.allTranslationLanguages,
            onSrcSelected = onSrcLangChanged,
            onTgtSelected = onTgtLangChanged,
            onSwap = onSwapLangs,
        )

        CoUiCard {
            CoUiTextField(
                value = uiState.inputText,
                onValueChange = onInputChanged,
                label = stringResource(R.string.ui_enter_text, uiState.srcTextLang.displayName),
                modifier = Modifier.height(120.dp),
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                CoUiOutlinedButton(
                    text = stringResource(R.string.ui_clear),
                    onClick = onClear,
                    icon = CoUiIconKind.CLOSE,
                )
                CoUiButton(
                    text = stringResource(R.string.ui_translate),
                    onClick = onTranslate,
                    enabled = uiState.inputText.isNotBlank(),
                    loading = uiState.isTranslating,
                    icon = CoUiIconKind.CHECK,
                )
            }
        }

        GrammarCorrectionRows(
            enabled = uiState.grammarCorrectionEnabled,
            cleanedText = uiState.textGrammarCleanedText,
            onToggle = onGrammarToggle,
        )

        if (uiState.translatedText.isNotBlank()) {
            CoUiCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    CoUiText(
                        text = stringResource(R.string.ui_target_translation, uiState.tgtTextLang.displayName),
                        style = CoUiTheme.textStyles.headlineXS,
                        color = CoUiTheme.colors.labelPrimary,
                    )
                    CoUiBadge(
                        text = stringResource(R.string.ui_ms, uiState.latencyMs),
                        tone = CoUiTone.INFO,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CoUiBadge(
                        text = translationTier(uiState.latencyMs, uiState.tokensGenerated),
                        tone = CoUiTone.PRIMARY,
                    )
                    CoUiStatusChip(
                        text = stringResource(R.string.ui_tokens, uiState.tokensGenerated),
                        active = uiState.tokensGenerated > 0,
                    )
                }
                Spacer(Modifier.height(10.dp))
                CoUiLocalizedTextPanel(
                    text = uiState.translatedText,
                    rtl = uiState.tgtTextLang.isRtl,
                    tone = CoUiTone.PRIMARY,
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    CoUiButton(
                        text = stringResource(
                            if (uiState.isPlayingAudio) R.string.ui_speaking else R.string.ui_play_audio,
                        ),
                        onClick = onSpeakText,
                        enabled = !uiState.isPlayingAudio,
                        icon = CoUiIconKind.PLAY,
                    )
                    if (uiState.isPlayingAudio) {
                        CoUiOutlinedButton(
                            text = stringResource(R.string.ui_stop),
                            onClick = onStopPlayback,
                            icon = CoUiIconKind.STOP,
                        )
                    }
                    val copyLabel = stringResource(R.string.ui_copy_label_translation)
                    val copiedToast = stringResource(R.string.ui_copied_translation)
                    CoUiOutlinedButton(
                        text = stringResource(R.string.ui_copy),
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText(copyLabel, uiState.translatedText))
                            Toast.makeText(context, copiedToast, Toast.LENGTH_SHORT).show()
                        },
                        icon = CoUiIconKind.COPY,
                    )
                }
            }
        }

        uiState.errorMessage?.let { error -> CoUiErrorBanner(message = localizedError(error)) }
    }
}

@Composable
private fun translationTier(latencyMs: Long, tokensGenerated: Int): String =
    when {
        latencyMs <= 2L && tokensGenerated == 0 -> stringResource(R.string.ui_tier_cache)
        tokensGenerated > 0 -> stringResource(R.string.ui_tier_engine)
        else -> stringResource(R.string.ui_tier_phrasebook)
    }

/**
 * OCR tab: import/downsample an image, preview it, pick a built-in offline sample, edit the
 * extracted text, then clean / translate / speak it.
 */
@Composable
private fun OcrTab(
    uiState: MainUiState,
    scrollState: ScrollState,
    onOcrInputChanged: (String) -> Unit,
    onLoadSample: (OcrSample) -> Unit,
    onProcessOcrImage: (Uri) -> Unit,
    onCleanGrammar: (String) -> Unit,
    onTranslateOcr: () -> Unit,
    onSpeakOcr: () -> Unit,
) {
    val context = LocalContext.current
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var decodeFailed by remember { mutableStateOf(false) }

    val ocrPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) {
            val decoded = decodeDownsampledBitmap(context, uri)
            if (decoded != null) {
                decodeFailed = false
                previewBitmap = decoded
                // Real bundled ML Kit OCR — never a canned sample for a picked image.
                onProcessOcrImage(uri)
            } else {
                decodeFailed = true
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CoUiCard {
            CoUiSectionTitle(text = stringResource(R.string.ui_ocr_title))
            Spacer(Modifier.height(10.dp))
            CoUiButton(
                text = stringResource(R.string.ui_ocr_pick_image),
                onClick = { ocrPickerLauncher.launch("image/*") },
                modifier = Modifier.fillMaxWidth(),
                icon = CoUiIconKind.FOLDER,
            )
        }

        if (decodeFailed) {
            CoUiErrorBanner(message = stringResource(R.string.ui_error_generic))
        }

        previewBitmap?.let { bitmap ->
            CoUiCard {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = stringResource(R.string.ui_ocr_extracted),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentScale = ContentScale.Fit,
                )
            }
        }

        CoUiCard {
            CoUiSectionTitle(text = stringResource(R.string.ui_ocr_samples))
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OfflineOcrEngine.SAMPLES.forEach { sample ->
                    CoUiBadge(
                        text = sample.title,
                        tone = if (uiState.selectedOcrSampleId == sample.id) {
                            CoUiTone.PRIMARY
                        } else {
                            CoUiTone.NEUTRAL
                        },
                        modifier = Modifier.clickable { onLoadSample(sample) },
                    )
                }
            }
        }

        CoUiCard {
            CoUiTextField(
                value = uiState.ocrInputText,
                onValueChange = onOcrInputChanged,
                label = stringResource(R.string.ui_ocr_edit_hint),
                modifier = Modifier.height(140.dp),
            )
            Spacer(Modifier.height(10.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CoUiOutlinedButton(
                    text = stringResource(R.string.ui_grammar_correction),
                    onClick = { onCleanGrammar(uiState.ocrInputText) },
                    modifier = Modifier.fillMaxWidth(),
                    icon = CoUiIconKind.CHECK,
                )
                CoUiButton(
                    text = stringResource(R.string.ui_translate),
                    onClick = onTranslateOcr,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = uiState.ocrInputText.isNotBlank(),
                    icon = CoUiIconKind.CHECK,
                )
                CoUiButton(
                    text = stringResource(R.string.ui_speak),
                    onClick = onSpeakOcr,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = uiState.ocrTranslatedText.isNotBlank(),
                    icon = CoUiIconKind.PLAY,
                )
            }
        }

        CoUiCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                CoUiSectionTitle(text = stringResource(R.string.ui_ocr_extracted))
                CoUiBadge(
                    text = stringResource(
                        when {
                            uiState.isProcessingOcr -> R.string.ui_processing
                            uiState.ocrTranslatedText.isNotBlank() -> R.string.ui_done
                            else -> R.string.ui_error_generic
                        },
                    ),
                    tone = if (uiState.isProcessingOcr) CoUiTone.WARNING else CoUiTone.SUCCESS,
                )
            }
            Spacer(Modifier.height(8.dp))
            CoUiCardListGroup(
                items = listOf(
                    @Composable {
                        CoUiListRow(
                            title = stringResource(R.string.ui_grammar_cleaned),
                            summary = uiState.ocrGrammarCleanedText.ifBlank { null },
                        )
                    },
                    @Composable {
                        CoUiListRow(
                            title = stringResource(R.string.ui_ocr_extracted),
                            summary = uiState.ocrTranslatedText.ifBlank { null },
                        )
                    },
                ),
            )
        }

        uiState.errorMessage?.let { error -> CoUiErrorBanner(message = localizedError(error)) }
    }
}

/** SOS tab: offline emergency phrasebook with category chips, speak actions and flashcards. */
@Composable
private fun SosTab(
    uiState: MainUiState,
    scrollState: ScrollState,
    onSelectCategory: (String) -> Unit,
    onSpeak: (String) -> Unit,
    onFlashcard: (String) -> Unit,
    onDismissFlashcard: () -> Unit,
) {
    val phrases = EmergencyPhrasebook.phrasesFor(uiState.selectedEmergencyCategory)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CoUiCard {
            CoUiSectionTitle(text = stringResource(R.string.ui_sos_title))
            Spacer(Modifier.height(12.dp))
            CoUiSectionTitle(text = stringResource(R.string.ui_emergency_categories))
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                EmergencyPhrasebook.CATEGORIES.forEach { category ->
                    CoUiBadge(
                        text = category,
                        tone = if (uiState.selectedEmergencyCategory == category) {
                            CoUiTone.PRIMARY
                        } else {
                            CoUiTone.NEUTRAL
                        },
                        modifier = Modifier.clickable { onSelectCategory(category) },
                    )
                }
            }
        }

        CoUiCardListGroup(
            items = phrases.map { phrase ->
                @Composable {
                    CoUiListRow(
                        title = phrase.english,
                        endActions = {
                            CoUiIconButton(
                                icon = CoUiIconKind.PLAY,
                                contentDescription = stringResource(R.string.ui_speak),
                                onClick = { onSpeak(phrase.english) },
                            )
                            Spacer(Modifier.width(8.dp))
                            CoUiBadge(
                                text = stringResource(R.string.ui_flashcard),
                                tone = CoUiTone.PRIMARY,
                                modifier = Modifier.clickable { onFlashcard(phrase.english) },
                            )
                        },
                    )
                }
            },
        )

        uiState.errorMessage?.let { error -> CoUiErrorBanner(message = localizedError(error)) }
    }

    uiState.flashcardText?.let { text ->
        Dialog(onDismissRequest = onDismissFlashcard) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(CoUiTheme.colors.error),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CoUiText(
                        text = text,
                        style = CoUiTheme.textStyles.displayXS,
                        color = CoUiTheme.colors.onPrimary,
                    )
                    Spacer(Modifier.height(24.dp))
                    CoUiOutlinedButton(
                        text = stringResource(R.string.ui_close),
                        onClick = onDismissFlashcard,
                        icon = CoUiIconKind.CLOSE,
                    )
                }
            }
        }
    }
}

/** History tab: searchable, filterable list of saved translations with replay/copy/pin actions. */
@Composable
private fun HistoryTab(
    uiState: MainUiState,
    scrollState: ScrollState,
    onFilterChanged: (String) -> Unit,
    onReplay: (String, String) -> Unit,
    onTogglePin: (String) -> Unit,
    onClear: () -> Unit,
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }

    val filters = listOf(
        stringResource(R.string.ui_history_all) to "All",
        stringResource(R.string.ui_history_voice) to "Voice",
        stringResource(R.string.ui_history_text) to "Text",
        stringResource(R.string.ui_history_ocr) to "OCR",
        stringResource(R.string.ui_history_pinned) to "Pinned",
    )
    val entries = uiState.chatHistory.filter { entry ->
        val matchesFilter = when (uiState.historyFilter) {
            "Pinned" -> entry.pinned
            "All" -> true
            else -> entry.source.equals(uiState.historyFilter, ignoreCase = true)
        }
        val matchesQuery = query.isBlank() ||
            entry.input.contains(query, ignoreCase = true) ||
            entry.output.contains(query, ignoreCase = true)
        matchesFilter && matchesQuery
    }

    val copyLabel = stringResource(R.string.ui_copy_label_translation)
    val copiedToast = stringResource(R.string.ui_copied_translation)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CoUiCard {
            CoUiSectionTitle(text = stringResource(R.string.ui_history_title))
            Spacer(Modifier.height(10.dp))
            CoUiTextField(
                value = query,
                onValueChange = { query = it },
                label = stringResource(R.string.ui_history_search),
                modifier = Modifier.height(72.dp),
                singleLine = true,
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                filters.forEach { (label, value) ->
                    CoUiBadge(
                        text = label,
                        tone = if (uiState.historyFilter == value) CoUiTone.PRIMARY else CoUiTone.NEUTRAL,
                        modifier = Modifier.clickable { onFilterChanged(value) },
                    )
                }
            }
        }

        if (entries.isEmpty()) {
            CoUiInfoBanner(text = stringResource(R.string.ui_no_history))
        } else {
            CoUiCardListGroup(
                items = entries.map { entry ->
                    @Composable {
                        CoUiListRow(
                            title = entry.source + " • " + entry.targetLang,
                            summary = entry.output,
                            endActions = {
                                CoUiIconButton(
                                    icon = CoUiIconKind.PLAY,
                                    contentDescription = stringResource(R.string.ui_replay),
                                    onClick = { onReplay(entry.output, entry.targetLang) },
                                )
                                Spacer(Modifier.width(6.dp))
                                CoUiIconButton(
                                    icon = CoUiIconKind.COPY,
                                    contentDescription = stringResource(R.string.ui_copy),
                                    onClick = {
                                        val clipboard =
                                            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText(copyLabel, entry.output))
                                        Toast.makeText(context, copiedToast, Toast.LENGTH_SHORT).show()
                                    },
                                )
                                Spacer(Modifier.width(6.dp))
                                CoUiBadge(
                                    text = stringResource(
                                        if (entry.pinned) R.string.ui_unpin else R.string.ui_pin,
                                    ),
                                    tone = if (entry.pinned) CoUiTone.SUCCESS else CoUiTone.NEUTRAL,
                                    modifier = Modifier.clickable { onTogglePin(entry.id) },
                                )
                            },
                        )
                    }
                },
            )
        }

        CoUiOutlinedButton(
            text = stringResource(R.string.ui_history_clear),
            onClick = onClear,
            modifier = Modifier.fillMaxWidth(),
            icon = CoUiIconKind.CLOSE,
        )

        uiState.errorMessage?.let { error -> CoUiErrorBanner(message = localizedError(error)) }
    }
}

@Composable
private fun ModelsTab(
    uiState: MainUiState,
    scrollState: ScrollState,
    onRefresh: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CoUiInfoBanner(text = stringResource(R.string.ui_offline_engine_info))

        CoUiSectionTitle(text = stringResource(R.string.ui_engine_readiness))
        val engines = listOf(
            stringResource(R.string.ui_engine_whisper) to "Whisper",
            stringResource(R.string.ui_engine_indictrans) to "IndicTrans",
            stringResource(R.string.ui_engine_piper) to "Piper",
            stringResource(R.string.ui_engine_emotion) to "Emotion",
        )
        CoUiCardListGroup(
            items = engines.map { (label, keyword) ->
                @Composable {
                    val model = uiState.modelsDiagnostics.firstOrNull {
                        it.name.contains(keyword, ignoreCase = true)
                    }
                    CoUiListRow(
                        title = label,
                        endActions = {
                            CoUiStatusChip(
                                text = model?.statusText ?: stringResource(R.string.ui_not_reported),
                                active = model?.isLoaded == true,
                            )
                        },
                    )
                }
            },
        )

        CoUiSectionTitle(text = stringResource(R.string.ui_installed_models))
        if (uiState.modelsDiagnostics.isEmpty()) {
            CoUiInfoBanner(text = stringResource(R.string.ui_no_diagnostics))
        } else {
            CoUiCardListGroup(
                items = uiState.modelsDiagnostics.map { model ->
                    @Composable {
                        CoUiListRow(
                            title = model.name,
                            summary = stringResource(
                                R.string.ui_model_info,
                                model.category,
                                model.fileSizeBytes / (1024 * 1024),
                            ),
                            endActions = {
                                CoUiStatusChip(
                                    text = model.statusText.ifBlank {
                                        stringResource(R.string.ui_status_unavailable)
                                    },
                                    active = model.isLoaded,
                                )
                            },
                        )
                    }
                },
            )
        }

        CoUiButton(
            text = stringResource(R.string.ui_refresh_status),
            onClick = onRefresh,
            modifier = Modifier.fillMaxWidth(),
            icon = CoUiIconKind.REFRESH,
        )

        uiState.errorMessage?.let { error -> CoUiErrorBanner(message = localizedError(error)) }
    }
}

package com.example.offlineaitranslator.ui.coui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.offlineaitranslator.R
import com.example.offlineaitranslator.engine.EmotionResult
import com.example.offlineaitranslator.engine.ExtractionState
import com.example.offlineaitranslator.engine.SupportedLanguage
import com.example.offlineaitranslator.ui.main.MainUiState

/**
 * Read-only bridges between the names used by the COUI UI brief and the ViewModel's real state.
 *
 * These are extension properties/functions only: no field is added, renamed or removed in
 * MainScreenViewModel. Every getter delegates directly to an existing [MainUiState] property.
 */

val MainUiState.isExtractingModels: Boolean
    get() = extractionState is ExtractionState.Extracting

val MainUiState.extractionProgress: Float
    get() = (extractionState as? ExtractionState.Extracting)?.progress ?: 0f

/** Localized extraction status — all user-facing text lives in `values/` and `values-zh/`. */
@Composable
fun MainUiState.extractionStatusText(): String = when (val state = extractionState) {
    is ExtractionState.Extracting -> stringResource(
        R.string.ui_extracting_status,
        state.currentFile,
        state.filesExtracted,
        state.totalFiles,
    )
    is ExtractionState.Completed -> stringResource(R.string.ui_models_ready, state.totalFiles)
    is ExtractionState.Error -> state.message
    ExtractionState.Idle -> stringResource(R.string.ui_extraction_idle)
}

val MainUiState.detectedEmotion: EmotionResult?
    get() = emotionResult

val SupportedLanguage.Companion.allTranslationLanguages: List<SupportedLanguage>
    get() = realLanguages

package com.example.offlineaitranslator.ui.main

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.offlineaitranslator.ui.coui.CoUiBadge
import com.example.offlineaitranslator.ui.coui.CoUiDivider
import com.example.offlineaitranslator.ui.coui.CoUiCard
import com.example.offlineaitranslator.ui.coui.CoUiMicButton
import com.example.offlineaitranslator.ui.coui.CoUiStatusChip
import com.example.offlineaitranslator.ui.coui.CoUiTone

/**
 * RETIRED legacy truck-art components.
 *
 * The Step 1 overhaul moved the whole app to the vendored `ui.coui` design system. These names are kept
 * only as deprecated delegating shims for source compatibility with any out-of-tree caller; production
 * code (MainScreen.kt) no longer references them. No legacy colours or geometry remain here.
 */

@Deprecated("Retired: use CoUiTabBar / CoUiDivider from ui.coui instead.")
@Composable
fun TruckArtKanguraTrim(modifier: Modifier = Modifier) {
    CoUiDivider(modifier = modifier)
}

@Deprecated("Retired: use CoUiMicButton from ui.coui instead.", ReplaceWith("CoUiMicButton(isRecording, isProcessing, languageLabel, onClick, modifier)"))
@Composable
fun TactileVintageMicButton(
    isRecording: Boolean,
    isProcessing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    languageLabel: String = "English",
) {
    CoUiMicButton(
        isRecording = isRecording,
        isProcessing = isProcessing,
        languageLabel = languageLabel,
        onClick = onClick,
        modifier = modifier,
    )
}

@Deprecated("Retired: use CoUiBadge from ui.coui instead.", ReplaceWith("CoUiBadge(text, CoUiTone.NEUTRAL, modifier)"))
@Composable
fun RetroSignBadge(text: String, modifier: Modifier = Modifier) {
    CoUiBadge(text = text, tone = CoUiTone.NEUTRAL, modifier = modifier)
}

@Deprecated("Retired: use CoUiStatusChip from ui.coui instead.", ReplaceWith("CoUiStatusChip(versionText, true, modifier)"))
@Composable
fun LicensePlateVersionBadge(versionText: String = "v2.0", modifier: Modifier = Modifier) {
    CoUiStatusChip(text = versionText, active = true, modifier = modifier)
}

@Deprecated("Retired: use CoUiBadge from ui.coui instead.", ReplaceWith("CoUiBadge(text, CoUiTone.SUCCESS, modifier)"))
@Composable
fun EnamelOfflineSeal(modifier: Modifier = Modifier, text: String = "Offline") {
    CoUiBadge(text = text, tone = CoUiTone.SUCCESS, modifier = modifier)
}

@Deprecated("Retired: use CoUiCard from ui.coui instead.", ReplaceWith("CoUiCard(modifier, content = content)"))
@Composable
fun TruckArtCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    CoUiCard(modifier = modifier, content = content)
}

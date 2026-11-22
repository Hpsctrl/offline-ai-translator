package com.example.offlineaitranslator.ui.coui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.example.offlineaitranslator.R
import com.example.offlineaitranslator.engine.EmotionResult
import com.example.offlineaitranslator.engine.SupportedLanguage
import com.example.offlineaitranslator.theme.CoUiShapes
import com.example.offlineaitranslator.theme.CoUiTheme

/** Semantic tones used by the vendored COUI chips and banners. */
enum class CoUiTone { PRIMARY, SUCCESS, INFO, WARNING, DANGER, NEUTRAL }

@Composable
private fun CoUiTone.accent(): Color = when (this) {
    CoUiTone.PRIMARY -> CoUiTheme.colors.primaryText
    CoUiTone.SUCCESS -> CoUiTheme.colors.green
    CoUiTone.INFO -> CoUiTheme.colors.blue
    CoUiTone.WARNING -> CoUiTheme.colors.orange
    CoUiTone.DANGER -> CoUiTheme.colors.error
    CoUiTone.NEUTRAL -> CoUiTheme.colors.labelSecondary
}

@Composable
private fun CoUiTone.container(): Color =
    accent().copy(alpha = if (CoUiTheme.colors.isDark) 0.22f else 0.12f)

/**
 * COUI page scaffold. The page surface is `backgroundWithCard` (COUI's #F0F1F2 / #000000), which is
 * what gives white cards their contrast — using plain `background` makes cards disappear. The large
 * title collapses into a pinned small bar as [scrollState] scrolls.
 */
@Composable
fun CoUiScaffold(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    privacyBadge: String? = null,
    version: String? = null,
    scrollState: ScrollState? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val collapsed by remember(scrollState) {
        derivedStateOf { scrollState != null && scrollState.value > 96 }
    }
    // COUI-17 style page-grow: the page shrinks slightly as the list scrolls up and grows back
    // on scroll down — a fluid "pop-up" effect on top of the collapsing top bar.
    val pageScale by remember(scrollState) {
        derivedStateOf {
            val scrolled = (scrollState?.value ?: 0f).coerceAtLeast(0f)
            1f - (scrolled / 400f).coerceIn(0f, 0.07f)
        }
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CoUiTheme.colors.backgroundWithCard),
    ) {
        CoUiTopBar(
            title = title,
            subtitle = subtitle,
            privacyBadge = privacyBadge,
            version = version,
            collapsed = collapsed,
            actions = actions,
        )
        Column(
            modifier = Modifier.fillMaxWidth().weight(1f).graphicsLayer {
                scaleX = pageScale
                scaleY = pageScale
            },
            content = content,
        )
    }
}

/** COUI 17 top-bar contour glow: a soft accent band under the bar (material edge light). */
@Composable
private fun CoUiTopBarGlow(modifier: Modifier = Modifier) {
    val accent = CoUiTheme.colors.primaryText
    Box(
        modifier
            .fillMaxWidth()
            .height(4.dp)
            .drawBehind {
                drawRect(
                    brush = Brush.horizontalGradient(
                        0f to Color.Transparent,
                        0.2f to accent.copy(alpha = 0.10f),
                        0.5f to accent.copy(alpha = 0.28f),
                        0.8f to accent.copy(alpha = 0.10f),
                        1f to Color.Transparent,
                    ),
                )
            },
    )
}

/**
 * COUI top bar, integrated into the page surface. It paints the page's `backgroundWithCard`
 * (never a separate `bar` slab) and has no divider — the only edge is the soft contour glow.
 * The 56dp action row stays pinned; the large title lives below it and collapses away on scroll,
 * so badges and text never share the same line.
 */
@Composable
fun CoUiTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    privacyBadge: String? = null,
    version: String? = null,
    collapsed: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Box(modifier.fillMaxWidth().background(CoUiTheme.colors.backgroundWithCard)) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (collapsed) {
                    CoUiText(
                        text = title,
                        style = CoUiTheme.textStyles.headlineXS,
                        color = CoUiTheme.colors.labelPrimary,
                        maxLines = 3,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                if (!collapsed) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (privacyBadge != null) CoUiBadge(text = privacyBadge, tone = CoUiTone.SUCCESS)
                        if (version != null) CoUiStatusChip(text = version, active = true)
                        actions()
                    }
                }
            }
            if (!collapsed) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 18.dp),
                ) {
                    CoUiText(
                        text = title,
                        style = CoUiTheme.textStyles.displayXS,
                        color = CoUiTheme.colors.labelPrimary,
                        maxLines = 3,
                    )
                    if (subtitle != null) {
                        CoUiText(
                            text = subtitle,
                            style = CoUiTheme.textStyles.bodyS,
                            color = CoUiTheme.colors.labelSecondary,
                        )
                    }
                }
            }
        }
        CoUiTopBarGlow(modifier = Modifier.align(Alignment.BottomCenter))
    }
}

/** COUI 17 capsule tab row (the library's `ChipTabRow`), with press-sink feedback. */
@Composable
fun CoUiTabBar(
    items: List<String>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    icons: List<CoUiIconKind>? = null,
) {
    Box(
        modifier
            .fillMaxWidth()
            .background(CoUiTheme.colors.backgroundWithCard)
            .padding(horizontal = 4.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp)
                .clip(CoUiShapes.section)
                .background(CoUiTheme.colors.container8)
                .border(0.5.dp, CoUiTheme.colors.divider, CoUiShapes.section)
                .padding(2.dp),
        ) {
            items.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                val interaction = remember { MutableInteractionSource() }
                val accent = if (selected) CoUiTheme.colors.primaryText else CoUiTheme.colors.labelSecondary
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .clip(CoUiShapes.section)
                        .background(if (selected) CoUiTheme.colors.card else Color.Transparent)
                        .border(
                            width = if (selected) 1.dp else 0.dp,
                            color = if (selected) CoUiTheme.colors.primaryText.copy(alpha = 0.45f) else Color.Transparent,
                            shape = CoUiShapes.section,
                        )
                        .clickable(
                            interactionSource = interaction,
                            indication = null,
                        ) { onTabSelected(index) }
                        .couiTouchMotion(maxStretch = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        if (icons != null && index < icons.size) {
                            CoUiIcon(
                                kind = icons[index],
                                tint = accent,
                                iconSize = 16.dp,
                                strokeWidth = 1.8.dp,
                            )
                            Spacer(Modifier.height(2.dp))
                        }
                        CoUiText(
                            text = label,
                            style = CoUiTheme.textStyles.tag,
                            color = accent,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
    CoUiDivider()
}

/** COUI card: 24dp corner (Normal.XL), no Material border or shadow. */
@Composable
fun CoUiCard(
    modifier: Modifier = Modifier,
    tonal: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    CoUiSurface(
        modifier = modifier.fillMaxWidth(),
        shape = CoUiShapes.card,
        color = if (tonal) CoUiTheme.colors.container8 else CoUiTheme.colors.card,
        contentAlignment = Alignment.TopStart,
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

/** Primary COUI button: 44dp capsule, theme fill, COUI press tint and spring sink. */
@Composable
fun CoUiButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    primary: Boolean = true,
    loading: Boolean = false,
    icon: CoUiIconKind? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val usable = enabled && !loading
    val container = when {
        !usable -> if (primary) CoUiTheme.colors.disable else CoUiTheme.colors.disabledSecondaryButton
        primary -> CoUiTheme.colors.primary
        else -> CoUiTheme.colors.secondaryButton
    }
    val content = when {
        !usable -> if (primary) CoUiTheme.colors.disabledOnPrimaryButton else CoUiTheme.colors.disabledOnSecondaryButton
        primary -> CoUiTheme.colors.onPrimary
        else -> CoUiTheme.colors.labelPrimary
    }
    CoUiSurface(
        modifier = modifier
            .defaultMinSize(minHeight = 44.dp)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = usable,
                onClick = onClick,
            )
            .couiTouchMotion(enabled = usable)
            .couiContourGlow(
                cornerRadius = 72.dp,
                color = CoUiTheme.colors.primaryText,
                enabled = usable,
                intensity = if (primary) 0.9f else 0.35f,
            ),
        shape = CoUiShapes.button,
        color = container,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            if (loading) {
                CoUiCircularProgress(modifier = Modifier.size(16.dp), color = content)
            } else {
                if (icon != null) CoUiIcon(icon, tint = content, iconSize = 18.dp, strokeWidth = 2.2.dp)
                CoUiText(text = text, style = CoUiTheme.textStyles.buttonL, color = content, maxLines = 1)
            }
        }
    }
}

/** COUI secondary button: an opaque neutral capsule, not an outlined Material button. */
@Composable
fun CoUiOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: CoUiIconKind? = null,
) {
    CoUiButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        primary = false,
        icon = icon,
    )
}

/** COUI capsule icon button with press sink. */
@Composable
fun CoUiIconButton(
    icon: CoUiIconKind,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val label = contentDescription
    CoUiSurface(
        modifier = modifier
            .size(40.dp)
            .semantics { this.contentDescription = label }
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .couiTouchMotion(enabled = enabled, maxStretch = 12.dp)
            .couiContourGlow(
                cornerRadius = 72.dp,
                color = CoUiTheme.colors.primaryText,
                enabled = enabled,
                intensity = 0.5f,
            ),
        shape = CoUiShapes.pill,
        color = CoUiTheme.colors.container8,
    ) {
        Box(contentAlignment = Alignment.Center) {
            CoUiIcon(
                kind = icon,
                tint = if (enabled) CoUiTheme.colors.primaryText else CoUiTheme.colors.disabledNeutral,
                iconSize = 20.dp,
            )
        }
    }
}

/** COUI capsule chip. */
@Composable
fun CoUiBadge(
    text: String,
    modifier: Modifier = Modifier,
    tone: CoUiTone = CoUiTone.NEUTRAL,
) {
    CoUiSurface(
        modifier = modifier,
        shape = CoUiShapes.chip,
        color = tone.container(),
        border = BorderStroke(0.33.dp, tone.accent().copy(alpha = 0.25f)),
    ) {
        CoUiText(
            text = text,
            style = CoUiTheme.textStyles.bodyXS,
            color = tone.accent(),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            maxLines = 1,
        )
    }
}

/** COUI status chip with a leading state dot. */
@Composable
fun CoUiStatusChip(
    text: String,
    modifier: Modifier = Modifier,
    active: Boolean = false,
) {
    val tone = if (active) CoUiTone.SUCCESS else CoUiTone.NEUTRAL
    CoUiSurface(
        modifier = modifier,
        shape = CoUiShapes.chip,
        color = tone.container(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            CoUiStateDot(color = tone.accent())
            CoUiText(
                text = text,
                style = CoUiTheme.textStyles.bodyXS,
                color = tone.accent(),
                maxLines = 1,
            )
        }
    }
}

/** COUI language field. The dropdown is a COUI card popup anchored to the field. */
@Composable
fun CoUiLanguagePicker(
    label: String,
    selected: SupportedLanguage,
    languages: List<SupportedLanguage>,
    onSelected: (SupportedLanguage) -> Unit,
    modifier: Modifier = Modifier,
    showBeta: Boolean = false,
) {
    var expanded by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val popOffset = with(LocalDensity.current) { (64f * density).roundToInt() }
    Box(modifier = modifier) {
        CoUiSurface(
            modifier = Modifier
                .clip(CoUiShapes.section)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                ) { expanded = true }
                .couiTouchMotion(maxStretch = 10.dp)
                .couiContourGlow(
                    cornerRadius = 16.dp,
                    color = CoUiTheme.colors.primaryText,
                    intensity = 0.4f,
                ),
            shape = CoUiShapes.section,
            color = CoUiTheme.colors.container8,
            border = BorderStroke(0.33.dp, CoUiTheme.colors.divider),
            contentAlignment = Alignment.TopStart,
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                CoUiText(
                    text = label,
                    style = CoUiTheme.textStyles.bodyXS,
                    color = CoUiTheme.colors.labelTertiary,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CoUiText(
                        text = selected.displayName,
                        style = CoUiTheme.textStyles.buttonM,
                        color = CoUiTheme.colors.labelPrimary,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    if (showBeta) {
                        Spacer(Modifier.width(4.dp))
                        CoUiBadge(
                            text = stringResource(R.string.ui_beta_badge),
                            tone = CoUiTone.WARNING,
                        )
                    }
                    CoUiIcon(
                        kind = CoUiIconKind.CHEVRON_DOWN,
                        tint = CoUiTheme.colors.labelTertiary,
                        iconSize = 16.dp,
                    )
                }
            }
        }
        if (expanded) {
            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(0, popOffset),
                onDismissRequest = { expanded = false },
                properties = PopupProperties(focusable = true),
            ) {
                CoUiSurface(
                    modifier = Modifier
                        .widthIn(min = 180.dp, max = 280.dp)
                        .padding(vertical = 4.dp),
                    shape = CoUiShapes.card,
                    color = CoUiTheme.colors.backgroundTop,
                    border = BorderStroke(0.5.dp, CoUiTheme.colors.divider),
                ) {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        languages.forEach { lang ->
                            val langInteraction = remember(lang) { MutableInteractionSource() }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 48.dp)
                                    .background(
                                        if (lang == selected) CoUiTheme.colors.container8 else Color.Transparent,
                                    )
                                    .clickable(
                                        interactionSource = langInteraction,
                                        indication = null,
                                    ) {
                                        onSelected(lang)
                                        expanded = false
                                    }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    CoUiText(
                                        text = lang.displayName,
                                        style = CoUiTheme.textStyles.bodyM,
                                        color = CoUiTheme.colors.labelPrimary,
                                        maxLines = 1,
                                    )
                                    if (lang.nativeName != lang.displayName) {
                                        CoUiText(
                                            text = lang.nativeName,
                                            style = CoUiTheme.textStyles.bodyXS,
                                            color = CoUiTheme.colors.labelSecondary,
                                        )
                                    }
                                }
                                if (lang == selected) {
                                    CoUiIcon(
                                        kind = CoUiIconKind.CHECK,
                                        tint = CoUiTheme.colors.primaryText,
                                        iconSize = 16.dp,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** COUI source/target language selector with a capsule swap action. */
@Composable
fun CoUiLanguageSelector(
    srcLang: SupportedLanguage,
    tgtLang: SupportedLanguage,
    sourceLanguages: List<SupportedLanguage>,
    targetLanguages: List<SupportedLanguage>,
    onSrcSelected: (SupportedLanguage) -> Unit,
    onTgtSelected: (SupportedLanguage) -> Unit,
    onSwap: () -> Unit,
    modifier: Modifier = Modifier,
    voiceMode: Boolean = false,
) {
    CoUiCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CoUiLanguagePicker(
                label = stringResource(R.string.ui_from),
                selected = srcLang,
                languages = sourceLanguages,
                onSelected = onSrcSelected,
                modifier = Modifier.weight(1f),
                showBeta = if (voiceMode) srcLang.isVoiceBeta else srcLang.isBeta,
            )
            Spacer(Modifier.width(8.dp))
            CoUiIconButton(
                icon = CoUiIconKind.SWAP,
                contentDescription = stringResource(R.string.ui_swap_languages),
                onClick = onSwap,
            )
            Spacer(Modifier.width(8.dp))
            CoUiLanguagePicker(
                label = stringResource(R.string.ui_to),
                selected = tgtLang,
                languages = targetLanguages,
                onSelected = onTgtSelected,
                modifier = Modifier.weight(1f),
                showBeta = if (voiceMode) tgtLang.isVoiceBeta else tgtLang.isBeta,
            )
        }
    }
}

/** COUI grouped card list. The group owns the 24dp surface and the inset dividers. */
@Composable
fun CoUiCardListGroup(
    items: List<@Composable () -> Unit>,
    modifier: Modifier = Modifier,
) {
    CoUiSurface(
        modifier = modifier.fillMaxWidth(),
        shape = CoUiShapes.card,
        color = CoUiTheme.colors.card,
        contentAlignment = Alignment.TopStart,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            items.forEachIndexed { index, item ->
                item()
                if (index < items.lastIndex) {
                    CoUiDivider(startIndent = 16.dp)
                }
            }
        }
    }
}

/** A plain COUI preference row: 56dp minimum height, title, summary and trailing actions. */
@Composable
fun CoUiListRow(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    endActions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            CoUiText(
                text = title,
                style = CoUiTheme.textStyles.headlineXS,
                color = CoUiTheme.colors.labelPrimary,
                maxLines = 2,
            )
            if (summary != null) {
                CoUiText(
                    text = summary,
                    style = CoUiTheme.textStyles.bodyXS,
                    color = CoUiTheme.colors.labelSecondary,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Row(content = endActions)
    }
}

/**
 * COUI text field: 62dp minimum height, 16sp body text, 0.33dp resting border turning 1dp/theme on
 * focus — the COUI `TextFieldDefaults` geometry, not a Material outlined field.
 */
@Composable
fun CoUiTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 62.dp)
            .onFocusChanged { focused = it.isFocused }
            .clip(CoUiShapes.section)
            .background(CoUiTheme.colors.container8)
            .border(
                BorderStroke(
                    width = if (focused) 1.dp else 0.33.dp,
                    color = if (focused) CoUiTheme.colors.primary else CoUiTheme.colors.divider,
                ),
                CoUiShapes.section,
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        textStyle = CoUiTheme.textStyles.bodyXL.copy(color = CoUiTheme.colors.labelPrimary),
        maxLines = if (singleLine) 1 else Int.MAX_VALUE,
        decorationBox = { innerTextField ->
            Column {
                CoUiText(
                    text = label,
                    style = CoUiTheme.textStyles.bodyXS,
                    color = CoUiTheme.colors.labelSecondary,
                )
                Spacer(Modifier.height(8.dp))
                innerTextField()
            }
        },
    )
}

/** COUI extraction progress card. */
@Composable
fun CoUiProgressBanner(
    progress: Float,
    title: String,
    detail: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    CoUiCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CoUiCircularProgress(modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            CoUiText(text = title, style = CoUiTheme.textStyles.headlineXS)
        }
        Spacer(Modifier.height(12.dp))
        CoUiLinearProgress(progress = progress)
        Spacer(Modifier.height(8.dp))
        CoUiText(
            text = detail,
            style = CoUiTheme.textStyles.bodyXS,
            color = CoUiTheme.colors.labelSecondary,
            maxLines = 2,
        )
        if (onRetry != null) {
            Spacer(Modifier.height(10.dp))
            CoUiOutlinedButton(text = stringResource(R.string.ui_retry), onClick = onRetry)
        }
    }
}

/** COUI error banner. */
@Composable
fun CoUiErrorBanner(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    CoUiCard(modifier = modifier) {
        CoUiBadge(text = stringResource(R.string.ui_error_title), tone = CoUiTone.DANGER)
        Spacer(Modifier.height(8.dp))
        CoUiText(text = message, style = CoUiTheme.textStyles.bodyS)
        if (onRetry != null) {
            Spacer(Modifier.height(10.dp))
            CoUiOutlinedButton(text = stringResource(R.string.ui_retry), onClick = onRetry)
        }
    }
}

/** COUI informational banner. */
@Composable
fun CoUiInfoBanner(text: String, modifier: Modifier = Modifier) {
    CoUiCard(modifier = modifier, tonal = true) {
        CoUiText(
            text = text,
            style = CoUiTheme.textStyles.bodyXS,
            color = CoUiTheme.colors.labelSecondary,
        )
    }
}

/** COUI category / section heading. */
@Composable
fun CoUiSectionTitle(text: String, modifier: Modifier = Modifier) {
    CoUiText(
        text = text,
        style = CoUiTheme.textStyles.headlineS,
        color = CoUiTheme.colors.labelPrimary,
        modifier = modifier,
    )
}

/** COUI 17 voice waveform: mirrored capsule-bar spectrum in the theme accent. */
@Composable
fun CoUiWaveform(isRecording: Boolean, modifier: Modifier = Modifier) {
    CoUiWaveform17(
        isRecording = isRecording,
        modifier = modifier,
        barColor = CoUiTheme.colors.primaryText,
        idleColor = CoUiTheme.colors.labelQuaternary,
    )
}

/** COUI microphone button. */
@Composable
fun CoUiMicButton(
    isRecording: Boolean,
    isProcessing: Boolean,
    languageLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (isRecording) {
                CoUiMicPulseRings(
                    color = CoUiTheme.colors.error,
                    modifier = Modifier.size(140.dp),
                )
            }
            CoUiMicTouchRipple(
                pressed = pressed && !isProcessing,
                color = CoUiTheme.colors.primaryText,
                modifier = Modifier.size(140.dp),
            )
            CoUiSurface(
                modifier = Modifier
                    .size(84.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = interaction,
                        indication = null,
                        enabled = !isProcessing,
                        onClick = onClick,
                    )
                    .couiTouchMotion(enabled = !isProcessing, maxStretch = 16.dp)
                    .couiContourGlow(
                        cornerRadius = 72.dp,
                        color = CoUiTheme.colors.primaryText,
                        enabled = !isProcessing,
                        intensity = 1.1f,
                    ),
                shape = CircleShape,
                color = when {
                    isRecording -> CoUiTheme.colors.error
                    isProcessing -> CoUiTheme.colors.fillSolid
                    else -> CoUiTheme.colors.primary
                },
                border = BorderStroke(2.dp, CoUiTheme.colors.divider),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (isProcessing) {
                        CoUiCircularProgress(
                            modifier = Modifier.size(28.dp),
                            color = CoUiTheme.colors.onPrimary,
                        )
                    } else {
                        CoUiMicGlyph(
                            isRecording = isRecording,
                            tint = CoUiTheme.colors.onPrimary,
                        )
                    }
                }
            }
        }
        if (isRecording) {
            CoUiBadge(text = stringResource(R.string.ui_listening), tone = CoUiTone.DANGER)
        } else {
            CoUiBadge(
                text = stringResource(R.string.ui_tap_to_speak, languageLabel),
                tone = CoUiTone.PRIMARY,
            )
        }
    }
}

/** COUI emotion badge backed by the Emotion2Vec result. */
@Composable
fun CoUiEmotionBadge(emotion: EmotionResult?, modifier: Modifier = Modifier) {
    if (emotion == null) {
        CoUiBadge(
            text = stringResource(R.string.ui_emotion_unavailable),
            tone = CoUiTone.NEUTRAL,
            modifier = modifier,
        )
        return
    }
    val tone = when (emotion.emotion.lowercase()) {
        "happy" -> CoUiTone.SUCCESS
        "sad" -> CoUiTone.INFO
        "angry" -> CoUiTone.DANGER
        "fearful", "disgusted" -> CoUiTone.WARNING
        "surprised" -> CoUiTone.INFO
        else -> CoUiTone.NEUTRAL
    }
    CoUiBadge(
        text = "${emotion.emotion.uppercase()} ${(emotion.confidence * 100).toInt()}%",
        tone = tone,
        modifier = modifier,
    )
}

/** Text panel that honours RTL languages using COUI surface tones. */
@Composable
fun CoUiLocalizedTextPanel(
    text: String,
    rtl: Boolean,
    modifier: Modifier = Modifier,
    tone: CoUiTone = CoUiTone.NEUTRAL,
) {
    val direction = if (rtl) androidx.compose.ui.unit.LayoutDirection.Rtl else androidx.compose.ui.unit.LayoutDirection.Ltr
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalLayoutDirection provides direction,
    ) {
        CoUiSurface(
            modifier = modifier.fillMaxWidth(),
            shape = CoUiShapes.section,
            color = tone.container(),
            border = BorderStroke(0.33.dp, tone.accent().copy(alpha = 0.2f)),
            contentAlignment = Alignment.TopStart,
        ) {
            CoUiText(
                text = text,
                style = CoUiTheme.textStyles.bodyXL,
                color = CoUiTheme.colors.labelPrimary,
                modifier = Modifier.padding(14.dp),
            )
        }
    }
}

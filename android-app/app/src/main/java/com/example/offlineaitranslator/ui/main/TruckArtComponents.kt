package com.example.offlineaitranslator.ui.main

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Hairline divider.
 */
@Composable
fun TruckArtKanguraTrim(
    modifier: Modifier = Modifier,
    heightDp: Int = 1,
    primaryColor: Color = Color.Transparent,
    secondaryColor: Color = Color.Transparent,
    accentColor: Color = Color.Transparent
) {
    HorizontalDivider(
        modifier = modifier.fillMaxWidth(),
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
    )
}

/**
 * Clean modern microphone recording button.
 */
@Composable
fun TactileVintageMicButton(
    isRecording: Boolean,
    isProcessing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    languageLabel: String = "English"
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val infiniteTransition = rememberInfiniteTransition(label = "mic_waves")
    val waveScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_scale"
    )
    val waveAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_alpha"
    )

    val scale = if (isPressed) 0.94f else 1.0f

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(100.dp)
                .scale(scale)
        ) {
            if (isRecording) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .scale(waveScale)
                        .background(Color(0xFFEF4444).copy(alpha = waveAlpha), CircleShape)
                )
            }

            val buttonBg = when {
                isRecording -> Color(0xFFDC2626)
                isProcessing -> MaterialTheme.colorScheme.surfaceVariant
                else -> MaterialTheme.colorScheme.primary
            }

            Surface(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        enabled = !isProcessing,
                        onClick = onClick
                    ),
                shape = CircleShape,
                color = buttonBg,
                shadowElevation = if (isRecording) 4.dp else 2.dp,
                border = BorderStroke(
                    width = 2.dp,
                    color = if (isRecording) Color(0xFFF87171) else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                )
            ) {
                Box(contentAlignment = Alignment.Center) {
                    when {
                        isProcessing -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                strokeWidth = 2.5.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        isRecording -> {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color.White)
                            )
                        }
                        else -> {
                            Text(
                                text = "🎙️",
                                fontSize = 28.sp
                            )
                        }
                    }
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = if (isRecording) Color(0xFFFEE2E2) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
            border = BorderStroke(
                1.dp,
                if (isRecording) Color(0xFFFCA5A5) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
            )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isRecording) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(Color(0xFFDC2626), CircleShape)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Listening... Tap to stop",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFB91C1C)
                    )
                } else {
                    Text(
                        text = "Tap to speak ($languageLabel)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Standard Status Chip.
 */
@Composable
fun RetroSignBadge(
    text: String,
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    textColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    borderColor: Color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            maxLines = 1,
            softWrap = false
        )
    }
}

/**
 * Version Badge: "v1.6"
 */
@Composable
fun LicensePlateVersionBadge(
    versionText: String = "v1.6",
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(Color(0xFF10B981), CircleShape)
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = versionText,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

/**
 * Clean status chip.
 */
@Composable
fun EnamelOfflineSeal(
    modifier: Modifier = Modifier,
    text: String = "Offline"
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF10B981).copy(alpha = 0.12f),
        border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .background(Color(0xFF10B981), CircleShape)
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = text,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF059669),
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

/**
 * Standard Card container.
 */
@Composable
fun TruckArtCard(
    modifier: Modifier = Modifier,
    borderColor: Color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
    kanguraHeight: Int = 0,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, borderColor)
    ) {
        content()
    }
}

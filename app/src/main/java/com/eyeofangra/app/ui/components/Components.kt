package com.eyeofangra.app.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eyeofangra.app.ui.theme.Angra
import kotlinx.coroutines.delay
import java.util.Locale

/// Familiar camera affordance: ring plus centre. The centre morphs from circle to
/// rounded square when active, so "recording" is carried by shape as well as colour.
@Composable
fun CaptureButton(
    recording: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val inner by animateDpAsState(
        targetValue = if (recording) 30.dp else 62.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "captureInner",
    )
    val corner by animateDpAsState(
        targetValue = if (recording) 8.dp else 31.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "captureCorner",
    )
    val ring = if (enabled) Angra.TextPrimary else Angra.TextDisabled
    val centre = when {
        !enabled -> Angra.TextDisabled
        else -> Angra.Recording
    }
    // Platform haptics already honour the system's own vibration setting, so no
    // in-app toggle is needed to turn this off.
    val haptics = LocalHapticFeedback.current
    Box(
        modifier
            .size(84.dp)
            .border(3.dp, ring, CircleShape)
            .clickable(enabled = enabled) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
            .semantics {
                contentDescription = if (recording) "Stop recording" else "Start recording"
                stateDescription = if (recording) "Recording" else "Idle"
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(inner)
                .clip(RoundedCornerShape(corner))
                .background(centre),
        )
    }
}

/// Ticks elapsed time off the service's start timestamp, so it stays correct across
/// rotation and process recreation.
///
/// It lives here rather than in the root composable on purpose: a clock high in the
/// tree recomposes every screen and the whole navigation bar twice a second. Only the
/// screen that displays the time should pay for it.
@Composable
fun rememberElapsed(startedAt: Long?): String {
    var text by remember { mutableStateOf("00:00") }
    LaunchedEffect(startedAt) {
        if (startedAt == null) {
            text = "00:00"
        } else {
            while (true) {
                val seconds = ((System.currentTimeMillis() - startedAt) / 1000).coerceAtLeast(0)
                text = String.format(Locale.US, "%02d:%02d", seconds / 60, seconds % 60)
                delay(500)
            }
        }
    }
    return text
}

/// Wide labelled action, used where there is no viewfinder to anchor a camera-style
/// control. The word carries the meaning; the shape change carries the state.
/// No glow — a halo on a dark ground costs contrast and reads as decoration.
@Composable
fun PillAction(
    label: String,
    active: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = when {
        !enabled -> Angra.SurfaceAlt
        active -> Angra.SurfaceAlt
        else -> Angra.Recording
    }
    val content = if (enabled) Angra.TextPrimary else Angra.TextDisabled
    val haptics = LocalHapticFeedback.current
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(container)
            .clickable(enabled = enabled) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
            .padding(horizontal = Angra.s5),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Square while active, circle while idle: state survives colour loss.
        Box(
            Modifier
                .size(if (active) 14.dp else 18.dp)
                .clip(if (active) RoundedCornerShape(3.dp) else CircleShape)
                .background(if (active) Angra.Recording else Angra.TextPrimary),
        )
        Text(
            label,
            Modifier.padding(start = Angra.s3),
            color = content,
            fontSize = Angra.titleSize,
            fontWeight = FontWeight.Medium,
        )
    }
}

/// Active-recording notice. States the mode in words and marks it with a shape,
/// never relying on the red alone.
@Composable
fun RecordingBanner(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Angra.radiusSm))
            .background(Angra.Recording)
            .padding(horizontal = Angra.s4, vertical = Angra.s3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Angra.s3),
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(Angra.TextPrimary))
        Text(text, color = Angra.TextPrimary, fontSize = Angra.bodySize, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier.padding(start = Angra.s4, top = Angra.s5, bottom = Angra.s2),
        color = Angra.Gold,
        fontSize = Angra.labelSize,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
fun SettingRow(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = Angra.s4, vertical = Angra.s1)
            .clip(RoundedCornerShape(Angra.radiusSm))
            .background(Angra.Surface)
            .heightIn(min = Angra.touchTarget)
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = Angra.s4, vertical = Angra.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Angra.TextPrimary, fontSize = Angra.bodySize)
            Text(summary, color = Angra.TextSecondary, fontSize = Angra.labelSize)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Angra.Background,
                checkedTrackColor = Angra.Gold,
                uncheckedTrackColor = Angra.SurfaceAlt,
            ),
        )
    }
}

/// Static information row for values the app reports but the user cannot toggle.
@Composable
fun InfoRow(title: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = Angra.s4, vertical = Angra.s1)
            .clip(RoundedCornerShape(Angra.radiusSm))
            .background(Angra.Surface)
            .heightIn(min = Angra.touchTarget)
            .padding(horizontal = Angra.s4, vertical = Angra.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, Modifier.weight(1f), color = Angra.TextPrimary, fontSize = Angra.bodySize)
        Text(value, color = Angra.TextSecondary, fontSize = Angra.bodySize)
    }
}

@Composable
fun BodyText(text: String, modifier: Modifier = Modifier, color: Color = Angra.TextSecondary) {
    Text(text, modifier.padding(horizontal = Angra.s4), color = color, fontSize = Angra.bodySize)
}

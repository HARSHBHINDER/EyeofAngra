package com.eyeofangra.app.feature

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.eyeofangra.app.RecorderService
import com.eyeofangra.app.RecordingStore
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eyeofangra.app.ui.components.PillAction
import com.eyeofangra.app.ui.components.RecordingBanner
import com.eyeofangra.app.ui.components.rememberElapsed
import com.eyeofangra.app.ui.theme.Angra
import com.eyeofangra.app.ui.theme.TimerTextStyle
import kotlinx.coroutines.delay

@Composable
fun AudioScreen(activeMode: String?) {
    val context = LocalContext.current
    val startedAt by RecorderService.startedAt.collectAsStateWithLifecycle()
    val elapsed = rememberElapsed(startedAt)
    val recording = activeMode == "audio"
    val blockedByVideo = activeMode == "video"
    val levels = remember { mutableStateListOf<Float>() }
    var free by remember { mutableStateOf(RecordingStore.formatBytes(RecordingStore.freeBytes(context))) }

    // Collect inside one coroutine. Keying the effect on amplitude instead would tear
    // down and restart it ten times a second, which is pure jank.
    LaunchedEffect(recording) {
        if (!recording) {
            levels.clear()
            return@LaunchedEffect
        }
        RecorderService.amplitude.collect { level ->
            levels.add(level)
            if (levels.size > 60) levels.removeAt(0)
        }
    }
    LaunchedEffect(recording) {
        while (recording) {
            free = RecordingStore.formatBytes(RecordingStore.freeBytes(context))
            delay(5_000)
        }
    }

    Column(
        Modifier.fillMaxSize().background(Angra.Background).padding(Angra.s4),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Angra.s4),
    ) {
        if (recording) {
            RecordingBanner("Recording audio · continues while locked")
        }

        Spacer(Modifier.height(Angra.s6))

        Text(elapsed, style = TimerTextStyle)
        Text(
            if (recording) "Mono · AAC 44.1 kHz" else "Ready · mono AAC",
            color = Angra.TextSecondary,
            fontSize = Angra.bodySize,
        )

        LevelMeter(levels, Modifier.fillMaxWidth().height(96.dp).padding(vertical = Angra.s4))

        Text("$free free on device", color = Angra.TextSecondary, fontSize = Angra.labelSize)

        Spacer(Modifier.height(Angra.s6))

        if (blockedByVideo) {
            Text(
                "Video recording is running.\nStop it before recording audio — both need the microphone.",
                color = Angra.TextSecondary,
                textAlign = TextAlign.Center,
            )
        }

        // Audio has no viewfinder, so a labelled action beats a bare camera circle.
        // Video and Photo keep the circular control, where the camera metaphor holds.
        PillAction(
            label = if (recording) "Stop" else "Start",
            active = recording,
            enabled = !blockedByVideo,
            onClick = {
                if (recording) {
                    context.stopService(Intent(context, RecorderService::class.java))
                } else {
                    ContextCompat.startForegroundService(
                        context,
                        Intent(context, RecorderService::class.java)
                            .putExtra(RecorderService.EXTRA_MODE, "audio"),
                    )
                }
            },
        )
    }
}

/// Bars are drawn from measured microphone amplitude. When idle the meter rests flat
/// rather than animating, so a still meter honestly means "not recording".
@Composable
private fun LevelMeter(levels: List<Float>, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val barCount = 60
        val slot = size.width / barCount
        val barWidth = slot * 0.55f
        val mid = size.height / 2f
        repeat(barCount) { i ->
            val level = levels.getOrNull(levels.size - barCount + i) ?: 0f
            val h = (level * size.height).coerceAtLeast(2f)
            drawRoundRect(
                color = if (level > 0.02f) Angra.Gold else Angra.SurfaceAlt,
                topLeft = Offset(i * slot + (slot - barWidth) / 2f, mid - h / 2f),
                size = Size(barWidth, h),
                cornerRadius = CornerRadius(barWidth / 2f),
            )
        }
    }
}

package com.eyeofangra.app.feature

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.core.content.ContextCompat
import com.eyeofangra.app.CameraEngine
import com.eyeofangra.app.CameraPreview
import com.eyeofangra.app.RecorderService
import com.eyeofangra.app.RecordingStore
import com.eyeofangra.app.ui.components.CaptureButton
import com.eyeofangra.app.ui.components.RecordingBanner
import com.eyeofangra.app.ui.theme.Angra
import kotlinx.coroutines.delay

@Composable
fun VideoScreen(activeMode: String?, elapsed: String) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val recording = activeMode == "video"
    val blockedByAudio = activeMode == "audio"
    var free by remember { mutableStateOf(RecordingStore.formatBytes(RecordingStore.freeBytes(context))) }

    // While idle the screen owns the viewfinder. Once recording starts the service
    // rebinds the same Preview to its own lifecycle, so the surface keeps working
    // and capture survives the screen locking.
    DisposableEffect(recording, blockedByAudio) {
        if (!recording && !blockedByAudio) {
            CameraEngine.bindPreview(context, owner, withPhoto = false)
        }
        onDispose { }
    }

    // Storage matters most while writing; refresh only while recording.
    LaunchedEffect(recording) {
        while (recording) {
            free = RecordingStore.formatBytes(RecordingStore.freeBytes(context))
            delay(5_000)
        }
    }

    Box(Modifier.fillMaxSize().background(Angra.Background)) {
        if (blockedByAudio) {
            Text(
                "Audio recording is running.\nStop it before recording video — both need the microphone.",
                Modifier.align(Alignment.Center).padding(Angra.s5),
                color = Angra.TextSecondary,
                textAlign = TextAlign.Center,
            )
        } else {
            CameraPreview(Modifier.fillMaxSize())
        }

        Column(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(Angra.s4),
            verticalArrangement = Arrangement.spacedBy(Angra.s3),
        ) {
            if (recording) {
                RecordingBanner("Recording video with sound · continues while locked")
                StatusStrip(elapsed = elapsed, free = free, mic = "Mic on")
            }
        }

        Column(
            Modifier.align(Alignment.BottomCenter).padding(bottom = Angra.s6),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Angra.s3),
        ) {
            if (!recording && !blockedByAudio) {
                Text("$free free", color = Angra.TextSecondary, fontSize = Angra.labelSize)
            }
            CaptureButton(
                recording = recording,
                enabled = !blockedByAudio,
                onClick = {
                    if (recording) {
                        context.stopService(Intent(context, RecorderService::class.java))
                    } else {
                        ContextCompat.startForegroundService(
                            context,
                            Intent(context, RecorderService::class.java)
                                .putExtra(RecorderService.EXTRA_MODE, "video"),
                        )
                    }
                },
            )
        }
    }
}

/// Live capture facts, kept legible over arbitrary preview content by its own surface.
@Composable
fun StatusStrip(elapsed: String, free: String, mic: String) {
    Row(
        Modifier
            .clip(RoundedCornerShape(Angra.radiusSm))
            .background(Angra.Surface.copy(alpha = 0.85f))
            .padding(horizontal = Angra.s4, vertical = Angra.s2),
        horizontalArrangement = Arrangement.spacedBy(Angra.s4),
    ) {
        Text(elapsed, color = Angra.TextPrimary, fontSize = Angra.bodySize)
        Text(mic, color = Angra.TextSecondary, fontSize = Angra.bodySize)
        Text("$free free", color = Angra.TextSecondary, fontSize = Angra.bodySize)
    }
}

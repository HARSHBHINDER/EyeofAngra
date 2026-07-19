package com.eyeofangra.app.feature

import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import com.eyeofangra.app.RecordingStore
import com.eyeofangra.app.ui.components.CaptureButton
import com.eyeofangra.app.ui.theme.Angra
import kotlinx.coroutines.delay

/// Capture status is reported only after the file is finalised — a "Saved" chip never
/// appears before the bytes are actually on disk.
private enum class SaveState { Idle, Saving, Saved, Failed }

@Composable
fun PhotoScreen(
    activeMode: String?,
    /// Hands the activity a shutter action while this screen is visible, and
    /// takes it back on dispose so volume keys behave normally elsewhere.
    onVolumeShutter: ((() -> Unit)?) -> Unit,
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val cameraBusy = activeMode == "video"
    var state by remember { mutableStateOf(SaveState.Idle) }

    fun capture() {
        if (cameraBusy) return
        state = SaveState.Saving
        val file = RecordingStore.newFile(context, "IMG", "jpg")
        CameraEngine.imageCapture.takePicture(
            ImageCapture.OutputFileOptions.Builder(file).build(),
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(results: ImageCapture.OutputFileResults) {
                    state = SaveState.Saved
                }
                override fun onError(exception: ImageCaptureException) {
                    state = SaveState.Failed
                }
            },
        )
    }

    // Only own the camera while this screen is showing and no recording holds it.
    DisposableEffect(cameraBusy) {
        if (!cameraBusy) CameraEngine.bindPreview(context, owner, withPhoto = true)
        onVolumeShutter(::capture)
        onDispose { onVolumeShutter(null) }
    }

    LaunchedEffect(state) {
        if (state == SaveState.Saved || state == SaveState.Failed) {
            delay(1_200)
            state = SaveState.Idle
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Angra.Background)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = !cameraBusy,
            ) { capture() },
    ) {
        if (cameraBusy) {
            Text(
                "Video recording is running.\nStop it to take photos — the camera is in use.",
                Modifier.align(Alignment.Center).padding(Angra.s5),
                color = Angra.TextSecondary,
                textAlign = TextAlign.Center,
            )
        } else {
            CameraPreview(Modifier.fillMaxSize())
        }

        AnimatedVisibility(
            visible = state != SaveState.Idle,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = Angra.s5),
        ) {
            val (label, colour) = when (state) {
                SaveState.Saving -> "Saving…" to Angra.SurfaceAlt
                SaveState.Saved -> "Photo saved" to Angra.Success
                SaveState.Failed -> "Capture failed" to Angra.Recording
                SaveState.Idle -> "" to Angra.SurfaceAlt
            }
            Text(
                label,
                Modifier
                    .clip(RoundedCornerShape(Angra.radiusSm))
                    .background(colour)
                    .padding(horizontal = Angra.s4, vertical = Angra.s2),
                color = Angra.TextPrimary,
                fontSize = Angra.bodySize,
            )
        }

        Column(
            Modifier.align(Alignment.BottomCenter).padding(bottom = Angra.s6),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Angra.s3),
        ) {
            Text(
                "Tap anywhere or press a volume key",
                color = Angra.TextSecondary,
                fontSize = Angra.labelSize,
            )
            CaptureButton(recording = false, enabled = !cameraBusy, onClick = ::capture)
        }
    }
}

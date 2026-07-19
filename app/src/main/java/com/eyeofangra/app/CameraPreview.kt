package com.eyeofangra.app

import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/// Viewfinder surface. It only attaches a surface to the shared `Preview` use case —
/// binding is owned by CameraEngine, so this stays correct whether the camera is
/// currently bound to the activity or to the recording service.
@Composable
fun CameraPreview(modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            PreviewView(context).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                // COMPATIBLE renders through a TextureView. The default SurfaceView is
                // faster but loses its surface when the screen locks, which showed up
                // on device as frozen frames after unlocking.
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                CameraEngine.preview.setSurfaceProvider(surfaceProvider)
            }
        },
        // Re-attach after the view is recreated; setting the same provider is a no-op.
        update = { CameraEngine.preview.setSurfaceProvider(it.surfaceProvider) },
    )
}

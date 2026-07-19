package com.eyeofangra.app

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner

/// Owns every CameraX binding so use cases are never bound twice or orphaned.
///
/// The binding lifecycle matters: while recording, the camera must be bound to the
/// *service* lifecycle, or locking the screen stops the activity and takes the
/// recording with it. The `Preview` instance is shared, so a screen's surface stays
/// attached across a rebind and the viewfinder survives the handover.
object CameraEngine {

    val preview: Preview by lazy { Preview.Builder().build() }
    val imageCapture: ImageCapture by lazy { ImageCapture.Builder().build() }

    private var provider: ProcessCameraProvider? = null
    private var videoCapture: VideoCapture<Recorder>? = null

    private fun withProvider(context: Context, block: (ProcessCameraProvider) -> Unit) {
        provider?.let { block(it); return }
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            val p = runCatching { future.get() }.getOrNull() ?: return@addListener
            provider = p
            block(p)
        }, ContextCompat.getMainExecutor(context))
    }

    /// Viewfinder only — used while idle, by both the Video and Photo screens.
    fun bindPreview(context: Context, owner: LifecycleOwner, withPhoto: Boolean) {
        withProvider(context) { p ->
            runCatching {
                p.unbindAll()
                if (withPhoto) {
                    p.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
                } else {
                    p.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview)
                }
            }
        }
    }

    /// Viewfinder plus recording, bound to the caller's lifecycle. The service is the
    /// only caller: its lifecycle is what keeps capture alive behind a locked screen.
    fun bindForRecording(
        context: Context,
        owner: LifecycleOwner,
        onReady: (VideoCapture<Recorder>) -> Unit,
    ) {
        withProvider(context) { p ->
            val recorder = Recorder.Builder()
                .setQualitySelector(
                    QualitySelector.from(
                        Quality.HIGHEST,
                        FallbackStrategy.lowerQualityOrHigherThan(Quality.SD),
                    ),
                )
                .build()
            val capture = VideoCapture.withOutput(recorder)
            val bound = runCatching {
                p.unbindAll()
                p.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
            }.isSuccess
            if (bound) {
                videoCapture = capture
                onReady(capture)
            }
        }
    }

    fun release() {
        runCatching { provider?.unbindAll() }
        videoCapture = null
    }
}

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

    /// Recording only — deliberately no Preview in this binding.
    ///
    /// Binding a viewfinder to the service means the camera keeps producing preview
    /// frames into a surface the system destroys at screen lock, then reconfigures the
    /// capture session when a new surface arrives at unlock. Reconfiguring a live
    /// session stutters the encoder and freezes frames. Evidence integrity outranks a
    /// viewfinder, so while recording there is no preview at all.
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
                // Drops the activity's Preview binding, so nothing renders while
                // recording and the session is never reconfigured mid-capture.
                p.unbindAll()
                p.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, capture)
            }.isSuccess
            if (bound) onReady(capture)
        }
    }

    fun release() {
        runCatching { provider?.unbindAll() }
    }
}

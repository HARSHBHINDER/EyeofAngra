package com.eyeofangra.app

import android.content.Context
import android.view.OrientationEventListener
import android.view.Surface
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/// Owns every CameraX binding so use cases are never bound twice or orphaned.
///
/// The binding lifecycle matters: while recording, the camera must be bound to the
/// *service* lifecycle, or locking the screen stops the activity and takes the
/// recording with it. The `Preview` instance is shared, so a screen's surface stays
/// attached across a rebind and the viewfinder survives the handover.
object CameraEngine {

    val preview: Preview by lazy { Preview.Builder().build() }
    val imageCapture: ImageCapture by lazy { ImageCapture.Builder().build() }

    // The screen is locked to portrait, so the display can no longer tell CameraX
    // how the phone is held. The sensor can: captures are tagged with the physical
    // rotation, so a sideways recording still plays back sideways-correct.
    private var rotation = Surface.ROTATION_0
    private var orientation: OrientationEventListener? = null

    private fun trackOrientation(context: Context) {
        if (orientation != null) return
        orientation = object : OrientationEventListener(context.applicationContext) {
            override fun onOrientationChanged(degrees: Int) {
                if (degrees == ORIENTATION_UNKNOWN) return
                rotation = when (degrees) {
                    in 45 until 135 -> Surface.ROTATION_270
                    in 135 until 225 -> Surface.ROTATION_180
                    in 225 until 315 -> Surface.ROTATION_90
                    else -> Surface.ROTATION_0
                }
                imageCapture.targetRotation = rotation
            }
        }.also { it.enable() }
    }

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
        trackOrientation(context)
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
            // Quality chosen in Settings; falls back lower on phones that cannot reach it.
            val quality = when (runBlocking { SettingsStore.flow(context).first().videoQuality }) {
                "4K" -> Quality.UHD
                "720p" -> Quality.HD
                else -> Quality.FHD
            }
            val recorder = Recorder.Builder()
                .setQualitySelector(
                    QualitySelector.from(quality, FallbackStrategy.lowerQualityOrHigherThan(quality)),
                )
                .build()
            trackOrientation(context)
            // Orientation is fixed at record start; an MP4 has one rotation for the whole clip.
            val capture = VideoCapture.Builder(recorder).setTargetRotation(rotation).build()
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

package com.eyeofangra.app

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaRecorder
import android.os.PowerManager
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import kotlinx.coroutines.flow.MutableStateFlow

/// Foreground service that records video+audio or audio-only. The foreground service
/// (with camera/microphone types) is what lets recording continue while the screen is
/// locked; its mandatory, non-dismissible notification is also the honest signal that
/// recording is active. Stops only when the user returns to the app and taps Stop.
class RecorderService : LifecycleService() {

    companion object {
        const val EXTRA_MODE = "mode" // "video" or "audio"
        /// Observed by the UI. null = idle.
        val activeMode = MutableStateFlow<String?>(null)
    }

    private var audioRecorder: MediaRecorder? = null
    private var videoRecording: Recording? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val mode = intent?.getStringExtra(EXTRA_MODE)
        if (mode == null || activeMode.value != null) return START_NOT_STICKY
        goForeground(mode)
        wakeLock = (getSystemService(POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "eyeofangra:recording")
            .apply { acquire() }
        if (mode == "video") startVideo() else startAudio()
        activeMode.value = mode
        return START_NOT_STICKY
    }

    private fun goForeground(mode: String) {
        val channelId = "recording"
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(channelId, "Recording", NotificationManager.IMPORTANCE_LOW)
        )
        val tapIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle(if (mode == "video") "Recording video + audio" else "Recording audio")
            .setContentText("Evidence stays on this device. Open EyeofAngra to stop.")
            .setOngoing(true)
            .setContentIntent(tapIntent)
            .build()
        val type = if (mode == "video")
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        else
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        ServiceCompat.startForeground(this, 1, notification, type)
    }

    @SuppressLint("MissingPermission") // UI blocks start until permissions granted
    private fun startVideo() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = future.get()
            val recorder = Recorder.Builder()
                .setQualitySelector(
                    QualitySelector.from(Quality.HIGHEST, FallbackStrategy.lowerQualityOrHigherThan(Quality.SD))
                )
                .build()
            val videoCapture = VideoCapture.withOutput(recorder)
            provider.unbindAll()
            provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, videoCapture)
            val file = RecordingStore.newFile(this, "VID", "mp4")
            videoRecording = recorder
                .prepareRecording(this, FileOutputOptions.Builder(file).build())
                .withAudioEnabled()
                .start(ContextCompat.getMainExecutor(this)) { }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun startAudio() {
        val file = RecordingStore.newFile(this, "AUD", "m4a")
        @Suppress("DEPRECATION") // context ctor needs API 31; minSdk is 26
        audioRecorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }
    }

    override fun onDestroy() {
        // Finalize files before dying — a stopped recording is evidence, a corrupt one isn't.
        videoRecording?.stop()
        videoRecording = null
        audioRecorder?.run {
            try { stop() } catch (_: Exception) { /* stop() throws if nothing was captured */ }
            release()
        }
        audioRecorder = null
        wakeLock?.release()
        activeMode.value = null
        super.onDestroy()
    }
}

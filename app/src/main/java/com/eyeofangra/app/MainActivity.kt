package com.eyeofangra.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eyeofangra.app.feature.AudioScreen
import com.eyeofangra.app.feature.OnboardingScreen
import com.eyeofangra.app.feature.PermissionGate
import com.eyeofangra.app.feature.PhotoScreen
import com.eyeofangra.app.feature.SettingsScreen
import com.eyeofangra.app.feature.VaultScreen
import com.eyeofangra.app.feature.VideoScreen
import com.eyeofangra.app.ui.Destination
import com.eyeofangra.app.ui.Shell
import com.eyeofangra.app.ui.theme.EyeofAngraTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : ComponentActivity() {

    /// Set by the Photo screen while it is visible. Volume keys are a shutter only
    /// there — everywhere else they stay volume keys, as users expect.
    private var volumeShutter: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Permissions are requested by PermissionGate, at the feature that needs them.
        setContent { App(onVolumeShutter = { volumeShutter = it }) }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            volumeShutter?.let { it(); return true }
        }
        return super.onKeyDown(keyCode, event)
    }

    fun setKeepScreenOn(enabled: Boolean) {
        if (enabled) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}

@Composable
private fun App(onVolumeShutter: ((() -> Unit)?) -> Unit) {
    val context = LocalContext.current
    val activity = context as MainActivity
    val scope = rememberCoroutineScope()

    val settings by SettingsStore.flow(context)
        .collectAsStateWithLifecycle(initialValue = Settings())
    val activeMode by RecorderService.activeMode.collectAsStateWithLifecycle()
    val startedAt by RecorderService.startedAt.collectAsStateWithLifecycle()
    val amplitude by RecorderService.amplitude.collectAsStateWithLifecycle()

    // Elapsed time ticks off the service's start timestamp, so it stays correct
    // across screen rotation and process recreation.
    var elapsed by remember { mutableStateOf("00:00") }
    LaunchedEffect(startedAt) {
        if (startedAt == null) {
            elapsed = "00:00"
        } else {
            while (true) {
                val seconds = ((System.currentTimeMillis() - startedAt!!) / 1000).coerceAtLeast(0)
                elapsed = String.format(
                    Locale.US, "%02d:%02d", seconds / 60, seconds % 60,
                )
                delay(500)
            }
        }
    }

    LaunchedEffect(settings.keepScreenOn) { activity.setKeepScreenOn(settings.keepScreenOn) }

    // Notification permission gates the ongoing recording notice on Android 13+.
    val notify = if (Build.VERSION.SDK_INT >= 33) listOf(Manifest.permission.POST_NOTIFICATIONS) else emptyList()
    val camera = Manifest.permission.CAMERA
    val mic = Manifest.permission.RECORD_AUDIO

    EyeofAngraTheme(pureBlack = settings.pureBlack) {
        Surface(Modifier.fillMaxSize()) {
            if (!settings.onboardingComplete) {
                OnboardingScreen(
                    onFinish = { scope.launch { SettingsStore.setOnboardingComplete(context, true) } },
                )
                return@Surface
            }
            Shell { destination ->
                when (destination) {
                    Destination.Video -> PermissionGate(
                        permissions = listOf(camera, mic) + notify,
                        title = "Camera and microphone",
                        rationale = "Video evidence needs the camera and the microphone. " +
                            "Recording starts only when you press the capture control, and " +
                            "Android shows an indicator the whole time it runs.",
                    ) { VideoScreen(activeMode, elapsed) }

                    Destination.Audio -> PermissionGate(
                        permissions = listOf(mic) + notify,
                        title = "Microphone",
                        rationale = "Audio evidence needs the microphone. Recording starts only " +
                            "when you press Start, and Android shows a microphone indicator " +
                            "for as long as it runs.",
                    ) { AudioScreen(activeMode, elapsed, amplitude) }

                    Destination.Photo -> PermissionGate(
                        permissions = listOf(camera),
                        title = "Camera",
                        rationale = "Evidence photos need the camera. Nothing is captured until " +
                            "you tap the screen or press a volume key.",
                    ) {
                        PhotoScreen(
                            activeMode = activeMode,
                            onVolumeShutter = { action ->
                                onVolumeShutter(if (settings.volumeKeyShutter) action else null)
                            },
                        )
                    }
                    // activeMode drives the refresh: the list reloads the moment a
                    // recording finishes, without polling the filesystem.
                    Destination.Vault -> VaultScreen(refreshKey = activeMode)
                    Destination.Settings -> SettingsScreen(
                        settings = settings,
                        onPureBlack = { scope.launch { SettingsStore.setPureBlack(context, it) } },
                        onHaptics = { scope.launch { SettingsStore.setHaptics(context, it) } },
                        onKeepScreenOn = { scope.launch { SettingsStore.setKeepScreenOn(context, it) } },
                        onVolumeShutter = { scope.launch { SettingsStore.setVolumeShutter(context, it) } },
                    )
                }
            }
        }
    }
}

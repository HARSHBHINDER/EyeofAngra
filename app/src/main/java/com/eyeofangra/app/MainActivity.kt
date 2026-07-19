package com.eyeofangra.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {

    /// Set by PhotoScreen while it is visible; volume keys trigger it.
    var volumeCapture: (() -> Unit)? = null

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val permissions = mutableListOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) permissions += Manifest.permission.POST_NOTIFICATIONS
        permissionLauncher.launch(permissions.toTypedArray())
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(Modifier.fillMaxSize()) { App(this) }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            volumeCapture?.let { it(); return true }
        }
        return super.onKeyDown(keyCode, event)
    }
}

@Composable
fun App(activity: MainActivity) {
    var tab by remember { mutableIntStateOf(0) }
    Scaffold(bottomBar = {
        NavigationBar {
            NavigationBarItem(tab == 0, { tab = 0 }, icon = {}, label = { Text("Video") })
            NavigationBarItem(tab == 1, { tab = 1 }, icon = {}, label = { Text("Audio") })
            NavigationBarItem(tab == 2, { tab = 2 }, icon = {}, label = { Text("Photo") })
            NavigationBarItem(tab == 3, { tab = 3 }, icon = {}, label = { Text("Info") })
        }
    }) { padding ->
        Box(Modifier.padding(padding)) {
            when (tab) {
                0 -> RecordScreen(mode = "video", prefix = "VID",
                    banner = "● Recording video + audio — keeps running with the screen locked. Unlock and press Stop to end.")
                1 -> RecordScreen(mode = "audio", prefix = "AUD",
                    banner = "● Recording audio — keeps running with the screen locked. Unlock and press Stop to end.")
                2 -> PhotoScreen(activity)
                else -> SafetyScreen()
            }
        }
    }
}

/// One screen serves both Video and Audio tabs — same service, different mode.
@Composable
fun RecordScreen(mode: String, prefix: String, banner: String) {
    val context = LocalContext.current
    val active by RecorderService.activeMode.collectAsState()
    var files by remember { mutableStateOf(RecordingStore.list(context, prefix)) }
    LaunchedEffect(active) { if (active == null) files = RecordingStore.list(context, prefix) }

    val recordingThis = active == mode
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (recordingThis) {
            Text(banner, color = Color.White,
                modifier = Modifier.fillMaxWidth().background(Color(0xFFB3261E)).padding(12.dp))
        }
        Button(
            onClick = {
                if (recordingThis) {
                    context.stopService(Intent(context, RecorderService::class.java))
                } else {
                    ContextCompat.startForegroundService(
                        context,
                        Intent(context, RecorderService::class.java).putExtra(RecorderService.EXTRA_MODE, mode)
                    )
                }
            },
            enabled = active == null || recordingThis,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (recordingThis) Color.Gray else Color(0xFFB3261E)
            ),
            modifier = Modifier.fillMaxWidth().height(72.dp)
        ) {
            Text(if (recordingThis) "Stop" else if (mode == "video") "Record" else "Start",
                style = MaterialTheme.typography.headlineMedium)
        }
        if (active != null && !recordingThis) {
            Text("Another recording is running — stop it first.", color = Color.Gray)
        }
        FileList(files) { files = RecordingStore.list(context, prefix) }
    }
}

@Composable
fun FileList(files: List<File>, refresh: () -> Unit) {
    val context = LocalContext.current
    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items(files.take(20)) { file ->
            Row(
                Modifier.fillMaxWidth().clickable { RecordingStore.open(context, file) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(file.name, Modifier.weight(1f))
                TextButton(onClick = { file.delete(); refresh() }) { Text("Delete") }
            }
        }
    }
}

@Composable
fun PhotoScreen(activity: MainActivity) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val active by RecorderService.activeMode.collectAsState()
    var confirmation by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val imageCapture = remember { ImageCapture.Builder().build() }

    fun capture() {
        val file = RecordingStore.newFile(context, "IMG", "jpg")
        imageCapture.takePicture(
            ImageCapture.OutputFileOptions.Builder(file).build(),
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(results: ImageCapture.OutputFileResults) {
                    confirmation = true
                    scope.launch { delay(900); confirmation = false }
                }
                override fun onError(exception: ImageCaptureException) {}
            }
        )
    }

    // The video service owns the camera while it records; don't fight it for the lens.
    if (active == "video") {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            Text("Video is recording — stop it before taking photos.",
                color = Color.Gray, textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(24.dp))
        }
        return
    }

    DisposableEffect(Unit) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            val provider = future.get()
            provider.unbindAll()
            provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, imageCapture)
        }, ContextCompat.getMainExecutor(context))
        activity.volumeCapture = ::capture
        onDispose {
            activity.volumeCapture = null
            ProcessCameraProvider.getInstance(context).get().unbindAll()
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black).clickable { capture() }) {
        Text("Tap anywhere or press a volume key\nto take an evidence photo",
            color = Color(0xFF555555), textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.Center))
        if (confirmation) {
            Text("Photo captured", color = Color.Black,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 24.dp)
                    .background(Color.White).padding(horizontal = 16.dp, vertical = 8.dp))
        }
    }
}

@Composable
fun SafetyScreen() {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Safety & Legal", style = MaterialTheme.typography.headlineSmall)
        Text("EyeofAngra is for emergency evidence: protecting yourself and preserving proof if you are attacked, harassed, or falsely accused.")
        Text("What it records: video with sound, audio only, and still photos — only when you explicitly start a recording or tap/press to capture.")
        Text("While recording, Android shows a permanent notification plus its own camera/microphone indicators. Recording continues with the screen locked until you unlock and press Stop.")
        Text("Everything stays on this device, in this app's private folder. Nothing is uploaded, synced, or shared automatically.")
        Text("Laws on recording conversations and filming people differ by country and state. You are responsible for complying with the laws that apply to you. This app is not a surveillance tool.")
    }
}

package com.eyeofangra.app.feature

import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eyeofangra.app.MediaLibrary
import com.eyeofangra.app.RecordingStore
import com.eyeofangra.app.ScreenLock
import com.eyeofangra.app.Settings
import com.eyeofangra.app.SettingsStore
import com.eyeofangra.app.StorageLocation
import com.eyeofangra.app.ui.components.BodyText
import com.eyeofangra.app.ui.components.InfoRow
import com.eyeofangra.app.ui.components.SectionHeader
import com.eyeofangra.app.ui.components.SettingRow
import com.eyeofangra.app.ui.theme.Angra
import com.eyeofangra.app.ui.theme.WordmarkTextStyle
import kotlinx.coroutines.launch

/// Labels are also the stored values; CameraEngine maps them to CameraX qualities.
private val qualities = listOf(
    "4K" to "Sharpest, largest files (on phones that support it)",
    "1080p" to "Full HD — the balanced default",
    "720p" to "Smaller files, longer recordings",
)

@Composable
fun SettingsScreen(
    settings: Settings,
    onPureBlack: (Boolean) -> Unit,
    onKeepScreenOn: (Boolean) -> Unit,
    onVolumeShutter: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showLegal by remember { mutableStateOf(false) }
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull() ?: "—"
    }
    val pickFolder = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            scope.launch {
                SettingsStore.setStorageUri(context, uri.toString())
                SettingsStore.setCustomStorage(context, true)
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Angra.Background)
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            "Settings",
            Modifier.padding(start = Angra.s4, top = Angra.s5),
            color = Angra.TextPrimary,
            fontSize = 30.sp,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.SemiBold,
        )

        SectionHeader("Capture")
        QualityCard(settings.videoQuality) { q -> scope.launch { SettingsStore.setVideoQuality(context, q) } }
        SettingRow(
            "Volume key shutter",
            "Press either volume key to take a photo on the Photo screen",
            settings.volumeKeyShutter, onVolumeShutter,
        )

        SectionHeader("Recording")
        SettingRow(
            "Lock screen when recording starts",
            "Turns the display off the instant a capture begins; recording keeps going",
            ScreenLock.isEnabled(context),
            onCheckedChange = { enable ->
                if (enable) (context as? android.app.Activity)?.let { ScreenLock.requestAdmin(it) }
                else ScreenLock.disable(context)
            },
        )
        SettingRow(
            "Keep screen awake",
            "Stop the display sleeping while a capture screen is open",
            settings.keepScreenOn, onKeepScreenOn,
        )

        SectionHeader("Storage")
        SettingRow(
            "Save to a chosen folder",
            "Off: kept on board, in this app's private storage. On: saved to a folder you pick.",
            settings.customStorage,
            onCheckedChange = { on ->
                scope.launch { SettingsStore.setCustomStorage(context, on) }
                // Turning it on with no folder yet: ask for one immediately.
                if (on && settings.storageUri == null) pickFolder.launch(null)
            },
        )
        if (settings.customStorage) {
            LinkRow("Folder — ${StorageLocation.label(context)}") { pickFolder.launch(null) }
        }
        InfoRow("Captured", RecordingStore.formatBytes(MediaLibrary.usedBytes(context)))
        InfoRow("Free space", RecordingStore.formatBytes(RecordingStore.freeBytes(context)))
        BodyText(
            "Nothing is uploaded or shared. A chosen folder in shared storage may appear in " +
                "your gallery; on-board storage never does.",
            Modifier.padding(vertical = Angra.s2),
        )

        SectionHeader("Appearance")
        SettingRow(
            "Pure black theme",
            "Use true black surfaces, which saves power on OLED screens",
            settings.pureBlack, onPureBlack,
        )

        SectionHeader("About")
        LinkRow("Safety & Legal", expanded = showLegal) { showLegal = !showLegal }
        AnimatedVisibility(showLegal) {
            Column {
                LegalBlock("Intended use", "Personal safety, emergency evidence, and lawful documentation.")
                LegalBlock("What is recorded", "Video with sound, audio alone, and photographs — only when you start a capture yourself.")
                LegalBlock(
                    "Recording indicators",
                    "An ongoing notification and Android's own camera and microphone indicators are shown " +
                        "throughout. This app is not a covert recorder and must not be used as one.",
                )
                LegalBlock("Local storage", "Everything stays on this device unless you deliberately export or share it.")
                LegalBlock(
                    "Your responsibility",
                    "Recording and consent laws differ by country and state. You are responsible for using this app lawfully.",
                )
                LegalBlock(
                    "Limitations",
                    "EyeofAngra cannot guarantee your safety, the recovery of an interrupted file, " +
                        "or that a recording will be accepted as evidence anywhere.",
                )
            }
        }
        LinkRow("App permissions") {
            context.startActivity(
                Intent(
                    AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null),
                ),
            )
        }
        InfoRow("Version", version)
        InfoRow("Licence", "MIT — open source")
        BodyText(
            "If locked-screen recording stops early, set this app's battery usage to Unrestricted.",
            Modifier.padding(vertical = Angra.s2),
        )

        // Signature, not chrome: the wordmark closes the list like a maker's mark.
        Column(
            Modifier.fillMaxWidth().padding(top = Angra.s6, bottom = Angra.s7),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row {
                Text("Eyeof", style = WordmarkTextStyle.copy(fontSize = 24.sp, brush = SolidColor(Angra.TextPrimary)))
                Text("Angra", style = WordmarkTextStyle.copy(fontSize = 24.sp))
            }
            Text(
                "Evidence, the moment it matters",
                Modifier.padding(top = Angra.s1),
                color = Angra.TextDisabled,
                fontSize = 11.sp,
                letterSpacing = 0.6.sp,
            )
        }
    }
}

/// Segmented quality picker inside a card, matching the iOS segmented control.
@Composable
private fun QualityCard(selected: String, onSelect: (String) -> Unit) {
    val shape = RoundedCornerShape(Angra.radiusMd)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Angra.s4, vertical = Angra.s1)
            .clip(shape)
            .background(Angra.CardGradient)
            .border(Angra.hairline, Angra.EdgeLight, shape)
            .padding(horizontal = Angra.s4, vertical = Angra.s3),
    ) {
        Text("Video quality", color = Angra.TextPrimary, fontSize = Angra.bodySize)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = Angra.s3)
                .clip(RoundedCornerShape(10.dp))
                .background(Angra.SurfaceAlt)
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            qualities.forEach { (label, _) ->
                val on = label == selected
                Text(
                    label,
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (on) Angra.GoldGradient else SolidColor(Color.Transparent))
                        .clickable { onSelect(label) }
                        .padding(vertical = 8.dp),
                    color = if (on) Angra.Background else Angra.TextSecondary,
                    fontSize = 14.sp,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Text(
            qualities.firstOrNull { it.first == selected }?.second.orEmpty(),
            Modifier.padding(top = Angra.s2),
            color = Angra.TextSecondary,
            fontSize = Angra.labelSize,
        )
    }
}

@Composable
private fun LinkRow(label: String, expanded: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(Angra.radiusMd)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Angra.s4, vertical = Angra.s1)
            .clip(shape)
            .background(Angra.CardGradient)
            .border(Angra.hairline, Angra.EdgeLight, shape)
            .clickable(onClick = onClick)
            .heightIn(min = Angra.touchTarget)
            .padding(horizontal = Angra.s4, vertical = Angra.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), color = Angra.TextPrimary, fontSize = Angra.bodySize)
        // Drawn chevron; points down while its section is open.
        Canvas(Modifier.size(Angra.s3).rotate(if (expanded) 90f else 0f)) {
            val path = Path().apply {
                moveTo(size.width * 0.25f, 0f)
                lineTo(size.width * 0.75f, size.height / 2f)
                lineTo(size.width * 0.25f, size.height)
            }
            drawPath(path, Angra.TextSecondary, style = Stroke(width = size.width * 0.18f))
        }
    }
}

@Composable
private fun LegalBlock(title: String, body: String) {
    Column(Modifier.padding(horizontal = Angra.s6, vertical = Angra.s2)) {
        Text(title, color = Angra.Gold, fontSize = Angra.bodySize)
        Text(body, color = Angra.TextSecondary, fontSize = Angra.labelSize)
    }
}

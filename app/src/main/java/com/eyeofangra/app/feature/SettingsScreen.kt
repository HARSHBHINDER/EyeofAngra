package com.eyeofangra.app.feature

import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.eyeofangra.app.RecordingStore
import com.eyeofangra.app.Settings
import com.eyeofangra.app.ui.components.BodyText
import com.eyeofangra.app.ui.components.InfoRow
import com.eyeofangra.app.ui.components.SectionHeader
import com.eyeofangra.app.ui.components.SettingRow
import com.eyeofangra.app.ui.theme.Angra

@Composable
fun SettingsScreen(
    settings: Settings,
    onPureBlack: (Boolean) -> Unit,
    onHaptics: (Boolean) -> Unit,
    onKeepScreenOn: (Boolean) -> Unit,
    onVolumeShutter: (Boolean) -> Unit,
) {
    val context = LocalContext.current

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
            fontSize = Angra.titleSize,
        )

        SectionHeader("Capture")
        SettingRow(
            "Volume key shutter",
            "Press either volume key to take a photo on the Photo screen",
            settings.volumeKeyShutter, onVolumeShutter,
        )
        SettingRow(
            "Capture haptics",
            "Vibrate when a capture starts or stops",
            settings.haptics, onHaptics,
        )

        SectionHeader("Recording behaviour")
        SettingRow(
            "Keep screen awake",
            "Stop the display sleeping while a capture screen is open",
            settings.keepScreenOn, onKeepScreenOn,
        )
        BodyText(
            "Recording continues when the screen locks. Android requires an ongoing " +
                "notification while it does, and shows its own camera and microphone " +
                "indicators. EyeofAngra does not hide either.",
            Modifier.padding(vertical = Angra.s2),
        )

        SectionHeader("Appearance")
        SettingRow(
            "Pure black theme",
            "Use true black surfaces, which saves power on OLED screens",
            settings.pureBlack, onPureBlack,
        )

        SectionHeader("Storage")
        InfoRow("Captured media", RecordingStore.formatBytes(RecordingStore.usedBytes(context)))
        InfoRow("Free space", RecordingStore.formatBytes(RecordingStore.freeBytes(context)))
        BodyText(
            "Recordings are kept in this app's private folder. They are not uploaded, " +
                "synchronised, or shared, and Android removes them if you uninstall the app.",
            Modifier.padding(vertical = Angra.s2),
        )

        SectionHeader("Permissions")
        BodyText(
            "EyeofAngra uses the camera, the microphone, and notifications. It does not " +
                "request internet access, location, contacts, or storage management.",
        )
        LinkRow("Open app permissions") {
            context.startActivity(
                Intent(
                    AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null),
                ),
            )
        }

        SectionHeader("Safety & legal")
        LegalBlock(
            "Intended use",
            "Personal safety, emergency evidence, and lawful documentation.",
        )
        LegalBlock(
            "What is recorded",
            "Video with sound, audio alone, and photographs — only when you start a capture yourself.",
        )
        LegalBlock(
            "Recording indicators",
            "An ongoing notification and Android's own camera and microphone indicators are shown " +
                "throughout. This app is not a covert recorder and must not be used as one.",
        )
        LegalBlock(
            "Local storage",
            "Everything stays on this device unless you deliberately export or share it.",
        )
        LegalBlock(
            "Your responsibility",
            "Recording and consent laws differ by country and state. You are responsible for " +
                "using this app lawfully.",
        )
        LegalBlock(
            "Limitations",
            "EyeofAngra cannot guarantee your safety, the recovery of an interrupted file, " +
                "or that a recording will be accepted as evidence anywhere.",
        )

        SectionHeader("About")
        InfoRow("Version", "1.0")
        InfoRow("Licence", "MIT — open source")
        BodyText(
            "Some manufacturers restrict background apps aggressively. If locked-screen " +
                "recording stops early, set this app's battery usage to Unrestricted.",
            Modifier.padding(vertical = Angra.s2),
        )
        Text("", Modifier.padding(bottom = Angra.s7))
    }
}

@Composable
private fun LinkRow(label: String, onClick: () -> Unit) {
    Text(
        label,
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = Angra.touchTarget)
            .padding(horizontal = Angra.s4, vertical = Angra.s3),
        color = Angra.Gold,
        fontSize = Angra.bodySize,
    )
}

@Composable
private fun LegalBlock(title: String, body: String) {
    Column(Modifier.padding(horizontal = Angra.s4, vertical = Angra.s2)) {
        Text(title, color = Angra.TextPrimary, fontSize = Angra.bodySize)
        Text(body, color = Angra.TextSecondary, fontSize = Angra.labelSize)
    }
}

package com.eyeofangra.app.feature

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.eyeofangra.app.R
import com.eyeofangra.app.ui.theme.Angra
import com.eyeofangra.app.ui.theme.WordmarkTextStyle

/// First-launch explanation. It sets expectations honestly before any permission
/// dialog appears: what gets recorded, what the device will show while it happens,
/// and where the files stay.
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    val lastStep = 2

    Column(
        Modifier
            .fillMaxSize()
            .background(Angra.Background)
            .verticalScroll(rememberScrollState())
            .padding(Angra.s5),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(Angra.s7))

        when (step) {
            0 -> {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(160.dp),
                )
                Text("EyeofAngra", style = WordmarkTextStyle)
                Spacer(Modifier.height(Angra.s2))
                Text(
                    "Secure evidence. Private by design.",
                    color = Angra.TextSecondary,
                    textAlign = TextAlign.Center,
                )
            }

            1 -> {
                StepTitle("You are always in control")
                Card(
                    "Nothing records by itself",
                    "Video, audio, and photos are captured only when you start them. " +
                        "There is no automatic, scheduled, or remote capture.",
                )
                Card(
                    "Recording is always visible",
                    "Android shows its own camera and microphone indicators. Continuing a " +
                        "recording outside the app requires an ongoing notification that " +
                        "cannot be dismissed. EyeofAngra does not hide either — it is not a " +
                        "covert recorder.",
                )
                Card(
                    "It keeps going when the screen locks",
                    "A recording you started continues while the phone is locked, so you do " +
                        "not have to hold the app open. It stops when you return and press Stop.",
                )
            }

            2 -> {
                StepTitle("Your files, your responsibility")
                Card(
                    "Everything stays on this device",
                    "Recordings are written to this app's private folder. Nothing is uploaded, " +
                        "synced, analysed, or shared. The app has no internet permission at all.",
                )
                Card(
                    "You decide what leaves",
                    "Files move off the device only when you deliberately export or share them.",
                )
                Card(
                    "Recording laws vary",
                    "Rules on recording conversations and filming people differ by country and " +
                        "state. You are responsible for using this app lawfully. It cannot " +
                        "guarantee your safety or that a recording will be accepted as evidence.",
                )
            }
        }

        Spacer(Modifier.height(Angra.s6))

        PrimaryAction(if (step == lastStep) "Open EyeofAngra" else "Continue") {
            if (step == lastStep) onFinish() else step++
        }

        if (step < lastStep) {
            Text(
                "Skip",
                Modifier
                    .padding(Angra.s4)
                    .clickable { step = lastStep },
                color = Angra.TextSecondary,
            )
        }

        Row(
            Modifier.padding(Angra.s4),
            horizontalArrangement = Arrangement.spacedBy(Angra.s2),
        ) {
            repeat(lastStep + 1) { i ->
                Spacer(
                    Modifier
                        .size(if (i == step) 9.dp else 7.dp)
                        .clip(CircleShape)
                        .background(if (i == step) Angra.Gold else Angra.SurfaceAlt),
                )
            }
        }
    }
}

@Composable
private fun StepTitle(text: String) {
    Text(
        text,
        Modifier.padding(bottom = Angra.s4),
        color = Angra.TextPrimary,
        fontSize = Angra.titleSize,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun Card(title: String, body: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = Angra.s2)
            .clip(RoundedCornerShape(Angra.radiusMd))
            .background(Angra.Surface)
            .padding(Angra.s4),
    ) {
        Text(title, color = Angra.Gold, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(Angra.s1))
        Text(body, color = Angra.TextSecondary, fontSize = Angra.bodySize)
    }
}

@Composable
fun PrimaryAction(label: String, onClick: () -> Unit) {
    Text(
        label,
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(percent = 50))
            .background(Angra.Recording)
            .clickable(onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(vertical = Angra.s4),
        color = Angra.TextPrimary,
        fontSize = Angra.titleSize,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center,
    )
}

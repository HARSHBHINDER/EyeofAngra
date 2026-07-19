package com.eyeofangra.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eyeofangra.app.ui.components.AudioIcon
import com.eyeofangra.app.ui.components.PhotoIcon
import com.eyeofangra.app.ui.components.SettingsIcon
import com.eyeofangra.app.ui.components.VaultIcon
import com.eyeofangra.app.ui.components.VideoIcon
import com.eyeofangra.app.ui.theme.Angra

enum class Destination(val label: String) {
    Video("Video"), Audio("Audio"), Photo("Photo"), Vault("Vault"), Settings("Settings")
}

/// Five sibling destinations with no back stack between them, so a saved index is the
/// whole navigation requirement. Navigation Compose arrives with the first pushed
/// screen (media detail), not before.
@Composable
fun Shell(content: @Composable (Destination) -> Unit) {
    var current by rememberSaveable { mutableStateOf(Destination.Video) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = Angra.Surface, tonalElevation = 0.dp) {
                Destination.entries.forEach { destination ->
                    val selected = current == destination
                    NavigationBarItem(
                        selected = selected,
                        onClick = { current = destination },
                        icon = {
                            val tint = if (selected) Angra.Gold else Angra.TextSecondary
                            when (destination) {
                                Destination.Video -> VideoIcon(selected, tint)
                                Destination.Audio -> AudioIcon(selected, tint)
                                Destination.Photo -> PhotoIcon(selected, tint)
                                Destination.Vault -> VaultIcon(selected, tint)
                                Destination.Settings -> SettingsIcon(selected, tint)
                            }
                        },
                        label = {
                            Text(
                                destination.label,
                                fontSize = Angra.labelSize,
                                // Selection is carried by weight and icon fill too, never colour alone.
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Angra.Gold,
                            selectedTextColor = Angra.Gold,
                            unselectedIconColor = Angra.TextSecondary,
                            unselectedTextColor = Angra.TextSecondary,
                            indicatorColor = Angra.SurfaceAlt,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) { content(current) }
    }
}

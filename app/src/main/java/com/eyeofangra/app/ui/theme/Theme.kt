package com.eyeofangra.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/// Material 3 gives accessibility and component behaviour; the palette is entirely
/// EyeofAngra's. `pureBlack` drives the OLED-friendly Appearance option.
@Composable
fun EyeofAngraTheme(pureBlack: Boolean = false, content: @Composable () -> Unit) {
    val background = if (pureBlack) androidx.compose.ui.graphics.Color.Black else Angra.Background
    val scheme = darkColorScheme(
        primary = Angra.Gold,
        onPrimary = Angra.Background,
        secondary = Angra.GoldMuted,
        error = Angra.Recording,
        onError = Angra.TextPrimary,
        background = background,
        onBackground = Angra.TextPrimary,
        surface = if (pureBlack) Angra.Background else Angra.Surface,
        onSurface = Angra.TextPrimary,
        surfaceVariant = Angra.SurfaceAlt,
        onSurfaceVariant = Angra.TextSecondary,
        outline = Angra.Divider,
    )

    MaterialTheme(colorScheme = scheme, content = content)
}

val TimerTextStyle = TextStyle(
    fontSize = Angra.timerSize,
    fontWeight = FontWeight.Light,
    color = Angra.TextPrimary,
)

/// Serif is reserved for the wordmark and onboarding title — never for buttons,
/// settings, timers, warnings, or legal text.
///
/// ponytail: platform serif rather than Playfair Display; bundling the real face
/// needs a font binary added to res/font. Swap the fontFamily here once it exists.
val WordmarkTextStyle = TextStyle(
    fontFamily = FontFamily.Serif,
    fontSize = 34.sp,
    fontWeight = FontWeight.Medium,
    color = Angra.Gold,
)

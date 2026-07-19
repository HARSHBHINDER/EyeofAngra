package com.eyeofangra.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/// Single source for every visual constant. Nothing hard-codes a colour or a
/// dimension anywhere else in the app.
object Angra {

    // Surfaces — near-black foundation, two elevation steps. Values taken from the
    // brand reference's design-system panel.
    val Background = Color(0xFF0A0A0C)
    val Surface = Color(0xFF17181C)
    val SurfaceAlt = Color(0xFF212228)
    val Divider = Color(0x1AF5F2EC)

    // Text — warm off-white, muted secondary. Secondary sits near 7:1 on Background.
    val TextPrimary = Color(0xFFF5F2EC)
    val TextSecondary = Color(0xFFA8A39A)
    val TextDisabled = Color(0xFF5C5952)

    // Accent — gold marks selection and brand, never destructive or active-recording state.
    val Gold = Color(0xFFD4AF37)
    val GoldMuted = Color(0xFF8D713B)
    val Ember = Color(0xFFF4511E)

    // Semantic — recording red is reserved for active capture and nothing else.
    val Recording = Color(0xFFC62828)
    val Success = Color(0xFF42B56A)
    val Warning = Color(0xFFE0A030)

    // Spacing scale.
    val s1 = 4.dp
    val s2 = 8.dp
    val s3 = 12.dp
    val s4 = 16.dp
    val s5 = 24.dp
    val s6 = 32.dp
    val s7 = 48.dp

    // Corner radii.
    val radiusSm = 12.dp
    val radiusMd = 20.dp
    val radiusLg = 28.dp

    // Minimum touch target, per platform guidance.
    val touchTarget = 48.dp
    val iconSize = 24.dp

    // Motion, milliseconds. Overridden to 0 when the system asks for reduced motion.
    const val MOTION_FAST = 180
    const val MOTION_STANDARD = 240
    const val MOTION_SLOW = 300

    // Type sizes. Timers use tabular figures so digits do not jitter while counting.
    val timerSize = 56.sp
    val titleSize = 22.sp
    val bodySize = 15.sp
    val labelSize = 12.sp
}

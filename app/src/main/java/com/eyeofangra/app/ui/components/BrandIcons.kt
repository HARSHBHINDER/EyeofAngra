package com.eyeofangra.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.eyeofangra.app.ui.theme.Angra

/// Icons drawn with Canvas primitives rather than pulling material-icons-extended
/// (~10 MB for five glyphs). Selection changes the icon's *fill*, not only its
/// colour, so state survives greyscale and colour-blind viewing.

private val DrawScope.stroke get() = Stroke(width = size.minDimension * 0.083f)

@Composable
fun VideoIcon(selected: Boolean, tint: Color, modifier: Modifier = Modifier) =
    Canvas(modifier.size(Angra.iconSize)) {
        val body = Size(size.width * 0.58f, size.height * 0.5f)
        val top = Offset(size.width * 0.08f, size.height * 0.25f)
        val lens = Path().apply {
            moveTo(size.width * 0.70f, size.height * 0.5f)
            lineTo(size.width * 0.94f, size.height * 0.29f)
            lineTo(size.width * 0.94f, size.height * 0.71f)
            close()
        }
        if (selected) {
            drawRoundRect(tint, top, body, CornerRadius(size.width * 0.12f))
            drawPath(lens, tint)
        } else {
            drawRoundRect(tint, top, body, CornerRadius(size.width * 0.12f), style = stroke)
            drawPath(lens, tint, style = stroke)
        }
    }

@Composable
fun AudioIcon(selected: Boolean, tint: Color, modifier: Modifier = Modifier) =
    Canvas(modifier.size(Angra.iconSize)) {
        val capsule = Size(size.width * 0.30f, size.height * 0.50f)
        val topLeft = Offset(size.width * 0.35f, size.height * 0.10f)
        if (selected) {
            drawRoundRect(tint, topLeft, capsule, CornerRadius(size.width * 0.15f))
        } else {
            drawRoundRect(tint, topLeft, capsule, CornerRadius(size.width * 0.15f), style = stroke)
        }
        // Cradle and stand read the same either way — they carry the "mic" meaning.
        drawArc(
            color = tint, startAngle = 0f, sweepAngle = 180f, useCenter = false,
            topLeft = Offset(size.width * 0.22f, size.height * 0.42f),
            size = Size(size.width * 0.56f, size.height * 0.40f), style = stroke,
        )
        drawLine(
            tint, Offset(size.width * 0.5f, size.height * 0.78f),
            Offset(size.width * 0.5f, size.height * 0.92f), strokeWidth = stroke.width,
        )
    }

@Composable
fun PhotoIcon(selected: Boolean, tint: Color, modifier: Modifier = Modifier) =
    Canvas(modifier.size(Angra.iconSize)) {
        val body = Size(size.width * 0.86f, size.height * 0.62f)
        val topLeft = Offset(size.width * 0.07f, size.height * 0.24f)
        drawRoundRect(tint, topLeft, body, CornerRadius(size.width * 0.14f), style = stroke)
        val r = size.minDimension * 0.17f
        val centre = Offset(size.width * 0.5f, size.height * 0.55f)
        if (selected) drawCircle(tint, r, centre) else drawCircle(tint, r, centre, style = stroke)
    }

@Composable
fun VaultIcon(selected: Boolean, tint: Color, modifier: Modifier = Modifier) =
    Canvas(modifier.size(Angra.iconSize)) {
        val body = Size(size.width * 0.66f, size.height * 0.46f)
        val topLeft = Offset(size.width * 0.17f, size.height * 0.44f)
        // Shackle stays open-stroked in both states; only the body fills.
        drawArc(
            color = tint, startAngle = 180f, sweepAngle = 180f, useCenter = false,
            topLeft = Offset(size.width * 0.29f, size.height * 0.16f),
            size = Size(size.width * 0.42f, size.height * 0.42f), style = stroke,
        )
        if (selected) {
            drawRoundRect(tint, topLeft, body, CornerRadius(size.width * 0.10f))
        } else {
            drawRoundRect(tint, topLeft, body, CornerRadius(size.width * 0.10f), style = stroke)
        }
    }

@Composable
fun SettingsIcon(selected: Boolean, tint: Color, modifier: Modifier = Modifier) =
    Canvas(modifier.size(Angra.iconSize)) {
        // Three sliders: legible at 24dp where a gear's teeth turn to mush.
        val rows = listOf(0.26f, 0.5f, 0.74f)
        val knobs = listOf(0.68f, 0.36f, 0.58f)
        rows.forEachIndexed { i, y ->
            drawLine(
                tint, Offset(size.width * 0.10f, size.height * y),
                Offset(size.width * 0.90f, size.height * y), strokeWidth = stroke.width,
            )
            val centre = Offset(size.width * knobs[i], size.height * y)
            val r = size.minDimension * 0.11f
            if (selected) drawCircle(tint, r, centre)
            else {
                drawCircle(Angra.Background, r, centre)
                drawCircle(tint, r, centre, style = stroke)
            }
        }
    }

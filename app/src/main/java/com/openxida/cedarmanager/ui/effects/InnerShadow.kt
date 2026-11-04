package com.openxida.cedarmanager.ui.effects

import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

/**
 * A lightweight inner rim for the selected liquid tab. This avoids recursive
 * GraphicsLayer capture on Compose 1.11 hardware renderers.
 */
@Immutable
data class InnerShadow(
    val radius: Dp = 24.dp,
    val offset: DpOffset = DpOffset(0.dp, radius),
    val color: Color = Color.Black.copy(alpha = 0.15f),
    val alpha: Float = 1f,
)

fun Modifier.innerShadow(shape: Shape, shadow: () -> InnerShadow?): Modifier =
    drawWithContent {
        drawContent()
        val current = shadow() ?: return@drawWithContent
        val outline = shape.createOutline(size, layoutDirection, this)
        val rimAlpha = (current.alpha * 0.55f).coerceIn(0f, 1f)
        if (outline is Outline.Rounded) {
            drawRoundRect(
                color = current.color.copy(alpha = rimAlpha),
                topLeft = Offset.Zero,
                size = size,
                cornerRadius = CornerRadius(size.minDimension / 2f),
                style = Stroke(width = current.radius.toPx().coerceIn(1f, size.minDimension / 3f)),
            )
        }
    }

package com.openxida.cedarmanager.ui.effects

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.launch
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.PI
import kotlin.math.atan2

/** Circular liquid-glass action with press growth, damped drag deformation and spring return. */
@Composable
fun LiquidGlassCircleButton(
    backdrop: Backdrop,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = 48.dp,
    surfaceColor: Color = Color.Unspecified,
    content: @Composable BoxScope.() -> Unit,
) {
    val density = LocalDensity.current
    val animationScope = rememberCoroutineScope()
    val interaction = remember(animationScope) { InteractiveHighlight(animationScope) }
    val dragOffset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    var accumulatedDrag by remember { mutableStateOf(Offset.Zero) }
    val maxDragPx = with(density) { 6.dp.toPx() }
    val isDark = isSystemInDarkTheme()
    val surface = if (surfaceColor == Color.Unspecified) MiuixTheme.colorScheme.surfaceContainer else surfaceColor
    val containerColor = surface.copy(alpha = 0.38f)
    val interactionSource = remember { MutableInteractionSource() }

    fun dampDrag(raw: Offset): Offset {
        val damped = raw * 0.14f
        val distance = damped.getDistance()
        return if (distance > maxDragPx && distance > 0f) damped * (maxDragPx / distance) else damped
    }

    Box(
        modifier
            .size(size)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .then(if (enabled) interaction.gestureModifier else Modifier)
            .then(
                if (enabled) {
                    Modifier.pointerInput(animationScope, maxDragPx) {
                        inspectDragGestures(
                            onDragStart = {
                                accumulatedDrag = Offset.Zero
                                animationScope.launch { dragOffset.snapTo(Offset.Zero) }
                            },
                            onDragEnd = {
                                accumulatedDrag = Offset.Zero
                                animationScope.launch {
                                    dragOffset.animateTo(
                                        Offset.Zero,
                                        spring(dampingRatio = 0.48f, stiffness = 340f),
                                    )
                                }
                            },
                            onDragCancel = {
                                accumulatedDrag = Offset.Zero
                                animationScope.launch {
                                    dragOffset.animateTo(
                                        Offset.Zero,
                                        spring(dampingRatio = 0.48f, stiffness = 340f),
                                    )
                                }
                            },
                            onDrag = { _, amount ->
                                accumulatedDrag += amount
                                val target = dampDrag(accumulatedDrag)
                                animationScope.launch { dragOffset.snapTo(target) }
                            },
                        )
                    }
                } else Modifier
            ),
        contentAlignment = Alignment.Center,
    ) {
        val pressProgress = interaction.pressProgress
        val offset = dragOffset.value
        val dragProgress = (offset.getDistance() / maxDragPx).fastCoerceIn(0f, 1f)
        val direction = if (offset.getDistance() > 0.1f) {
            (atan2(offset.y, offset.x) * 180f / PI).toFloat()
        } else 0f
        val baseScale = lerp(1f, 1.14f, pressProgress)
        val stretch = dragProgress * pressProgress
        val rimBrush = Brush.linearGradient(
            0f to Color.White.copy(alpha = if (isDark) 0.4f else 0.72f),
            0.42f to Color.White.copy(alpha = 0.12f),
            0.72f to Color.Transparent,
            1f to Color.White.copy(alpha = if (isDark) 0.2f else 0.34f),
        )

        Box(
            Modifier
                .size(size - 6.dp)
                .graphicsLayer {
                    clip = false
                    translationX = offset.x
                    translationY = offset.y
                    rotationZ = direction
                    scaleX = baseScale + 0.1f * stretch
                    scaleY = baseScale - 0.045f * stretch
                }
                .dropShadow(
                    CircleShape,
                    Shadow(
                        radius = 8.dp,
                        color = Color.Black,
                        alpha = if (isDark) 0.24f else 0.13f,
                    ),
                )
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { CircleShape },
                    effects = {
                        vibrancy()
                        blur(2.dp.toPx())
                        lens(
                            refractionHeight = 12.dp.toPx(),
                            refractionAmount = 24.dp.toPx(),
                        )
                    },
                    highlight = { Highlight.Default.copy(alpha = lerp(0.78f, 1f, pressProgress)) },
                    onDrawSurface = { drawRect(containerColor) },
                )
                .border(0.8.dp, rimBrush, CircleShape)
                .clip(CircleShape)
                .then(interaction.modifier),
        )

        Box(
            Modifier.graphicsLayer {
                translationX = offset.x * 0.55f
                translationY = offset.y * 0.55f
                val iconScale = lerp(1f, 1.07f, pressProgress)
                scaleX = iconScale
                scaleY = iconScale
            },
            contentAlignment = Alignment.Center,
            content = content,
        )
    }
}

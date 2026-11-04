package com.openxida.cedarmanager.ui.effects

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.lerp
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext
import kotlin.math.pow
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.openxida.cedarmanager.ui.scaledAnimationMillis

/** Debug-tunable multiplier for the diagonal popup tilt. */
val LocalPopupTiltStrength = compositionLocalOf { 1f }

internal object SettingsPopupMotion {
    const val OpenMillis = 360
    const val CloseMillis = 212

    /** Content height of the 3-item popup in the reference layout. */
    const val BaseContentHeightDp = 168f
    const val LongContentHeightDp = 380f
    private const val HeightBlendSpanDp = 192f

    data class Frame(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        operator fun minus(other: Frame) = Frame(
            left - other.left, top - other.top, right - other.right, bottom - other.bottom,
        )

        operator fun plus(other: Frame) = Frame(
            left + other.left, top + other.top, right + other.right, bottom + other.bottom,
        )

        operator fun times(factor: Float) = Frame(
            left * factor, top * factor, right * factor, bottom * factor,
        )

        /** Mirrors the motion around the horizontal axis for a bottom-anchored popup. */
        fun mirrored() = Frame(left, 1f - bottom, right, 1f - top)
    }

    val Anchor = Frame(198f / 638f, 0f, 1f, 88f / 475f)
    val Settled = Frame(0f, 0f, 1f, 1f)

    /**
     * 0 for the 3-item menu, 1 for the 20-item menu, smooth in between.
     * Heights come from the demo's 3 / 6 / 20 item variants (168 / 324 / 380 dp).
     */
    internal fun lengthWeight(contentHeightDp: Float): Float {
        val raw = (contentHeightDp - BaseContentHeightDp) / HeightBlendSpanDp
        return smoothStep(raw.fastCoerceIn(0f, 1f))
    }

    fun anchor(bottomAnchored: Boolean, contentHeightDp: Float = BaseContentHeightDp): Frame {
        val adapted = adapt(Anchor, contentHeightDp)
        return if (bottomAnchored) adapted.mirrored() else adapted
    }

    fun settled(bottomAnchored: Boolean): Frame = if (bottomAnchored) Settled.mirrored() else Settled

    // Least-squares analytic fits; original samples live only in test resources.
    // Reference-space max errors: open 2.64 px, close 4.62 px (638 x 475 bounds).
    private val openCurves = MotionCurves(
        left = PopupMotionCurve(
            Anchor.left, Settled.left,
            -0.218107884920, -4.932466197277, 16.491070111853, -20.375559903902, 8.786671512305,
        ),
        top = PopupMotionCurve(
            Anchor.top, Settled.top,
            1.030160058009, 5.195618094900, -33.173639414347, 51.921249511758, -26.308095031465,
        ),
        right = PopupMotionCurve(
            Anchor.right, Settled.right,
            -0.301028672480, -1.701527747379, 9.830882732252, -13.757696368278, 5.751588808441,
        ),
        bottom = PopupMotionCurve(
            Anchor.bottom, Settled.bottom,
            0.295443003448, 12.821597445917, -41.938416772601, 55.965857145269, -28.321712259630,
        ),
    )
    private val closeCurves = MotionCurves(
        left = PopupMotionCurve(
            Settled.left, Anchor.left,
            0.178353856294, -0.509162972308, 4.058706902487, -7.096544234089, 3.338121851239,
        ),
        top = PopupMotionCurve(
            Settled.top, Anchor.top,
            1.286241862588, -1.236279199940, 0.829807141388, -1.552201773857, 1.466815530612,
        ),
        right = PopupMotionCurve(
            Settled.right, Anchor.right,
            -0.376941525043, 0.410360515104, -1.453917502768, 2.926828039206, -1.958213912989,
        ),
        bottom = PopupMotionCurve(
            Settled.bottom, Anchor.bottom,
            -0.914558521472, 2.444687540805, -10.578745405073, 17.115526320496, -8.344563054238,
        ),
    )

    fun sample(
        open: Boolean,
        fraction: Float,
        from: Frame,
        bottomAnchored: Boolean = false,
        contentHeightDp: Float = BaseContentHeightDp,
    ): Frame {
        val clamped = fraction.fastCoerceIn(0f, 1f)
        val start = if (open) Anchor else Settled
        val end = if (open) Settled else Anchor
        val displayStart = adapt(start, contentHeightDp).let { if (bottomAnchored) it.mirrored() else it }
        val displayEnd = adapt(end, contentHeightDp).let { if (bottomAnchored) it.mirrored() else it }
        if (clamped == 0f) return from
        if (clamped == 1f) return displayEnd
        val fitted = (if (open) openCurves else closeCurves).valueAt(clamped)
        val adapted = adapt(fitted, contentHeightDp)
        val path = if (bottomAnchored) adapted.mirrored() else adapted
        val remaining = 1f - clamped
        // Preserve position when interrupted; the correction also ends at zero velocity.
        return path + (from - displayStart) * (remaining * remaining)
    }

    /**
     * The demo's height-adaptive geometry: short menus keep the full fitted motion,
     * long menus flatten their vertical travel, reduce horizontal deformation and
     * start closer to the final top edge.
     */
    private fun adapt(frame: Frame, contentHeightDp: Float): Frame {
        val amount = lengthWeight(contentHeightDp)
        if (amount <= 0f) return frame
        val height = contentHeightDp.coerceAtLeast(BaseContentHeightDp)
        val anchorBottom = Anchor.bottom
        val expand = (frame.bottom - frame.top - anchorBottom) / (1f - anchorBottom)
        val oldTravel = height * (1f - anchorBottom)
        val travel = oldTravel * (1f - amount) + 96f * amount
        val expandedHeight = height - travel + travel * expand
        val top = frame.top * BaseContentHeightDp * (1f - 0.65f * amount) +
            8f * amount * (1f - expand)
        val horizontal = 1f - 0.72f * amount
        return Frame(
            left = frame.left * horizontal,
            top = top / height,
            right = 1f + (frame.right - 1f) * horizontal,
            bottom = (top + expandedHeight) / height,
        )
    }

    fun blurPixels(
        open: Boolean,
        fraction: Float,
        contentHeightDp: Float = BaseContentHeightDp,
    ): Float {
        val millis = fraction.fastCoerceIn(0f, 1f) * if (open) OpenMillis else CloseMillis
        val base = if (open) {
            42f * (1f - millis / 220f).fastCoerceIn(0f, 1f).pow(1.4f)
        } else {
            36f * (millis / 185f).fastCoerceIn(0f, 1f).pow(1.3f)
        }
        return base * (1f - 0.7f * lengthWeight(contentHeightDp))
    }

    fun alpha(
        open: Boolean,
        fraction: Float,
        contentHeightDp: Float = BaseContentHeightDp,
    ): Float {
        val millis = fraction.fastCoerceIn(0f, 1f) * if (open) OpenMillis else CloseMillis
        val old = if (open) (millis / 75f).fastCoerceIn(0f, 1f)
        else (1f - fraction).fastCoerceIn(0f, 1f).pow(2)
        val amount = lengthWeight(contentHeightDp)
        if (amount <= 0f) return old
        val soft = if (open) smoothStep(millis / 140f) else 1f - smoothStep(fraction)
        return old * (1f - amount) + soft * amount
    }

    private data class MotionCurves(
        val left: PopupMotionCurve,
        val top: PopupMotionCurve,
        val right: PopupMotionCurve,
        val bottom: PopupMotionCurve,
    ) {
        fun valueAt(fraction: Float) = Frame(
            left = left.valueAt(fraction),
            top = top.valueAt(fraction),
            right = right.valueAt(fraction),
            bottom = bottom.valueAt(fraction),
        )
    }

}

private fun smoothStep(value: Float): Float {
    val t = value.fastCoerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/** A clipped popup surface with unscaled content and whole-surface motion blur. */
@Composable
fun LiquidGlassPopupSurface(    visible: Boolean,
    onDismiss: () -> Unit,
    onPresentationChanged: (Boolean) -> Unit = {},
    backdrop: SampledLayerBackdrop,
    dark: Boolean,
    expandedWidth: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    surfaceColor: Color = Color.Unspecified,
    content: @Composable ColumnScope.() -> Unit,
) {
    var present by remember { mutableStateOf(visible) }
    var timeline by remember { mutableFloatStateOf(1f) }
    var opening by remember { mutableStateOf(visible) }
    var fromFrame by remember { mutableStateOf(if (visible) SettingsPopupMotion.Settled else SettingsPopupMotion.Anchor) }
    var bottomAnchored by remember { mutableStateOf(false) }
    var rightAnchored by remember { mutableStateOf(true) }
    var anchorReady by remember { mutableStateOf(false) }
    var contentHeightDp by remember { mutableFloatStateOf(SettingsPopupMotion.BaseContentHeightDp) }
    var displayedFrame by remember { mutableStateOf(SettingsPopupMotion.Settled) }
    val dragOffset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val animationScope = rememberCoroutineScope()
    val interaction = remember(animationScope) { InteractiveHighlight(animationScope) }
    val currentOnPresentationChanged by rememberUpdatedState(onPresentationChanged)
    SideEffect { backdrop.demandFullCapture(present) }
    val density = LocalDensity.current
    val windowSize = LocalWindowInfo.current.containerSize
    val popupTiltStrength = LocalPopupTiltStrength.current
    val isDark = dark
    val maxDragPx = with(density) { 4.dp.toPx() }
    val shadowMargin = 48.dp
    val shadowMarginPx = with(density) { shadowMargin.toPx() }
    val frame = SettingsPopupMotion.sample(opening, timeline, fromFrame, bottomAnchored, contentHeightDp)
    SideEffect { displayedFrame = frame }
    val lengthWeight = SettingsPopupMotion.lengthWeight(contentHeightDp)
    val tiltAmount = (
        if (opening) {
            1f - smoothStep((timeline / 0.62f).fastCoerceIn(0f, 1f))
        } else {
            smoothStep(((timeline - 0.3f) / 0.7f).fastCoerceIn(0f, 1f))
        }
        ) * (1f - 0.3f * lengthWeight)
    val motionBlur = expandedWidth *
        (SettingsPopupMotion.blurPixels(opening, timeline, contentHeightDp) * 1.5f / 638f)
    val motionAlpha = if (anchorReady) {
        SettingsPopupMotion.alpha(opening, timeline, contentHeightDp)
    } else {
        0f
    }
    val radiusPx = with(density) { 24.dp.toPx() }
    val motionShape = remember(frame, radiusPx, shadowMarginPx) {
        GenericShape { size, _ ->
            val width = size.width - shadowMarginPx * 2f
            val height = size.height - shadowMarginPx * 2f
            addRoundRect(
                RoundRect(
                    left = shadowMarginPx + width * frame.left,
                    top = shadowMarginPx + height * frame.top,
                    right = shadowMarginPx + width * frame.right,
                    bottom = shadowMarginPx + height * frame.bottom,
                    cornerRadius = CornerRadius(radiusPx),
                ),
            )
        }
    }
    LaunchedEffect(visible) {
        val durationScale = coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
        // Capture the frame that was actually drawn before mutating timeline.
        // Recomputing it after timeline = 0 would lose the interrupted position and
        // make rapid open/close toggles drift toward the expanded state.
        val interruptedFrame = displayedFrame
        val wasFullyClosed = !opening && timeline >= 1f

        suspend fun play(baseDurationMillis: Int) {
            val durationMillis = scaledAnimationMillis(baseDurationMillis, durationScale)
            var startNanos = 0L
            do {
                val frameNanos = withFrameNanos { it }
                if (startNanos == 0L) startNanos = frameNanos
                timeline = ((frameNanos - startNanos) / (durationMillis * 1_000_000f))
                    .fastCoerceIn(0f, 1f)
            } while (timeline < 1f)
        }

        if (visible) {
            val wasPresent = present
            if (!wasPresent) anchorReady = false
            present = true
            currentOnPresentationChanged(true)
            dragOffset.snapTo(Offset.Zero)
            if (wasPresent) {
                fromFrame = if (wasFullyClosed) {
                    SettingsPopupMotion.anchor(bottomAnchored, contentHeightDp)
                } else {
                    interruptedFrame
                }
                opening = true
                timeline = 0f
                play(SettingsPopupMotion.OpenMillis)
            } else {
                // Let the first layout report whether the popup expands from the top or bottom.
                withFrameNanos { }
                if (!anchorReady) withFrameNanos { }
                fromFrame = SettingsPopupMotion.anchor(bottomAnchored, contentHeightDp)
                opening = true
                timeline = 0f
                play(SettingsPopupMotion.OpenMillis)
            }
        } else if (present) {
            fromFrame = interruptedFrame
            opening = false
            timeline = 0f
            play(SettingsPopupMotion.CloseMillis)
            // Give the zero-alpha frame one draw before removing the surface.
            withFrameNanos { }
            present = false
            currentOnPresentationChanged(false)
        }
    }

    BackHandler(enabled = present, onBack = onDismiss)

    if (present) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .then(
                        if (visible) {
                            Modifier.pointerInput(onDismiss) {
                                detectTapGestures(onTap = { onDismiss() })
                            }
                        } else {
                            Modifier
                        },
                    ),
            )
            val materialShape = RoundedCornerShape(24.dp)
            val containerColor = if (surfaceColor != Color.Unspecified) surfaceColor.copy(alpha = 0.4f)
                else if (isDark) Color(0xFF121212).copy(alpha = 0.4f)
                else Color(0xFFFAFAFA).copy(alpha = 0.6f)
            val rimBrush = Brush.linearGradient(
                0f to Color.White.copy(alpha = if (isDark) 0.38f else 0.7f),
                0.38f to Color.White.copy(alpha = 0.12f),
                0.72f to Color.Transparent,
                1f to Color.White.copy(alpha = if (isDark) 0.2f else 0.34f),
            )
            Box(
                modifier
                    .width(expandedWidth)
                    .onGloballyPositioned { coordinates ->
                        val position = coordinates.positionInWindow()
                        val size = coordinates.size
                        bottomAnchored = position.y + size.height / 2f > windowSize.height / 2f
                        rightAnchored = position.x + size.width / 2f > windowSize.width / 2f
                        val measuredHeightDp =
                            ((size.height - shadowMarginPx * 2f) / density.density)
                                .coerceAtLeast(1f)
                        if (kotlin.math.abs(measuredHeightDp - contentHeightDp) > 0.5f) {
                            contentHeightDp = measuredHeightDp
                        }
                        anchorReady = true
                    },
            ) {
                Column(
                    Modifier
                        .offset(y = -shadowMargin)
                        .requiredWidth(expandedWidth + shadowMargin * 2)
                        .graphicsLayer {
                            val pressProgress = interaction.pressProgress
                            val dragScale = 1f - (dragOffset.value.getDistance() / 1200f).fastCoerceIn(0f, 0.08f)
                            val pressScale = lerp(1f, 0.988f, pressProgress)
                            val tiltScale = 1f - 0.05f * tiltAmount * popupTiltStrength
                            scaleX = dragScale * pressScale * tiltScale
                            scaleY = dragScale * pressScale * tiltScale
                            alpha = motionAlpha
                            translationX = dragOffset.value.x
                            translationY = dragOffset.value.y
                            val pointer = interaction.pointerPosition
                            val pointerTiltX = if (size.height > 0f) {
                                -((pointer.y / size.height) - 0.5f).fastCoerceIn(-0.5f, 0.5f) * 5f
                            } else 0f
                            val pointerTiltY = if (size.width > 0f) {
                                ((pointer.x / size.width) - 0.5f).fastCoerceIn(-0.5f, 0.5f) * 6f
                            } else 0f
                            // Rotate the bottom edge back for a top-anchored popup and the top edge
                            // back for a bottom-anchored one. Combined with the left-edge rotation
                            // below this sends the diagonal-opposite corner into the screen.
                            val inwardTiltX = (if (bottomAnchored) 13f else -13f) * popupTiltStrength
                            val inwardTiltY = (if (rightAnchored) -18f else 18f) * popupTiltStrength
                            rotationX = pointerTiltX * pressProgress + inwardTiltX * tiltAmount
                            rotationY = pointerTiltY * pressProgress + inwardTiltY * tiltAmount
                            cameraDistance = 12f * density.density
                            transformOrigin = TransformOrigin(
                                if (rightAnchored) 1f else 0f,
                                if (bottomAnchored) 1f else 0f,
                            )
                            compositingStrategy = CompositingStrategy.Offscreen
                            clip = false
                        }
                        .blur(motionBlur, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                        .dropShadow(
                            motionShape,
                            Shadow(
                                radius = 18.dp,
                                color = Color.Black,
                                alpha = if (isDark) 0.28f else 0.16f,
                            ),
                        )
                        .clip(motionShape)
                        .drawBackdrop(
                            backdrop = backdrop,
                            shape = { materialShape },
                            effects = {
                                colorControls(
                                    brightness = if (isDark) 0f else 0.2f,
                                    saturation = 1.5f,
                                )
                                blur((if (isDark) 14.dp else 16.dp).toPx())
                                lens(
                                    refractionHeight = 24.dp.toPx(),
                                    refractionAmount = 48.dp.toPx(),
                                    depthEffect = true,
                                )
                            },
                            highlight = { Highlight.Plain },
                            onDrawSurface = { drawRect(containerColor) },
                        )
                        .border(0.8.dp, rimBrush, motionShape)
                        .then(interaction.modifier)
                        .then(interaction.gestureModifier)
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDrag = { change, amount ->
                                    change.consume()
                                    val next = dragOffset.value + amount * 0.1f
                                    animationScope.launch {
                                        dragOffset.snapTo(
                                            Offset(
                                                next.x.fastCoerceIn(-maxDragPx, maxDragPx),
                                                next.y.fastCoerceIn(-maxDragPx, maxDragPx),
                                            ),
                                        )
                                    }
                                },
                                onDragEnd = {
                                    animationScope.launch {
                                        dragOffset.animateTo(Offset.Zero, spring(dampingRatio = 0.52f, stiffness = 420f))
                                    }
                                },
                                onDragCancel = {
                                    animationScope.launch {
                                        dragOffset.animateTo(Offset.Zero, spring(dampingRatio = 0.52f, stiffness = 420f))
                                    }
                                },
                            )
                        }
                        .padding(shadowMargin)
                        .padding(vertical = 6.dp),
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                translationX = size.width * frame.left
                                translationY = (size.height + 12.dp.toPx()) * frame.top
                            },
                        content = content,
                    )
                }
            }
        }
    }
}

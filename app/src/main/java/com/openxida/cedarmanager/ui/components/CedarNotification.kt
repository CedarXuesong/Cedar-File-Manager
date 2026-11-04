package com.openxida.cedarmanager.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.openxida.cedarmanager.domain.OperationProgress
import com.openxida.cedarmanager.ui.effects.SampledLayerBackdrop
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

private data class NoticeContent(
    val title: String? = null,
    val detail: String,
    val fraction: Float? = null,
    val running: Boolean = false,
    val failed: Boolean = false,
    val cancel: (() -> Unit)? = null,
)

private class NoticeHolder(var value: NoticeContent? = null)

/**
 * Top-right glass notification shared by transient messages and operation progress.
 * One surface at a time keeps the corner calm while still surfacing the latest result.
 */
@Composable
fun CedarTopNotificationHost(
    operation: OperationProgress?,
    message: String?,
    dark: Boolean,
    backdrop: SampledLayerBackdrop,
    onCancelOperation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    var operationVisible by remember { mutableStateOf(false) }
    LaunchedEffect(operation) {
        if (operation == null) {
            operationVisible = false
        } else {
            operationVisible = true
            if (!operation.running) {
                delay(3_000)
                operationVisible = false
            }
        }
    }

    val activeOperation = if (operationVisible) operation else null
    val notice = when {
        activeOperation != null -> NoticeContent(
            title = activeOperation.title,
            detail = message ?: "${activeOperation.current}/${activeOperation.total} · ${activeOperation.detail}",
            fraction = if (activeOperation.total > 0) {
                activeOperation.current.toFloat() / activeOperation.total.toFloat()
            } else {
                0f
            },
            running = activeOperation.running,
            failed = activeOperation.failed > 0,
            cancel = if (activeOperation.running) onCancelOperation else null,
        )
        message != null -> NoticeContent(detail = message)
        else -> null
    }

    CedarGlassNotice(
        notice = notice,
        dark = dark,
        backdrop = backdrop,
        modifier = modifier.padding(top = statusTop + 8.dp, end = 12.dp),
    )
}

@Composable
private fun CedarGlassNotice(
    notice: NoticeContent?,
    dark: Boolean,
    backdrop: SampledLayerBackdrop,
    modifier: Modifier = Modifier,
) {
    var present by remember { mutableStateOf(false) }
    val lastNotice = remember { NoticeHolder() }
    SideEffect { backdrop.demandFullCapture(present) }
    if (notice != null) lastNotice.value = notice
    val appearance = remember { Animatable(0f) }
    val exit = remember { Animatable(0f) }
    val density = LocalDensity.current
    val enterOffset = with(density) { 76.dp.toPx() }
    val exitOffset = with(density) { 460.dp.toPx() }

    LaunchedEffect(notice != null) {
        if (notice != null) {
            if (!present) {
                appearance.snapTo(0f)
                exit.snapTo(0f)
                present = true
            } else if (exit.value > 0f) {
                exit.animateTo(0f, spring(dampingRatio = 0.76f, stiffness = 460f))
            }
            appearance.animateTo(
                1f,
                spring(dampingRatio = 0.68f, stiffness = 320f, visibilityThreshold = 0.001f),
            )
        } else if (present) {
            exit.animateTo(1f, tween(durationMillis = 260, easing = FastOutLinearInEasing))
            present = false
            appearance.snapTo(0f)
            exit.snapTo(0f)
        }
    }

    val content = notice ?: lastNotice.value ?: return
    if (!present) return

    val appearValue = appearance.value
    val exitValue = exit.value
    val motionBlur = with(density) {
        (((1f - appearValue).coerceIn(0f, 1f) * 10f) + exitValue * 8f).dp
    }
    val shape = RoundedCornerShape(22.dp)
    val containerColor = if (dark) {
        Color(0xFF141619).copy(alpha = 0.52f)
    } else {
        Color(0xFFFDFDFD).copy(alpha = 0.64f)
    }
    val rim = Brush.linearGradient(
        0f to Color.White.copy(alpha = if (dark) 0.36f else 0.74f),
        0.4f to Color.White.copy(alpha = 0.1f),
        0.74f to Color.Transparent,
        1f to Color.White.copy(alpha = if (dark) 0.18f else 0.3f),
    )

    Box(
        modifier
            .graphicsLayer {
                translationY = (appearValue - 1f) * enterOffset
                translationX = exitValue * exitOffset
                alpha = appearValue.coerceIn(0f, 1f) * (1f - exitValue)
                val expand = appearValue.coerceIn(0f, 1.06f)
                scaleX = (0.68f + 0.32f * expand) * (1f - 0.06f * exitValue)
                scaleY = (0.84f + 0.16f * expand) * (1f - 0.04f * exitValue)
                transformOrigin = TransformOrigin(1f, 0f)
            }
            .then(
                if (motionBlur > 0.1.dp) {
                    Modifier.blur(motionBlur, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                } else {
                    Modifier
                },
            )
            .dropShadow(
                shape,
                Shadow(
                    radius = 18.dp,
                    color = Color.Black,
                    alpha = if (dark) 0.3f else 0.16f,
                ),
            )
            .clip(shape)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    vibrancy()
                    blur((if (dark) 13.dp else 15.dp).toPx())
                    lens(
                        refractionHeight = 18.dp.toPx(),
                        refractionAmount = 34.dp.toPx(),
                        depthEffect = true,
                    )
                },
                highlight = { Highlight.Default },
                onDrawSurface = { drawRect(containerColor) },
            )
            .border(0.8.dp, rim, shape)
            .padding(horizontal = 14.dp, vertical = 11.dp),
    ) {
        if (content.fraction != null) {
            OperationNotice(content)
        } else {
            MessageNotice(content)
        }
    }
}

@Composable
private fun OperationNotice(content: NoticeContent) {
    Row(
        Modifier
            .widthIn(max = 220.dp)
            .animateContentSize(
                animationSpec = spring(dampingRatio = 0.72f, stiffness = 500f),
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = content.title.orEmpty(),
                fontSize = 13.sp,
                lineHeight = 17.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = content.detail,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                color = MiuixTheme.colorScheme.onSurfaceSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (content.running && content.cancel != null) {
            CedarIconButton(
                icon = "stop",
                description = "取消任务",
                compact = true,
                onClick = content.cancel,
            )
        }
        ProgressRing(fraction = content.fraction ?: 0f, failed = content.failed)
    }
}

@Composable
private fun MessageNotice(content: NoticeContent) {
    val failed = content.detail.contains("失败") ||
        content.detail.contains("错误") ||
        content.detail.contains("无法")
    val accent = if (failed) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.primary
    Row(
        Modifier
            .widthIn(max = 220.dp)
            .animateContentSize(
                animationSpec = spring(dampingRatio = 0.72f, stiffness = 500f),
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(28.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.72f)),
        )
        Spacer(Modifier.width(9.dp))
        Text(
            text = content.detail,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ProgressRing(fraction: Float, failed: Boolean) {
    val target = fraction.coerceIn(0f, 1f)
    val progress by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
        label = "notification progress",
    )
    val percent = (progress * 100f).roundToInt()
    val accent = if (failed) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.primary
    val track = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.14f)
    Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 3.dp.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = track,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            if (progress > 0f) {
                drawArc(
                    color = accent,
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        Text(
            text = "$percent%",
            fontSize = 9.sp,
            lineHeight = 10.sp,
            fontWeight = FontWeight.Medium,
            color = accent,
        )
    }
}

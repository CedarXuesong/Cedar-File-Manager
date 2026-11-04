package com.openxida.cedarmanager.ui.effects

import android.os.SystemClock
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.DefaultCameraDistance
import androidx.compose.ui.graphics.DefaultShadowColor
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawTransform
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import com.kyant.backdrop.Backdrop
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.roundToInt

/** Which part of a source node is captured for a backdrop layer. */
sealed interface BackdropCapture {
    data object Full : BackdropCapture
    data class Top(val height: Dp) : BackdropCapture
}

@Composable
fun rememberSampledLayerBackdrop(
    maxFps: Int = 30,
    fullFps: Int = 60,
    idleFps: Int = 1,
    capture: BackdropCapture = BackdropCapture.Full,
    onDraw: ContentDrawScope.() -> Unit,
): SampledLayerBackdrop {
    val layer = rememberGraphicsLayer()
    return remember(layer, maxFps, fullFps, idleFps, capture) {
        SampledLayerBackdrop(layer, onDraw, maxFps, fullFps, idleFps, capture)
    }.also { it.onDraw = onDraw }
}

fun Modifier.sampledLayerBackdrop(backdrop: SampledLayerBackdrop, active: Boolean = false): Modifier =
    this.then(SampledLayerBackdropElement(backdrop, active))

@Stable
class SampledLayerBackdrop internal constructor(
    internal val layer: GraphicsLayer,
    internal var onDraw: ContentDrawScope.() -> Unit,
    internal val maxFps: Int,
    internal val fullFps: Int,
    internal val idleFps: Int,
    internal val capture: BackdropCapture,
) : Backdrop {
    override val isCoordinatesDependent: Boolean = true
    internal var layerCoordinates: LayoutCoordinates? by mutableStateOf(null)
    private val inverseScope = InverseSampledLayerScope()
    private val mainHandler = Handler(Looper.getMainLooper())
    internal var invalidateSource: (() -> Unit)? = null

    /**
     * When true the layer is captured at full size instead of the cheaper strip in [capture].
     * Glass popups and notifications set this while they are present.
     */
    var fullCapture: Boolean by mutableStateOf(false)
        private set

    internal var layerCaptureTopPx: Int = 0
    internal var layerCaptureHeightPx: Int = 0

    fun demandFullCapture(value: Boolean) {
        if (fullCapture == value) return
        fullCapture = value
        invalidateSource?.invoke()
    }

    fun requestRefresh() {
        if (Looper.myLooper() == Looper.getMainLooper()) invalidateSource?.invoke()
        else mainHandler.post { invalidateSource?.invoke() }
    }

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?,
    ) {
        val target = coordinates ?: return
        val source = layerCoordinates ?: return
        if (layerCaptureHeightPx <= 0) return
        withTransform({
            if (layerBlock != null) {
                inverseScope.reset()
                with(inverseScope) { inverseTransform(density, layerBlock) }
            }
            val offset = try {
                source.localPositionOf(target)
            } catch (_: Exception) {
                target.positionInWindow() - source.positionInWindow()
            }
            translate(-offset.x, -offset.y + layerCaptureTopPx)
        }) {
            drawLayer(layer)
        }
    }
}

private data class SampledLayerBackdropElement(val backdrop: SampledLayerBackdrop, val active: Boolean) :
    ModifierNodeElement<SampledLayerBackdropNode>() {
    override fun create() = SampledLayerBackdropNode(backdrop, active)
    override fun update(node: SampledLayerBackdropNode) {
        if (node.backdrop !== backdrop) {
            node.backdrop.layerCoordinates = null
            node.backdrop.invalidateSource = null
            node.backdrop = backdrop
            node.resetSample()
            node.bindSource()
        }
        node.setActive(active)
    }
}

private class SampledLayerBackdropNode(var backdrop: SampledLayerBackdrop, private var active: Boolean) :
    DrawModifierNode, GlobalPositionAwareModifierNode, Modifier.Node() {
    private val handler = Handler(Looper.getMainLooper())
    private var stripLimiter = SamplingRateLimiter(backdrop.maxFps, backdrop.idleFps)
    private var fullLimiter = SamplingRateLimiter(backdrop.fullFps, backdrop.idleFps)
    private var recordedCapture = IntSize.Zero
    private var pendingChange = true
    private var timerDraw = false
    private val refresh = Runnable {
        if (isAttached) {
            timerDraw = true
            invalidateDraw()
        }
    }

    override fun onAttach() {
        bindSource()
    }

    fun bindSource() {
        backdrop.invalidateSource = {
            if (isAttached) {
                pendingChange = true
                handler.removeCallbacks(refresh)
                invalidateDraw()
            }
        }
    }

    fun setActive(value: Boolean) {
        if (active == value) return
        active = value
        pendingChange = true
        handler.removeCallbacks(refresh)
        if (isAttached) invalidateDraw()
    }

    fun resetSample() {
        handler.removeCallbacks(refresh)
        stripLimiter = SamplingRateLimiter(backdrop.maxFps, backdrop.idleFps)
        fullLimiter = SamplingRateLimiter(backdrop.fullFps, backdrop.idleFps)
        recordedCapture = IntSize.Zero
        pendingChange = true
        timerDraw = false
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        if (!timerDraw) pendingChange = true
        timerDraw = false
        val now = SystemClock.elapsedRealtimeNanos()
        val nextSize = IntSize(size.width.toInt(), size.height.toInt())
        if (nextSize.width <= 0 || nextSize.height <= 0) return

        val full = backdrop.fullCapture
        val captureHeight = when (val region = backdrop.capture) {
            BackdropCapture.Full -> nextSize.height
            is BackdropCapture.Top -> region.height.toPx().roundToInt().coerceIn(1, nextSize.height)
        }
        val captureTop = when (backdrop.capture) {
            BackdropCapture.Full -> 0
            is BackdropCapture.Top -> 0
        }.let { if (full) 0 else it }
        val captureSize = IntSize(nextSize.width, if (full) nextSize.height else captureHeight)
        val limiter = if (full) fullLimiter else stripLimiter

        if (limiter.shouldSample(now, active, pendingChange, captureSize != recordedCapture)) {
            backdrop.layerCaptureTopPx = captureTop
            backdrop.layerCaptureHeightPx = captureSize.height
            val source = this
            backdrop.layer.record(captureSize) {
                backdrop.onDraw(source)
            }
            limiter.record(now)
            recordedCapture = captureSize
            pendingChange = false
        }

        handler.removeCallbacks(refresh)
        handler.postDelayed(refresh, limiter.nextDelayMillis(now, active, pendingChange))
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        if (coordinates.isAttached) backdrop.layerCoordinates = coordinates
    }

    override fun onDetach() {
        backdrop.layerCoordinates = null
        backdrop.invalidateSource = null
        resetSample()
    }
}

private class InverseSampledLayerScope : GraphicsLayerScope {
    override var size: Size = Size.Unspecified
    override var density: Float = 1f
    override var fontScale: Float = 1f
    override var scaleX: Float = 1f
    override var scaleY: Float = 1f
    override var alpha: Float = 1f
    override var translationX: Float = 0f
    override var translationY: Float = 0f
    override var shadowElevation: Float = 0f
    override var ambientShadowColor: Color = DefaultShadowColor
    override var spotShadowColor: Color = DefaultShadowColor
    override var rotationX: Float = 0f
    override var rotationY: Float = 0f
    override var rotationZ: Float = 0f
    override var cameraDistance: Float = DefaultCameraDistance
    override var transformOrigin: TransformOrigin = TransformOrigin.Center
    override var shape: Shape = RectangleShape
    override var clip: Boolean = false
    override var renderEffect: RenderEffect? = null
    override var blendMode: BlendMode = BlendMode.SrcOver
    override var colorFilter: ColorFilter? = null
    override var compositingStrategy: CompositingStrategy = CompositingStrategy.Auto

    fun reset() {
        scaleX = 1f
        scaleY = 1f
        rotationZ = 0f
    }

    fun DrawTransform.inverseTransform(density: Density, block: GraphicsLayerScope.() -> Unit) {
        this@InverseSampledLayerScope.size = size
        this@InverseSampledLayerScope.density = density.density
        fontScale = density.fontScale
        block()
        if (rotationZ == 0f) {
            if (scaleX != 0f && scaleY != 0f) scale(1f / scaleX, 1f / scaleY, Offset.Zero)
            return
        }
        val radians = rotationZ * (PI / 180.0)
        val sin = sin(radians).toFloat()
        val cos = cos(radians).toFloat()
        val a00 = cos * scaleX
        val a01 = sin * scaleY
        val a10 = -sin * scaleX
        val a11 = cos * scaleY
        val determinant = a00 * a11 - a01 * a10
        if (determinant == 0f) return
        transform(Matrix().apply {
            this[0, 0] = a11 / determinant
            this[0, 1] = -a01 / determinant
            this[1, 0] = -a10 / determinant
            this[1, 1] = a00 / determinant
        })
    }
}

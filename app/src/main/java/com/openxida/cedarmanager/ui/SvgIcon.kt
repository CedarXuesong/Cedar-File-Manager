package com.openxida.cedarmanager.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.Path
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import androidx.core.graphics.PathParser
import com.kyant.shapes.RoundedCornerStyle
import com.kyant.shapes.RoundedRectangle
import org.w3c.dom.Element
import java.util.concurrent.ConcurrentHashMap
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.roundToInt

private data class SvgPath(
    val path: Path,
    val fill: Boolean,
    val stroke: Boolean,
    val strokeWidth: Float,
)

private data class SvgDocument(
    val minX: Float,
    val minY: Float,
    val width: Float,
    val height: Float,
    val paths: List<SvgPath>,
)

private class SvgPaints(argb: Int) {
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = argb
        style = Paint.Style.FILL
    }
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = argb
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
}

private object SvgCache {
    private val cache = ConcurrentHashMap<String, SvgDocument?>()

    fun load(context: Context, name: String): SvgDocument? = cache.getOrPut(name) {
        runCatching {
            context.assets.open("icons/$name.svg").use { input ->
                val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(input).documentElement
                val viewBox = root.getAttribute("viewBox").trim().split(Regex("[ ,]+"))
                val defaultFill = root.getAttribute("fill").ifBlank { "none" }
                val defaultStroke = root.getAttribute("stroke").ifBlank { "none" }
                val defaultStrokeWidth = root.getAttribute("stroke-width").toFloatOrNull() ?: 2f
                val nodes = root.getElementsByTagName("path")
                val paths = buildList {
                    for (index in 0 until nodes.length) {
                        val element = nodes.item(index) as? Element ?: continue
                        val data = element.getAttribute("d")
                        val path = PathParser.createPathFromPathData(data)
                        if (element.getAttribute("fill-rule") == "evenodd") {
                            path.fillType = Path.FillType.EVEN_ODD
                        }
                        val fill = element.getAttribute("fill").ifBlank { defaultFill } != "none"
                        val stroke = element.getAttribute("stroke").ifBlank { defaultStroke } != "none"
                        val strokeWidth = element.getAttribute("stroke-width").toFloatOrNull() ?: defaultStrokeWidth
                        add(SvgPath(path, fill, stroke, strokeWidth))
                    }
                }
                SvgDocument(
                    minX = viewBox.getOrNull(0)?.toFloatOrNull() ?: 0f,
                    minY = viewBox.getOrNull(1)?.toFloatOrNull() ?: 0f,
                    width = viewBox.getOrNull(2)?.toFloatOrNull() ?: 24f,
                    height = viewBox.getOrNull(3)?.toFloatOrNull() ?: 24f,
                    paths = paths,
                )
            }
        }.getOrNull()
    }
}

/**
 * Icon rendering used across the app.
 *
 * Icons are plain glyphs by default. Only the file list opts into [withBackground],
 * which draws a rounded tile slightly larger than the glyph so rows keep a calm,
 * scannable rhythm without decorating every other icon in the UI.
 */
@Composable
fun CedarSvgIcon(
    name: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
    size: Dp = 24.dp,
    withBackground: Boolean = false,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val color = if (tint == Color.Unspecified) Color(0xFF17191D) else tint
    val document = remember(name) { SvgCache.load(context, name) }
    val tileSize = if (withBackground) size * IconTileScale else size
    val tilePx = with(density) { tileSize.roundToPx() }.coerceAtLeast(1)
    val bitmap = remember(document, name, color, tilePx, withBackground) {
        document?.let {
            SvgBitmapCache.load(it, name, color.toArgb(), tilePx, withBackground)
        }
    }
    val backgroundShape = remember(tileSize) {
        RoundedRectangle(tileSize * IconTileCornerScale, RoundedCornerStyle.Continuous)
    }
    Canvas(
        modifier = modifier
            .size(tileSize)
            .then(
                if (withBackground) {
                    Modifier.background(
                        color.copy(alpha = color.alpha * IconTileAlpha),
                        backgroundShape,
                    )
                } else {
                    Modifier
                },
            )
            .semantics { this.contentDescription = contentDescription }
    ) {
        if (bitmap != null) {
            drawImage(bitmap)
            return@Canvas
        }
        val svg = document ?: return@Canvas
        val glyphFraction = if (withBackground) 1f / IconTileScale else 1f
        drawSvg(svg, this.size, SvgPaints(color.toArgb()), glyphFraction)
    }
}

private const val IconTileScale = 1.5f
private const val IconTileCornerScale = 0.3f
private const val IconTileAlpha = 0.14f

private data class IconBitmapKey(
    val name: String,
    val color: Int,
    val sizePx: Int,
    val withBackground: Boolean,
)

private object SvgBitmapCache {
    private val cache = object : LruCache<IconBitmapKey, ImageBitmap>(64) {}

    fun load(
        document: SvgDocument,
        name: String,
        color: Int,
        sizePx: Int,
        withBackground: Boolean,
    ): ImageBitmap? {
        if (sizePx <= 0) return null
        val key = IconBitmapKey(name, color, sizePx, withBackground)
        cache.get(key)?.let { return it }
        return runCatching {
            val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val canvas = AndroidCanvas(bitmap)
            drawSvg(
                document = document,
                canvas = canvas,
                width = sizePx.toFloat(),
                height = sizePx.toFloat(),
                paints = SvgPaints(color),
                glyphFraction = if (withBackground) 1f / IconTileScale else 1f,
            )
            bitmap.asImageBitmap().also { cache.put(key, it) }
        }.getOrNull()
    }
}

private fun DrawScope.drawSvg(
    document: SvgDocument,
    canvasSize: Size,
    paints: SvgPaints,
    glyphFraction: Float = 1f,
) {
    drawIntoCanvas { canvas ->
        drawSvg(
            document = document,
            canvas = canvas.nativeCanvas,
            width = canvasSize.width,
            height = canvasSize.height,
            paints = paints,
            glyphFraction = glyphFraction,
        )
    }
}

private fun drawSvg(
    document: SvgDocument,
    canvas: AndroidCanvas,
    width: Float,
    height: Float,
    paints: SvgPaints,
    glyphFraction: Float = 1f,
) {
    val glyphWidth = width * glyphFraction
    val glyphHeight = height * glyphFraction
    val scale = minOf(glyphWidth / document.width, glyphHeight / document.height)
    val offsetX = (width - document.width * scale) / 2f
    val offsetY = (height - document.height * scale) / 2f
    canvas.save()
    canvas.translate(offsetX, offsetY)
    canvas.scale(scale, scale)
    canvas.translate(-document.minX, -document.minY)
    document.paths.forEach { item ->
        if (item.fill) {
            canvas.drawPath(item.path, paints.fill)
        }
        if (item.stroke) {
            paints.stroke.strokeWidth = item.strokeWidth
            canvas.drawPath(item.path, paints.stroke)
        }
    }
    canvas.restore()
}

package com.openxida.cedarmanager.ui.components

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.openxida.cedarmanager.BuildConfig
import com.openxida.cedarmanager.domain.RootState
import com.openxida.cedarmanager.ui.CedarFontFamily
import com.openxida.cedarmanager.ui.CedarSvgIcon
import com.openxida.cedarmanager.ui.effects.verticalGradientBlur
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawPlainBackdrop
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal data class StorageSnapshot(
    val availableBytes: Long,
    val totalBytes: Long,
) {
    val usedBytes: Long get() = (totalBytes - availableBytes).coerceAtLeast(0L)

    val usage: Float
        get() = if (totalBytes > 0L) {
            (usedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }

    val percent: Int get() = (usage * 100f).roundToInt()
}

internal data class HomeStorageState(
    val snapshot: StorageSnapshot?,
    val loading: Boolean,
)

internal data class DeviceDetails(
    val version: String,
    val model: String,
    val certifiedModel: String,
    val processor: String,
    val memory: String,
    val androidVersion: String,
    val kernel: String,
    val baseband: String,
    val securityPatch: String,
)

@Composable
internal fun rememberHomeStorage(): HomeStorageState {
    val context = LocalContext.current
    var snapshot by remember { mutableStateOf<StorageSnapshot?>(null) }
    var loading by remember { mutableStateOf(true) }
    var refreshKey by remember { mutableIntStateOf(0) }
    LaunchedEffect(refreshKey) {
        val loaded = withContext(Dispatchers.IO) { readStorageSnapshot() }
        if (loaded != null) snapshot = loaded
        loading = false
    }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshKey++
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return HomeStorageState(snapshot, loading)
}

@Composable
internal fun rememberDeviceDetails(): DeviceDetails {
    val context = LocalContext.current
    return remember(context) { readDeviceDetails(context) }
}

private fun readStorageSnapshot(): StorageSnapshot? = runCatching {
    val stat = StatFs(Environment.getExternalStorageDirectory().absolutePath)
    StorageSnapshot(availableBytes = stat.availableBytes, totalBytes = stat.totalBytes)
}.getOrNull()

private fun knownDeviceValue(value: String?): String? {
    val trimmed = value?.trim().orEmpty()
    return trimmed.takeIf {
        it.isNotEmpty() && !it.equals("unknown", ignoreCase = true)
    }
}

private fun readCpuHardware(): String? = runCatching {
    File("/proc/cpuinfo").useLines { lines ->
        lines.mapNotNull { line ->
            val key = line.substringBefore(':').trim()
            if (key.equals("Hardware", ignoreCase = true) ||
                key.equals("model name", ignoreCase = true)
            ) {
                line.substringAfter(':').trim().takeIf { it.isNotEmpty() }
            } else {
                null
            }
        }.firstOrNull()
    }
}.getOrNull()

private fun readDeviceDetails(context: Context): DeviceDetails {
    val memory = runCatching {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        manager.getMemoryInfo(info)
        info.totalMem
    }.getOrDefault(0L)
    val soc = listOfNotNull(
        knownDeviceValue(Build.SOC_MANUFACTURER),
        knownDeviceValue(Build.SOC_MODEL),
    ).joinToString(" ")
    val processor = soc.ifBlank {
        readCpuHardware()
            ?: knownDeviceValue(Build.HARDWARE)
                ?.takeUnless {
                    it.equals(Build.MANUFACTURER, ignoreCase = true) ||
                        it.equals(Build.BRAND, ignoreCase = true)
                }
            ?: "未知"
    }
    val certifiedModel = sequenceOf(Build.DEVICE, Build.PRODUCT)
        .map { it.orEmpty().trim() }
        .firstOrNull { it.isNotEmpty() }
        .orEmpty()
    val kernel = System.getProperty("os.version")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: "未知"
    return DeviceDetails(
        version = "v${BuildConfig.VERSION_NAME}",
        model = deviceModelLabel(Build.MANUFACTURER, Build.MODEL),
        certifiedModel = certifiedModel.ifBlank { "未知" },
        processor = processor,
        memory = if (memory > 0L) homeFormatBytes(memory) else "未知",
        androidVersion = Build.VERSION.RELEASE?.trim()?.takeIf { it.isNotEmpty() }
            ?.let { "Android $it" }
            ?: "Android",
        kernel = kernel,
        baseband = Build.getRadioVersion()?.trim()?.takeIf { it.isNotEmpty() } ?: "未知",
        securityPatch = Build.VERSION.SECURITY_PATCH?.trim()?.takeIf { it.isNotEmpty() } ?: "未知",
    )
}

@Composable
internal fun CedarHomeHeader(
    height: Dp,
    backdrop: Backdrop,
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val collapseDistancePx = with(density) { 72.dp.toPx() }
    val target = (scrollState.value / collapseDistancePx).coerceIn(0f, 1f)
    val collapse by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 100),
        label = "home title flip",
    )
    val outProgress = (collapse / 0.5f).coerceIn(0f, 1f)
    val inProgress = ((collapse - 0.5f) / 0.5f).coerceIn(0f, 1f)
    val frontVisible = collapse < 0.5f
    val cameraDistance = 200f * density.density
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clipToBounds(),
    ) {
        Box(
            Modifier
                .matchParentSize()
                .drawPlainBackdrop(
                    backdrop = backdrop,
                    shape = { RectangleShape },
                    effects = { verticalGradientBlur() },
                ),
        )
        Text(
            "CedarManager",
            fontSize = 20.sp,
            lineHeight = 24.sp,
            fontFamily = CedarFontFamily,
            fontWeight = FontWeight.Light,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = height - 80.dp + 16.dp)
                .graphicsLayer {
                    rotationX = 90f * (1f - inProgress)
                    alpha = if (collapse < 0.5f) 0f else 1f
                    this.cameraDistance = cameraDistance
                    transformOrigin = TransformOrigin(0.5f, 1.6f)
                },
        )
        Text(
            "CedarManager",
            fontSize = 32.sp,
            lineHeight = 36.sp,
            fontFamily = CedarFontFamily,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 28.dp, end = 20.dp, bottom = 8.dp)
                .graphicsLayer {
                    rotationX = 90f * outProgress
                    alpha = if (frontVisible) 1f else 0f
                    this.cameraDistance = cameraDistance
                    transformOrigin = TransformOrigin(0.5f, 1.6f)
                },
        )
    }
}

@Composable
internal fun HomeStorageCard(
    storage: StorageSnapshot?,
    loading: Boolean,
    modifier: Modifier,
) {
    val usage = storage?.usage ?: 0f
    val percent = storage?.percent ?: 0
    val animatedUsage by animateFloatAsState(
        targetValue = usage,
        animationSpec = tween(durationMillis = 600),
        label = "home storage usage",
    )
    val track = MiuixTheme.colorScheme.outline.copy(alpha = 0.18f)
    val accent = MiuixTheme.colorScheme.primary
    HomeCard(
        modifier = modifier,
        containerColor = MiuixTheme.colorScheme.primary.copy(alpha = 0.08f),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val ringSize = (maxWidth * 0.86f)
                .coerceAtMost(maxHeight)
                .coerceIn(120.dp, 210.dp)
            val ringOffset = ringSize * 0.30f
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = ringOffset, y = ringOffset)
                    .size(ringSize)
                    .semantics {
                        progressBarRangeInfo = ProgressBarRangeInfo(usage, 0f..1f)
                        contentDescription = if (storage == null) {
                            "存储空间不可用"
                        } else {
                            "存储使用 $percent%"
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val strokeWidth = 13.dp.toPx()
                    val inset = strokeWidth / 2f
                    val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
                    drawArc(
                        color = track,
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    )
                    if (animatedUsage > 0f) {
                        drawArc(
                            color = accent,
                            startAngle = -90f,
                            sweepAngle = animatedUsage * 360f,
                            useCenter = false,
                            topLeft = Offset(inset, inset),
                            size = arcSize,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                        )
                    }
                }
                Text(
                    if (storage == null) "--" else "$percent%",
                    fontSize = if (ringSize >= 180.dp) 34.sp else 29.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    maxLines = 1,
                )
            }
            Column(
                Modifier.align(Alignment.TopStart),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    "存储空间",
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                    maxLines = 1,
                )
                Text(
                    when {
                        storage == null && loading -> "读取中"
                        storage == null -> "存储暂不可用"
                        else -> homeFormatBytes(storage.availableBytes)
                    },
                    fontSize = 21.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (storage != null) {
                    Text(
                        "已用 ${homeFormatBytes(storage.usedBytes)} / 共 ${homeFormatBytes(storage.totalBytes)}",
                        fontSize = 10.sp,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
internal fun HomeStatusMiniCard(
    icon: String,
    label: String,
    value: String,
    accent: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier,
) {
    val accentColor = if (accent) {
        MiuixTheme.colorScheme.primary
    } else {
        MiuixTheme.colorScheme.onSurfaceSecondary
    }
    val clickModifier = if (onClick != null) {
        Modifier
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "$label $value" }
    } else {
        Modifier
    }
    HomeCard(modifier.then(clickModifier)) {
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CedarSvgIcon(icon, label, tint = accentColor, size = 18.dp)
                Spacer(Modifier.width(7.dp))
                Text(
                    label,
                    fontSize = 11.sp,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                    maxLines = 1,
                )
            }
            Text(
                value,
                fontSize = 21.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (accent) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                maxLines = 1,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
internal fun HomeDeviceInfoCard(
    details: DeviceDetails,
    modifier: Modifier = Modifier,
) {
    HomeCard(
        modifier = modifier,
        contentPadding = PaddingValues(start = 20.dp, top = 10.dp, end = 14.dp, bottom = 10.dp),
    ) {
        DeviceInfoList(details = details)
    }
}

@Composable
private fun DeviceInfoList(details: DeviceDetails) {
    val fields = listOf(
        DeviceField("版本号", details.version),
        DeviceField("机型", details.model),
        DeviceField("设备认证型号", details.certifiedModel),
        DeviceField("处理器", details.processor),
        DeviceField("运存", details.memory),
        DeviceField("安卓版本", details.androidVersion),
        DeviceField("内核版本", details.kernel),
        DeviceField("基带版本", details.baseband),
        DeviceField("安全补丁版本", details.securityPatch),
    )
    Column(Modifier.fillMaxWidth()) {
        fields.forEach { field ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    field.label,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                Text(
                    field.value,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
        }
    }
}

private data class DeviceField(
    val label: String,
    val value: String,
)

@Composable
private fun HomeCard(
    modifier: Modifier,
    containerColor: Color = MiuixTheme.colorScheme.surfaceContainer,
    contentPadding: PaddingValues = PaddingValues(14.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(CedarComponentTokens.GroupCorner))
            .background(containerColor)
            .padding(contentPadding),
        content = content,
    )
}

@Composable
internal fun HomeTerminalEntry(
    onOpenTerminal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(CedarComponentTokens.GroupCorner))
            .background(MiuixTheme.colorScheme.surfaceContainer)
            .clickable(role = Role.Button, onClick = onOpenTerminal)
            .semantics { contentDescription = "打开终端" }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CedarSvgIcon(
            "terminal",
            "终端",
            tint = MiuixTheme.colorScheme.onSurface,
            size = 21.dp,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            "终端",
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        Spacer(Modifier.weight(1f))
        CedarSvgIcon(
            "chevron",
            "进入终端",
            tint = MiuixTheme.colorScheme.onSurfaceSecondary,
            size = 18.dp,
        )
    }
}

internal fun rootAvailability(state: RootState): String = when (state) {
    RootState.CHECKING -> "检测中"
    RootState.GRANTED -> "可用"
    RootState.DENIED, RootState.UNAVAILABLE -> "不可用"
}

internal fun deviceModelLabel(manufacturer: String, model: String): String {
    val maker = manufacturer.trim().let { value ->
        if (value.isEmpty()) value else value[0].uppercaseChar() + value.substring(1)
    }
    val modelName = model.trim()
    return when {
        maker.isEmpty() -> modelName.ifBlank { "Android 设备" }
        modelName.isEmpty() -> maker
        modelName.startsWith(maker, ignoreCase = true) -> modelName
        else -> "$maker $modelName"
    }
}

internal fun homeFormatBytes(value: Long): String {
    if (value < 1024L) return "$value B"
    val units = arrayOf("KiB", "MiB", "GiB", "TiB")
    var size = value.toDouble()
    var index = -1
    do {
        size /= 1024.0
        index++
    } while (size >= 1024 && index < units.lastIndex)
    return String.format(Locale.US, "%.1f %s", size, units[index])
}

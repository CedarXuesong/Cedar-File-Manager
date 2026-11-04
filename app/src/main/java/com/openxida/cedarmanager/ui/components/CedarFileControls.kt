package com.openxida.cedarmanager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openxida.cedarmanager.domain.FileEntry
import com.openxida.cedarmanager.domain.PermissionSpec
import com.openxida.cedarmanager.ui.CedarFontFamily
import com.openxida.cedarmanager.ui.CedarSvgIcon
import com.openxida.cedarmanager.ui.effects.verticalGradientBlur
import java.text.DateFormat
import java.util.Date
import top.yukonga.miuix.kmp.basic.Text
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawPlainBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Active-pane path bar used above the dual file panes. */
@Composable
fun CedarFilePathBar(
    path: String,
    height: Dp,
    topInset: Dp,
    backdrop: Backdrop,
    onPathClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(height),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .drawPlainBackdrop(
                    backdrop = backdrop,
                    shape = { RectangleShape },
                    effects = { verticalGradientBlur() },
            ),
        )
        Box(
            Modifier
                .fillMaxSize()
                .padding(top = topInset),
        ) {
            Row(
                Modifier
                    .fillMaxSize()
                    .combinedClickable(onClick = onPathClick, onLongClick = onPathClick)
                    .padding(start = 16.dp, end = 66.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = path,
                    fontFamily = CedarFontFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** Kept separate from the path bar so popup shadows can render above the blurred toolbar. */
@Composable
fun CedarFileMoreButton(
    backdrop: Backdrop,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CedarLiquidGlassIconButton(
        icon = "more",
        description = "更多工具",
        backdrop = backdrop,
        modifier = modifier,
        onClick = onClick,
    )
}

/** Compact parent-directory row used by both file panes. */
@Composable
fun ParentFolderRow(onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = CedarComponentTokens.FileRowMinHeight)
            .combinedClickable(onClick = onClick, onLongClick = onClick)
            .padding(
                horizontal = CedarComponentTokens.FileRowHorizontalPadding,
                vertical = CedarComponentTokens.FileRowVerticalPadding,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CedarSvgIcon(
            name = "folder",
            contentDescription = "返回上级目录",
            tint = MiuixTheme.colorScheme.primary,
            size = CedarComponentTokens.FileIconSize,
            withBackground = true,
        )
        Spacer(Modifier.width(10.dp))
        Text("...", fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

/** A single file item. Keep this API data-only so it can be reused by search/results views. */
@Composable
fun FileRow(
    entry: FileEntry,
    selected: Boolean,
    dateFormatter: DateFormat,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onSwipeRight: () -> Unit,
) {
    val icon = when {
        entry.isDirectory -> "folder"
        entry.isArchive -> "archive"
        entry.isApk -> "apk"
        entry.isText -> "code"
        else -> "file"
    }
    val detail = remember(entry, dateFormatter) {
        if (entry.isDirectory) {
            "${entry.mode.toString(8).padStart(3, '0')} · ${dateFormatter.format(Date(entry.modifiedAt))}"
        } else {
            "${formatBytes(entry.size)} · ${entry.mode.toString(8).padStart(3, '0')}"
        }
    }
    val swipeThreshold = with(LocalDensity.current) { 28.dp.toPx() }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = CedarComponentTokens.FileRowMinHeight)
            .background(if (selected) MiuixTheme.colorScheme.tertiaryContainer else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .pointerInput(entry.path, swipeThreshold) {
                awaitEachGesture {
                    val down = awaitFirstDown(
                        requireUnconsumed = false,
                        pass = PointerEventPass.Initial,
                    )
                    val pointerId = down.id
                    var draggedX = 0f
                    var draggedY = 0f
                    var selectedByGesture = false
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                        if (!change.pressed) break

                        draggedX += change.position.x - change.previousPosition.x
                        draggedY += change.position.y - change.previousPosition.y
                        if (!selectedByGesture &&
                            kotlin.math.abs(draggedX) >= swipeThreshold &&
                            kotlin.math.abs(draggedX) > kotlin.math.abs(draggedY)
                        ) {
                            if (draggedX <= 0f) return@awaitEachGesture
                            selectedByGesture = true
                            change.consume()
                            onSwipeRight()
                        } else if (selectedByGesture) {
                            change.consume()
                        }
                    }
                }
            }
            .padding(
                horizontal = CedarComponentTokens.FileRowHorizontalPadding,
                vertical = CedarComponentTokens.FileRowVerticalPadding,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CedarSvgIcon(
            name = if (selected) "check" else icon,
            contentDescription = when {
                entry.isDirectory -> "目录"
                entry.isArchive -> "压缩包"
                else -> "文件"
            },
            tint = if (selected) MiuixTheme.colorScheme.primary else fileIconColor(entry),
            size = CedarComponentTokens.FileIconSize,
            withBackground = true,
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = entry.name,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = detail,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
            )
        }
    }
}

@Composable
fun CedarPermissionGrid(spec: PermissionSpec, onChange: (PermissionSpec) -> Unit) {
    val groups = listOf(
        "所有者" to listOf(0x100, 0x80, 0x40),
        "用户组" to listOf(0x20, 0x10, 0x8),
        "其他" to listOf(0x4, 0x2, 0x1),
    )
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
    ) {
        groups.forEach { (label, masks) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(label, fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurfaceSecondary)
                Row {
                    masks.zip(listOf("R", "W", "X")).forEach { (mask, text) ->
                        Text(
                            text,
                            color = if (spec.has(mask)) {
                                MiuixTheme.colorScheme.onPrimary
                            } else {
                                MiuixTheme.colorScheme.onSurfaceSecondary
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            modifier = Modifier
                                .padding(2.dp)
                                .clip(CircleShape)
                                .background(
                                    if (spec.has(mask)) MiuixTheme.colorScheme.primary
                                    else MiuixTheme.colorScheme.secondaryContainer,
                                )
                                .combinedClickable(
                                    onClick = { onChange(spec.toggle(mask)) },
                                    onLongClick = { onChange(spec.toggle(mask)) },
                                )
                                .padding(8.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun fileIconColor(entry: FileEntry): Color = when {
    entry.isDirectory -> Color(0xFF2563EB)
    entry.isApk -> Color(0xFF18794E)
    entry.isText -> Color(0xFF855400)
    else -> MiuixTheme.colorScheme.onSurfaceSecondary
}


private fun formatBytes(value: Long): String {
    if (value < 1024) return "$value B"
    val units = arrayOf("KiB", "MiB", "GiB", "TiB")
    var size = value.toDouble()
    var index = -1
    do {
        size /= 1024.0
        index++
    } while (size >= 1024 && index < units.lastIndex)
    return "%.1f %s".format(size, units[index])
}

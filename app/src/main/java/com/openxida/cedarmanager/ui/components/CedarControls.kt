package com.openxida.cedarmanager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openxida.cedarmanager.ui.CedarSvgIcon
import com.openxida.cedarmanager.ui.effects.LiquidGlassCircleButton
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.DropdownArrowEndAction
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.DropdownItem
import com.kyant.backdrop.Backdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Shared compact icon action used by toolbars, dialogs and operation feedback. */
@Composable
fun CedarIconButton(
    icon: String,
    description: String,
    compact: Boolean = false,
    enabled: Boolean = true,
    danger: Boolean = false,
    tint: Color = Color.Unspecified,
    onClick: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val buttonSize = if (compact) CedarComponentTokens.CompactIconButtonSize else CedarComponentTokens.IconButtonSize
    Box(
        Modifier
            .size(buttonSize)
            .clip(CircleShape)
            .combinedClickable(
                enabled = enabled,
                onClick = onClick,
                onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
            ),
        contentAlignment = Alignment.Center,
    ) {
        CedarSvgIcon(
            name = icon,
            contentDescription = description,
            tint = when {
                !enabled -> MiuixTheme.colorScheme.disabledOnSurface
                tint != Color.Unspecified -> tint
                danger -> MiuixTheme.colorScheme.error
                else -> MiuixTheme.colorScheme.onSurface
            },
            size = if (compact) CedarComponentTokens.CompactIconButtonIconSize else CedarComponentTokens.IconButtonIconSize,
        )
    }
}

/** Liquid-glass icon action used where the control sits over a live backdrop. */
@Composable
fun CedarLiquidGlassIconButton(
    icon: String,
    description: String,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    LiquidGlassCircleButton(
        backdrop = backdrop,
        modifier = modifier,
        enabled = enabled,
        onClick = onClick,
    ) {
        CedarSvgIcon(
            name = icon,
            contentDescription = description,
            tint = if (enabled) MiuixTheme.colorScheme.onSurface else MiuixTheme.colorScheme.disabledOnSurface,
            size = 19.dp,
        )
    }
}

@Composable
fun CedarMenuItem(
    icon: String,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CedarSvgIcon(
            name = icon,
            contentDescription = label,
            tint = if (enabled) MiuixTheme.colorScheme.onSurfaceSecondary else MiuixTheme.colorScheme.disabledOnSurface,
            size = 20.dp,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            fontSize = 14.sp,
            color = if (enabled) MiuixTheme.colorScheme.onSurface else MiuixTheme.colorScheme.disabledOnSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun CedarChoiceMenuItem(
    label: String,
    selected: Boolean,
    index: Int,
    optionSize: Int,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    DropdownImpl(
        text = label,
        optionSize = optionSize,
        isSelected = selected,
        index = index,
        enabled = enabled,
        dropdownColors = DropdownDefaults.dropdownColors(
            containerColor = Color.Transparent,
            selectedContainerColor = Color.Transparent,
        ),
        onSelectedIndexChange = { onClick() },
    )
}

/** Clickable Miuix setting row that reports its root position for an overlay menu. */
@Composable
fun CedarDropdownSetting(
    title: String,
    value: String,
    expanded: Boolean,
    onClick: () -> Unit,
    onPositioned: (LayoutCoordinates) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .onGloballyPositioned { coordinates ->
                onPositioned(coordinates)
            }
            .combinedClickable(onClick = onClick, onLongClick = onClick)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            fontSize = CedarComponentTokens.BodyFontSize,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            value,
            modifier = Modifier.widthIn(max = 160.dp).padding(end = 8.dp),
            fontSize = CedarComponentTokens.DetailFontSize,
            color = MiuixTheme.colorScheme.onSurfaceVariantActions,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        DropdownArrowEndAction(actionColor = MiuixTheme.colorScheme.onSurfaceVariantActions)
    }
}

/** Miuix-style grouped surface shared by home and settings pages. */
@Composable
fun MiuixGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CedarComponentTokens.GroupCorner))
            .background(MiuixTheme.colorScheme.surfaceContainer)
            .padding(
                horizontal = CedarComponentTokens.GroupHorizontalPadding,
                vertical = CedarComponentTokens.GroupVerticalPadding,
            ),
        content = content,
    )
}

@Composable
fun SectionLabel(value: String) {
    Text(
        value,
        color = MiuixTheme.colorScheme.onSurfaceSecondary,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 14.dp, top = 8.dp, bottom = 1.dp),
    )
}

@Composable
fun CenterStatus(text: String, error: Boolean = false, action: (() -> Unit)? = null) {
    Column(
        Modifier
            .fillMaxHeight()
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text,
            fontSize = 12.sp,
            color = if (error) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.onSurfaceSecondary,
        )
        if (action != null) {
            top.yukonga.miuix.kmp.basic.TextButton(
                "重试",
                action,
                modifier = Modifier.padding(top = 5.dp),
            )
        }
    }
}

@Composable
fun KeyValue(key: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Text(key, fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurfaceSecondary)
        Text(
            value,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            maxLines = 3,
            overflow = TextOverflow.MiddleEllipsis,
        )
    }
}

@Composable
fun CompactStatusRow(
    icon: String,
    title: String,
    detail: String,
    accent: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .combinedClickable(onClick = onClick, onLongClick = onClick)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CedarSvgIcon(
            icon,
            title,
            tint = if (accent) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceSecondary,
            size = 24.dp,
        )
        Spacer(Modifier.width(12.dp))
        Text(title, fontSize = CedarComponentTokens.BodyFontSize, modifier = Modifier.weight(1f))
        Text(
            detail,
            fontSize = CedarComponentTokens.DetailFontSize,
            color = if (accent) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceSecondary,
        )
        CedarSvgIcon(
            "chevron",
            "打开",
            tint = MiuixTheme.colorScheme.onSurfaceSecondary,
            size = 16.dp,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

@Composable
fun CompactAction(
    icon: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .height(64.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MiuixTheme.colorScheme.surfaceContainer)
            .combinedClickable(onClick = onClick, onLongClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        CedarSvgIcon(icon, label, tint = MiuixTheme.colorScheme.primary, size = 24.dp)
        Spacer(Modifier.width(9.dp))
        Text(label, fontSize = CedarComponentTokens.BodyFontSize, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun StorageUsageBar(usage: Float, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("存储使用", fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceSecondary)
            Spacer(Modifier.weight(1f))
            Text("${(usage * 100).roundToInt()}%", fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceSecondary)
        }
        Box(
            Modifier
                .padding(top = 6.dp)
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MiuixTheme.colorScheme.surfaceContainer),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(usage.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(MiuixTheme.colorScheme.primary, RoundedCornerShape(3.dp)),
            )
        }
    }
}

@Composable
fun StatusPanel(icon: String, title: String, detail: String, accent: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MiuixTheme.colorScheme.surfaceContainer)
            .combinedClickable(onClick = onClick, onLongClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CedarSvgIcon(
            icon,
            title,
            tint = if (accent) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceSecondary,
            size = 26.dp,
        )
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(detail, fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceSecondary)
        }
    }
}

@Composable
fun QuickAction(
    icon: String,
    title: String,
    detail: String,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MiuixTheme.colorScheme.surfaceContainer)
            .combinedClickable(onClick = onClick, onLongClick = onClick)
            .padding(15.dp),
    ) {
        CedarSvgIcon(icon, title, tint = MiuixTheme.colorScheme.primary, size = 27.dp)
        Spacer(Modifier.height(15.dp))
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text(
            detail,
            fontSize = 10.sp,
            color = MiuixTheme.colorScheme.onSurfaceSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun PlainRow(title: String, detail: String, icon: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CedarSvgIcon(icon, title, tint = MiuixTheme.colorScheme.onSurfaceSecondary, size = 24.dp)
        Spacer(Modifier.width(12.dp))
        Text(title, fontSize = CedarComponentTokens.BodyFontSize, modifier = Modifier.weight(1f))
        Text(
            detail,
            fontSize = CedarComponentTokens.DetailFontSize,
            color = MiuixTheme.colorScheme.onSurfaceSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun SwitchSetting(title: String, detail: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .combinedClickable(onClick = { onChange(!value) }, onLongClick = { onChange(!value) })
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, fontSize = CedarComponentTokens.BodyFontSize)
            Text(detail, fontSize = CedarComponentTokens.DetailFontSize, color = MiuixTheme.colorScheme.onSurfaceSecondary)
        }
        Switch(value, onChange)
    }
}

@Composable
fun ActionSetting(title: String, detail: String, icon: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 76.dp)
            .combinedClickable(onClick = onClick, onLongClick = onClick)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CedarSvgIcon(icon, title, tint = MiuixTheme.colorScheme.primary, size = 24.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = CedarComponentTokens.BodyFontSize)
            Text(
                detail,
                fontSize = CedarComponentTokens.DetailFontSize,
                color = MiuixTheme.colorScheme.onSurfaceSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun <T> SegmentedSetting(
    title: String,
    entries: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 12.dp)) {
        Text(title, fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            entries.forEach { entry ->
                SelectChip(label(entry), entry == selected, Modifier.weight(1f)) { onSelect(entry) }
            }
        }
    }
}

@Composable
fun SelectChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .height(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) MiuixTheme.colorScheme.primary.copy(alpha = 0.16f)
                else MiuixTheme.colorScheme.secondaryContainer,
            )
            .border(
                1.dp,
                if (selected) MiuixTheme.colorScheme.primary else Color.Transparent,
                RoundedCornerShape(12.dp),
            )
            .combinedClickable(onClick = onClick, onLongClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = 12.sp,
            color = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
        )
    }
}

@Composable
fun StepperSetting(title: String, value: Int, onChange: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, fontSize = 14.sp, modifier = Modifier.weight(1f))
        CedarIconButton("minus", "减小", onClick = { onChange(value - 1) })
        Text(
            "$value sp",
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            modifier = Modifier.width(52.dp),
        )
        CedarIconButton("plus", "增大", onClick = { onChange(value + 1) })
    }
}

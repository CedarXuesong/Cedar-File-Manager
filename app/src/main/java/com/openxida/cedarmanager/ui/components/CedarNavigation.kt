package com.openxida.cedarmanager.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openxida.cedarmanager.ui.CedarSvgIcon
import com.kyant.backdrop.catalog.components.LiquidBottomTab
import com.kyant.backdrop.catalog.components.LiquidBottomTabs
import top.yukonga.miuix.kmp.basic.Text
import com.kyant.backdrop.Backdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme

data class CedarNavigationItem(
    val label: String,
    val icon: String,
)

/** App-level liquid navigation dock with no knowledge of page or route types. */
@Composable
fun CedarNavigationDock(
    items: List<CedarNavigationItem>,
    selectedIndex: Int,
    dark: Boolean,
    backdrop: Backdrop,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    require(items.isNotEmpty()) { "CedarNavigationDock requires at least one item" }
    val selectedIndexState = rememberUpdatedState(selectedIndex.coerceIn(items.indices))
    val selectedTabIndex = remember { { selectedIndexState.value } }
    val dockModifier = modifier
        .padding(
            start = 28.dp,
            end = 28.dp,
            bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                .let { inset -> if (inset != 0.dp) 8.dp + inset else 28.dp },
        )
        .widthIn(
            min = CedarComponentTokens.NavigationDockMinWidth,
            max = CedarComponentTokens.NavigationDockMaxWidth,
        )
    val tabs: @Composable RowScope.() -> Unit = {
        items.forEachIndexed { index, item ->
            val selected = index == selectedIndex
            val contentColor = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface
            LiquidBottomTab(
                onClick = { onSelect(index) },
            ) {
                CedarSvgIcon(
                    name = item.icon,
                    contentDescription = item.label,
                    tint = contentColor,
                    size = 24.dp,
                )
                Text(
                    item.label,
                    color = contentColor,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Visible,
                )
            }
        }
    }
    LiquidBottomTabs(
        selectedTabIndex = selectedTabIndex,
        onTabSelected = onSelect,
        dark = dark,
        backdrop = backdrop,
        tabsCount = items.size,
        modifier = dockModifier,
        content = tabs,
    )
}

package com.openxida.cedarmanager.ui.components

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Cedar's component-level dimensions and typography.
 *
 * Keep product-wide Miuix tuning here. Feature-specific spacing belongs to the
 * feature, while reusable control sizing belongs in this object.
 */
object CedarComponentTokens {
    val GroupCorner = 24.dp
    val GroupHorizontalPadding = 16.dp
    val GroupVerticalPadding = 4.dp

    val FileRowMinHeight = 46.dp
    val FileRowHorizontalPadding = 12.dp
    val FileRowVerticalPadding = 2.dp
    val FileIconSize = 24.dp

    val IconButtonSize = 48.dp
    val CompactIconButtonSize = 36.dp
    val IconButtonIconSize = 21.dp
    val CompactIconButtonIconSize = 18.dp

    val NavigationDockMinWidth = 236.dp
    val NavigationDockMaxWidth = 280.dp

    val DialogCorner = 22.dp
    val DialogMaxWidth = 520.dp
    val DialogPadding = 20.dp

    val BodyFontSize = 17.sp
    val DetailFontSize = 14.sp
    val CaptionFontSize = 12.sp
}

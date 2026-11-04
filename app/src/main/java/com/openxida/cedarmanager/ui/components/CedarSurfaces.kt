package com.openxida.cedarmanager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.openxida.cedarmanager.ui.CedarFontFamily
import com.openxida.cedarmanager.ui.effects.verticalGradientBlur
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawPlainBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Shared full-width title surface with Cedar's vertical Gaussian blur. */
@Composable
fun CedarGradientTitleBar(
    title: String,
    height: Dp,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .drawPlainBackdrop(
                backdrop = backdrop,
                shape = { RectangleShape },
                effects = { verticalGradientBlur() },
            ),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Text(
            text = title,
            fontSize = 30.sp,
            lineHeight = 36.sp,
            fontFamily = CedarFontFamily,
            fontWeight = FontWeight.Normal,
            modifier = Modifier.padding(bottom = 20.dp),
            textAlign = TextAlign.Center,
        )
    }
}

/** Standard dialog surface. Dialog contents remain owned by the feature using it. */
@Composable
fun CedarDialogSurface(
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .widthIn(max = CedarComponentTokens.DialogMaxWidth)
                .clip(RoundedCornerShape(CedarComponentTokens.DialogCorner))
                .background(MiuixTheme.colorScheme.surfaceContainer)
                .padding(CedarComponentTokens.DialogPadding),
            content = content,
        )
    }
}

@Composable
fun CedarDialogActions(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    confirmLabel: String,
    enabled: Boolean,
    destructive: Boolean = false,
) {
    Row(
        Modifier.fillMaxWidth().padding(top = 18.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        TextButton("取消", onDismiss)
        Spacer(Modifier.width(8.dp))
        Button(
            onClick = onConfirm,
            enabled = enabled,
            colors = if (destructive) {
                top.yukonga.miuix.kmp.basic.ButtonDefaults.buttonColors(color = MiuixTheme.colorScheme.error)
            } else {
                top.yukonga.miuix.kmp.basic.ButtonDefaults.buttonColors()
            },
        ) {
            Text(confirmLabel)
        }
    }
}


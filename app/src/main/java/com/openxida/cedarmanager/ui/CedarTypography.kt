package com.openxida.cedarmanager.ui

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.openxida.cedarmanager.R
import top.yukonga.miuix.kmp.theme.defaultTextStyles

internal val CedarFontFamily = FontFamily(
    Font(R.font.misans_regular, FontWeight.Normal),
    Font(R.font.misans_medium, FontWeight.Medium),
    Font(R.font.misans_semibold, FontWeight.SemiBold),
    Font(R.font.misans_bold, FontWeight.Bold),
)

private fun TextStyle.withMiSans() = copy(fontFamily = CedarFontFamily)

internal val CedarTextStyles = defaultTextStyles().let { styles ->
    styles.copy(
        main = styles.main.withMiSans(),
        paragraph = styles.paragraph.withMiSans(),
        body1 = styles.body1.withMiSans(),
        body2 = styles.body2.withMiSans(),
        button = styles.button.withMiSans(),
        footnote1 = styles.footnote1.withMiSans(),
        footnote2 = styles.footnote2.withMiSans(),
        headline1 = styles.headline1.withMiSans(),
        headline2 = styles.headline2.withMiSans(),
        subtitle = styles.subtitle.withMiSans(),
        title1 = styles.title1.withMiSans(),
        title2 = styles.title2.withMiSans(),
        title3 = styles.title3.withMiSans(),
        title4 = styles.title4.withMiSans(),
    )
}

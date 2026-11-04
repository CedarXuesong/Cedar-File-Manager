package com.openxida.cedarmanager.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openxida.cedarmanager.domain.AppSettings
import com.openxida.cedarmanager.ui.components.ActionSetting
import com.openxida.cedarmanager.ui.components.CedarComponentTokens
import com.openxida.cedarmanager.ui.components.CedarIconButton
import com.openxida.cedarmanager.ui.components.MiuixGroup
import com.openxida.cedarmanager.ui.components.SectionLabel
import java.util.Locale
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

fun formatAnimationSpeed(value: Float): String =
    String.format(Locale.US, "%.2f", value.coerceIn(AppSettings.MIN_ANIMATION_SPEED, AppSettings.MAX_ANIMATION_SPEED))

fun formatTiltStrength(value: Float): String =
    String.format(Locale.US, "%.2f", value.coerceIn(AppSettings.MIN_TILT_STRENGTH, AppSettings.MAX_TILT_STRENGTH))

/**
 * Second-level settings page used to tune app-wide debug behavior. It is intentionally
 * reachable only from the Settings tab and is not part of the normal user-facing flow.
 */
@Composable
fun CedarDebugSettingsScreen(
    animationSpeed: Float,
    onAnimationSpeed: (Float) -> Unit,
    tiltStrength: Float,
    onTiltStrength: (Float) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val previewProgress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    Column(
        Modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.surface)
            .padding(top = insets.calculateTopPadding()),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CedarIconButton("back", "返回", onClick = onBack)
            Spacer(Modifier.width(4.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "调试",
                    fontFamily = CedarFontFamily,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Normal,
                )
                Text(
                    "开发者选项",
                    fontSize = 11.sp,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                )
            }
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SectionLabel("动画")
            MiuixGroup {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("全局动画速度", fontSize = CedarComponentTokens.BodyFontSize)
                            Text(
                                "影响所有界面动画",
                                fontSize = CedarComponentTokens.DetailFontSize,
                                color = MiuixTheme.colorScheme.onSurfaceSecondary,
                            )
                        }
                        Text(
                            "×${formatAnimationSpeed(animationSpeed)}",
                            fontFamily = CedarFontFamily,
                            fontSize = 18.sp,
                            color = MiuixTheme.colorScheme.primary,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Slider(
                        value = animationSpeed,
                        onValueChange = onAnimationSpeed,
                        modifier = Modifier.fillMaxWidth(),
                        valueRange = AppSettings.MIN_ANIMATION_SPEED..AppSettings.MAX_ANIMATION_SPEED,
                        steps = 14,
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("0.5× 慢", fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurfaceSecondary)
                        Text("2.0× 快", fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurfaceSecondary)
                    }
                }
            }

            MiuixGroup {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("弹窗倾斜幅度", fontSize = CedarComponentTokens.BodyFontSize)
                            Text(
                                "对角内倾的旋转角度",
                                fontSize = CedarComponentTokens.DetailFontSize,
                                color = MiuixTheme.colorScheme.onSurfaceSecondary,
                            )
                        }
                        Text(
                            "×${formatTiltStrength(tiltStrength)}",
                            fontFamily = CedarFontFamily,
                            fontSize = 18.sp,
                            color = MiuixTheme.colorScheme.primary,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Slider(
                        value = tiltStrength,
                        onValueChange = onTiltStrength,
                        modifier = Modifier.fillMaxWidth(),
                        valueRange = AppSettings.MIN_TILT_STRENGTH..AppSettings.MAX_TILT_STRENGTH,
                        steps = 14,
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("0.5× 轻", fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurfaceSecondary)
                        Text("2.0× 强", fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurfaceSecondary)
                    }
                }
            }

            MiuixGroup {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 12.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 44.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("动画预览", fontSize = CedarComponentTokens.BodyFontSize)
                            Text(
                                "点击后观察当前速度",
                                fontSize = CedarComponentTokens.DetailFontSize,
                                color = MiuixTheme.colorScheme.onSurfaceSecondary,
                            )
                        }
                        TextButton(
                            "播放动画",
                            {
                                scope.launch {
                                    previewProgress.snapTo(0f)
                                    previewProgress.animateTo(
                                        1f,
                                        tween(durationMillis = 900, easing = FastOutSlowInEasing),
                                    )
                                }
                            },
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(CircleShape)
                            .background(MiuixTheme.colorScheme.outline.copy(alpha = 0.3f)),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(previewProgress.value.coerceIn(0f, 1f))
                                .fillMaxHeight()
                                .clip(CircleShape)
                                .background(MiuixTheme.colorScheme.primary),
                        )
                    }
                }
            }

            MiuixGroup {
                ActionSetting(
                    "恢复默认",
                    "动画速度 ×1.00 · 倾斜幅度 ×1.00",
                    "refresh",
                ) {
                    onAnimationSpeed(1f)
                    onTiltStrength(1f)
                }
            }

            Text(
                "动画速度影响全局时长；倾斜幅度只影响弹窗入场时的对角内倾。两项设置都会保存，数值越大效果越强。",
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceSecondary,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
            )
            Spacer(Modifier.height(insets.calculateBottomPadding() + 16.dp))
        }
    }
}

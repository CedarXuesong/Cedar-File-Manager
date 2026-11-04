package com.openxida.cedarmanager.ui.effects

import androidx.compose.ui.unit.dp
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.runtimeShaderEffect

// Mirrors the ProgressiveBlurContent effect in AndroidLiquidGlass.
fun BackdropEffectScope.verticalGradientBlur() {
    blur(4.dp.toPx())
    runtimeShaderEffect(
        key = "CedarProgressiveBlurAlphaMask",
        shaderString = ALPHA_MASK_SHADER,
        uniformShaderName = "content",
    ) {
        setFloatUniform("size", size.width, size.height)
    }
}

private const val ALPHA_MASK_SHADER = """
uniform shader content;
uniform float2 size;

half4 main(float2 coord) {
    float blurAlpha = smoothstep(size.y, size.y * 0.5, coord.y);
    return content.eval(coord) * blurAlpha;
}
"""

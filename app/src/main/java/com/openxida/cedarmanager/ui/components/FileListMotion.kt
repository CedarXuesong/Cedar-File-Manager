package com.openxida.cedarmanager.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer

internal fun shouldStartFileReveal(hasEntries: Boolean, finished: Boolean): Boolean =
    hasEntries && !finished

@Composable
internal fun FileRevealItem(
    progress: Animatable<Float, AnimationVector1D>,
    finished: Boolean,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .then(
                if (finished) Modifier else Modifier.graphicsLayer {
                    alpha = progress.value
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                },
            ),
    ) {
        content()
    }
}

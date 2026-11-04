package com.openxida.cedarmanager.ui

import android.view.View
import androidx.compose.runtime.Recomposer
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.platform.WindowRecomposerFactory
import androidx.compose.ui.platform.WindowRecomposerPolicy
import androidx.compose.ui.platform.createLifecycleAwareWindowRecomposer
import androidx.lifecycle.findViewTreeLifecycleOwner
import com.openxida.cedarmanager.domain.AppSettings

/** Converts a user-facing speed multiplier into Compose's duration scale. */
fun animationDurationScale(speed: Float): Float =
    1f / speed.coerceIn(AppSettings.MIN_ANIMATION_SPEED, AppSettings.MAX_ANIMATION_SPEED)

/** Applies a Compose duration scale to a manually clocked animation. */
fun scaledAnimationMillis(baseMillis: Int, durationScale: Float): Float =
    (baseMillis * durationScale).coerceAtLeast(1f)

private class ScaledMotionDurationScale(
    private val speed: () -> Float,
) : MotionDurationScale {
    override val scaleFactor: Float
        get() = animationDurationScale(speed())
}

/**
 * Installs a window recomposer that supplies a process-wide [MotionDurationScale].
 *
 * Compose reads the duration scale from the recomposer's coroutine context, so replacing
 * the window recomposer factory scales every Compose animation in the app, including
 * animations owned by third-party components. Must be called before `setContent`.
 */
@OptIn(InternalComposeUiApi::class)
fun installAnimationSpeedRecomposerFactory(speed: () -> Float) {
    WindowRecomposerPolicy.setFactory(
        object : WindowRecomposerFactory {
            override fun createRecomposer(windowRootView: View): Recomposer {
                val lifecycle = windowRootView.findViewTreeLifecycleOwner()?.lifecycle
                if (lifecycle == null) {
                    return WindowRecomposerFactory.LifecycleAware.createRecomposer(windowRootView)
                }
                return windowRootView.createLifecycleAwareWindowRecomposer(
                    ScaledMotionDurationScale(speed),
                    lifecycle,
                )
            }
        },
    )
}

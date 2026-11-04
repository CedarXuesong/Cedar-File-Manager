package com.openxida.cedarmanager

import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Rect
import android.os.SystemClock
import android.util.Log
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LauncherPopupBoundsProbeTest {
    @Test
    fun captureAnimationSpeedPopupBounds() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }

        val target = waitForNode(automation, 3_000) {
            it.contentDescription?.toString()?.startsWith("动画速率\n") == true
        } ?: error("Animation speed setting is not visible")
        Log.i(TAG, "TRIGGER ${target.bounds().csv()}")
        val openStart = SystemClock.elapsedRealtimeNanos()
        check(target.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        sample(automation, "open", openStart)

        val downTime = SystemClock.uptimeMillis()
        for (action in intArrayOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, 200f, 1400f, 0)
            automation.injectInputEvent(event, true)
            event.recycle()
        }
        val closeStart = SystemClock.elapsedRealtimeNanos()
        sample(automation, "close", closeStart)
    }

    private fun sample(
        automation: android.app.UiAutomation,
        phase: String,
        startNanos: Long,
    ) {
        val end = startNanos + 450_000_000L
        var previous = ""
        var sequence = 0
        while (SystemClock.elapsedRealtimeNanos() < end) {
            val windows = automation.windows
            val popup = windows.firstNotNullOfOrNull { window ->
                val first = window.root?.findFirst { it.contentDescription?.toString() == "优雅" }
                    ?: return@firstNotNullOfOrNull null
                val middle = first.parent?.getChild(1) ?: return@firstNotNullOfOrNull null
                val last = first.parent?.getChild(2) ?: return@firstNotNullOfOrNull null
                val container = first.parent ?: return@firstNotNullOfOrNull null
                "windowType=${window.type} window=${window.bounds().csv()} " +
                    "container=${container.bounds().csv()} first=${first.bounds().csv()} " +
                    "middle=${middle.bounds().csv()} last=${last.bounds().csv()}"
            } ?: "absent"
            if (popup != previous || sequence % 8 == 0) {
                val millis = (SystemClock.elapsedRealtimeNanos() - startNanos) / 1_000_000.0
                Log.i(TAG, "SAMPLE phase=$phase seq=$sequence ms=$millis $popup")
                previous = popup
            }
            sequence++
            SystemClock.sleep(4)
        }
    }

    private fun waitForNode(
        automation: android.app.UiAutomation,
        timeoutMillis: Long,
        predicate: (AccessibilityNodeInfo) -> Boolean,
    ): AccessibilityNodeInfo? {
        val end = SystemClock.elapsedRealtime() + timeoutMillis
        while (SystemClock.elapsedRealtime() < end) {
            val node = automation.windows.firstNotNullOfOrNull { it.root?.findFirst(predicate) }
            if (node != null) return node
            SystemClock.sleep(20)
        }
        return null
    }

    private fun AccessibilityNodeInfo.findFirst(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (predicate(this)) return this
        for (index in 0 until childCount) {
            getChild(index)?.findFirst(predicate)?.let { return it }
        }
        return null
    }

    private fun AccessibilityNodeInfo.bounds() = Rect().also(::getBoundsInScreen)
    private fun AccessibilityWindowInfo.bounds() = Rect().also(::getBoundsInScreen)
    private fun Rect.csv() = "$left,$top,$right,$bottom"

    private companion object {
        const val TAG = "LauncherPopupBounds"
    }
}

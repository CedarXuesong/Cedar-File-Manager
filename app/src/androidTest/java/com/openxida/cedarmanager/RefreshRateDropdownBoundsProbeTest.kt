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
class RefreshRateDropdownBoundsProbeTest {
    @Test
    fun sampleRefreshRateDropdown() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }

        val spinner = waitFor(3_000) {
            automation.windows.firstNotNullOfOrNull { window ->
                window.root?.findAccessibilityNodeInfosByViewId(SPINNER_ID)?.firstOrNull()
            }
        } ?: error("Refresh-rate spinner not visible")
        Log.i(TAG, "TRIGGER ${spinner.bounds().csv()}")
        val clickable = generateSequence(spinner) { it.parent }.firstOrNull { it.isClickable }
            ?: error("Refresh-rate row not clickable")
        val openStart = SystemClock.elapsedRealtimeNanos()
        check(clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK))

        val popup = waitFor(2_000) {
            automation.windows.firstNotNullOfOrNull { window ->
                val container = window.root
                    ?.findAccessibilityNodeInfosByViewId(POPUP_ID)
                    ?.firstOrNull() ?: return@firstNotNullOfOrNull null
                Popup(window, container)
            }
        } ?: error("Dropdown animation container not found")
        sample("open", openStart, popup)

        val closeStart = SystemClock.elapsedRealtimeNanos()
        tapOutside(automation)
        sample("close", closeStart, popup)
    }

    private fun sample(phase: String, startNanos: Long, popup: Popup) {
        val end = startNanos + 500_000_000L
        var previousNanos = 0L
        var index = 0
        while (SystemClock.elapsedRealtimeNanos() < end) {
            val alive = popup.container.refresh()
            val now = SystemClock.elapsedRealtimeNanos()
            val delta = if (previousNanos == 0L) 0.0 else (now - previousNanos) / 1_000_000.0
            val bounds = if (alive) popup.container.bounds().csv() else "absent"
            Log.i(
                TAG,
                "SAMPLE phase=$phase seq=${index++} ms=${(now - startNanos) / 1_000_000.0} " +
                    "gapMs=$delta container=$bounds",
            )
            previousNanos = now
            if (!alive) break
            SystemClock.sleep(1)
        }
        Log.i(TAG, "WINDOW phase=$phase bounds=${popup.window.bounds().csv()}")
    }

    private fun tapOutside(automation: android.app.UiAutomation) {
        val down = SystemClock.uptimeMillis()
        for (action in intArrayOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, 200f, 1400f, 0)
            automation.injectInputEvent(event, true)
            event.recycle()
        }
    }

    private fun <T> waitFor(timeoutMillis: Long, block: () -> T?): T? {
        val end = SystemClock.elapsedRealtime() + timeoutMillis
        while (SystemClock.elapsedRealtime() < end) {
            block()?.let { return it }
            SystemClock.sleep(4)
        }
        return null
    }

    private fun AccessibilityNodeInfo.bounds() = Rect().also(::getBoundsInScreen)
    private fun AccessibilityWindowInfo.bounds() = Rect().also(::getBoundsInScreen)
    private fun Rect.csv() = "$left,$top,$right,$bottom"

    private data class Popup(
        val window: AccessibilityWindowInfo,
        val container: AccessibilityNodeInfo,
    )

    private companion object {
        const val TAG = "RefreshDropdownBounds"
        const val SPINNER_ID = "com.xiaomi.misettings:id/spinner"
        const val POPUP_ID = "com.xiaomi.misettings:id/miuix_popupwidget_popup_anim_container"
    }
}

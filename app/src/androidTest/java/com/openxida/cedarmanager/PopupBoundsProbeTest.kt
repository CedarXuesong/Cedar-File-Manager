package com.openxida.cedarmanager

import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Rect
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PopupBoundsProbeTest {
    @Test
    fun captureStatusBarBatteryPopupBounds() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }

        automation.waitForIdle(100, 2_000)
        val targetDeadline = SystemClock.elapsedRealtime() + 2_000
        var target: AccessibilityNodeInfo? = null
        while (target == null && SystemClock.elapsedRealtime() < targetDeadline) {
            target = automation.windows.firstNotNullOfOrNull { window ->
                window.root?.findFirst { it.text?.toString() == TARGET_LABEL }
            }
            if (target == null) SystemClock.sleep(20)
        }
        target ?: error("Target preference was not visible")
        val clickableTarget = target.firstClickableParent()
            ?: error("Target preference had no clickable parent")
        val targetBounds = clickableTarget.screenBounds()
        val existingWindowIds = automation.windows.mapTo(mutableSetOf()) { it.id }

        Log.i(TAG, "TARGET bounds=${targetBounds.csv()}")
        val startNanos = SystemClock.elapsedRealtimeNanos()
        check(clickableTarget.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            "Target preference rejected ACTION_CLICK"
        }

        val deadline = startNanos + SAMPLE_DURATION_NANOS
        var sequence = 0
        var popupNodes: PopupNodes? = null
        while (SystemClock.elapsedRealtimeNanos() < deadline) {
            if (popupNodes == null) {
                val windows = automation.windows
                val popupCandidates = (
                    windows.filter { it.id !in existingWindowIds } +
                        windows.filter { window ->
                        val bounds = window.screenBounds()
                        window.type == AccessibilityWindowInfo.TYPE_APPLICATION &&
                            bounds.left > 0 && bounds.top > 0
                    }
                    ).distinctBy { it.id }
                popupNodes = popupCandidates.firstNotNullOfOrNull(::findPopupNodes)
            } else {
                popupNodes.refresh()
            }
            val popup = popupNodes?.snapshot()
            val now = SystemClock.elapsedRealtimeNanos()
            Log.i(
                TAG,
                "SAMPLE seq=${sequence++} tNs=${now - startNanos} " +
                    if (popup == null) {
                        "present=0"
                    } else {
                        "present=1 windowType=${popup.windowType} " +
                            "window=${popup.window.csv()} container=${popup.container.csv()} " +
                            "first=${popup.first.csv()} middle=${popup.middle.csv()} last=${popup.last.csv()}"
                    },
            )
            SystemClock.sleep(SAMPLE_DELAY_MILLIS)
        }
    }

    private fun findPopupNodes(window: AccessibilityWindowInfo): PopupNodes? {
        val root = window.root ?: return null
        val container = root.findAccessibilityNodeInfosByViewId("android:id/list").firstOrNull()
            ?: return null
        if (container.childCount < 3) return null
        val first = container.getChild(0) ?: return null
        val middle = container.getChild(1) ?: return null
        val last = container.getChild(2) ?: return null

        return PopupNodes(
            windowType = window.type,
            root = root,
            container = container,
            first = first,
            middle = middle,
            last = last,
        )
    }

    private fun AccessibilityNodeInfo.findFirst(
        predicate: (AccessibilityNodeInfo) -> Boolean,
    ): AccessibilityNodeInfo? {
        if (predicate(this)) return this
        for (index in 0 until childCount) {
            val result = getChild(index)?.findFirst(predicate)
            if (result != null) return result
        }
        return null
    }

    private fun AccessibilityNodeInfo.firstClickableParent(): AccessibilityNodeInfo? {
        var node: AccessibilityNodeInfo? = this
        while (node != null && !node.isClickable) node = node.parent
        return node
    }

    private fun AccessibilityNodeInfo.screenBounds(): Rect = Rect().also(::getBoundsInScreen)

    private fun AccessibilityWindowInfo.screenBounds(): Rect = Rect().also(::getBoundsInScreen)

    private fun Rect.csv(): String = "$left,$top,$right,$bottom"

    private data class PopupSnapshot(
        val windowType: Int,
        val window: Rect,
        val container: Rect,
        val first: Rect,
        val middle: Rect,
        val last: Rect,
    )

    private data class PopupNodes(
        val windowType: Int,
        val root: AccessibilityNodeInfo,
        val container: AccessibilityNodeInfo,
        val first: AccessibilityNodeInfo,
        val middle: AccessibilityNodeInfo,
        val last: AccessibilityNodeInfo,
    ) {
        fun refresh() {
            container.refresh()
        }

        fun snapshot(): PopupSnapshot = PopupSnapshot(
            windowType = windowType,
            window = root.screenBounds(),
            container = container.screenBounds(),
            first = first.screenBounds(),
            middle = middle.screenBounds(),
            last = last.screenBounds(),
        )

        private fun AccessibilityNodeInfo.screenBounds(): Rect = Rect().also(::getBoundsInScreen)
    }

    private companion object {
        const val TAG = "PopupBoundsProbe"
        const val TARGET_LABEL = "状态栏电量样式"
        const val FIRST_OPTION = "不显示数字"
        const val MIDDLE_OPTION = "图标内显示数字"
        const val LAST_OPTION = "图标外显示数字"
        const val SAMPLE_DELAY_MILLIS = 4L
        const val SAMPLE_DURATION_NANOS = 1_200_000_000L
    }
}

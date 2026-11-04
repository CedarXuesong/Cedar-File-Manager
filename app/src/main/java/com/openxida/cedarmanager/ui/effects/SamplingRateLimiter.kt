package com.openxida.cedarmanager.ui.effects

internal class SamplingRateLimiter(maxFps: Int = 60, idleFps: Int = 1) {
    init {
        require(maxFps in 1..60 && idleFps in 1..maxFps)
    }

    private val activeIntervalNanos = 1_000_000_000L / maxFps
    private val idleIntervalNanos = 1_000_000_000L / idleFps
    private var lastSampleNanos = Long.MIN_VALUE

    fun shouldSample(nowNanos: Long, active: Boolean, dirty: Boolean, sizeChanged: Boolean): Boolean {
        if (lastSampleNanos == Long.MIN_VALUE || sizeChanged) return true
        val interval = if (active || dirty) activeIntervalNanos else idleIntervalNanos
        return nowNanos - lastSampleNanos >= interval
    }

    fun record(nowNanos: Long) {
        lastSampleNanos = nowNanos
    }

    fun nextDelayMillis(nowNanos: Long, active: Boolean, dirty: Boolean): Long {
        if (lastSampleNanos == Long.MIN_VALUE) return 1L
        val interval = if (active || dirty) activeIntervalNanos else idleIntervalNanos
        val remaining = (interval - (nowNanos - lastSampleNanos)).coerceAtLeast(1L)
        return (remaining + 999_999L) / 1_000_000L
    }
}

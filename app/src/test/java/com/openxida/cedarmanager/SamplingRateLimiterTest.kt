package com.openxida.cedarmanager

import com.openxida.cedarmanager.ui.effects.SamplingRateLimiter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SamplingRateLimiterTest {
    @Test
    fun idleSamplesOncePerSecond() {
        val limiter = SamplingRateLimiter()
        assertTrue(limiter.shouldSample(0L, active = false, dirty = true, sizeChanged = false))
        limiter.record(0L)
        assertFalse(limiter.shouldSample(999_999_999L, active = false, dirty = false, sizeChanged = false))
        assertTrue(limiter.shouldSample(1_000_000_000L, active = false, dirty = false, sizeChanged = false))
        assertEquals(1000L, limiter.nextDelayMillis(0L, active = false, dirty = false))
    }

    @Test
    fun movementImmediatelyRestoresSixtyHertzCadence() {
        val limiter = SamplingRateLimiter()
        limiter.record(0L)
        assertTrue(limiter.shouldSample(20_000_000L, active = false, dirty = true, sizeChanged = false))
        limiter.record(20_000_000L)
        assertFalse(limiter.shouldSample(28_000_000L, active = true, dirty = true, sizeChanged = false))
        assertEquals(9L, limiter.nextDelayMillis(28_000_000L, active = true, dirty = true))
        assertTrue(limiter.shouldSample(36_666_667L, active = true, dirty = true, sizeChanged = false))
    }

    @Test
    fun lateChangeIsDeferredRatherThanLostAndResizeIsImmediate() {
        val limiter = SamplingRateLimiter()
        limiter.record(1_000_000_000L)
        assertFalse(limiter.shouldSample(1_005_000_000L, active = false, dirty = true, sizeChanged = false))
        assertEquals(12L, limiter.nextDelayMillis(1_005_000_000L, active = false, dirty = true))
        assertTrue(limiter.shouldSample(1_016_666_667L, active = false, dirty = true, sizeChanged = false))
        limiter.record(1_016_666_667L)
        assertTrue(limiter.shouldSample(1_017_000_000L, active = false, dirty = false, sizeChanged = true))
    }
}

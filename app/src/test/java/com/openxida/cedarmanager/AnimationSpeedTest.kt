package com.openxida.cedarmanager

import com.openxida.cedarmanager.ui.animationDurationScale
import com.openxida.cedarmanager.ui.formatAnimationSpeed
import com.openxida.cedarmanager.ui.scaledAnimationMillis
import org.junit.Assert.assertEquals
import org.junit.Test

class AnimationSpeedTest {
    @Test
    fun speedMapsToComposeDurationScale() {
        assertEquals(1f, animationDurationScale(1f), 0.0001f)
        assertEquals(0.5f, animationDurationScale(2f), 0.0001f)
        assertEquals(2f, animationDurationScale(0.5f), 0.0001f)
    }

    @Test
    fun durationScaleIsClampedToSupportedRange() {
        assertEquals(2f, animationDurationScale(0f), 0.0001f)
        assertEquals(2f, animationDurationScale(-1f), 0.0001f)
        assertEquals(0.5f, animationDurationScale(10f), 0.0001f)
    }

    @Test
    fun manuallyClockedAnimationsFollowTheScale() {
        assertEquals(360f, scaledAnimationMillis(360, 1f), 0.0001f)
        assertEquals(180f, scaledAnimationMillis(360, 0.5f), 0.0001f)
        assertEquals(720f, scaledAnimationMillis(360, 2f), 0.0001f)
        assertEquals(1f, scaledAnimationMillis(0, 0f), 0.0001f)
    }

    @Test
    fun speedLabelUsesTwoDecimals() {
        assertEquals("1.00", formatAnimationSpeed(1f))
        assertEquals("0.50", formatAnimationSpeed(0.5f))
        assertEquals("2.00", formatAnimationSpeed(2f))
        assertEquals("2.00", formatAnimationSpeed(9f))
    }
}

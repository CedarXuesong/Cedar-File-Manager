package com.openxida.cedarmanager

import com.openxida.cedarmanager.ui.effects.SettingsPopupMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PopupMotionTest {
    @Test
    fun closeNarrowsProportionallyAndFadesOut() {
        val start = SettingsPopupMotion.sample(false, 0f, SettingsPopupMotion.Settled)
        val middle = SettingsPopupMotion.sample(false, 0.5f, SettingsPopupMotion.Settled)
        val end = SettingsPopupMotion.sample(false, 1f, SettingsPopupMotion.Settled)
        val fullWidth = start.right - start.left
        val middleRatio = (middle.right - middle.left) / fullWidth
        val endRatio = (end.right - end.left) / fullWidth

        assertEquals(1f, fullWidth, 0.001f)
        assertTrue(middleRatio in 0.65f..0.75f)
        assertEquals(440f / 638f, endRatio, 0.001f)
        assertEquals(1f, SettingsPopupMotion.alpha(false, 0f), 0.001f)
        assertEquals(0.25f, SettingsPopupMotion.alpha(false, 0.5f), 0.001f)
        assertEquals(0f, SettingsPopupMotion.alpha(false, 1f), 0.001f)
    }

    @Test
    fun shortMediumAndLongMenusUseTheDemoHeightAdaptation() {
        assertEquals(0f, SettingsPopupMotion.lengthWeight(168f), 0.001f)
        assertTrue(SettingsPopupMotion.lengthWeight(324f) in 0.85f..0.95f)
        assertEquals(1f, SettingsPopupMotion.lengthWeight(380f), 0.001f)

        val short = SettingsPopupMotion.sample(
            open = true,
            fraction = 0.5f,
            from = SettingsPopupMotion.Anchor,
            contentHeightDp = 168f,
        )
        val long = SettingsPopupMotion.sample(
            open = true,
            fraction = 0.5f,
            from = SettingsPopupMotion.anchor(false, 380f),
            contentHeightDp = 380f,
        )
        assertTrue(long.top < short.top)
        assertTrue(SettingsPopupMotion.alpha(true, 0.08f, 380f) <
            SettingsPopupMotion.alpha(true, 0.08f, 168f))
        assertTrue(SettingsPopupMotion.blurPixels(true, 0.08f, 380f) <
            SettingsPopupMotion.blurPixels(true, 0.08f, 168f))
    }
}

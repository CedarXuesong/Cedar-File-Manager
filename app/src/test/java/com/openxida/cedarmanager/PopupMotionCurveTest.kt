package com.openxida.cedarmanager

import com.openxida.cedarmanager.ui.effects.SettingsPopupMotion
import com.openxida.cedarmanager.ui.effects.SettingsPopupMotion.Frame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

class PopupMotionCurveTest {
    private fun Frame.edges() = floatArrayOf(left, top, right, bottom)
    private val scales = floatArrayOf(638f, 475f, 638f, 475f)
    private val origins = floatArrayOf(553f, 1665f, 553f, 1665f)

    private fun assertFrame(expected: Frame, actual: Frame) {
        expected.edges().zip(actual.edges()).forEach { (a, b) -> assertEquals(a, b, 0.000001f) }
    }

    @Test
    fun analyticFitStaysCloseToOriginalCapturedBounds() {
        val lines = requireNotNull(javaClass.getResourceAsStream("/popup-motion-keyframes.csv"))
            .bufferedReader().use { it.readLines().drop(1) }
        for (open in listOf(true, false)) {
            val direction = if (open) "open" else "close"
            val duration = if (open) SettingsPopupMotion.OpenMillis else SettingsPopupMotion.CloseMillis
            val from = if (open) SettingsPopupMotion.Anchor else SettingsPopupMotion.Settled
            val maxError = if (open) 3f else 5f
            var squaredError = 0.0
            var count = 0
            for (line in lines.filter { it.startsWith("$direction,") }) {
                val fields = line.split(',')
                val fraction = fields[1].toFloat() / duration
                val actual = SettingsPopupMotion.sample(open, fraction, from).edges()
                for (edge in actual.indices) {
                    val error = actual[edge] * scales[edge] + origins[edge] - fields[edge + 2].toFloat()
                    assertTrue("$direction at ${fields[1]}ms, edge $edge: error=$error px", abs(error) < maxError)
                    squaredError += error * error
                    count++
                }
            }
            assertTrue("Missing reference samples", count > 80)
            val rmse = sqrt(squaredError / count)
            assertTrue("$direction RMSE=$rmse", rmse < if (open) 0.8 else 1.4)
        }
    }

    @Test
    fun endpointsAndOutOfRangeTimesAreExact() {
        for (open in listOf(true, false)) {
            val start = if (open) SettingsPopupMotion.Anchor else SettingsPopupMotion.Settled
            val end = if (open) SettingsPopupMotion.Settled else SettingsPopupMotion.Anchor
            for (fraction in listOf(-1f, 0f)) {
                assertFrame(start, SettingsPopupMotion.sample(open, fraction, start))
            }
            for (fraction in listOf(1f, 2f)) {
                assertFrame(end, SettingsPopupMotion.sample(open, fraction, start))
            }
        }
    }

    @Test
    fun fittedMotionPreservesDipReboundAndValidBoundsBetweenSamples() {
        for (open in listOf(true, false)) {
            val from = if (open) SettingsPopupMotion.Anchor else SettingsPopupMotion.Settled
            var maxTop = 0f
            var minTop = 0f
            for (step in 0..10000) {
                val frame = SettingsPopupMotion.sample(open, step / 10000f, from)
                assertTrue(frame.edges().all { it.isFinite() })
                assertTrue(frame.right - frame.left in 0.64f..1.01f)
                assertTrue(frame.bottom - frame.top in 0.18f..1.02f)
                assertTrue(frame.left in -0.004f..0.311f)
                assertTrue(frame.top in -0.012f..0.15f)
                assertTrue(frame.right in 0.95f..1.004f)
                assertTrue(frame.bottom in 0.18f..1.01f)
                maxTop = maxOf(maxTop, frame.top)
                minTop = minOf(minTop, frame.top)
            }
            assertTrue("Downward dip lost", maxTop in 0.13f..0.15f)
            if (open) assertTrue("Opening rebound lost", minTop < -0.005f)
        }
    }

    @Test
    fun reversingMidFlightKeepsPositionAndFinishesAtRest() {
        for (open in listOf(true, false)) {
            val previousStart = if (open) SettingsPopupMotion.Settled else SettingsPopupMotion.Anchor
            val end = if (open) SettingsPopupMotion.Settled else SettingsPopupMotion.Anchor
            val duration = if (open) SettingsPopupMotion.OpenMillis else SettingsPopupMotion.CloseMillis
            for (interruption in listOf(0.1f, 0.5f, 0.9f)) {
                val from = SettingsPopupMotion.sample(!open, interruption, previousStart)
                assertFrame(from, SettingsPopupMotion.sample(open, 0f, from))
                assertFrame(end, SettingsPopupMotion.sample(open, 1f, from))
                val justBeforeEnd = SettingsPopupMotion.sample(open, 0.9999f, from).edges()
                end.edges().forEachIndexed { edge, value ->
                    val terminalSpeed = abs(value - justBeforeEnd[edge]) * scales[edge] / (duration * 0.0001f)
                    assertTrue("Terminal speed: $terminalSpeed px/ms", terminalSpeed < 0.005f)
                }
            }
        }
    }

    @Test
    fun bottomAnchoredPopupMirrorsTheVerticalExpansion() {
        val bottomAnchor = SettingsPopupMotion.anchor(bottomAnchored = true)
        val bottomSettled = SettingsPopupMotion.settled(bottomAnchored = true)

        assertEquals(0f, bottomSettled.top, 0.000001f)
        assertEquals(1f, bottomSettled.bottom, 0.000001f)
        assertEquals(1f, bottomAnchor.bottom, 0.000001f)
        assertTrue(bottomAnchor.top > 0.7f)

        val start = SettingsPopupMotion.sample(
            open = true,
            fraction = 0f,
            from = bottomAnchor,
            bottomAnchored = true,
        )
        val end = SettingsPopupMotion.sample(
            open = true,
            fraction = 1f,
            from = bottomAnchor,
            bottomAnchored = true,
        )
        assertFrame(bottomAnchor, start)
        assertFrame(bottomSettled, end)

        val middle = SettingsPopupMotion.sample(
            open = true,
            fraction = 0.5f,
            from = bottomAnchor,
            bottomAnchored = true,
        )
        assertTrue(middle.top > 0f && middle.top < bottomAnchor.top)
        assertTrue(middle.bottom > 0.75f)
    }
}

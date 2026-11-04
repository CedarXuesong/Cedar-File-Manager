package com.openxida.cedarmanager.ui.effects

/**
 * Endpoint-constrained degree-seven polynomial fitted to captured popup bounds.
 *
 * f(t) = end + (1-t)^2 * (start-end + t*(a1 + t*(a2 + t*(a3 + t*(a4 + t*a5)))))
 *
 * t is normalized time, not Bezier path distance. The endpoints are exact and
 * the terminal velocity is zero. Use Double for Horner evaluation to avoid
 * cancellation between fitted coefficients, then return a normalized Float.
 * See tools/fit_popup_motion.py and the frozen test reference CSV.
 */
internal class PopupMotionCurve(
    private val start: Float,
    private val end: Float,
    private val a1: Double,
    private val a2: Double,
    private val a3: Double,
    private val a4: Double,
    private val a5: Double,
) {
    fun valueAt(fraction: Float): Float {
        if (fraction <= 0f) return start
        if (fraction >= 1f) return end
        val t = fraction.toDouble()
        val remaining = 1.0 - t
        val polynomial = a1 + t * (a2 + t * (a3 + t * (a4 + t * a5)))
        return (end.toDouble() + remaining * remaining *
            (start.toDouble() - end.toDouble() + t * polynomial)).toFloat()
    }
}

package com.example.testapp.vision

import android.graphics.Bitmap
import com.example.testapp.model.RoiPct
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object HpDetector {
    fun detectWhiteBarPercent(bmp: Bitmap, roi: RoiPct): Double {
        val W = bmp.width; val H = bmp.height
        val x0 = (roi.x * W).toInt()
        val y0 = (roi.y * H).toInt()
        val w  = (roi.w * W).toInt().coerceAtLeast(1)
        val h  = (roi.h * H).toInt().coerceAtLeast(1)

        // Lấy dòng giữa ROI để đo độ dài dải trắng
        val midY = (y0 + h/2).coerceIn(0, H-1)

        var run = 0; var best = 0
        for (x in x0 until min(W, x0 + w)) {
            val p = bmp.getPixel(x, midY)
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            val white = (r + g + b) >= 700 && abs(r - g) < 12 && abs(g - b) < 12
            run = if (white) run + 1 else 0
            best = max(best, run)
        }
        return (best.toDouble() / w).coerceIn(0.0, 1.0)
    }

    fun ema(prev: Double?, now: Double, alpha: Double = 0.6): Double =
        if (prev == null) now else alpha * now + (1 - alpha) * prev
}

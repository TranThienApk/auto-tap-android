package com.example.testapp.vision

import android.graphics.Bitmap
import com.example.testapp.model.RoiPct
import kotlin.math.roundToInt

object HpDetector {
    fun scaleIfNeeded(src: Bitmap, targetW: Int): Bitmap {
        if (src.width <= targetW) return src
        val ratio = targetW.toFloat() / src.width
        val nh = (src.height * ratio).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, targetW, nh, false)
    }

    // Tỷ lệ pixel "sáng" trong ROI (coi là vạch HP). Trả 0..1.
    fun detectWhiteBarPercent(src: Bitmap, roi: RoiPct, thr: Int = 220): Double {
        val x0 = (roi.x * src.width).toInt().coerceIn(0, src.width - 1)
        val y0 = (roi.y * src.height).toInt().coerceIn(0, src.height - 1)
        val w = (roi.w * src.width).toInt().coerceAtLeast(1).coerceAtMost(src.width - x0)
        val h = (roi.h * src.height).toInt().coerceAtLeast(1).coerceAtMost(src.height - y0)

        val buf = IntArray(w * h)
        src.getPixels(buf, 0, w, x0, y0, w, h)
        var brightCnt = 0
        val total = buf.size
        for (c in buf) {
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            val bright = maxOf(r, g, b)
            if (bright >= thr) brightCnt++
        }
        return (brightCnt.toDouble() / total).coerceIn(0.0, 1.0)
    }

    fun ema(prev: Double?, x: Double, alpha: Double): Double =
        if (prev == null) x else (alpha * x + (1 - alpha) * prev)
}

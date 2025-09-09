package com.example.testapp.vision

import android.graphics.Bitmap
import android.graphics.Color
import com.example.testapp.model.RoiPct
import kotlin.math.min

object HpDetector {
    fun detectWhiteBarPercent(
        full: Bitmap,
        roi: RoiPct,
        satMax: Float = 0.14f,
        valMin: Float = 0.84f
    ): Double {
        val W = full.width; val H = full.height
        val left = (roi.x * W).toInt().coerceIn(0, W - 1)
        val top = (roi.y * H).toInt().coerceIn(0, H - 1)
        val width = (roi.w * W).toInt().coerceAtLeast(1)
        val height = (roi.h * H).toInt().coerceAtLeast(1)
        val right = min(W, left + width)
        val bottom = min(H, top + height)

        val cols = BooleanArray(right - left)
        val hsv = FloatArray(3)
        for (y in top until bottom) {
            var i = 0
            for (x in left until right) {
                Color.colorToHSV(full.getPixel(x, y), hsv)
                if (hsv[1] <= satMax && hsv[2] >= valMin) cols[i] = true
                i++
            }
        }
        var l = -1; var r = -1
        for (i in cols.indices) if (cols[i]) { if (l == -1) l = i; r = i }
        if (l == -1) return 0.0
        val filled = (r - l + 1).coerceAtLeast(0)
        return (filled.toDouble() / cols.size).coerceIn(0.0, 1.0)
    }

    fun ema(prev: Double?, cur: Double, alpha: Double = 0.6): Double =
        prev?.let { alpha * it + (1 - alpha) * cur } ?: cur
}

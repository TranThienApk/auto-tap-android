package com.example.testapp.vision

import android.graphics.Bitmap
import com.example.testapp.model.RoiPct
import kotlin.math.abs
import kotlin.math.max

object HpAutoRoi {
    /** Tìm dải trắng dài gần phía dưới, trả về ROI theo phần trăm màn hình. */
    fun detectHpRoiPct(bmp: Bitmap): RoiPct? {
        val W = bmp.width; val H = bmp.height
        val yStart = (H * 0.65f).toInt()
        val yEnd = (H * 0.92f).toInt().coerceAtMost(H-1)

        var bestRun = 0
        var bestY = -1

        for (y in yStart until yEnd) {
            var run = 0
            for (x in 0 until W) {
                val p = bmp.getPixel(x, y)
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                val white = (r + g + b) >= 700 && abs(r - g) < 12 && abs(g - b) < 12
                run = if (white) run + 1 else 0
                if (run > bestRun) { bestRun = run; bestY = y }
            }
        }

        if (bestY < 0 || bestRun < (W * 0.15f)) return null

        val roiH = (H * 0.025f).toInt().coerceAtLeast(4)
        val top = (bestY - roiH / 2).coerceIn(0, H - 1)
        return RoiPct(
            x = 0f,
            y = top.toFloat() / H,
            w = 1f,
            h = roiH.toFloat() / H
        )
    }
}

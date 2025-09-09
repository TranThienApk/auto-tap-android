package com.example.testapp.vision

import android.graphics.Bitmap
import com.example.testapp.model.RoiPct
import kotlin.math.max
import kotlin.math.min

object HpAutoRoi {
    // Tìm dải trắng dài nhất ở nửa trên màn hình → coi là thanh HP
    fun detectHpRoiPct(src: Bitmap): RoiPct? {
        val targetW = 640
        val scaled = HpDetector.scaleIfNeeded(src, targetW)
        val w = scaled.width
        val h = scaled.height
        val topScan = (h * 0.5).toInt()

        var bestLen = 0
        var bestY = -1
        var bestStart = 0
        var bestEnd = 0

        val thr = 235
        val row = IntArray(w)
        for (y in 0 until topScan) {
            scaled.getPixels(row, 0, w, 0, y, w, 1)
            var curStart = -1
            var curLen = 0
            var rowBestStart = 0
            var rowBestLen = 0
            for (x in 0 until w) {
                val c = row[x]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                val bright = max(max(r, g), b)
                val isWhite = bright >= thr
                if (isWhite) {
                    if (curStart == -1) curStart = x
                    curLen++
                } else if (curStart != -1) {
                    if (curLen > rowBestLen) { rowBestLen = curLen; rowBestStart = curStart }
                    curStart = -1; curLen = 0
                }
            }
            if (curStart != -1 && curLen > rowBestLen) { rowBestLen = curLen; rowBestStart = curStart }
            if (rowBestLen > bestLen) { bestLen = rowBestLen; bestStart = rowBestStart; bestEnd = rowBestStart + rowBestLen; bestY = y }
        }

        if (bestLen < w * 0.15) return null
        val barH = (h * 0.02f).coerceAtLeast(2f)
        val y0 = max(0f, bestY - barH / 2)
        val x0 = max(0f, bestStart.toFloat())
        val x1 = min(w - 1f, bestEnd.toFloat())
        val roi = RoiPct(x = x0 / w, y = y0 / h, w = (x1 - x0) / w, h = barH / h)
        return if (roi.w > 0f && roi.h > 0f) roi else null
    }
}

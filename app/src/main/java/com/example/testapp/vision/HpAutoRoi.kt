package com.example.testapp.vision

import android.graphics.Bitmap
import com.example.testapp.model.RoiPct
import kotlin.math.max
import kotlin.math.min

object HpAutoRoi {
    /**
     * Tự động tìm ROI thanh HP dạng **vạch trắng ngang** ở nửa trên màn hình.
     * Chiến lược: downscale, quét từng hàng để tìm dải pixel sáng liên tục dài nhất.
     */
    fun detectHpRoiPct(src: Bitmap): RoiPct? {
        val targetW = 640
        val scaled = HpDetector.scaleIfNeeded(src, targetW)
        val w = scaled.width
        val h = scaled.height
        val topScan = (h * 0.45).toInt() // quét 45% đầu

        var bestLen = 0
        var bestY = -1
        var bestStart = 0
        var bestEnd = 0

        val threshold = 235 // độ sáng coi là "trắng"
        val px = IntArray(w)
        for (y in 0 until topScan) {
            scaled.getPixels(px, 0, w, 0, y, w, 1)
            var curStart = -1
            var curLen = 0
            var rowBestStart = 0
            var rowBestLen = 0
            for (x in 0 until w) {
                val c = px[x]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                val bright = max(max(r, g), b)
                val isWhite = bright >= threshold
                if (isWhite) {
                    if (curStart == -1) curStart = x
                    curLen++
                } else if (curStart != -1) {
                    if (curLen > rowBestLen) { rowBestLen = curLen; rowBestStart = curStart }
                    curStart = -1; curLen = 0
                }
            }
            if (curStart != -1 && curLen > rowBestLen) {
                rowBestLen = curLen; rowBestStart = curStart
            }
            if (rowBestLen > bestLen) {
                bestLen = rowBestLen; bestStart = rowBestStart; bestEnd = rowBestStart + rowBestLen; bestY = y
            }
        }

        if (bestLen < w * 0.15) return null // quá ngắn, coi như không thấy HP bar

        val barH = (h * 0.02f).coerceAtLeast(2f) // 2% chiều cao
        val y0 = max(0f, bestY - barH / 2)
        val x0 = max(0f, bestStart.toFloat())
        val x1 = min(w - 1f, bestEnd.toFloat())
        val roi = RoiPct(
            x = x0 / w, y = y0 / h,
            w = (x1 - x0) / w, h = barH / h
        )
        return if (roi.w > 0f && roi.h > 0f) roi else null
    }
}

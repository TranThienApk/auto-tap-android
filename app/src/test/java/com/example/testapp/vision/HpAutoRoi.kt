package com.example.testapp.vision

import android.graphics.Bitmap
import android.graphics.Color
import com.example.testapp.model.RoiPct
import kotlin.math.max
import kotlin.math.min

object HpAutoRoi {
    private const val SAT_MAX = 0.16f // S <= ~0.16
    private const val VAL_MIN = 0.84f // V >= ~0.84

    fun detectHpRoiPct(full: Bitmap, searchBottomRatio: Float = 0.35f): RoiPct? {
        val W = full.width; val H = full.height
        if (W <= 0 || H <= 0) return null

        val yStart = (H * (1f - searchBottomRatio)).toInt().coerceAtLeast(0)
        val yEnd = H
        val rowWhite = FloatArray(yEnd - yStart)
        val hsv = FloatArray(3)

        for (y in yStart until yEnd) {
            var white = 0
            for (x in 0 until W) {
                Color.colorToHSV(full.getPixel(x, y), hsv)
                if (hsv[1] <= SAT_MAX && hsv[2] >= VAL_MIN) white++
            }
            rowWhite[y - yStart] = white.toFloat() / W
        }
        smooth(rowWhite, 9)

        val band = bestBand(rowWhite, minBandPx = (H * 0.008f).toInt()) ?: return null
        val bandTop = yStart + band.first
        val bandBot = yStart + band.second

        val colsFilled = BooleanArray(W)
        for (x in 0 until W) {
            var any = false
            var y = bandTop
            while (y <= bandBot) {
                Color.colorToHSV(full.getPixel(x, y), hsv)
                if (hsv[1] <= SAT_MAX && hsv[2] >= VAL_MIN) { any = true; break }
                y++
            }
            colsFilled[x] = any
        }
        val left = colsFilled.indexOfFirst { it }
        val right = colsFilled.indexOfLast { it }
        if (left == -1 || right <= left) return null

        val padY = ((bandBot - bandTop + 1) * 0.2f).toInt().coerceAtLeast(1)
        val top = (bandTop - padY).coerceAtLeast(0)
        val bottom = (bandBot + padY).coerceAtMost(H - 1)

        val xPct = left.toFloat() / W
        val yPct = top.toFloat() / H
        val wPct = (right - left + 1).toFloat() / W
        val hPct = (bottom - top + 1).toFloat() / H
        return RoiPct(xPct, yPct, wPct, hPct)
    }

    private fun smooth(a: FloatArray, win: Int) {
        if (win <= 1) return
        val half = win / 2
        val tmp = a.copyOf()
        for (i in a.indices) {
            var s = 0f; var c = 0
            val L = max(0, i - half); val R = min(a.lastIndex, i + half)
            for (j in L..R) { s += tmp[j]; c++ }
            a[i] = if (c > 0) s / c else tmp[i]
        }
    }

    private fun bestBand(row: FloatArray, minBandPx: Int): Pair<Int, Int>? {
        val maxV = row.maxOrNull() ?: return null
        val thr = maxV * 0.6f
        var a = -1; var sum = 0f
        var bestSum = 0f; var bestA = -1; var bestB = -1
        for (i in row.indices) {
            val v = row[i]
            if (v >= thr) {
                if (a == -1) { a = i; sum = 0f }
                sum += v
            } else if (a != -1) {
                val b = i - 1
                if ((b - a + 1) >= minBandPx && sum > bestSum) { bestSum = sum; bestA = a; bestB = b }
                a = -1; sum = 0f
            }
        }
        if (a != -1) {
            val b = row.lastIndex
            if ((b - a + 1) >= minBandPx && sum > bestSum) { bestSum = sum; bestA = a; bestB = b }
        }
        return if (bestA >= 0) bestA to bestB else null
    }
}

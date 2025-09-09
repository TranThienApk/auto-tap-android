package com.example.testapp

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.util.DisplayMetrics
import android.widget.Button
import android.widget.TextView
import android.widget.ToggleButton
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.example.testapp.capture.ScreenCapture
import com.example.testapp.core.FrameBus
import com.example.testapp.model.RoiPct
import com.example.testapp.vision.HpAutoRoi
import com.example.testapp.vision.HpDetector
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var roiHp: RoiPct? = null
    private var hpEma: Double? = null
    private var loopJob: Job? = null

    private var projection: MediaProjection? = null
    private var screenCapture: ScreenCapture? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val tv = findViewById<TextView>(R.id.tvStatus)
        val btnStartCap = findViewById<Button>(R.id.btnStartCapture)
        val tg = findViewById<ToggleButton>(R.id.tgAuto)

        btnStartCap.setOnClickListener { requestMediaProjection() }

        tg.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                lifecycleScope.launch {
                    // Lấy 1 frame hiện tại để tự ROI
                    val frame = FrameBus.latest.filterNotNull().first()
                    val roi = HpAutoRoi.detectHpRoiPct(frame)
                    if (roi == null) {
                        tv.text = "Không tìm thấy ROI HP. Hãy bật khi thanh trắng rõ."
                        tg.isChecked = false
                        return@launch
                    }
                    roiHp = roi
                    hpEma = null
                    tv.text = "Đã khóa ROI HP. Đang đọc…"

                    // Loop đọc HP liên tục
                    loopJob?.cancel()
                    loopJob = launch {
                        while (true) {
                            FrameBus.latest.value?.let { f ->
                                val raw = HpDetector.detectWhiteBarPercent(f, roi)
                                hpEma = HpDetector.ema(hpEma, raw, 0.6)
                                val pct = hpEma ?: raw
                                tv.text = "HP: ${(pct*100).toInt()}%  | ROI: x=%.3f y=%.3f".format(roi.x, roi.y)
                                // TODO: nếu pct < 0.25 → gọi Accessibility để tap mua máu
                            }
                            delay(120) // ~8 FPS là đủ
                        }
                    }
                }
            } else {
                loopJob?.cancel(); loopJob = null
                roiHp = null; hpEma = null
                tv.text = "HP: -- %   ROI: chưa có"
            }
        }
    }

    // ====== MediaProjection ======
    private fun requestMediaProjection() {
        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(mpm.createScreenCaptureIntent(), 3366)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 3366 && resultCode == Activity.RESULT_OK && data != null) {
            val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projection = mpm.getMediaProjection(resultCode, data)
            startCapture()
        }
    }

    private fun startCapture() {
        val metrics = DisplayMetrics()
        display?.getRealMetrics(metrics)
        val w = metrics.widthPixels
        val h = metrics.heightPixels
        val dpi = metrics.densityDpi

        screenCapture?.stop()
        screenCapture = ScreenCapture(this).apply {
            start(requireNotNull(projection), w, h, dpi) { bitmap ->
                // Có thể scale nhỏ để nhanh hơn (ví dụ targetWidth = 1280)
                val scaled = scaleIfNeeded(bitmap, targetW = 1280)
                FrameBus.update(scaled)
            }
        }
    }

    private fun scaleIfNeeded(src: Bitmap, targetW: Int): Bitmap {
        if (src.width <= targetW) return src
        val ratio = targetW.toFloat() / src.width
        val h = (src.height * ratio).toInt()
        return Bitmap.createScaledBitmap(src, targetW, h, false)
    }

    override fun onDestroy() {
        super.onDestroy()
        loopJob?.cancel()
        screenCapture?.stop()
        projection?.stop()
    }
}

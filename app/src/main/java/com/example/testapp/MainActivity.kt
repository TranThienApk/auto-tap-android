package com.example.testapp

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Bundle
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

    private var projection: MediaProjection? = null
    private var capturer: ScreenCapture? = null

    private var roiHp: RoiPct? = null
    private var hpEma: Double? = null
    private var loopJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnStart = findViewById<Button>(R.id.btnStartCapture)
        val tgAuto   = findViewById<ToggleButton>(R.id.tgAuto)
        val tv       = findViewById<TextView>(R.id.tvStatus)

        btnStart.setOnClickListener { requestMediaProjection() }

        tgAuto.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                // Chưa có frame thì không cho ON
                if (FrameBus.latest.value == null) {
                    tv.text = "Chưa có frame. Hãy bấm 'Bắt đầu quay màn hình' rồi bật lại."
                    tgAuto.isChecked = false
                    return@setOnCheckedChangeListener
                }

                lifecycleScope.launch {
                    // Tự tìm ROI 1 lần tại thời điểm bật
                    val frame = FrameBus.latest.filterNotNull().first()
                    val roi = HpAutoRoi.detectHpRoiPct(frame)
                    if (roi == null) {
                        tv.text = "Không tìm thấy ROI HP. Hãy bật khi thanh trắng rõ."
                        tgAuto.isChecked = false
                        return@launch
                    }
                    roiHp = roi
                    hpEma = null
                    tv.text = "Đã khóa ROI HP. Đang đọc…"

                    loopJob?.cancel()
                    loopJob = launch {
                        while (true) {
                            FrameBus.latest.value?.let { f ->
                                val raw = HpDetector.detectWhiteBarPercent(f, roi)
                                hpEma = HpDetector.ema(hpEma, raw, 0.6)
                                val pct = hpEma ?: raw
                                tv.text = "HP: ${(pct*100).toInt()}% | ROI: x=%.3f y=%.3f"
                                    .format(roi.x, roi.y)
                                // TODO: pct < 0.25 -> gọi Accessibility tap mua máu
                            }
                            delay(120)
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

    // -------- MediaProjection ----------
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
            startCaptureSafe()
        }
    }

    private fun startCaptureSafe() {
        try {
            val (w, h, dpi) = run {
                val wm = getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    val b = wm.currentWindowMetrics.bounds
                    val dm = resources.displayMetrics
                    Triple(b.width(), b.height(), dm.densityDpi)
                } else {
                    val dm = resources.displayMetrics
                    Triple(dm.widthPixels, dm.heightPixels, dm.densityDpi)
                }
            }

            capturer?.stop()
            capturer = ScreenCapture(this).apply {
                start(requireNotNull(projection), w, h, dpi) { bmp ->
                    val scaled = scaleIfNeeded(bmp, 1280)
                    FrameBus.update(scaled)
                }
            }
        } catch (t: Throwable) {
            // Nếu lỗi, hiển thị để biết
            findViewById<TextView>(R.id.tvStatus).text = "Capture error: ${t.javaClass.simpleName}"
        }
    }

    private fun scaleIfNeeded(src: Bitmap, targetW: Int): Bitmap {
        if (src.width <= targetW) return src
        val ratio = targetW.toFloat() / src.width
        val nh = (src.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, targetW, nh, false)
    }

    override fun onDestroy() {
        super.onDestroy()
        loopJob?.cancel()
        capturer?.stop()
        projection?.stop()
    }
}

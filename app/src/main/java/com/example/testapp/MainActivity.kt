package com.example.testapp

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import android.widget.ToggleButton
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
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
    private var healing = false

    private lateinit var tv: TextView
    private lateinit var tgAuto: ToggleButton

    // Dùng Activity Result API để xin quyền capture
    private val captureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val mpm = getSystemService(MediaProjectionManager::class.java)
            projection = mpm.getMediaProjection(result.resultCode, result.data!!)
            startCaptureSafe()
        } else {
            Toast.makeText(this, "Bạn đã không cho phép quay màn hình", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnStart = findViewById<Button>(R.id.btnStartCapture)
        tgAuto = findViewById(R.id.tgAuto)
        tv = findViewById(R.id.tvStatus)

        btnStart.setOnClickListener { startScreenCaptureFlow() }

        tgAuto.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) startAutoLoop() else stopAutoLoop()
        }
    }

    private fun startScreenCaptureFlow() {
        val mpm = getSystemService(MediaProjectionManager::class.java)
        captureLauncher.launch(mpm.createScreenCaptureIntent())
    }

    private fun getDisplayDims(): Triple<Int, Int, Int> {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val dm = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getMetrics(dm)
        return Triple(dm.widthPixels, dm.heightPixels, dm.densityDpi)
    }

    private fun startCaptureSafe() {
        try {
            val (w, h, dpi) = getDisplayDims()
            capturer?.stop()
            capturer = ScreenCapture(this).apply {
                start(requireNotNull(projection), w, h, dpi) { bmp ->
                    val scaled = HpDetector.scaleIfNeeded(bmp, 1280)
                    FrameBus.update(scaled)
                }
            }
            tv.text = "Đang nhận frame…"
        } catch (t: Throwable) {
            tv.text = "Capture error: ${t.javaClass.simpleName}"
        }
    }

    private fun startAutoLoop() {
        if (FrameBus.latest.value == null) {
            tv.text = "Chưa có frame. Hãy bấm \"Bắt đầu quay màn hình\" rồi bật lại."
            tgAuto.isChecked = false
            return
        }

        lifecycleScope.launch {
            val frame = FrameBus.latest.filterNotNull().first()
            val roi = HpAutoRoi.detectHpRoiPct(frame)
            if (roi == null) {
                tv.text = "Không tìm thấy ROI HP. Bật khi thanh HP hiển thị rõ."
                tgAuto.isChecked = false
                return@launch
            }
            roiHp = roi
            hpEma = null
            healing = false
            tv.text = "Đã khóa ROI HP (x=${"%.3f".format(roi.x)}, y=${"%.3f".format(roi.y)}) — Đang đọc…"

            loopJob?.cancel()
            loopJob = launch {
                while (true) {
                    FrameBus.latest.value?.let { f ->
                        val raw = HpDetector.detectWhiteBarPercent(f, roi)
                        hpEma = HpDetector.ema(hpEma, raw, 0.6)
                        val pct = hpEma ?: raw
                        tv.text = "HP: ${((pct * 100).coerceIn(0.0,100.0)).toInt()}%  |  ROI: x=${"%.3f".format(roi.x)} y=${"%.3f".format(roi.y)}"

                        // Ngưỡng bật/tắt để tránh rung
                        if (!healing && pct < 0.25) {
                            sendTapHeal()
                            healing = true
                        } else if (healing && pct > 0.35) {
                            healing = false
                        }
                    }
                    delay(120)
                }
            }
        }
    }

    private fun stopAutoLoop() {
        loopJob?.cancel(); loopJob = null
        roiHp = null; hpEma = null; healing = false
        tv.text = "HP: -- %   ROI: chưa có"
    }

    private fun sendTapHeal() {
        // Tap tương đối: 90% chiều rộng, 85% chiều cao (tuỳ game)
        val (w, h, _) = getDisplayDims()
        val x = (w * 0.90f)
        val y = (h * 0.85f)
        val intent = Intent("com.example.testapp.ACTION_TAP").apply {
            putExtra("x", x)
            putExtra("y", y)
        }
        sendBroadcast(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAutoLoop()
        capturer?.stop()
        projection?.stop()
    }
}

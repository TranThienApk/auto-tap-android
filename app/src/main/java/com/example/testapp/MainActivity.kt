package com.example.testapp

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.ToggleButton
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.lifecycleScope
import com.example.testapp.capture.ScreenCapture
import com.example.testapp.core.FrameBus
import com.example.testapp.model.RoiPct
import com.example.testapp.service.OverlayService
import com.example.testapp.vision.HpAutoRoi
import com.example.testapp.vision.HpDetector
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.max

class MainActivity : ComponentActivity() {

    private var projection: MediaProjection? = null
    private var capturer: ScreenCapture? = null

    private var hpRoi: RoiPct? = null
    private var hpEma: Double? = null
    private var loop: Job? = null

    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { res ->
        if (res.resultCode == Activity.RESULT_OK && res.data != null) {
            val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projection = mgr.getMediaProjection(res.resultCode, res.data!!)
            findViewById<TextView>(R.id.tvStatus).text = "Đã có quyền MediaProjection"
            startCapture()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnOverlay = findViewById<Button>(R.id.btnOverlay)
        val btnAskProj = findViewById<Button>(R.id.btnAskProjection)
        val btnAcc     = findViewById<Button>(R.id.btnOpenAccessibility)
        val tgAuto     = findViewById<ToggleButton>(R.id.tgAutoHp)
        val tv         = findViewById<TextView>(R.id.tvStatus)

        btnOverlay.setOnClickListener {
            requestOverlayIfNeeded()
            startService(Intent(this, OverlayService::class.java))
        }

        btnAskProj.setOnClickListener {
            val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projectionLauncher.launch(mpm.createScreenCaptureIntent())
        }

        btnAcc.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        if (Build.VERSION.SDK_INT >= 33 &&
            !NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 123)
        }

        tgAuto.setOnCheckedChangeListener { _, on ->
            if (on) {
                if (FrameBus.latest.value == null) {
                    tv.text = "Chưa có frame. Bấm 'Xin quyền ghi màn hình' rồi bật lại."
                    tgAuto.isChecked = false; return@setOnCheckedChangeListener
                }
                lifecycleScope.launch {
                    val frame = FrameBus.latest.filterNotNull().first()
                    val roi = HpAutoRoi.detectHpRoiPct(frame)
                    if (roi == null) {
                        tv.text = "Không tìm thấy thanh HP. Bật khi dải trắng rõ."
                        tgAuto.isChecked = false; return@launch
                    }
                    hpRoi = roi; hpEma = null
                    tv.text = "Đã khóa ROI HP. Đang đọc…"

                    loop?.cancel()
                    loop = launch {
                        while (true) {
                            FrameBus.latest.value?.let { f ->
                                val raw = HpDetector.detectWhiteBarPercent(f, roi)
                                hpEma = HpDetector.ema(hpEma, raw, 0.6)
                                val pct = hpEma ?: raw
                                tv.text = "HP: ${(pct*100).toInt()}% | ROI x=%.3f y=%.3f"
                                    .format(roi.x, roi.y)
                                // TODO: nếu pct < NGƯỠNG -> gọi Accessibility tap mua máu
                            }
                            delay(120)
                        }
                    }
                }
            } else {
                loop?.cancel(); loop = null
                hpRoi = null; hpEma = null
                tv.text = "Status: idle"
            }
        }
    }

    private fun startCapture() {
        // Dùng kích thước an toàn: ≤1280 và bội số 8 (tránh crash stride)
        val dm = resources.displayMetrics
        val srcW = dm.widthPixels
        val srcH = dm.heightPixels
        val targetW = 1280
        val scale = if (srcW > targetW) targetW / srcW.toFloat() else 1f
        val w = roundTo8((srcW * scale).toInt().coerceAtLeast(320))
        val h = roundTo8((srcH * scale).toInt().coerceAtLeast(320))
        val dpi = dm.densityDpi

        capturer?.stop()
        capturer = ScreenCapture(this).apply {
            try {
                start(requireNotNull(projection), w, h, dpi) { bmp ->
                    FrameBus.update(bmp) // đã nhỏ sẵn
                }
                findViewById<TextView>(R.id.tvStatus).text = "Đang capture: ${w}x${h}"
            } catch (t: Throwable) {
                findViewById<TextView>(R.id.tvStatus).text = "Capture error: ${t.javaClass.simpleName}"
            }
        }
    }

    private fun roundTo8(v: Int): Int = max(8, (v / 8) * 8)

    private fun requestOverlayIfNeeded() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        loop?.cancel()
        capturer?.stop()
        projection?.stop()
    }
}

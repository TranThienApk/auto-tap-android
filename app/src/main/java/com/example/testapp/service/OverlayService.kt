package com.example.testapp.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.*
import android.widget.Button
import androidx.core.app.NotificationCompat
import com.example.testapp.R

class OverlayService : Service() {
    private lateinit var wm: WindowManager
    private var view: View? = null
    private val CHANNEL_ID = "overlay_fg"

    override fun onCreate() {
        super.onCreate()
        createChannelIfNeeded()
        startForeground(
            1,
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Overlay đang chạy")
                .setOngoing(true)
                .build()
        )

        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val type = if (Build.VERSION.SDK_INT >= 26)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_PHONE

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.START
        lp.x = 60; lp.y = 180

        view = LayoutInflater.from(this).inflate(R.layout.overlay_small, null).apply {
            findViewById<Button>(R.id.btnTap).setOnClickListener {
                // ví dụ: tap giữa màn hình (cần bật Trợ năng)
                val dm = resources.displayMetrics
                AutoAccessibilityService.safeTap(dm.widthPixels/2f, dm.heightPixels/2f)
            }
            findViewById<Button>(R.id.btnClose).setOnClickListener { stopSelf() }

            // kéo thả bong bóng
            setOnTouchListener(object : View.OnTouchListener {
                var px = 0f; var py = 0f; var ox = 0; var oy = 0
                override fun onTouch(v: View, e: MotionEvent): Boolean {
                    when (e.action) {
                        MotionEvent.ACTION_DOWN -> { px = e.rawX; py = e.rawY; ox = lp.x; oy = lp.y }
                        MotionEvent.ACTION_MOVE -> {
                            lp.x = (ox + (e.rawX - px)).toInt()
                            lp.y = (oy + (e.rawY - py)).toInt()
                            wm.updateViewLayout(view, lp)
                        }
                    }
                    return false
                }
            })
        }
        wm.addView(view, lp)
    }

    private fun createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= 26) {
            val chan = NotificationChannel(
                CHANNEL_ID, "Overlay", NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)
                ?.createNotificationChannel(chan)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        view?.let { wm.removeView(it) }
        view = null
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

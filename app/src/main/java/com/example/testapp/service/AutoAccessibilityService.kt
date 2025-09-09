package com.example.testapp.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent

class AutoAccessibilityService : AccessibilityService() {

    private val tapReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.example.testapp.ACTION_TAP") {
                val x = intent.getFloatExtra("x", -1f)
                val y = intent.getFloatExtra("y", -1f)
                if (x >= 0 && y >= 0) tap(x, y)
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val f = IntentFilter("com.example.testapp.ACTION_TAP")
        registerReceiver(tapReceiver, f)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    private fun tap(x: Float, y: Float, durationMs: Long = 60L) {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }

    override fun onDestroy() {
        try { unregisterReceiver(tapReceiver) } catch (_: Throwable) {}
        super.onDestroy()
    }
}

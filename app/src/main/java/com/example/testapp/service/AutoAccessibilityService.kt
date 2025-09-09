package com.example.testapp.service
import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
class AutoAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    // Sau này thêm dispatchGesture để tap mua máu
}

package com.mxcfe.key.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import com.mxcfe.key.prefs.Prefs
import com.mxcfe.key.privilege.ShizukuAccess
import com.mxcfe.key.ui.Feedback

class KeepAliveAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        instance = this
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        Feedback.dismiss()
        super.onDestroy()
    }

    override fun onInterrupt() = Unit

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (ActionKeyService.running) return
        val prefs = Prefs(this)
        if (!prefs.enabled() || !ShizukuAccess.granted()) return
        startForegroundService(Intent(this, ActionKeyService::class.java))
    }

    override fun onKeyEvent(event: android.view.KeyEvent?): Boolean = false

    fun globalAction(action: Int): Boolean = try {
        performGlobalAction(action)
    } catch (_: Throwable) {
        false
    }

    fun threeFingerSwipe(): Boolean {
        val metrics = resources.displayMetrics
        val width = metrics.widthPixels.toFloat()
        val height = metrics.heightPixels.toFloat()
        val startY = height * 0.72f
        val endY = height * 0.28f
        val xs = floatArrayOf(width * 0.28f, width * 0.5f, width * 0.72f)
        val builder = GestureDescription.Builder()
        for (x in xs) {
            val path = Path().apply {
                moveTo(x, startY)
                lineTo(x, endY)
            }
            builder.addStroke(GestureDescription.StrokeDescription(path, 0, 320))
        }
        return try {
            dispatchGesture(builder.build(), null, null)
        } catch (_: Throwable) {
            false
        }
    }

    fun twoFingerLongPress(): Boolean {
        val metrics = resources.displayMetrics
        val width = metrics.widthPixels.toFloat()
        val y = metrics.heightPixels * 0.62f
        val xs = floatArrayOf(width * 0.42f, width * 0.58f)
        val builder = GestureDescription.Builder()
        for (x in xs) {
            val path = Path().apply { moveTo(x, y) }
            builder.addStroke(GestureDescription.StrokeDescription(path, 0, 4000))
        }
        return try {
            dispatchGesture(builder.build(), null, null)
        } catch (_: Throwable) {
            false
        }
    }

    companion object {
        @Volatile
        var instance: KeepAliveAccessibilityService? = null
    }
}

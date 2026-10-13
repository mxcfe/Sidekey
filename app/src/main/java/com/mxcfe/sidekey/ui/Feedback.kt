package com.mxcfe.sidekey.ui

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.VibratorManager
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.DrawableRes
import com.mxcfe.sidekey.R
import com.mxcfe.sidekey.prefs.Prefs
import com.mxcfe.sidekey.privilege.ShizukuAccess
import com.mxcfe.sidekey.privilege.ShizukuShell
import com.mxcfe.sidekey.service.KeepAliveAccessibilityService

object Feedback {
    enum class Tone { NEUTRAL, ON, OFF }

    private class Bubble(
        val root: LinearLayout,
        val icon: ImageView,
        val label: TextView,
    )

    private val main = Handler(Looper.getMainLooper())
    private var bubble: Bubble? = null
    private var windowManager: WindowManager? = null
    private var hideTask: Runnable? = null
    @Volatile
    private var grantTried = false
    @Volatile
    private var overlayReady = false

    fun prepare(context: Context) {
        val app = context.applicationContext
        if (Settings.canDrawOverlays(app)) {
            overlayReady = true
            return
        }
        if (grantTried || !ShizukuAccess.granted()) return
        grantTried = true
        val pkg = app.packageName
        val granted = ShizukuShell.exec(
            "appops set $pkg SYSTEM_ALERT_WINDOW allow",
            3,
        )
        overlayReady = granted || Settings.canDrawOverlays(app)
    }

    fun show(
        context: Context,
        text: CharSequence,
        long: Boolean,
        tone: Tone = Tone.NEUTRAL,
        @DrawableRes icon: Int = 0,
    ) {
        val app = context.applicationContext
        vibrate(app, tone, icon)
        val duration = if (long) 3500L else 2000L
        if (overlayReady || Settings.canDrawOverlays(app)) {
            val wm = app.getSystemService(WindowManager::class.java)
            if (showOverlay(app, wm, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, text, duration, tone, icon)) {
                return
            }
            overlayReady = false
        }
        val service = KeepAliveAccessibilityService.instance
        if (service != null) {
            val wm = service.getSystemService(WindowManager::class.java)
            if (showOverlay(service, wm, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, text, duration, tone, icon)) {
                return
            }
        }
        val manager = app.getSystemService(NotificationManager::class.java)
        if (manager.areNotificationsEnabled()) {
            notify(app, manager, text, duration, tone, icon)
            return
        }
        Toast.makeText(
            app,
            text,
            if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT,
        ).show()
    }

    fun dismiss() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            dismissLocked()
        } else {
            main.post { dismissLocked() }
        }
    }

    private fun vibrate(context: Context, tone: Tone, @DrawableRes icon: Int) {
        if (!Prefs(context).vibrate()) return
        try {
            val vibrator = context.getSystemService(VibratorManager::class.java).defaultVibrator
            if (!vibrator.hasVibrator()) return
            val effect = when (tone) {
                // 开启：一下稍长的确认震
                Tone.ON -> VibrationEffect.createOneShot(48, VibrationEffect.DEFAULT_AMPLITUDE)
                // 关闭：两下短震
                Tone.OFF -> VibrationEffect.createWaveform(longArrayOf(0, 28, 70, 28), -1)
                // 中性状态（如振动模式）：短-长；普通错误提示不额外震
                Tone.NEUTRAL -> {
                    if (icon == 0) return
                    VibrationEffect.createWaveform(longArrayOf(0, 22, 45, 55), -1)
                }
            }
            vibrator.vibrate(effect)
        } catch (error: Throwable) {
            Log.w(TAG, "vibrate failed", error)
        }
    }

    private fun showOverlay(
        context: Context,
        wm: WindowManager,
        type: Int,
        text: CharSequence,
        duration: Long,
        tone: Tone,
        @DrawableRes icon: Int,
    ): Boolean {
        return try {
            val current = bubble
            if (current != null && windowManager === wm) {
                bind(context, current, text, tone, icon)
            } else {
                dismissLocked()
                val created = messageView(context, text, tone, icon)
                wm.addView(created.root, layoutParams(context, type))
                bubble = created
                windowManager = wm
            }
            hideTask?.let(main::removeCallbacks)
            val task = Runnable { dismissLocked() }
            hideTask = task
            main.postDelayed(task, duration)
            true
        } catch (error: Throwable) {
            Log.w(TAG, "overlay message failed", error)
            dismissLocked()
            false
        }
    }

    private fun messageView(
        context: Context,
        text: CharSequence,
        tone: Tone,
        @DrawableRes icon: Int,
    ): Bubble {
        val density = context.resources.displayMetrics.density
        val iconView = ImageView(context).apply {
            val size = (22 * density).toInt()
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                marginEnd = (8 * density).toInt()
            }
        }
        val label = TextView(context).apply {
            textSize = 14f
            maxWidth = (240 * density).toInt()
        }
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val padH = (14 * density).toInt()
            val padV = (10 * density).toInt()
            setPadding(padH, padV, padH, padV)
            background = GradientDrawable().apply { cornerRadius = 22 * density }
            elevation = 6 * density
            addView(iconView)
            addView(label)
        }
        val created = Bubble(root, iconView, label)
        bind(context, created, text, tone, icon)
        return created
    }

    private fun bind(
        context: Context,
        bubble: Bubble,
        text: CharSequence,
        tone: Tone,
        @DrawableRes icon: Int,
    ) {
        val colors = palette(context, tone)
        (bubble.root.background as GradientDrawable).setColor(colors.background)
        bubble.label.text = text
        bubble.label.setTextColor(colors.foreground)
        if (icon == 0) {
            bubble.icon.visibility = View.GONE
        } else {
            bubble.icon.visibility = View.VISIBLE
            bubble.icon.setImageResource(icon)
            bubble.icon.setColorFilter(colors.foreground)
        }
    }

    private data class Palette(val background: Int, val foreground: Int)

    private fun palette(context: Context, tone: Tone): Palette {
        return when (tone) {
            Tone.ON -> Palette(0xF21E8E4A.toInt(), 0xFFFFFFFF.toInt())
            Tone.OFF -> Palette(0xF2D14343.toInt(), 0xFFFFFFFF.toInt())
            Tone.NEUTRAL -> {
                val night = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                    Configuration.UI_MODE_NIGHT_YES
                if (night) Palette(0xF22C2C2E.toInt(), 0xFFF5F5F5.toInt()) else Palette(0xF2FFFFFF.toInt(), 0xFF1A1C1E.toInt())
            }
        }
    }

    private fun layoutParams(context: Context, type: Int): WindowManager.LayoutParams {
        val density = context.resources.displayMetrics.density
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = (88 * density).toInt()
            title = "SidekeyFeedback"
        }
    }

    private fun notify(
        context: Context,
        manager: NotificationManager,
        text: CharSequence,
        duration: Long,
        tone: Tone,
        @DrawableRes icon: Int,
    ) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.ak_feedback_channel),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(if (icon != 0) icon else R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(text)
            .setTimeoutAfter(duration)
            .setAutoCancel(true)
            .setColor(palette(context, tone).background)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
        main.postDelayed({ manager.cancel(NOTIFICATION_ID) }, duration)
    }

    private fun dismissLocked() {
        hideTask?.let(main::removeCallbacks)
        hideTask = null
        val shown = bubble?.root
        val wm = windowManager
        bubble = null
        windowManager = null
        if (shown != null && wm != null) {
            runCatching { wm.removeView(shown) }
        }
    }

    private const val TAG = "ActionKeyFeedback"
    private const val CHANNEL_ID = "action_feedback"
    private const val NOTIFICATION_ID = 42
}

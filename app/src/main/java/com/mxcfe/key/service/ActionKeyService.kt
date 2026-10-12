package com.mxcfe.key.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.VibratorManager
import android.util.Log
import com.mxcfe.key.R
import com.mxcfe.key.action.ActionDispatcher
import com.mxcfe.key.action.ActionId
import com.mxcfe.key.action.KeyGesture
import com.mxcfe.key.detect.AiKeyDetector
import com.mxcfe.key.detect.HandlerDelayQueue
import com.mxcfe.key.keepalive.Watchdog
import com.mxcfe.key.prefs.Prefs
import com.mxcfe.key.privilege.AccessibilityEnabler
import com.mxcfe.key.privilege.ShizukuAccess
import com.mxcfe.key.privilege.ShizukuShell
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.atomic.AtomicBoolean

class ActionKeyService : Service() {
    private val stop = AtomicBoolean(false)
    private var reader: Thread? = null
    private var process: Process? = null
    private var detector: AiKeyDetector? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(
            NOTIFICATION_ID,
            notification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
        if (!Prefs(this).enabled()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        running = true
        Watchdog.schedule(this)
        if (reader?.isAlive != true) {
            stop.set(false)
            detector = AiKeyDetector(
                clock = { SystemClock.elapsedRealtime() },
                delays = HandlerDelayQueue(Handler(Looper.getMainLooper())),
                onGesture = ::onGesture,
            )
            reader = Thread(::listen, "ai-key-logcat").also { it.start() }
        }
        Thread(::enableAccessibility, "enable-a11y").start()
        return START_STICKY
    }

    override fun onDestroy() {
        stop.set(true)
        running = false
        state.value = MonitorState.STOPPED
        process?.destroy()
        detector?.reset()
        detector = null
        reader?.interrupt()
        super.onDestroy()
    }

    private fun enableAccessibility() {
        AccessibilityEnabler.enableIfNeeded(this)
    }

    private fun onGesture(gesture: KeyGesture, reason: String) {
        Log.i(TAG, "detected ${gesture.id} :: $reason")
        val prefs = Prefs(this)
        val action = ActionId.fromId(prefs.actionId(gesture))
        // 开关类动作改在结果提示时震动，开启和关闭用不同节奏。
        val deferVibrate = action == ActionId.FLASHLIGHT ||
            action == ActionId.BLUETOOTH ||
            action == ActionId.RING_MODE
        if (prefs.vibrate() && !deferVibrate) {
            try {
                val vibrator = getSystemService(VibratorManager::class.java).defaultVibrator
                vibrator.vibrate(VibrationEffect.createOneShot(24, VibrationEffect.DEFAULT_AMPLITUDE))
            } catch (error: Throwable) {
                Log.w(TAG, "vibrate failed", error)
            }
        }
        ActionDispatcher.dispatch(this, gesture)
    }

    private fun listen() {
        val prefs = Prefs(this)
        while (!stop.get() && prefs.enabled()) {
            if (!ShizukuAccess.granted()) {
                state.value = MonitorState.WAITING
                if (!sleep()) return
                continue
            }
            state.value = MonitorState.STARTING
            try {
                val started = ShizukuShell.start(LOGCAT)
                process = started
                state.value = MonitorState.LISTENING
                started.inputStream.bufferedReader().use { reader ->
                    while (!stop.get()) {
                        val line = reader.readLine() ?: break
                        detector?.onLine(line)
                    }
                }
            } catch (error: Throwable) {
                if (stop.get()) return
                Log.w(TAG, "logcat failed", error)
            } finally {
                process?.destroy()
                process = null
            }
            if (stop.get()) return
            state.value = MonitorState.RETRYING
            if (!sleep()) return
        }
    }

    private fun sleep(): Boolean {
        return try {
            Thread.sleep(1500)
            !stop.get()
        } catch (_: InterruptedException) {
            false
        }
    }

    private fun notification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.ak_channel),
            NotificationManager.IMPORTANCE_LOW,
        )
        manager.createNotificationChannel(channel)
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.ak_service_title))
            .setContentText(getString(R.string.ak_service_text))
            .setOngoing(true)
            .build()
    }

    enum class MonitorState {
        STOPPED,
        STARTING,
        LISTENING,
        RETRYING,
        WAITING,
    }

    companion object {
        @Volatile
        var running: Boolean = false

        val state = MutableStateFlow(MonitorState.STOPPED)

        fun setEnabled(context: Context, enabled: Boolean) {
            Prefs(context).setEnabled(enabled)
            val intent = Intent(context, ActionKeyService::class.java)
            if (enabled) {
                context.startForegroundService(intent)
            } else {
                Watchdog.cancel(context)
                context.stopService(intent)
            }
        }

        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "action_key"
        // 只拉 AI 键相关日志，避免 *:V 全量扫日志拖垮耗电与 CPU。
        private const val LOGCAT =
            "logcat -T 1 -v brief | grep --line-buffered -iE 'action_button|ai_key|handleActionButtonClick|stkeylog|KeyLog_Oplus|pantanal\\.intent\\.business\\.app\\.system\\.action_key|ActionButtonKeyUtils'"
        private const val TAG = "ActionKeyLogcat"
    }
}

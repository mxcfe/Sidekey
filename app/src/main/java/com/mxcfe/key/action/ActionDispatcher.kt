package com.mxcfe.key.action

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.widget.Toast
import com.mxcfe.key.R
import com.mxcfe.key.prefs.Prefs
import com.mxcfe.key.privilege.ShizukuShell
import android.accessibilityservice.AccessibilityService
import com.mxcfe.key.service.KeepAliveAccessibilityService
import com.mxcfe.key.ui.MainActivity
import java.util.concurrent.Executors

object ActionDispatcher {
    private val main = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    @Volatile
    private var torchOn = false

    fun dispatch(context: Context, gesture: KeyGesture) {
        val app = context.applicationContext
        val prefs = Prefs(app)
        val action = ActionId.fromId(prefs.actionId(gesture))
        if (action == ActionId.NONE) return
        executor.execute {
            Log.i(TAG, "dispatch ${action.id} gesture=${gesture.id}")
            run(app, action, gesture)
        }
    }

    private fun run(context: Context, action: ActionId, gesture: KeyGesture) {
        when (action) {
            ActionId.NONE -> Unit
            ActionId.OPEN_APP -> openMain(context)
            ActionId.CUSTOM_APP -> openCustom(context, gesture)
            ActionId.FLASHLIGHT -> toggleFlashlight(context)
            ActionId.CAMERA -> start(context, Intent("android.media.action.STILL_IMAGE_CAMERA"))
            ActionId.SCREENSHOT -> screenshot(context)
            ActionId.NOTIFICATION_PANEL -> notifications(context)
            ActionId.QUICK_SETTINGS -> quickSettings(context)
            ActionId.RING_MODE -> cycleRinger(context)
            ActionId.MEDIA_PLAY_PAUSE -> media(context)
            ActionId.HOME -> home(context)
            ActionId.LOCK -> lock(context)
            ActionId.XIAOBU_MEMORY -> xiaobuMemory(context)
            ActionId.XIAOBU_VOICE_NOTE -> xiaobuVoice(context)
            ActionId.XIAOBU_SCREEN_SEARCH -> xiaobuScreen(context)
        }
    }

    private fun openMain(context: Context) {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        start(context, intent)
    }

    private fun openCustom(context: Context, gesture: KeyGesture) {
        val pkg = Prefs(context).customPackage(gesture)
        if (pkg.isBlank()) {
            toast(context, R.string.ak_custom_app_unset, long = false)
            openMain(context)
            return
        }
        val launch = context.packageManager.getLaunchIntentForPackage(pkg)
        if (launch == null) {
            toast(context, R.string.ak_custom_app_missing, long = false)
            return
        }
        start(context, launch)
    }

    private fun toggleFlashlight(context: Context) {
        try {
            val camera = context.getSystemService(CameraManager::class.java)
            val id = camera.cameraIdList.firstOrNull { cameraId ->
                camera.getCameraCharacteristics(cameraId)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return
            torchOn = !torchOn
            camera.setTorchMode(id, torchOn)
            toast(
                context,
                if (torchOn) R.string.ak_flashlight_on else R.string.ak_flashlight_off,
                long = false,
            )
        } catch (error: Throwable) {
            torchOn = false
            Log.w(TAG, "flashlight failed", error)
        }
    }

    private fun screenshot(context: Context) {
        val service = KeepAliveAccessibilityService.instance
        val ok = service?.globalAction(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT) == true ||
            shell("input keyevent 120") ||
            shell("cmd screenshot")
        if (!ok) toast(context, R.string.ak_screenshot_need_a11y, long = false)
    }

    private fun notifications(context: Context) {
        val ok = KeepAliveAccessibilityService.instance
            ?.globalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS) == true ||
            shell("cmd statusbar expand-notifications")
        if (!ok) toast(context, R.string.ak_need_a11y_or_privilege, long = false)
    }

    private fun quickSettings(context: Context) {
        val ok = KeepAliveAccessibilityService.instance
            ?.globalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS) == true ||
            shell("cmd statusbar expand-settings")
        if (!ok) toast(context, R.string.ak_need_a11y_or_privilege, long = false)
    }

    private fun home(context: Context) {
        val ok = KeepAliveAccessibilityService.instance
            ?.globalAction(AccessibilityService.GLOBAL_ACTION_HOME) == true
        if (ok) return
        start(
            context,
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
        )
    }

    private fun lock(context: Context) {
        val ok = KeepAliveAccessibilityService.instance
            ?.globalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN) == true ||
            shell("input keyevent 26")
        if (!ok) toast(context, R.string.ak_need_a11y_or_privilege, long = false)
    }

    private fun cycleRinger(context: Context) {
        val audio = context.getSystemService(AudioManager::class.java)
        val notifications = context.getSystemService(android.app.NotificationManager::class.java)
        val mode = audio.ringerMode
        if (!notifications.isNotificationPolicyAccessGranted && mode == AudioManager.RINGER_MODE_VIBRATE) {
            toast(context, R.string.ak_ring_need_dnd, long = true)
            start(context, Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            return
        }
        try {
            val next = when (mode) {
                AudioManager.RINGER_MODE_VIBRATE -> AudioManager.RINGER_MODE_SILENT
                AudioManager.RINGER_MODE_NORMAL -> AudioManager.RINGER_MODE_VIBRATE
                else -> AudioManager.RINGER_MODE_NORMAL
            }
            audio.ringerMode = next
            val text = when (next) {
                AudioManager.RINGER_MODE_VIBRATE -> R.string.ak_ring_vibrate
                AudioManager.RINGER_MODE_NORMAL -> R.string.ak_ring_normal
                else -> R.string.ak_ring_silent
            }
            toast(context, text, long = false)
        } catch (error: Throwable) {
            Log.w(TAG, "ring mode failed", error)
            toast(context, R.string.ak_ring_need_dnd, long = true)
        }
    }

    private fun media(context: Context) {
        val audio = context.getSystemService(AudioManager::class.java)
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
    }

    private fun xiaobuMemory(context: Context) {
        val commands = listOf(
            "am start-foreground-service -a oplus.gleanerservice.intent.action.COLLECT_DATA --ei triggerType 2",
            "am start-foreground-service -p com.oplus.gleanerservice --ei triggerType 2",
            "am start-foreground-service -n com.oplus.gleanerservice/.flashnotes.business.service.DataCollectService --ei triggerType 2",
        )
        if (commands.any { shell(it) }) {
            Log.i(TAG, "triggered Xiaobu memory using shell")
            return
        }
        val gestured = KeepAliveAccessibilityService.instance?.threeFingerSwipe() == true
        if (gestured) {
            Log.i(TAG, "triggered Xiaobu memory using accessibility fallback")
        } else {
            toast(context, R.string.ak_xiaobu_memory_need_access, long = true)
        }
    }

    private fun xiaobuVoice(context: Context) {
        val commands = listOf(
            "am start-foreground-service -a oplus.aimemory.intent.action.COLLECT_DATA --ei triggerType 2",
            "am start-foreground-service -p com.oplus.aimemory --ei triggerType 2",
            "am start-foreground-service -n com.oplus.aimemory/.voicecollect.service.DataCollectService --ei triggerType 2",
        )
        if (commands.any { shell(it) }) {
            Log.i(TAG, "triggered Xiaobu voice note using shell")
        } else {
            toast(context, R.string.ak_xiaobu_voice_note_need_privilege, long = true)
        }
    }

    private fun xiaobuScreen(context: Context) {
        if (shell("am startservice -a com.coloros.colordirectservice.ACCESSIBILITY_SCAN")) {
            Log.i(TAG, "triggered Xiaobu screen search using shell")
            return
        }
        val gestured = KeepAliveAccessibilityService.instance?.twoFingerLongPress() == true
        if (gestured) {
            Log.i(TAG, "triggered Xiaobu screen search using accessibility fallback")
        } else {
            toast(context, R.string.ak_xiaobu_screen_need_access, long = true)
        }
    }

    private fun shell(command: String): Boolean = ShizukuShell.exec(command, 4)

    private fun start(context: Context, intent: Intent): Boolean {
        return try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (error: Throwable) {
            Log.w(TAG, "startActivity failed $intent", error)
            false
        }
    }

    private fun toast(context: Context, res: Int, long: Boolean) {
        main.post {
            Toast.makeText(
                context,
                res,
                if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT,
            ).show()
        }
    }

    private const val TAG = "ActionKeyDispatcher"
}

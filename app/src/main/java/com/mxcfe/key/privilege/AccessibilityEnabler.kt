package com.mxcfe.key.privilege

import android.content.Context
import android.provider.Settings
import android.util.Log
import com.mxcfe.key.service.KeepAliveAccessibilityService

object AccessibilityEnabler {
    fun enableIfNeeded(context: Context) {
        if (isEnabled(context)) return
        if (!ShizukuAccess.granted()) return
        val component = componentName(context)
        val script = """
            svc=$component
            appops set ${context.packageName} ACCESS_RESTRICTED_SETTINGS allow
            cur=${'$'}(settings get secure enabled_accessibility_services)
            if [ -z "${'$'}cur" ] || [ "${'$'}cur" = "null" ]; then
              settings put secure enabled_accessibility_services "${'$'}svc"
            else
              case ":${'$'}cur:" in
                *":${'$'}svc:"*) ;;
                *) settings put secure enabled_accessibility_services "${'$'}cur:${'$'}svc" ;;
              esac
            fi
            settings put secure accessibility_enabled 1
        """.trimIndent()
        val ok = ShizukuShell.exec(script, 8)
        Log.i(TAG, if (ok) "accessibility enabled via shizuku" else "accessibility enable command failed")
    }

    fun isEnabled(context: Context): Boolean {
        val expected = componentName(context)
        val current = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        return current.split(':').any { it.equals(expected, ignoreCase = true) }
    }

    private fun componentName(context: Context): String {
        return "${context.packageName}/${KeepAliveAccessibilityService::class.java.name}"
    }

    private const val TAG = "EnableA11y"
}

package com.mxcfe.key.prefs

import android.content.Context
import android.content.SharedPreferences
import com.mxcfe.key.action.ActionId
import com.mxcfe.key.action.KeyGesture

class Prefs(context: Context) {
    val raw: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun enabled(): Boolean = raw.getBoolean(KEY_ENABLED, false)

    fun setEnabled(value: Boolean) {
        raw.edit().putBoolean(KEY_ENABLED, value).apply()
    }

    fun vibrate(): Boolean = raw.getBoolean(KEY_VIBRATE, true)

    fun setVibrate(value: Boolean) {
        raw.edit().putBoolean(KEY_VIBRATE, value).apply()
    }

    fun actionId(gesture: KeyGesture): String {
        return raw.getString(actionKey(gesture), ActionId.NONE.id) ?: ActionId.NONE.id
    }

    fun setAction(gesture: KeyGesture, id: String) {
        raw.edit().putString(actionKey(gesture), id).apply()
    }

    fun customPackage(gesture: KeyGesture): String {
        return raw.getString(pkgKey(gesture), "") ?: ""
    }

    fun customLabel(gesture: KeyGesture): String {
        return raw.getString(labelKey(gesture), "") ?: ""
    }

    fun setCustomApp(gesture: KeyGesture, packageName: String, label: String) {
        raw.edit()
            .putString(actionKey(gesture), ActionId.CUSTOM_APP.id)
            .putString(pkgKey(gesture), packageName)
            .putString(labelKey(gesture), label)
            .apply()
    }

    fun pageUrl(gesture: KeyGesture): String {
        return raw.getString(urlKey(gesture), "") ?: ""
    }

    fun setPageUrl(gesture: KeyGesture, url: String) {
        raw.edit()
            .putString(actionKey(gesture), ActionId.OPEN_URL.id)
            .putString(urlKey(gesture), url)
            .apply()
    }

    fun setAppShortcut(
        gesture: KeyGesture,
        packageName: String,
        appLabel: String,
        shortcutId: String,
        shortcutLabel: String,
    ) {
        raw.edit()
            .putString(pkgKey(gesture), packageName)
            .putString(labelKey(gesture), appLabel)
            .putString(shortcutIdKey(gesture), shortcutId)
            .putString(shortcutLabelKey(gesture), shortcutLabel)
            .apply()
    }

    fun themeMode(): String {
        val value = raw.getString(KEY_THEME, THEME_SYSTEM)
        return if (value == THEME_LIGHT || value == THEME_DARK) value else THEME_SYSTEM
    }

    fun setThemeMode(value: String) {
        val stored = if (value == THEME_LIGHT || value == THEME_DARK) value else THEME_SYSTEM
        raw.edit().putString(KEY_THEME, stored).apply()
    }

    fun hideFromRecents(): Boolean = raw.getBoolean(KEY_HIDE, false)

    fun setHideFromRecents(value: Boolean) {
        raw.edit().putBoolean(KEY_HIDE, value).apply()
    }

    companion object {
        const val FILE = "smart_scan_prefs"
        const val KEY_ENABLED = "action_key_enabled"
        const val KEY_LONG = "action_key_ai_long_action_id"
        const val KEY_DOUBLE = "action_key_ai_double_action_id"
        const val KEY_VIBRATE = "action_key_vibrate"
        const val KEY_PKG_LONG = "custom_app_pkg_long"
        const val KEY_LABEL_LONG = "custom_app_label_long"
        const val KEY_PKG_DOUBLE = "custom_app_pkg_double"
        const val KEY_LABEL_DOUBLE = "custom_app_label_double"
        const val KEY_URL_LONG = "page_url_long"
        const val KEY_URL_DOUBLE = "page_url_double"
        const val KEY_SHORTCUT_ID_LONG = "app_shortcut_id_long"
        const val KEY_SHORTCUT_ID_DOUBLE = "app_shortcut_id_double"
        const val KEY_SHORTCUT_LABEL_LONG = "app_shortcut_label_long"
        const val KEY_SHORTCUT_LABEL_DOUBLE = "app_shortcut_label_double"

        private fun actionKey(gesture: KeyGesture): String = when (gesture) {
            KeyGesture.LONG -> KEY_LONG
            KeyGesture.DOUBLE -> KEY_DOUBLE
        }

        private fun pkgKey(gesture: KeyGesture): String = when (gesture) {
            KeyGesture.LONG -> KEY_PKG_LONG
            KeyGesture.DOUBLE -> KEY_PKG_DOUBLE
        }

        private fun labelKey(gesture: KeyGesture): String = when (gesture) {
            KeyGesture.LONG -> KEY_LABEL_LONG
            KeyGesture.DOUBLE -> KEY_LABEL_DOUBLE
        }

        private fun urlKey(gesture: KeyGesture): String = when (gesture) {
            KeyGesture.LONG -> KEY_URL_LONG
            KeyGesture.DOUBLE -> KEY_URL_DOUBLE
        }

        private fun shortcutIdKey(gesture: KeyGesture): String = when (gesture) {
            KeyGesture.LONG -> KEY_SHORTCUT_ID_LONG
            KeyGesture.DOUBLE -> KEY_SHORTCUT_ID_DOUBLE
        }

        private fun shortcutLabelKey(gesture: KeyGesture): String = when (gesture) {
            KeyGesture.LONG -> KEY_SHORTCUT_LABEL_LONG
            KeyGesture.DOUBLE -> KEY_SHORTCUT_LABEL_DOUBLE
        }
        const val KEY_THEME = "theme_mode"
        const val KEY_HIDE = "hide_from_recents"
        const val THEME_SYSTEM = "system"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"
    }
}

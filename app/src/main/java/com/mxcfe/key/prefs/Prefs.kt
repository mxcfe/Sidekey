package com.mxcfe.key.prefs

import android.content.Context
import android.content.SharedPreferences
import com.mxcfe.key.action.ActionId

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

    fun shortActionId(): String {
        val primary = raw.getString(KEY_SHORT, null)
        if (!primary.isNullOrEmpty()) return primary
        return raw.getString(KEY_LEGACY, ActionId.NONE.id) ?: ActionId.NONE.id
    }

    fun setShortAction(id: String) {
        raw.edit()
            .putString(KEY_SHORT, id)
            .putString(KEY_LEGACY, id)
            .apply()
    }

    fun customPackage(): String {
        val slot = raw.getString(KEY_PKG, null)
        if (!slot.isNullOrEmpty()) return slot
        return raw.getString(KEY_PKG_LEGACY, "") ?: ""
    }

    fun customLabel(): String {
        val slot = raw.getString(KEY_LABEL, null)
        if (!slot.isNullOrEmpty()) return slot
        return raw.getString(KEY_LABEL_LEGACY, "") ?: ""
    }

    fun setCustomApp(packageName: String, label: String) {
        raw.edit()
            .putString(KEY_SHORT, ActionId.CUSTOM_APP.id)
            .putString(KEY_LEGACY, ActionId.CUSTOM_APP.id)
            .putString(KEY_PKG, packageName)
            .putString(KEY_LABEL, label)
            .putString(KEY_PKG_LEGACY, packageName)
            .putString(KEY_LABEL_LEGACY, label)
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
        const val KEY_SHORT = "action_key_ai_short_action_id"
        const val KEY_LEGACY = "action_key_action_id"
        const val KEY_VIBRATE = "action_key_vibrate"
        const val KEY_PKG = "custom_app_pkg_single"
        const val KEY_LABEL = "custom_app_label_single"
        const val KEY_PKG_LEGACY = "custom_app_package"
        const val KEY_LABEL_LEGACY = "custom_app_label"
        const val KEY_THEME = "theme_mode"
        const val KEY_HIDE = "hide_from_recents"
        const val THEME_SYSTEM = "system"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"
    }
}

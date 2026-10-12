package com.mxcfe.key.action

import androidx.annotation.StringRes
import com.mxcfe.key.R

enum class ActionId(
    val id: String,
    @StringRes val title: Int,
    val inAppGroup: Boolean,
) {
    OPEN_APP("open_app", R.string.ak_action_open_app, true),
    NONE("none", R.string.ak_action_none, true),
    CUSTOM_APP("custom_app", R.string.ak_action_custom_app, false),
    OPEN_URL("open_url", R.string.ak_action_open_url, false),
    FLASHLIGHT("flashlight", R.string.ak_action_flashlight, false),
    BLUETOOTH("bluetooth", R.string.ak_action_bluetooth, false),
    CAMERA("camera", R.string.ak_action_camera, false),
    SCREENSHOT("screenshot", R.string.ak_action_screenshot, false),
    NOTIFICATION_PANEL("notification_panel", R.string.ak_action_notification_panel, false),
    QUICK_SETTINGS("quick_settings", R.string.ak_action_quick_settings, false),
    RING_MODE("ring_mode", R.string.ak_action_ring_mode, false),
    MEDIA_PLAY_PAUSE("media_play_pause", R.string.ak_action_media_play_pause, false),
    HOME("home", R.string.ak_action_home, false),
    LOCK("lock", R.string.ak_action_lock, false),
    XIAOBU_MEMORY("xiaobu_memory", R.string.ak_action_xiaobu_memory, false),
    XIAOBU_VOICE_NOTE("xiaobu_voice_note", R.string.ak_action_xiaobu_voice_note, false),
    XIAOBU_SCREEN_SEARCH("xiaobu_screen_search", R.string.ak_action_xiaobu_screen_search, false);

    companion object {
        fun fromId(id: String?): ActionId {
            return entries.firstOrNull { it.id == id } ?: NONE
        }
    }
}

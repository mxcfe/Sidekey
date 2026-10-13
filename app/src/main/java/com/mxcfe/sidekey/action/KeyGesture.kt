package com.mxcfe.sidekey.action

enum class KeyGesture(val id: String) {
    LONG("long"),
    DOUBLE("double");

    companion object {
        fun fromId(id: String?): KeyGesture = entries.firstOrNull { it.id == id } ?: LONG
    }
}

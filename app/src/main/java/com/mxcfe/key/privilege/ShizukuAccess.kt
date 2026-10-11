package com.mxcfe.key.privilege

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

object ShizukuAccess {
    fun binderAlive(): Boolean = try {
        Shizuku.pingBinder()
    } catch (_: Throwable) {
        false
    }

    fun granted(): Boolean = try {
        binderAlive() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) {
        false
    }

    const val REQUEST_CODE = 1001
}

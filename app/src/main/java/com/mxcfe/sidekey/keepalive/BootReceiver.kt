package com.mxcfe.sidekey.keepalive

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mxcfe.sidekey.prefs.Prefs
import com.mxcfe.sidekey.service.ActionKeyService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!Prefs(context).enabled()) return
        context.startForegroundService(Intent(context, ActionKeyService::class.java))
    }
}

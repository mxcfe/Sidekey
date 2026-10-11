package com.mxcfe.key.keepalive

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mxcfe.key.prefs.Prefs
import com.mxcfe.key.service.ActionKeyService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!Prefs(context).enabled()) return
        context.startForegroundService(Intent(context, ActionKeyService::class.java))
    }
}

package com.mxcfe.key.keepalive

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mxcfe.key.prefs.Prefs
import com.mxcfe.key.service.ActionKeyService

class WatchdogReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Watchdog.ACTION) return
        if (!Prefs(context).enabled()) return
        Watchdog.schedule(context)
        if (!ActionKeyService.running) {
            context.startForegroundService(Intent(context, ActionKeyService::class.java))
        }
    }
}

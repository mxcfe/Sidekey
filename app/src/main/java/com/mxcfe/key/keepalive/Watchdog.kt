package com.mxcfe.key.keepalive

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log

object Watchdog {
    const val ACTION = "com.mxcfe.key.action.WATCHDOG"
    private const val INTERVAL_MS = 180_000L

    fun schedule(context: Context) {
        val alarm = context.getSystemService(AlarmManager::class.java)
        val trigger = SystemClock.elapsedRealtime() + INTERVAL_MS
        alarm.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pending(context))
        Log.i(TAG, "scheduled in ${INTERVAL_MS}ms")
    }

    fun cancel(context: Context) {
        val alarm = context.getSystemService(AlarmManager::class.java)
        alarm.cancel(pending(context))
    }

    private fun pending(context: Context): PendingIntent {
        val intent = Intent(context, WatchdogReceiver::class.java).setAction(ACTION)
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private const val TAG = "KeepAliveWatchdog"
}

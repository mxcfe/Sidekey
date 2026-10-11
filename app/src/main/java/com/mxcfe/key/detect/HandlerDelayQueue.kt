package com.mxcfe.key.detect

import android.os.Handler

class HandlerDelayQueue(private val handler: Handler) : DelayQueue {
    private val runnables = HashMap<Any, Runnable>()

    override fun post(token: Any, delayMs: Long, block: () -> Unit) {
        cancel(token)
        val runnable = Runnable { block() }
        runnables[token] = runnable
        handler.postDelayed(runnable, delayMs)
    }

    override fun cancel(token: Any) {
        runnables.remove(token)?.let(handler::removeCallbacks)
    }
}

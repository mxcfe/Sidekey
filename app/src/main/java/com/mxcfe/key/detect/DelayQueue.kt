package com.mxcfe.key.detect

interface DelayQueue {
    fun post(token: Any, delayMs: Long, block: () -> Unit)
    fun cancel(token: Any)
}

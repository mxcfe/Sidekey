package com.mxcfe.sidekey.detect

interface DelayQueue {
    fun post(token: Any, delayMs: Long, block: () -> Unit)
    fun cancel(token: Any)
}

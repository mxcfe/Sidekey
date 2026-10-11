package com.mxcfe.key.detect

import org.junit.Assert.assertEquals
import org.junit.Test

class AiKeyDetectorTest {
    private class VirtualDelayQueue : DelayQueue {
        var now = 0L
        private val tasks = mutableListOf<Task>()

        override fun post(token: Any, delayMs: Long, block: () -> Unit) {
            cancel(token)
            tasks += Task(token, now + delayMs, block)
        }

        override fun cancel(token: Any) {
            tasks.removeAll { it.token === token }
        }

        fun advance(ms: Long) {
            val target = now + ms
            while (true) {
                val next = tasks.filter { it.at <= target }.minByOrNull { it.at } ?: break
                tasks.remove(next)
                now = next.at
                next.block()
            }
            now = target
        }

        private data class Task(val token: Any, val at: Long, val block: () -> Unit)
    }

    @Test
    fun summaryBecomesOneShortPressAt600ms() {
        val queue = VirtualDelayQueue()
        val hits = mutableListOf<String>()
        val detector = AiKeyDetector(clock = { queue.now }, delays = queue, onShortPress = hits::add)
        detector.onLine("PhoneWindowManager: handleActionButtonClick")
        queue.advance(599)
        assertEquals(emptyList<String>(), hits)
        queue.advance(1)
        assertEquals(listOf("aggregated SHORT x1"), hits)
    }

    @Test
    fun summariesInside120msCountOnce() {
        val queue = VirtualDelayQueue()
        val hits = mutableListOf<String>()
        val detector = AiKeyDetector(clock = { queue.now }, delays = queue, onShortPress = hits::add)
        detector.onLine("handleActionButtonClick")
        queue.advance(50)
        detector.onLine("handleActionButtonClick again")
        queue.advance(600)
        assertEquals(listOf("aggregated SHORT x1"), hits)
    }

    @Test
    fun rawClickCancelsPendingAggregate() {
        val queue = VirtualDelayQueue()
        val hits = mutableListOf<String>()
        val detector = AiKeyDetector(clock = { queue.now }, delays = queue, onShortPress = hits::add)
        detector.onLine("keycode_action_button_click action_down")
        detector.onLine("keycode_action_button_click action_up")
        queue.advance(1)
        detector.onLine("handleActionButtonClick")
        queue.advance(80)
        assertEquals(listOf("raw click channel (no gesture channel)"), hits)
        queue.advance(2000)
        assertEquals(1, hits.size)
    }

    @Test
    fun clickUpWithoutDownDoesNothing() {
        val queue = VirtualDelayQueue()
        val hits = mutableListOf<String>()
        val detector = AiKeyDetector(clock = { queue.now }, delays = queue, onShortPress = hits::add)
        detector.onLine("keycode_action_button_click action_up")
        queue.advance(500)
        assertEquals(emptyList<String>(), hits)
    }

    @Test
    fun ownLogLinesAreIgnored() {
        val queue = VirtualDelayQueue()
        val hits = mutableListOf<String>()
        val detector = AiKeyDetector(clock = { queue.now }, delays = queue, onShortPress = hits::add)
        detector.onLine("I/ActionKeyLogcat: handleActionButtonClick")
        queue.advance(1000)
        assertEquals(emptyList<String>(), hits)
    }

    @Test
    fun realKeyDownDoesNotEmitOnUp() {
        val queue = VirtualDelayQueue()
        val hits = mutableListOf<String>()
        val detector = AiKeyDetector(clock = { queue.now }, delays = queue, onShortPress = hits::add)
        detector.onLine("keycode_action_button action_down")
        detector.onLine("keycode_action_button action_up")
        detector.onLine("handleActionButtonClick")
        queue.advance(2000)
        assertEquals(emptyList<String>(), hits)
    }
}

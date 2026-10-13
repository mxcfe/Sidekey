package com.mxcfe.sidekey.detect

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

    private fun detector(queue: VirtualDelayQueue, hits: MutableList<String>): AiKeyDetector {
        return AiKeyDetector(clock = { queue.now }, delays = queue) { gesture, reason ->
            hits += "${gesture.id}:$reason"
        }
    }

    @Test
    fun singleSummaryDoesNotFire() {
        val queue = VirtualDelayQueue()
        val hits = mutableListOf<String>()
        val detector = detector(queue, hits)
        detector.onLine("PhoneWindowManager: handleActionButtonClick")
        queue.advance(2000)
        assertEquals(emptyList<String>(), hits)
    }

    @Test
    fun summariesInside120msCountOnce() {
        val queue = VirtualDelayQueue()
        val hits = mutableListOf<String>()
        val detector = detector(queue, hits)
        detector.onLine("handleActionButtonClick")
        queue.advance(50)
        detector.onLine("handleActionButtonClick again")
        queue.advance(2000)
        assertEquals(emptyList<String>(), hits)
    }

    @Test
    fun twoSummariesBecomeDoubleClick() {
        val queue = VirtualDelayQueue()
        val hits = mutableListOf<String>()
        val detector = detector(queue, hits)
        detector.onLine("handleActionButtonClick")
        queue.advance(200)
        detector.onLine("handleActionButtonClick second")
        queue.advance(399)
        assertEquals(emptyList<String>(), hits)
        queue.advance(1)
        assertEquals(listOf("double:aggregated DOUBLE x2"), hits)
    }

    @Test
    fun rawClickDoesNotFireAndCancelsAggregate() {
        val queue = VirtualDelayQueue()
        val hits = mutableListOf<String>()
        val detector = detector(queue, hits)
        detector.onLine("keycode_action_button_click action_down")
        detector.onLine("keycode_action_button_click action_up")
        queue.advance(1)
        detector.onLine("handleActionButtonClick")
        queue.advance(2000)
        assertEquals(emptyList<String>(), hits)
    }

    @Test
    fun twoRawClicksBecomeDouble() {
        val queue = VirtualDelayQueue()
        val hits = mutableListOf<String>()
        val detector = detector(queue, hits)
        detector.onLine("keycode_action_button_click action_down")
        detector.onLine("keycode_action_button_click action_up")
        queue.advance(120)
        detector.onLine("keycode_action_button_click action_down")
        detector.onLine("keycode_action_button_click action_up")
        queue.advance(AiKeyDetector.DOUBLE_TAP_MS - 1)
        assertEquals(emptyList<String>(), hits)
        queue.advance(1)
        assertEquals(listOf("double:raw double click"), hits)
    }

    @Test
    fun clickUpWithoutDownDoesNothing() {
        val queue = VirtualDelayQueue()
        val hits = mutableListOf<String>()
        val detector = detector(queue, hits)
        detector.onLine("keycode_action_button_click action_up")
        queue.advance(500)
        assertEquals(emptyList<String>(), hits)
    }

    @Test
    fun ownLogLinesAreIgnored() {
        val queue = VirtualDelayQueue()
        val hits = mutableListOf<String>()
        val detector = detector(queue, hits)
        detector.onLine("I/ActionKeyLogcat: handleActionButtonClick")
        queue.advance(1000)
        assertEquals(emptyList<String>(), hits)
    }

    @Test
    fun realKeyDownDoesNotEmitOnUp() {
        val queue = VirtualDelayQueue()
        val hits = mutableListOf<String>()
        val detector = detector(queue, hits)
        detector.onLine("keycode_action_button action_down")
        detector.onLine("keycode_action_button action_up")
        detector.onLine("handleActionButtonClick")
        queue.advance(2000)
        assertEquals(emptyList<String>(), hits)
    }

    @Test
    fun longPressLogFiresOnceAndBlocksSummary() {
        val queue = VirtualDelayQueue()
        val hits = mutableListOf<String>()
        val detector = detector(queue, hits)
        detector.onLine("keycode_action_button_long_press")
        detector.onLine("keycode_action_button_long_press action_up")
        detector.onLine("handleActionButtonClick")
        queue.advance(2000)
        assertEquals(listOf("long:long press channel"), hits)
    }

    @Test
    fun holdFiresLongPress() {
        val queue = VirtualDelayQueue()
        val hits = mutableListOf<String>()
        val detector = detector(queue, hits)
        detector.onLine("keycode_action_button_click action_down")
        queue.advance(AiKeyDetector.LONG_PRESS_MS - 1)
        assertEquals(emptyList<String>(), hits)
        queue.advance(1)
        detector.onLine("keycode_action_button_click action_up")
        queue.advance(2000)
        assertEquals(listOf("long:hold"), hits)
    }

    @Test
    fun doubleClickLogCancelsPendingSummary() {
        val queue = VirtualDelayQueue()
        val hits = mutableListOf<String>()
        val detector = detector(queue, hits)
        detector.onLine("handleActionButtonClick")
        detector.onLine("keycode_action_button_double_tap")
        queue.advance(2000)
        assertEquals(listOf("double:double click channel"), hits)
    }

    @Test
    fun tripleClickDoesNotFire() {
        val queue = VirtualDelayQueue()
        val hits = mutableListOf<String>()
        val detector = detector(queue, hits)
        detector.onLine("keycode_action_button_triple_tap")
        detector.onLine("handleActionButtonClick")
        queue.advance(200)
        detector.onLine("handleActionButtonClick second")
        queue.advance(2000)
        assertEquals(emptyList<String>(), hits)
    }
}

package com.mxcfe.key.detect

import java.util.ArrayDeque
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/**
 * AI 键短按检测。日志行的分类、120ms 汇总节流、80ms 原始点击、
 * 聚合延时，以及两条回调互相取消，与当前软件一致。
 */
class AiKeyDetector(
    private val clock: () -> Long,
    private val delays: DelayQueue,
    private val onShortPress: (String) -> Unit,
) {
    private val rawToken = Any()
    private val aggToken = Any()
    private val clickCount = AtomicInteger(0)
    private val intervals = ArrayDeque<Long>()

    private var downAt = -1L
    private var rawPending = false
    private var lastSummaryGate = -1L
    private var lastSummaryAt = -1L
    private var realClick = false
    private var suppress = false
    private var longSeen = false
    private var lastDownEvent = Long.MIN_VALUE
    private var lastUpEvent = Long.MIN_VALUE

    fun onLine(raw: String) {
        if (raw.contains("ActionKeyLogcat")) return
        val lower = raw.lowercase(Locale.ROOT)
        if (containsAny(lower, MIUI)) return
        if (!containsAny(lower, KEYWORDS)) return
        if (isSummary(lower)) {
            onSummary()
            return
        }
        if (isGesture(lower)) return
        if (!containsAny(lower, KEY_NAMES)) return

        val realKey = lower.contains("keycode_action_button") &&
            !lower.contains("keycode_action_button_click")
        val eventTime = EVENT_TIME.find(lower)?.groupValues?.getOrNull(1)?.toLongOrNull()
        when {
            isUp(lower) -> onKeyed(isDown = false, realKey = realKey, eventTime = eventTime)
            isDown(lower) -> onKeyed(isDown = true, realKey = realKey, eventTime = eventTime)
        }
    }

    fun reset() {
        delays.cancel(rawToken)
        delays.cancel(aggToken)
        rawPending = false
        downAt = -1L
        clickCount.set(0)
        lastSummaryGate = -1L
        realClick = false
        suppress = false
        lastDownEvent = Long.MIN_VALUE
        lastUpEvent = Long.MIN_VALUE
        lastSummaryAt = -1L
        intervals.clear()
        longSeen = false
    }

    private fun onKeyed(isDown: Boolean, realKey: Boolean, eventTime: Long?) {
        if (eventTime != null) {
            if (isDown) {
                if (eventTime == lastDownEvent) return
                lastDownEvent = eventTime
            } else {
                if (eventTime == lastUpEvent) return
                lastUpEvent = eventTime
            }
        }
        if (realKey && !suppress) {
            suppress = true
            delays.cancel(aggToken)
            clickCount.set(0)
            rawPending = false
            delays.cancel(rawToken)
            downAt = -1L
        }
        if (realKey && suppress) return
        if (isDown) {
            downAt = clock()
            longSeen = false
            realClick = false
            return
        }
        if (downAt < 0L) return
        downAt = -1L
        if (longSeen) {
            longSeen = false
            return
        }
        delays.cancel(rawToken)
        rawPending = true
        delays.post(rawToken, 80L, ::onRaw)
    }

    private fun onSummary() {
        if (suppress) return
        val now = clock()
        if (lastSummaryGate >= 0L && now - lastSummaryGate < 120L) return
        lastSummaryGate = now
        if (downAt >= 0L) return
        realClick = true
        if (lastSummaryAt >= 0L) {
            intervals.addLast(now - lastSummaryAt)
            while (intervals.size > 8) intervals.removeFirst()
        }
        lastSummaryAt = now
        clickCount.incrementAndGet()
        delays.cancel(aggToken)
        delays.post(aggToken, aggregateDelay(), ::onAggregate)
    }

    private fun aggregateDelay(): Long {
        if (intervals.isEmpty()) return 480L
        val average = intervals.sum().toDouble() / intervals.size
        return (average * 1.6).toLong().coerceIn(400L, 600L)
    }

    private fun onAggregate() {
        val count = clickCount.get()
        if (count == 1 && realClick) {
            val elapsed = clock() - lastSummaryAt
            if (elapsed < 600L) {
                delays.post(aggToken, 600L - elapsed, ::onAggregate)
                return
            }
        }
        clickCount.set(0)
        delays.cancel(rawToken)
        rawPending = false
        onShortPress("aggregated SHORT x$count")
    }

    private fun onRaw() {
        if (!rawPending) return
        rawPending = false
        delays.cancel(aggToken)
        clickCount.set(0)
        onShortPress("raw click channel (no gesture channel)")
    }

    private fun isGesture(lower: String): Boolean {
        val gesture = isLong(lower) || isDouble(lower) || isTriple(lower)
        if (!gesture) return false
        if (isUp(lower)) return true
        if (isLong(lower)) {
            longSeen = true
            rawPending = false
            delays.cancel(rawToken)
        }
        return true
    }

    companion object {
        private val MIUI = listOf("miuiinputkeyeventlog", "miaikeyrule")
        private val KEYWORDS = listOf(
            "keycode_action_button",
            "action_button",
            "ai_key",
            "handleactionbuttonclick",
            "stkeylog",
            "keylog_opluskeyeventutil",
            "pantanal.intent.business.app.system.action_key",
            "actionbuttonkeyutils",
        )
        private val KEY_NAMES = listOf("keycode_action_button", "action_button", "ai_key")
        private val EVENT_TIME = Regex("eventtime\\s*[:=]\\s*(\\d+)")

        private fun containsAny(line: String, needles: List<String>): Boolean {
            return needles.any { line.contains(it) }
        }

        private fun isSummary(lower: String): Boolean {
            if (lower.contains("handleactionbuttonclick")) return true
            if (lower.contains("stkeylog") && lower.contains("action button")) return true
            if (lower.contains("keylog_opluskeyeventutil") && lower.contains("undefined keys")) return true
            if (lower.contains("pantanal.intent.business.app.system.action_key")) return true
            return lower.contains("actionbuttonkeyutils") && lower.contains("isappinstalled")
        }

        private fun isUp(lower: String): Boolean {
            return lower.contains("action_up") || lower.contains("action=1")
        }

        private fun isDown(lower: String): Boolean {
            return lower.contains("action_down") || lower.contains("action=0")
        }

        private fun isLong(lower: String): Boolean {
            return lower.contains("keycode_action_button_long_press") ||
                lower.contains("longpress") ||
                lower.contains("long_press") ||
                lower.contains("long_click")
        }

        private fun isDouble(lower: String): Boolean {
            return lower.contains("keycode_action_button_double_tap") ||
                lower.contains("doubleclick") ||
                lower.contains("double_click") ||
                lower.contains("clickcount=2") ||
                lower.contains("clickcount:2")
        }

        private fun isTriple(lower: String): Boolean {
            return lower.contains("keycode_action_button_triple_tap") ||
                lower.contains("tripleclick") ||
                lower.contains("triple_click") ||
                lower.contains("clickcount=3") ||
                lower.contains("clickcount:3")
        }
    }
}

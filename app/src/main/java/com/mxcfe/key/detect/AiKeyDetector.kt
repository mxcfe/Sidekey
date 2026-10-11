package com.mxcfe.key.detect

import com.mxcfe.key.action.KeyGesture
import java.util.ArrayDeque
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/**
 * AI 键手势检测，只回调长按和双击。单击会被吃掉，用来把双击和长按分开。
 * 汇总行 120ms 内只计一次，单击汇总至少等到 600ms。原始点击等双击窗口结束再判定，
 * 按住达到长按时间则视为长按。系统日志里的 long / double 会立刻生效，并取消尚未发出的单击。
 */
class AiKeyDetector(
    private val clock: () -> Long,
    private val delays: DelayQueue,
    private val onGesture: (KeyGesture, String) -> Unit,
) {
    private val rawToken = Any()
    private val aggToken = Any()
    private val longToken = Any()
    private val clickCount = AtomicInteger(0)
    private val intervals = ArrayDeque<Long>()

    private var downAt = -1L
    private var rawPending = false
    private var rawClicks = 0
    private var lastSummaryGate = -1L
    private var lastSummaryAt = -1L
    private var realClick = false
    private var suppress = false
    private var longSeen = false
    private var lastDownEvent = Long.MIN_VALUE
    private var lastUpEvent = Long.MIN_VALUE
    private var blockSummariesUntil = 0L
    private var ignoreKeyedUntil = 0L
    private var lastEmitAt = Long.MIN_VALUE
    private var lastEmit: KeyGesture? = null

    fun onLine(raw: String) {
        if (raw.contains("ActionKeyLogcat")) return
        val lower = raw.lowercase(Locale.ROOT)
        if (containsAny(lower, MIUI)) return
        if (!containsAny(lower, KEYWORDS)) return
        if (isLong(lower) || isDouble(lower) || isTriple(lower)) {
            onGestureLine(lower)
            return
        }
        if (isSummary(lower)) {
            onSummary()
            return
        }
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
        delays.cancel(longToken)
        rawPending = false
        rawClicks = 0
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
        blockSummariesUntil = 0L
        ignoreKeyedUntil = 0L
        lastEmitAt = Long.MIN_VALUE
        lastEmit = null
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
            delays.cancel(rawToken)
            delays.cancel(longToken)
            clickCount.set(0)
            rawPending = false
            rawClicks = 0
            downAt = -1L
        }
        if (realKey && suppress) return
        if (clock() < ignoreKeyedUntil) {
            delays.cancel(longToken)
            downAt = -1L
            return
        }
        if (isDown) {
            delays.cancel(rawToken)
            downAt = clock()
            longSeen = false
            realClick = false
            delays.post(longToken, LONG_PRESS_MS, ::onHold)
            return
        }
        delays.cancel(longToken)
        if (longSeen) {
            longSeen = false
            downAt = -1L
            return
        }
        if (downAt < 0L) return
        val held = clock() - downAt
        downAt = -1L
        if (held >= LONG_PRESS_MS) {
            rawPending = false
            rawClicks = 0
            clickCount.set(0)
            delays.cancel(rawToken)
            delays.cancel(aggToken)
            emit(KeyGesture.LONG, "hold")
            return
        }
        rawClicks++
        rawPending = true
        delays.post(rawToken, DOUBLE_TAP_MS, ::onRaw)
    }

    private fun onHold() {
        if (downAt < 0L || longSeen) return
        longSeen = true
        consumePending()
        emit(KeyGesture.LONG, "hold")
    }

    private fun onSummary() {
        if (suppress) return
        val now = clock()
        if (now < blockSummariesUntil) return
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
        rawClicks = 0
        if (count == 2) emit(KeyGesture.DOUBLE, "aggregated DOUBLE x$count")
    }

    private fun onRaw() {
        if (!rawPending) return
        val count = rawClicks
        rawPending = false
        rawClicks = 0
        delays.cancel(aggToken)
        clickCount.set(0)
        if (count == 2) emit(KeyGesture.DOUBLE, "raw double click")
    }

    private fun onGestureLine(lower: String) {
        if (isTriple(lower)) {
            if (!isUp(lower)) consumePending()
            return
        }
        if (isLong(lower)) {
            if (isUp(lower)) {
                if (!longSeen) {
                    consumePending()
                    emit(KeyGesture.LONG, "long press channel")
                }
                longSeen = false
                return
            }
            if (longSeen) return
            longSeen = true
            consumePending()
            emit(KeyGesture.LONG, "long press channel")
            return
        }
        if (isUp(lower)) return
        consumePending()
        emit(KeyGesture.DOUBLE, "double click channel")
    }

    private fun consumePending() {
        rawPending = false
        rawClicks = 0
        clickCount.set(0)
        realClick = false
        delays.cancel(rawToken)
        delays.cancel(aggToken)
        delays.cancel(longToken)
        val now = clock()
        blockSummariesUntil = now + 500L
        ignoreKeyedUntil = now + 400L
    }

    private fun emit(gesture: KeyGesture, reason: String) {
        val now = clock()
        if (gesture == lastEmit && now - lastEmitAt < 400L) return
        lastEmit = gesture
        lastEmitAt = now
        onGesture(gesture, reason)
    }

    companion object {
        const val LONG_PRESS_MS = 400L
        const val DOUBLE_TAP_MS = 300L

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

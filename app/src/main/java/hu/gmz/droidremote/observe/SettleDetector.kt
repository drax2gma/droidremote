package hu.gmz.droidremote.observe

import kotlinx.coroutines.delay

object SettleDetector {

    data class SettleResult(val stable: Boolean, val changed: Boolean, val waitedMs: Long)

    suspend fun wait(
        before: String,
        observe: suspend () -> String,
        pollMs: Long = 120,
        timeoutMs: Long = 1600,
        requiredEqualSamples: Int = 2,
    ): SettleResult {
        val start = System.nanoTime()
        var previous = before.hashCode()
        var equalStreak = 0
        var changed = false

        while (true) {
            delay(pollMs)
            val current = observe().hashCode()
            val elapsed = (System.nanoTime() - start) / 1_000_000

            if (current != previous) {
                changed = true
                equalStreak = 0
                previous = current
            } else {
                equalStreak++
            }

            if (equalStreak >= requiredEqualSamples) {
                return SettleResult(stable = true, changed = changed, waitedMs = elapsed)
            }
            if (elapsed >= timeoutMs) {
                return SettleResult(stable = false, changed = changed, waitedMs = elapsed)
            }
        }
    }
}

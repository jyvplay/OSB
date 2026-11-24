// SPDX-License-Identifier: Apache-2.0
package dev.fusion.perf

/**
 * Bounded frame budget monitor. Tracks frame presentation times against a
 * target frame budget (e.g. 16.67 ms for 60 Hz). On each [frame] call it
 * records the elapsed time since the previous frame, classifies the frame
 * as on-time or over-budget, and tracks a rolling consecutive-over-budget
 * streak that triggers a thermal-throttle advisory when it exceeds
 * [MAX_OVER_BUDGET_STREAK].
 *
 * Declared narrowings: at most [MAX_FRAMES] frames tracked; budget in
 * milliseconds must be positive and at most [MAX_BUDGET_MS]; elapsed times
 * are non-negative and at most [MAX_ELAPSED_MS]; the monitor does not
 * interact with Choreographer or the display pipeline — timestamps are
 * supplied by the caller.
 */
class FrameBudgetMonitor private constructor(
    private val budgetMs: Double,
    private val frames: MutableList<FrameRecord>
) {
    companion object {
        const val MAX_FRAMES = 10_000
        const val MAX_BUDGET_MS = 1000.0
        const val MAX_ELAPSED_MS = 10_000.0
        const val MAX_OVER_BUDGET_STREAK = 100

        data class FrameRecord(
            val frameIndex: Int,
            val elapsedMs: Double,
            val overBudget: Boolean
        )

        data class Status(
            val averageMs: Double,
            val overBudgetCount: Int,
            val consecutiveOverBudget: Int,
            val thermalThrottleAdvised: Boolean
        )

        fun empty(budgetMs: Double): FrameBudgetMonitor {
            require(budgetMs > 0.0 && budgetMs <= MAX_BUDGET_MS) {
                "budget must be in (0, $MAX_BUDGET_MS] ms"
            }
            return FrameBudgetMonitor(budgetMs, mutableListOf())
        }
    }

    fun frame(elapsedMs: Double): Status {
        require(elapsedMs >= 0.0 && elapsedMs <= MAX_ELAPSED_MS) {
            "elapsedMs out of bounds: $elapsedMs"
        }
        require(frames.size < MAX_FRAMES) { "too many frames: $MAX_FRAMES" }
        val overBudget = elapsedMs > budgetMs
        val streak = if (overBudget) {
            val prev = frames.lastOrNull()?.let { it }
            val base = if (prev != null && overBudget) {
                // count consecutive over-budget from the end
                var s = 1
                for (i in frames.size - 2 downTo 0) {
                    if (frames[i].overBudget) s++ else break
                }
                s
            } else 1
            streak.coerceAtMost(MAX_OVER_BUDGET_STREAK)
        } else 0
        frames.add(FrameRecord(frames.size, elapsedMs, overBudget))
        val overCount = frames.count { it.overBudget }
        val avg = frames.averageOf { it.elapsedMs }
        return Status(avg, overCount, streak, streak >= MAX_OVER_BUDGET_STREAK)
    }

    fun status(): Status {
        val overCount = frames.count { it.overBudget }
        var streak = 0
        for (i in frames.size - 1 downTo 0) {
            if (frames[i].overBudget) streak++ else break
        }
        val avg = if (frames.isEmpty()) 0.0 else frames.averageOf { it.elapsedMs }
        return Status(avg, overCount, streak, streak >= MAX_OVER_BUDGET_STREAK)
    }

    fun frameCount(): Int = frames.size
    fun reset() = frames.clear()

    private fun Int.coerceAtMost(max: Int) = if (this > max) max else this
}

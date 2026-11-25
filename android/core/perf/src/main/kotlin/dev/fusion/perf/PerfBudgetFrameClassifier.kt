// SPDX-License-Identifier: Apache-2.0
package dev.fusion.perf

/**
 * Bounded frame duration classifier against target frame budget (ms).
 */
class PerfBudgetFrameClassifier private constructor() {
    companion object {
        enum class FrameClass { SMOOTH, JANK, FROZEN }

        fun classify(frameMs: Double, budgetMs: Double = 16.67): FrameClass {
            require(frameMs >= 0.0 && budgetMs > 0.0) { "invalid frame timing" }
            return when {
                frameMs <= budgetMs -> FrameClass.SMOOTH
                frameMs <= 700.0 -> FrameClass.JANK
                else -> FrameClass.FROZEN
            }
        }
    }
}

// SPDX-License-Identifier: Apache-2.0
package dev.fusion.testing

import kotlin.math.abs
import kotlin.math.max

/**
 * Bounded floating-point tolerance comparator.
 */
class TestAssertionTolerance private constructor() {
    companion object {
        fun isClose(a: Double, b: Double, absTol: Double = 1e-9, relTol: Double = 1e-6): Boolean {
            require(absTol >= 0.0 && relTol >= 0.0) { "negative tolerance" }
            if (!a.isFinite() || !b.isFinite()) return a == b
            val diff = abs(a - b)
            val bound = absTol + relTol * max(abs(a), abs(b))
            return diff <= bound
        }
    }
}

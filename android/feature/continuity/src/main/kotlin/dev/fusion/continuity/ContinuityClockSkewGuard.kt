// SPDX-License-Identifier: Apache-2.0
package dev.fusion.continuity

import kotlin.math.abs

/**
 * Bounded continuity clock-skew guard.
 */
class ContinuityClockSkewGuard private constructor() {
    companion object {
        fun isWithinSkew(localMs: Long, remoteMs: Long, maxSkewMs: Long): Boolean {
            require(maxSkewMs >= 0L) { "negative maxSkewMs" }
            return abs(remoteMs - localMs) <= maxSkewMs
        }
    }
}

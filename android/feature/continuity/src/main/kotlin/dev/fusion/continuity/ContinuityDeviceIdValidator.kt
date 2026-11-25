// SPDX-License-Identifier: Apache-2.0
package dev.fusion.continuity

/**
 * Bounded continuity device ID validator.
 */
class ContinuityDeviceIdValidator private constructor() {
    companion object {
        private val ID_RE = Regex("^[a-zA-Z0-9_-]{4,64}$")

        fun isValid(deviceId: String): Boolean = ID_RE.matches(deviceId)
    }
}

// SPDX-License-Identifier: Apache-2.0
package dev.fusion.hw

/**
 * Bounded USB `VVVV:PPPP` 4-hex-digit vendor/product ID parser.
 */
class HwUsbVendorProductPair private constructor() {
    companion object {
        private val USB_RE = Regex("^([0-9a-fA-F]{4}):([0-9a-fA-F]{4})$")

        fun parse(vidPid: String): Pair<Int, Int> {
            val m = USB_RE.matchEntire(vidPid) ?: throw IllegalArgumentException("invalid VID:PID: $vidPid")
            return m.groupValues[1].toInt(16) to m.groupValues[2].toInt(16)
        }
    }
}

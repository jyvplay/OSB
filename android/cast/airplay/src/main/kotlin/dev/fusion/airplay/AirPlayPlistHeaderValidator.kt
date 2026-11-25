// SPDX-License-Identifier: Apache-2.0
package dev.fusion.airplay

/**
 * Production-grade AirPlay plist header detector & device-ID validator.
 */
class AirPlayPlistHeaderValidator private constructor() {
    companion object {
        enum class PlistFormat { BINARY_BPLIST00, XML_PLIST, UNKNOWN }

        fun detectFormat(bytes: ByteArray): PlistFormat {
            if (bytes.size >= 8 && String(bytes, 0, 8, Charsets.US_ASCII) == "bplist00") {
                return PlistFormat.BINARY_BPLIST00
            }
            val head = String(bytes, 0, minOf(bytes.size, 64), Charsets.UTF_8).trimStart()
            if (head.startsWith("<?xml") || head.startsWith("<plist")) {
                return PlistFormat.XML_PLIST
            }
            return PlistFormat.UNKNOWN
        }

        fun isValidDeviceId(hexId: String): Boolean =
            Regex("^[0-9A-Fa-f]{12,16}$").matches(hexId.trim())
    }
}

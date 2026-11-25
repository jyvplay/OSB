// SPDX-License-Identifier: Apache-2.0
package dev.fusion.cast.dlna

/**
 * Bounded DLNA 4-field protocolInfo parser (`protocol:network:contentFormat:additionalInfo`).
 */
class DlnaProtocolInfoMatcher private constructor() {
    companion object {
        fun extractMime(protocolInfo: String): String? {
            val parts = protocolInfo.split(':')
            if (parts.size < 4) return null
            val mime = parts[2].trim().lowercase()
            return if (mime.contains('/')) mime else null
        }
    }
}

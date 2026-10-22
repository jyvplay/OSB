// SPDX-License-Identifier: Apache-2.0
package dev.fusion.hw

import dev.fusion.model.CodecInfo

/** Selected encoder with the reason it won. Reasons are stable text. */
data class CodecChoice(val mime: String, val hardwareAccelerated: Boolean, val reason: String)

/**
 * Pure encoder selection over HwProbe's lists: first candidate (in caller
 * order) with a hardware match wins; without one, the first candidate with
 * any match wins only when software is admitted. Null when no candidate
 * matches at all. The probe owns measurement; this owns the ranking.
 */
object CodecPreference {
    fun select(
        encoders: List<CodecInfo>,
        candidates: List<String>,
        hardwareRequired: Boolean,
    ): CodecChoice? {
        require(candidates.isNotEmpty()) { "empty candidates" }
        for (mime in candidates) {
            val matches = encoders.filter { it.mime.equals(mime, ignoreCase = true) }
            val hw = matches.firstOrNull { it.hardwareAccelerated }
            if (hw != null) return CodecChoice(hw.mime, true, "hardware $mime")
            if (!hardwareRequired && matches.isNotEmpty()) {
                return CodecChoice(matches[0].mime, false, "software fallback $mime")
            }
        }
        return null
    }
}

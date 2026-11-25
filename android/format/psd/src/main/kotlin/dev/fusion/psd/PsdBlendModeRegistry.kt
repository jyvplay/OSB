// SPDX-License-Identifier: Apache-2.0
package dev.fusion.psd

/**
 * Bounded PSD 4-character blend mode key registry.
 */
class PsdBlendModeRegistry private constructor() {
    companion object {
        enum class BlendMode { NORMAL, MULTIPLY, SCREEN, OVERLAY, DARKEN, LIGHTEN, UNKNOWN }

        fun resolve(key: String): BlendMode {
            require(key.length == 4) { "PSD blend key must be 4 chars" }
            return when (key) {
                "norm" -> BlendMode.NORMAL
                "mul " -> BlendMode.MULTIPLY
                "scrn" -> BlendMode.SCREEN
                "over" -> BlendMode.OVERLAY
                "dark" -> BlendMode.DARKEN
                "lite" -> BlendMode.LIGHTEN
                else -> BlendMode.UNKNOWN
            }
        }
    }
}

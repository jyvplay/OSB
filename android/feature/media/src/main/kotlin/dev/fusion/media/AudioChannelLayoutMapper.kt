// SPDX-License-Identifier: Apache-2.0
package dev.fusion.media

/**
 * Bounded audio channel count to standard speaker layout mapper.
 */
class AudioChannelLayoutMapper private constructor() {
    companion object {
        enum class Layout { MONO, STEREO, SURROUND_5_1, SURROUND_7_1, CUSTOM }

        fun map(channels: Int): Layout {
            require(channels in 1..64) { "channel count out of bounds: $channels" }
            return when (channels) {
                1 -> Layout.MONO
                2 -> Layout.STEREO
                6 -> Layout.SURROUND_5_1
                8 -> Layout.SURROUND_7_1
                else -> Layout.CUSTOM
            }
        }
    }
}

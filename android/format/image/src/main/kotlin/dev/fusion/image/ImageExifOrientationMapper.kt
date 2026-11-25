// SPDX-License-Identifier: Apache-2.0
package dev.fusion.image

/**
 * Bounded EXIF orientation tag (1..8) to (rotationDegrees, flipHorizontal) mapper.
 */
class ImageExifOrientationMapper private constructor() {
    companion object {
        data class Transform(val rotationDegrees: Int, val flipHorizontal: Boolean)

        fun map(orientationTag: Int): Transform = when (orientationTag) {
            1 -> Transform(0, false)
            2 -> Transform(0, true)
            3 -> Transform(180, false)
            4 -> Transform(180, true)
            5 -> Transform(90, true)
            6 -> Transform(90, false)
            7 -> Transform(270, true)
            8 -> Transform(270, false)
            else -> throw IllegalArgumentException("invalid EXIF orientation: $orientationTag")
        }
    }
}

// SPDX-License-Identifier: Apache-2.0
package dev.fusion.maps

/**
 * Bounded map zoom level clamper.
 */
class MapsZoomLevelClamper private constructor() {
    companion object {
        fun clamp(zoom: Double, minZoom: Double = 0.0, maxZoom: Double = 22.0): Double {
            require(zoom.isFinite() && minZoom in 0.0..maxZoom && maxZoom <= 22.0) { "invalid zoom bounds" }
            return zoom.coerceIn(minZoom, maxZoom)
        }
    }
}

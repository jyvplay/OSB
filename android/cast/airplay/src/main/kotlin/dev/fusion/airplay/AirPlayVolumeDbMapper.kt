// SPDX-License-Identifier: Apache-2.0
package dev.fusion.airplay

/**
 * Bounded AirPlay volume dB mapper. Maps AirPlay dB volume `[-30.0, 0.0]`
 * (and mute `-144.0`) to/from normalized `[0.0, 1.0]` slider values.
 */
class AirPlayVolumeDbMapper private constructor() {
    companion object {
        const val MIN_DB = -30.0
        const val MAX_DB = 0.0
        const val MUTE_DB = -144.0

        fun sliderToDb(slider: Double): Double {
            require(!slider.isNaN() && slider in 0.0..1.0) { "slider out of bounds: $slider" }
            if (slider == 0.0) return MUTE_DB
            return MIN_DB + slider * (MAX_DB - MIN_DB)
        }

        fun dbToSlider(db: Double): Double {
            require(!db.isNaN()) { "db is NaN" }
            if (db <= MUTE_DB || db < MIN_DB) return 0.0
            if (db >= MAX_DB) return 1.0
            return (db - MIN_DB) / (MAX_DB - MIN_DB)
        }
    }
}

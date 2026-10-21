// SPDX-License-Identifier: Apache-2.0
package dev.fusion.maps

import java.util.Collections
import java.util.Locale

/**
 * Embedded offline place index: major world cities with APPROXIMATE reference
 * coordinates (±50 km). This is a bundled lookup table for the offline atlas — not
 * geocoding, not a survey, not navigation, and never a location reading. Coordinates
 * are rounded to whole degrees for small footprint; distances inherit that error and
 * say so on the value.
 */
object Gazetteer {
    data class Place(val name: String, val country: String, val latitude: Double, val longitude: Double)

    /** Approximate city-center references. Whole-degree rounding is deliberate and disclosed. */
    val PLACES: List<Place> = Collections.unmodifiableList(listOf(
        Place("New York", "United States", 41.0, -74.0), Place("Los Angeles", "United States", 34.0, -118.0),
        Place("Chicago", "United States", 42.0, -88.0), Place("Houston", "United States", 30.0, -95.0),
        Place("Toronto", "Canada", 44.0, -79.0), Place("Mexico City", "Mexico", 19.0, -99.0),
        Place("Sao Paulo", "Brazil", -24.0, -46.0), Place("Buenos Aires", "Argentina", -35.0, -58.0),
        Place("London", "United Kingdom", 51.0, 0.0), Place("Paris", "France", 49.0, 2.0),
        Place("Berlin", "Germany", 53.0, 13.0), Place("Madrid", "Spain", 40.0, -4.0),
        Place("Rome", "Italy", 42.0, 12.0), Place("Moscow", "Russia", 56.0, 38.0),
        Place("Cairo", "Egypt", 30.0, 31.0), Place("Lagos", "Nigeria", 6.0, 3.0),
        Place("Nairobi", "Kenya", -1.0, 37.0), Place("Johannesburg", "South Africa", -26.0, 28.0),
        Place("Istanbul", "Turkiye", 41.0, 29.0), Place("Dubai", "UAE", 25.0, 55.0),
        Place("Mumbai", "India", 19.0, 73.0), Place("Delhi", "India", 29.0, 77.0),
        Place("Karachi", "Pakistan", 25.0, 67.0), Place("Dhaka", "Bangladesh", 24.0, 90.0),
        Place("Bangkok", "Thailand", 14.0, 100.0), Place("Jakarta", "Indonesia", -6.0, 107.0),
        Place("Singapore", "Singapore", 1.0, 104.0), Place("Hong Kong", "China", 22.0, 114.0),
        Place("Shanghai", "China", 31.0, 121.0), Place("Beijing", "China", 40.0, 116.0),
        Place("Seoul", "South Korea", 38.0, 127.0), Place("Tokyo", "Japan", 36.0, 140.0),
        Place("Sydney", "Australia", -34.0, 151.0), Place("Auckland", "New Zealand", -37.0, 175.0),
        Place("Honolulu", "United States", 21.0, -158.0), Place("Anchorage", "United States", 61.0, -150.0),
        Place("Raleigh", "United States", 36.0, -79.0), Place("Seattle", "United States", 48.0, -122.0),
        Place("Denver", "United States", 40.0, -105.0), Place("Miami", "United States", 26.0, -80.0),
        Place("Vancouver", "Canada", 49.0, -123.0), Place("Lima", "Peru", -12.0, -77.0),
        Place("Bogota", "Colombia", 5.0, -74.0), Place("Santiago", "Chile", -33.0, -71.0),
        Place("Lisbon", "Portugal", 39.0, -9.0), Place("Amsterdam", "Netherlands", 52.0, 5.0),
        Place("Stockholm", "Sweden", 59.0, 18.0), Place("Athens", "Greece", 38.0, 24.0),
    ))

    const val MAX_RESULTS = 16

    init {
        require(PLACES.size >= 40) { "Gazetteer must carry at least 40 places" }
        PLACES.forEach {
            require(it.name.length in 2..64 && it.country.length in 2..64)
            require(it.latitude in -90.0..90.0 && it.longitude in -180.0..180.0)
            require(it.latitude == kotlin.math.round(it.latitude) && it.longitude == kotlin.math.round(it.longitude)) { "Whole-degree rounding only" }
        }
        require(PLACES.map { it.name.lowercase(Locale.ROOT) }.distinct().size == PLACES.size) { "Duplicate place" }
    }

    /** Case-insensitive substring search over name and country. Empty/short queries refused. */
    fun search(query: String): List<Place> {
        val q = query.trim().lowercase(Locale.ROOT)
        require(q.length in 2..64) { "Search needs 2..64 characters" }
        return Collections.unmodifiableList(PLACES.filter {
            it.name.lowercase(Locale.ROOT).contains(q) || it.country.lowercase(Locale.ROOT).contains(q)
        }.take(MAX_RESULTS))
    }

    fun exact(name: String): Place? {
        val q = name.trim().lowercase(Locale.ROOT)
        return PLACES.firstOrNull { it.name.lowercase(Locale.ROOT) == q }
    }

    /** k nearest places to a point. Approximate inputs yield approximate ordering — disclosed. */
    fun nearest(latitude: Double, longitude: Double, k: Int): List<Place> {
        require(latitude in -90.0..90.0 && longitude in -180.0..180.0 && k in 1..MAX_RESULTS)
        val here = GpxTrack.Point(latitude, longitude, null)
        return Collections.unmodifiableList(PLACES.sortedBy {
            GpxTrack.haversine(here, GpxTrack.Point(it.latitude, it.longitude, null))
        }.take(k))
    }

    fun disclosure() = "Offline place index with approximate whole-degree references (±50 km). " +
        "Lookup only: not geocoding, routing, navigation, or a device location."
}

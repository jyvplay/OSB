// SPDX-License-Identifier: Apache-2.0
package dev.fusion.maps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineGazetteerTest {
    @Test
    fun findsRaleighAndComputesExactGreatCircleRouteAndGpxRoundTrip() {
        val raleigh = OfflineGazetteer.search("Raleigh").first()
        val london = OfflineGazetteer.search("London").first()
        assertEquals("Raleigh", raleigh.name)
        val distKm = OfflineGazetteer.routeMeters(listOf(raleigh.point, london.point)) / 1000.0
        assertTrue(distKm in 6150.0..6350.0)
        val gpx = GeoJsonGpxCodec.exportGpx(listOf(MapPin(1, "Raleigh", raleigh.point)))
        assertEquals("Raleigh", GeoJsonGpxCodec.importGpxWaypoints(gpx).first().first)
    }
}

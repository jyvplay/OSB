package dev.fusion.beam

import dev.fusion.perf.ThermalState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QualityGovernorTest {

    @Test
    fun testThrottledThermalForcesAVC720p() {
        val q = QualityGovernor.selectQuality(
            linkSpeedMbps = 500,
            thermalState = ThermalState.CRITICAL,
            receiverSupportsHevc = true
        )
        assertEquals("AVC", q.codec)
        assertEquals("720p", q.resolutionLabel)
        assertEquals(24, q.fps)
    }

    @Test
    fun testWeakLinkDropsResolution() {
        val q = QualityGovernor.selectQuality(
            linkSpeedMbps = 15,
            thermalState = ThermalState.COOL,
            receiverSupportsHevc = true
        )
        assertEquals("AVC", q.codec)
        assertEquals("720p", q.resolutionLabel)
        assertTrue(q.bitrateBps <= 2_000_000)
    }

    @Test
    fun testOptimalConditionsSelectHevc60fps() {
        val q = QualityGovernor.selectQuality(
            linkSpeedMbps = 180,
            thermalState = ThermalState.COOL,
            receiverSupportsHevc = true
        )
        assertEquals("HEVC", q.codec)
        assertEquals("1080p", q.resolutionLabel)
        assertEquals(60, q.fps)
        assertEquals(8_000_000, q.bitrateBps)
    }
}

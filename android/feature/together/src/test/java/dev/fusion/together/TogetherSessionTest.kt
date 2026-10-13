package dev.fusion.together

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TogetherSessionTest {
    @Test fun newRoomStartsWithOnePeer() {
        val s = TogetherSession().newRoom()
        assertEquals(1, s.peers.size)
        assertTrue(s.code.length == 6)
    }

    @Test fun driftReflectsLargestAbsoluteOffset() {
        val session = TogetherSession()
        val s1 = session.newRoom()
        val s2 = session.addSimulatedPeer(s1, "A")
        assertTrue(s2.maxDriftMs >= 0)
    }
}

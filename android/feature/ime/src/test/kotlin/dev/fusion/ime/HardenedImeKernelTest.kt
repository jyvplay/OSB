// SPDX-License-Identifier: Apache-2.0
package dev.fusion.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom

class HardenedImeKernelTest {
    @Test
    fun permutesKeypadQuantizesTimingAndQuarantinesSubEightMsBadUsbBursts() {
        val rng = SecureRandom(byteArrayOf(7, 8, 9, 10))
        val perm = ScrambledKeypadLayout.permute(ScrambledKeypadLayout.DIGITS, rng)
        assertEquals(10, perm.toSet().size)

        val attacked = HardenedPinBuffer(maxLength = 6)
        repeat(5) { idx ->
            attacked.pressSlot(0, HidEventSample(1_000_000L * idx, "badusb-ducky"))
        }
        assertTrue(attacked.quarantined && attacked.verdict == HidThreatVerdict.QUARANTINED_SUPERHUMAN_CADENCE)

        val sched = TimingQuantizer.paddedSchedule(listOf(5_000_000L, 6_000_000L), totalSlots = 6)
        assertEquals(6, sched.size)
        assertTrue(sched.zipWithNext().all { (a, b) -> b.first - a.first == TimingQuantizer.DEFAULT_QUANTUM_NANOS })
        assertEquals(
            HidThreatVerdict.QUARANTINED_OBSCURED_OVERLAY,
            HidBurstClassifier.classify(listOf(HidEventSample(10L, "kbd", obscuredWindow = true)))
        )
    }
}

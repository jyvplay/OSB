// SPDX-License-Identifier: Apache-2.0
package dev.fusion.ime

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HardenedInputEngineTest {
    @Test
    fun scrambledKeypadBadUsbBurstHotplugOverlayAndTimingQuantizerHold() {
        val pad = HardenedPinBuffer(maxLength = 8)
        val firstLayout = pad.keys
        assertEquals(10, firstLayout.size)
        assertEquals(('0'..'9').toSet(), firstLayout.toSet())

        val targetPin = "4927"
        var ts = 1_000_000_000L
        for (ch in targetPin) {
            val slot = pad.keys.indexOf(ch)
            assertTrue(slot >= 0)
            ts += 95_000_000L // normal human 95ms spacing
            assertEquals(HidThreatVerdict.CLEAN, pad.pressSlot(slot, HidEventSample(ts, "internal-touch")))
        }
        val secret = pad.consumeAndZeroize()
        assertArrayEquals(targetPin.toCharArray(), secret)
        assertEquals(0, pad.length)

        // Superhuman BadUSB burst (<8ms intervals x4) quarantines immediately & wipes buffer
        val burst = List(6) { idx -> HidEventSample(idx * 2_000_000L, "usb-1d6b:0104") }
        assertEquals(HidThreatVerdict.QUARANTINED_SUPERHUMAN_CADENCE, HidBurstClassifier.classify(burst))

        // Mid-entry USB hotplug descriptor change quarantines immediately
        assertEquals(
            HidThreatVerdict.QUARANTINED_HOTPLUG_SWAP,
            HidBurstClassifier.classify(
                listOf(
                    HidEventSample(100_000_000L, "kbd-primary"),
                    HidEventSample(300_000_000L, "usb-rogue-hid")
                )
            )
        )

        // Unprompted META+R shell injection chord quarantines immediately
        assertEquals(
            HidThreatVerdict.QUARANTINED_INJECTED_CHORD,
            HidBurstClassifier.classify(
                listOf(HidEventSample(100_000_000L, "usb-kbd", metaPressed = true, keyCode = HidBurstClassifier.KEYCODE_R))
            )
        )

        // Obscured overlay window touch quarantines immediately
        assertEquals(
            HidThreatVerdict.QUARANTINED_OBSCURED_OVERLAY,
            HidBurstClassifier.classify(
                listOf(HidEventSample(100_000_000L, "touch", obscuredWindow = true))
            )
        )

        // Timing quantizer aligns all ticks strictly to uniform 100ms multiples
        val schedule = TimingQuantizer.paddedSchedule(listOf(13_500_000L, 47_200_000L, 289_000_000L), totalSlots = 8)
        assertEquals(8, schedule.size)
        for (i in 1 until schedule.size) {
            assertEquals(TimingQuantizer.DEFAULT_QUANTUM_NANOS, schedule[i].first - schedule[i - 1].first)
        }
        assertEquals(3, schedule.count { it.second })
        assertNotEquals(schedule[0].first, schedule[1].first)
    }
}

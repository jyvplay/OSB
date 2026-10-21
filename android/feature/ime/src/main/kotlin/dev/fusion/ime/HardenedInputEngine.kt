// SPDX-License-Identifier: Apache-2.0
package dev.fusion.ime

import java.security.SecureRandom
import java.util.Collections

/**
 * Hardware-Attack-Resistant Input & Scrambled Touch Keypad Engine (`:feature:ime`).
 *
 * Defends secret/passphrase entry against four concrete hardware-origin and side-channel threat classes:
 * 1. Screen-coordinate tap loggers, smudge reconstruction & shoulder-surfing:
 *    CSPRNG Fisher-Yates permutation (`ScrambledKeypadLayout`) randomizes grid slot -> symbol assignment on
 *    every keystroke or session so `(x, y)` coordinates carry zero mutual information with entered symbols
 *    (`I(S; X,Y) = 0`, proved in Z3 model `prove_r26_models.py::prove_scrambled_keypad_zero_leakage`).
 * 2. BadUSB / Rubber-Ducky rogue HID keystroke injection (`HidBurstClassifier`):
 *    Flags superhuman inter-keystroke intervals (`< 8 ms` across `>= 4` consecutive intervals), unprompted
 *    shell-launch chords (`META+R`, `CTRL+ALT+T`, `ALT+F2`), and mid-entry USB device descriptor hotplug changes,
 *    immediately quarantining the volatile secret buffer before submission.
 * 3. Acoustic / IPC / interrupt inter-keystroke timing side channels (`TimingQuantizer`):
 *    Quantizes observable keystroke release slots onto a constant `quantumNanos` (`100 ms`) lattice with
 *    indistinguishable dummy chaff ticks when idle.
 * 4. Memory remnants:
 *    Stores characters in a fixed `CharArray` wiped via volatile read-back pass (never immutable `String` heap copies
 *    until explicit caller consumption).
 *
 * Explicit impossibility disclosure:
 * Cannot defeat a compromised OS kernel reading surface framebuffer pixels simultaneously with raw touch events,
 * pre-boot UEFI/firmware implants, or optical cameras recording both finger and screen directly.
 */
object ScrambledKeypadLayout {
    val DIGITS: List<Char> = ('0'..'9').toList()
    val HEX_ALPHANUM: List<Char> = (('0'..'9') + ('a'..'f')).toList()

    /** Unbiased Fisher-Yates shuffle backed by `SecureRandom` (or deterministic test `SecureRandom`). */
    fun permute(alphabet: List<Char> = DIGITS, rng: SecureRandom = SecureRandom()): List<Char> {
        require(alphabet.size in 2..64 && alphabet.distinct().size == alphabet.size) {
            "Alphabet must have 2..64 unique symbols"
        }
        val copy = ArrayList(alphabet)
        for (i in copy.lastIndex downTo 1) {
            val j = rng.nextInt(i + 1)
            val tmp = copy[i]
            copy[i] = copy[j]
            copy[j] = tmp
        }
        return Collections.unmodifiableList(copy)
    }
}

data class HidEventSample(
    val timestampNanos: Long,
    val deviceDescriptorHash: String,
    val metaPressed: Boolean = false,
    val ctrlPressed: Boolean = false,
    val altPressed: Boolean = false,
    val keyCode: Int = 0,
    val obscuredWindow: Boolean = false
)

enum class HidThreatVerdict {
    CLEAN,
    QUARANTINED_SUPERHUMAN_CADENCE,
    QUARANTINED_HOTPLUG_SWAP,
    QUARANTINED_INJECTED_CHORD,
    QUARANTINED_OBSCURED_OVERLAY
}

object HidBurstClassifier {
    const val MIN_HUMAN_INTERVAL_NANOS = 8_000_000L // 8 ms: fastest human typists exceed ~25-30 ms inter-key interval
    const val BURST_STREAK_THRESHOLD = 4
    // Android KeyEvent constants without Android framework dependency:
    const val KEYCODE_R = 46
    const val KEYCODE_T = 48
    const val KEYCODE_F2 = 132

    fun classify(events: List<HidEventSample>): HidThreatVerdict {
        if (events.isEmpty()) return HidThreatVerdict.CLEAN
        val initialDevice = events.first().deviceDescriptorHash
        var fastStreak = 0
        for ((index, ev) in events.withIndex()) {
            if (ev.obscuredWindow) return HidThreatVerdict.QUARANTINED_OBSCURED_OVERLAY
            if (ev.deviceDescriptorHash != initialDevice) return HidThreatVerdict.QUARANTINED_HOTPLUG_SWAP
            if ((ev.metaPressed && ev.keyCode == KEYCODE_R) ||
                (ev.ctrlPressed && ev.altPressed && ev.keyCode == KEYCODE_T) ||
                (ev.altPressed && ev.keyCode == KEYCODE_F2)
            ) {
                return HidThreatVerdict.QUARANTINED_INJECTED_CHORD
            }
            if (index > 0) {
                val delta = ev.timestampNanos - events[index - 1].timestampNanos
                require(delta >= 0L) { "Non-monotonic HID event timestamp refused" }
                if (delta < MIN_HUMAN_INTERVAL_NANOS) {
                    fastStreak++
                    if (fastStreak >= BURST_STREAK_THRESHOLD) {
                        return HidThreatVerdict.QUARANTINED_SUPERHUMAN_CADENCE
                    }
                } else {
                    fastStreak = 0
                }
            }
        }
        return HidThreatVerdict.CLEAN
    }
}

object TimingQuantizer {
    const val DEFAULT_QUANTUM_NANOS = 100_000_000L // 100.0 ms constant cadence

    /** Maps any raw arrival time onto the strictly next uniform lattice tick `k * quantumNanos`. */
    fun nextSlotNanos(arrivalNanos: Long, quantumNanos: Long = DEFAULT_QUANTUM_NANOS): Long {
        require(arrivalNanos >= 0L && quantumNanos in 1_000_000L..1_000_000_000L)
        val rem = arrivalNanos % quantumNanos
        return if (rem == 0L) arrivalNanos + quantumNanos else arrivalNanos + (quantumNanos - rem)
    }

    /** Generates fixed-length `slots` schedule (`true` = real symbol release, `false` = dummy chaff tick). */
    fun paddedSchedule(
        arrivalNanos: List<Long>,
        totalSlots: Int = 16,
        quantumNanos: Long = DEFAULT_QUANTUM_NANOS
    ): List<Pair<Long, Boolean>> {
        require(totalSlots in 4..64 && arrivalNanos.size <= totalSlots) { "Input exceeds fixed padded slot budget" }
        val base = if (arrivalNanos.isEmpty()) 0L else nextSlotNanos(arrivalNanos.first(), quantumNanos)
        var cursor = base
        val realSlots = LinkedHashSet<Long>()
        for (raw in arrivalNanos) {
            val candidate = maxOf(cursor, nextSlotNanos(raw, quantumNanos))
            realSlots.add(candidate)
            cursor = candidate + quantumNanos
        }
        val totalWindow = maxOf(totalSlots, ((cursor - base) / quantumNanos).toInt())
        return List(totalWindow) { idx ->
            val tick = base + idx * quantumNanos
            tick to (tick in realSlots)
        }
    }
}

class HardenedPinBuffer(
    private val maxLength: Int = 16,
    private val repermutePerStroke: Boolean = true,
    private val rng: SecureRandom = SecureRandom()
) {
    init { require(maxLength in 4..64) }

    private val buffer = CharArray(maxLength)
    private val events = ArrayList<HidEventSample>()
    @Volatile private var fenceSink: Int = 0

    var length: Int = 0
        private set
    var keys: List<Char> = ScrambledKeypadLayout.permute(ScrambledKeypadLayout.DIGITS, rng)
        private set
    var verdict: HidThreatVerdict = HidThreatVerdict.CLEAN
        private set
    val quarantined: Boolean get() = verdict != HidThreatVerdict.CLEAN

    fun pressSlot(slotIndex: Int, sample: HidEventSample): HidThreatVerdict {
        require(slotIndex in keys.indices) { "Keypad slot out of bounds: $slotIndex" }
        if (quarantined) {
            scrubSecretOnly()
            return verdict
        }
        events.add(sample)
        val detected = HidBurstClassifier.classify(events)
        if (detected != HidThreatVerdict.CLEAN) {
            verdict = detected
            scrubSecretOnly()
            return verdict
        }
        if (length < maxLength) {
            buffer[length++] = keys[slotIndex]
        }
        if (repermutePerStroke) {
            keys = ScrambledKeypadLayout.permute(ScrambledKeypadLayout.DIGITS, rng)
        }
        return verdict
    }

    fun backspace() {
        if (length > 0) {
            length--
            buffer[length] = '\u0000'
        }
        if (repermutePerStroke) {
            keys = ScrambledKeypadLayout.permute(ScrambledKeypadLayout.DIGITS, rng)
        }
    }

    /** Copy out current characters and immediately zeroize internal buffer. */
    fun consumeAndZeroize(): CharArray {
        check(!quarantined) { "Quarantined buffer ($verdict) cannot be consumed" }
        val out = buffer.copyOf(length)
        zeroize()
        return out
    }

    private fun scrubSecretOnly() {
        for (i in buffer.indices) buffer[i] = 0x55.toChar()
        var acc = 0
        for (c in buffer) acc = acc xor c.code
        for (i in buffer.indices) buffer[i] = '\u0000'
        for (c in buffer) acc = acc xor c.code
        fenceSink = acc
        length = 0
        keys = ScrambledKeypadLayout.permute(ScrambledKeypadLayout.DIGITS, rng)
    }

    fun zeroize() {
        scrubSecretOnly()
        verdict = HidThreatVerdict.CLEAN
        events.clear()
    }
}

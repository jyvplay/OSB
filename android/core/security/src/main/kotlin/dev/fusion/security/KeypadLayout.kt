// SPDX-License-Identifier: Apache-2.0
package dev.fusion.security

import java.util.Collections
import java.util.Random

/**
 * Countermeasure kernels against the most common local hardware-origin vector: a passive
 * inline keylogger (or keystroke-logging malware at the keyboard channel) that records
 * physical key events before software can intervene.
 *
 * Honest boundary (Tails states the same for altered hardware): no app can DETECT a passive
 * logger whose app-visible trace is identical to a clean keyboard's. The defense is channel
 * avoidance, not detection:
 *
 * - [InputChannel.threatTable] names, per input route, which observation channels it avoids
 *   and which still expose the secret (screen, pointer, camera, host, accessibility).
 * - [KeypadLayout.shuffled] produces a touch-only digit pad with a fresh random permutation
 *   per session, so a keyboard-channel-only logger observes no key events at all. Pointer /
 *   screen observers are NOT defeated; the table says so explicitly.
 */
object InputChannel {
    enum class Channel { KEY_EVENTS, POINTER, SCREEN, ACCESSIBILITY, CAMERA, HOST_FIRMWARE }
    enum class Route(val title: String) {
        PHYSICAL_KEYBOARD("Physical keyboard"),
        SYSTEM_IME("System keyboard (IME)"),
        TOUCH_PAD("Touch-only shuffled pad"),
    }

    /** Channels each route still exposes. Absence from the set is the avoidance claim. */
    fun exposed(route: Route): Set<Channel> = when (route) {
        Route.PHYSICAL_KEYBOARD -> setOf(Channel.KEY_EVENTS, Channel.POINTER, Channel.SCREEN, Channel.ACCESSIBILITY, Channel.CAMERA, Channel.HOST_FIRMWARE)
        Route.SYSTEM_IME -> setOf(Channel.POINTER, Channel.SCREEN, Channel.ACCESSIBILITY, Channel.CAMERA, Channel.HOST_FIRMWARE)
        Route.TOUCH_PAD -> setOf(Channel.POINTER, Channel.SCREEN, Channel.ACCESSIBILITY, Channel.CAMERA, Channel.HOST_FIRMWARE)
    }

    fun avoided(route: Route): Set<Channel> = Channel.entries.toSet() - exposed(route)

    fun disclosure(route: Route): String = buildString {
        append("${route.title}: avoids ${avoided(route).ifEmpty { "no observation channel" }}; ")
        append("still exposed to ${exposed(route).joinToString(", ")}. ")
        append("A passive logger with an identical app-visible trace cannot be detected from inside the app; ")
        append("use trusted hardware with physical custody for high-risk secrets.")
    }
}

object KeypadLayout {
    const val DIGITS = 10

    /** Fisher-Yates permutation of 0..9. Caller supplies the RNG so tests inject a fixed seed. */
    fun shuffled(random: Random): List<Int> {
        val digits = (0 until DIGITS).toMutableList()
        for (i in DIGITS - 1 downTo 1) {
            val j = random.nextInt(i + 1)
            val t = digits[i]; digits[i] = digits[j]; digits[j] = t
        }
        return Collections.unmodifiableList(digits)
    }

    fun valid(layout: List<Int>): Boolean = layout.size == DIGITS && layout.toSet() == (0 until DIGITS).toSet()

    /** Positions of each digit for hit-testing: index = digit, value = pad position 0..9. */
    fun positions(layout: List<Int>): List<Int> {
        require(valid(layout)) { "Hit-testing needs a valid permutation" }
        val pos = IntArray(DIGITS)
        layout.forEachIndexed { position, digit -> pos[digit] = position }
        return Collections.unmodifiableList(pos.toList())
    }
}

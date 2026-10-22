// SPDX-License-Identifier: Apache-2.0
package dev.fusion.security

/** PIN verdict: ok when [reasons] is empty. Reason strings are stable. */
data class PinVerdict(val ok: Boolean, val reasons: List<String>)

/**
 * Device-PIN strength policy: 4..12 ASCII digits, no all-same digit, no
 * straight ascending/descending run (mod-10 wrap counts, so 7890 and 2109
 * fail), no repeated half ("1212", "6969"), small blocklist. Every
 * violation is reported, not just the first. Policy only — storage,
 * rate limiting, and the secure element are outside this kernel.
 */
object PinRules {
    const val MIN_LEN = 4
    const val MAX_LEN = 12
    private val BLOCKED = setOf("1234", "4321", "0000", "2580", "0852")

    fun evaluate(pin: String): PinVerdict {
        val reasons = mutableListOf<String>()
        if (!pin.all { it in '0'..'9' }) reasons += "not all digits"
        if (pin.length !in MIN_LEN..MAX_LEN) reasons += "length ${pin.length} outside $MIN_LEN..$MAX_LEN"
        if (pin.isNotEmpty() && pin.all { it == pin[0] }) reasons += "all same digit"
        if (isRun(pin)) reasons += "straight run"
        if (pin.length % 2 == 0 && pin.isNotEmpty() && pin.substring(0, pin.length / 2) == pin.substring(pin.length / 2)) {
            reasons += "repeated half"
        }
        if (pin in BLOCKED) reasons += "blocklisted"
        return PinVerdict(reasons.isEmpty(), reasons)
    }

    private fun isRun(pin: String): Boolean {
        if (pin.length < 2 || !pin.all { it in '0'..'9' }) return false
        val ds = pin.map { it - '0' }
        val up = ds.zipWithNext().all { (a, b) -> (b - a + 10) % 10 == 1 }
        val down = ds.zipWithNext().all { (a, b) -> (a - b + 10) % 10 == 1 }
        return up || down
    }
}

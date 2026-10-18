package dev.fusion.testing

import dev.fusion.security.LocalExposure
import dev.fusion.security.TouchAdmission
import dev.fusion.security.VolatileDigits

/** Executable adversarial input corpus over REAL production kernels, not an Android/platform simulator. */
object R23Checks {
    private var assertions = 0
    private fun checkThat(ok: Boolean) { assertions++; check(ok) { "R23 assertion $assertions failed" } }
    private inline fun refused(block: () -> Unit) {
        var rejected = false
        try { block() } catch (_: IllegalArgumentException) { rejected = true }
        checkThat(rejected)
    }
    @JvmStatic fun main(args: Array<String>) {
        val phases = TouchAdmission.Phase.entries
        // Exhaustive 4-event words, each event = one phase and either/both/no obscuration flags.
        // Reference tracks the most recent boundary in a history, not a second TouchAdmission instance.
        val alphabet = phases.size * 4
        var traces = 1
        repeat(4) { traces *= alphabet }
        for (word in 0 until traces) {
            val gate = TouchAdmission(); val history = ArrayList<Pair<TouchAdmission.Phase, Int>>()
            var remaining = word
            repeat(4) {
                val item = remaining % alphabet; remaining /= alphabet
                val phase = phases[item / 4]; val flags = item % 4
                val boundary = history.indexOfLast { (p, f) -> f != 0 || p != TouchAdmission.Phase.CONTINUE }
                val priorDown = boundary >= 0 && history[boundary].first == TouchAdmission.Phase.DOWN && history[boundary].second == 0
                val expected = flags == 0 && (phase == TouchAdmission.Phase.DOWN || priorDown && phase != TouchAdmission.Phase.OTHER)
                checkThat(gate.admit(phase, flags and 1 != 0, flags and 2 != 0) == expected)
                history += phase to flags
            }
            gate.reset(); checkThat(!gate.admit(TouchAdmission.Phase.UP, false, false))
        }
        val digits = VolatileDigits()
        for (i in 0 until 64) checkThat(digits.append(('0'.code + i % 10).toChar(), i.toLong()))
        val full = digits.reveal(64)
        checkThat(full.length == 64 && digits.size(64) == 64)
        checkThat(!digits.append('9', 65) && digits.reveal(65) == full)
        // Refused input/read/reveal do not renew inactivity lifetime.
        checkThat(!digits.expire(60_062) && digits.reveal(60_062) == full)
        checkThat(digits.expire(60_063) && digits.reveal(60_063).isEmpty())
        checkThat(full.length == 64) // Concrete counterexample to "all prior String copies are erased".
        checkThat(!digits.backspace(60_064))
        for (char in listOf('x', '\u0000', '\n', '\u0661', '\uFF11')) refused { digits.append(char, 70_000) }
        refused { digits.append('0', -1) }
        checkThat(digits.append('1', 100)); checkThat(digits.expire(99)); checkThat(digits.size(100) == 0)
        checkThat(digits.append('2', 1)); checkThat(digits.remainingMs(60_000) == 1L)
        checkThat(digits.remainingMs(60_001) == 0L && digits.reveal(60_001).isEmpty())
        checkThat(digits.append('3', Long.MAX_VALUE - 1)); checkThat(digits.remainingMs(Long.MAX_VALUE) == 59_999L)
        checkThat(digits.backspace(Long.MAX_VALUE)); checkThat(digits.size(Long.MAX_VALUE) == 0)
        checkThat(digits.append('4', 1)); checkThat(digits.expire(Long.MIN_VALUE)); checkThat(digits.size(1) == 0)
        repeat(1000) { round ->
            val text = (0..round % 63).joinToString("") { ((it + round) % 10).toString() }
            digits.clear()
            text.forEachIndexed { i, char -> checkThat(digits.append(char, i.toLong())) }
            checkThat(digits.reveal(100) == text)
            for (i in text.indices.reversed()) { checkThat(digits.backspace(101)); checkThat(digits.reveal(101) == text.take(i)) }
            digits.clear(); checkThat(digits.reveal(102).isEmpty())
        }
        for (ax in listOf(null, 0, 1)) for (kb in listOf(null, 0, 1)) for (display in listOf(null, 0, 1))
            for (adb in listOf(null, false, true)) for (dev in listOf(null, false, true)) {
                val report = LocalExposure(ax, kb, display, adb, dev).describe()
                val unknowns = listOf(ax, kb, display, adb, dev).count { it == null }
                checkThat(Regex("UNKNOWN \\(unavailable\\)").findAll(report).count() == unknowns)
                checkThat(report.contains("NOT a malware/keylogger scan") && report.contains("Zero/disabled does NOT"))
                checkThat(report.contains("wireless debugging is NOT comprehensively inspected"))
            }
        refused { LocalExposure(-1, 0, 0, false, false) }
        refused { LocalExposure(0, -1, 0, false, false) }
        refused { LocalExposure(0, 0, -1, false, false) }
        println("R23: $assertions assertions PASS; $traces exhaustive touch traces; no Android or hardware execution inferred.")
    }
}

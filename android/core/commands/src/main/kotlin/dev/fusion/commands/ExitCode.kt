// SPDX-License-Identifier: Apache-2.0
package dev.fusion.commands

/**
 * Exit-status naming: sysexits + shell conventions + 128+N signals.
 *
 * Sits next to [ArgSplit]-family parsing and [RedirPlan] (which plans
 * redirections): nothing names the status a command DIED with. [name]
 * covers 0 (success), 1/2/126/127 (shell conventions), 64–78
 * (BSD sysexits.h), and 128+N (death by Linux signal N, full 1–31
 * table). Signal NUMBERS are the documented Linux assignment — other
 * kernels' tables are outside. Naming is diagnosis, never handling:
 * no process is waited on or signaled.
 *
 * Plain Kotlin, no Android dependency, JVM unit-testable.
 */
object ExitCode {
    private val SYSEXITS = mapOf(
        64 to "EX_USAGE", 65 to "EX_DATAERR", 66 to "EX_NOINPUT", 67 to "EX_NOUSER",
        68 to "EX_NOHOST", 69 to "EX_UNAVAILABLE", 70 to "EX_SOFTWARE",
        71 to "EX_OSERR", 72 to "EX_OSFILE", 73 to "EX_CANTCREAT",
        74 to "EX_IOERR", 75 to "EX_TEMPFAIL", 76 to "EX_PROTOCOL",
        77 to "EX_NOPERM", 78 to "EX_CONFIG",
    )

    private val SIGNALS = mapOf(
        1 to "HUP", 2 to "INT", 3 to "QUIT", 4 to "ILL", 5 to "TRAP",
        6 to "ABRT", 7 to "BUS", 8 to "FPE", 9 to "KILL", 10 to "USR1",
        11 to "SEGV", 12 to "USR2", 13 to "PIPE", 14 to "ALRM", 15 to "TERM",
        16 to "STKFLT", 17 to "CHLD", 18 to "CONT", 19 to "STOP", 20 to "TSTP",
        21 to "TTIN", 22 to "TTOU", 23 to "URG", 24 to "XCPU", 25 to "XFSZ",
        26 to "VTALRM", 27 to "PROF", 28 to "WINCH", 29 to "IO", 30 to "PWR", 31 to "SYS",
    )

    /**
     * Human name for exit status [code].
     *
     * @throws IllegalArgumentException outside 0..255.
     */
    fun name(code: Int): String {
        require(code in 0..255) { "code out of range" }
        if (code == 0) return "success"
        if (code == 1) return "general error"
        if (code == 2) return "shell misuse"
        if (code == 126) return "not executable"
        if (code == 127) return "command not found"
        SYSEXITS[code]?.let { return it }
        if (code > 128) {
            val sig = code - 128
            SIGNALS[sig]?.let { return "killed by SIG$it" }
            return "killed by signal $sig"
        }
        return "exit $code"
    }

    /** True only for status 0. */
    fun isSuccess(code: Int): Boolean {
        require(code in 0..255) { "code out of range" }
        return code == 0
    }
}

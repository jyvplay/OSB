package dev.fusion.commands

/**
 * POSIX shell quoting for DISPLAY and copy-paste (the inverse direction
 * of [ArgSplit], which parses; this kernel renders).
 *
 * Rule set mirrors Python's shlex.quote/shlex.join, this kernel's
 * held-out cross-verifier: a token containing only characters from the
 * safe set `A-Za-z0-9_@%+=:,./-` is emitted bare; anything else is
 * wrapped in single quotes with embedded single quotes rendered as
 * `'"'"'`. An empty token renders as `''`.
 *
 * Declared narrowings: rendering only — nothing here executes; NUL is
 * refused (no shell accepts it); the output targets POSIX sh, not
 * cmd.exe or PowerShell (those are different kernels if ever needed).
 */
object ShlexJoin {
    const val SCOPE = "POSIX sh quoting for display only; nothing executes; NUL refused; not cmd/PowerShell."
    private val SAFE = ('a'..'z').toSet() + ('A'..'Z') + ('0'..'9') + "_@%+=:,./-".toSet()

    fun quote(token: String): String {
        require('\u0000' !in token) { "NUL cannot be represented in a shell word" }
        if (token.isEmpty()) return "''"
        if (token.all { it in SAFE }) return token
        return "'" + token.replace("'", "'\"'\"'") + "'"
    }

    fun join(tokens: List<String>): String {
        require(tokens.isNotEmpty()) { "empty command line" }
        return tokens.joinToString(" ") { quote(it) }
    }
}

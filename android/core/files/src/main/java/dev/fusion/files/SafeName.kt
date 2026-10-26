package dev.fusion.files

/**
 * Cross-filesystem filename safety (Microsoft "Naming Files, Paths, and
 * Namespaces" rules + POSIX): forbidden characters < > : " / \ | ? * and
 * controls 0x00-0x1F; Windows device names CON PRN AUX NUL COM1-COM9
 * LPT1-LPT9 are reserved even WITH an extension (CON.txt is reserved);
 * names may not end in a dot or space; "." and ".." are path tokens.
 *
 * sanitize() is deterministic and idempotent: forbidden characters
 * become '_', trailing dots/spaces are trimmed, reserved stems get a
 * leading '_'. It refuses when nothing printable survives rather than
 * inventing a name.
 *
 * Declared narrowings: single path COMPONENTS only (no separators, no
 * drive letters, no length budgeting across a full path); the 255-char
 * component cap is enforced in UTF-16 units as the common denominator.
 */
object SafeName {
    const val SCOPE = "Single-component checks + idempotent sanitize; no full-path or drive-letter handling."
    private const val FORBIDDEN = "<>:\"/\\|?*"
    private val RESERVED = buildSet {
        addAll(listOf("CON", "PRN", "AUX", "NUL"))
        for (i in 1..9) { add("COM$i"); add("LPT$i") }
    }

    fun isReservedStem(name: String): Boolean {
        val stem = name.substringBefore('.')
        return stem.uppercase() in RESERVED
    }

    fun isSafe(name: String): Boolean =
        name.isNotEmpty() && name != "." && name != ".." &&
            name.length <= 255 &&
            name.none { it in FORBIDDEN || it.code < 0x20 } &&
            !name.endsWith('.') && !name.endsWith(' ') &&
            !isReservedStem(name)

    fun sanitize(raw: String): String {
        val replaced = raw.map { if (it in FORBIDDEN || it.code < 0x20) '_' else it }
            .joinToString("")
            .trimEnd('.', ' ')
        require(replaced.isNotEmpty() && replaced.any { it != '_' }) {
            "nothing printable survives sanitizing '${raw.take(32)}'"
        }
        require(replaced != "." && replaced != "..") { "path token" }
        val named = if (isReservedStem(replaced)) "_$replaced" else replaced
        val capped = if (named.length > 255) named.substring(0, 255).trimEnd('.', ' ') else named
        return capped
    }
}

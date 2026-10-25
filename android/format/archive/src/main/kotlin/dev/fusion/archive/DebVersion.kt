package dev.fusion.archive

/**
 * Debian version comparison (Debian Policy §3.2.2's published total
 * order): `[epoch:]upstream[-revision]`; epoch numeric (default 0);
 * upstream/revision compare by alternating non-digit/digit parts
 * where `~` sorts BEFORE everything including the empty string (the
 * rule that makes 1.0~rc1 < 1.0), letters before non-letters among
 * non-digits, and digit parts compare numerically.
 *
 * Declared narrowings: comparison and parsing only — no package
 * relationships (>= constraints live with DebControl's consumers).
 */
object DebVersion {
    const val SCOPE = "Policy §3.2.2 total order incl. the tilde rule; constraints are the caller's layer."

    data class V(val epoch: Int, val upstream: String, val revision: String)

    fun parse(text: String): V {
        require(text.isNotEmpty() && text.none { it.isWhitespace() }) { "malformed version" }
        var rest = text
        var epoch = 0
        val colon = rest.indexOf(':')
        if (colon >= 0) {
            val e = rest.substring(0, colon)
            require(e.isNotEmpty() && e.all { it.isDigit() }) { "epoch '" + e + "' not numeric" }
            epoch = e.toInt()
            rest = rest.substring(colon + 1)
        }
        val dash = rest.lastIndexOf('-')
        val upstream: String; val revision: String
        if (dash >= 0) { upstream = rest.substring(0, dash); revision = rest.substring(dash + 1) }
        else { upstream = rest; revision = "0" }
        require(upstream.isNotEmpty()) { "empty upstream version" }
        require(upstream.all { it.isLetterOrDigit() || it in ".+-:~" }) { "character outside the version alphabet" }
        return V(epoch, upstream, revision)
    }

    private fun order(c: Char): Int = when {
        c == '~' -> -1
        c.isDigit() -> 0
        c.isLetter() -> c.code
        else -> c.code + 256
    }

    fun cmpPart(a: String, b: String): Int {
        var i = 0; var j = 0
        while (i < a.length || j < b.length) {
            while ((i < a.length && !a[i].isDigit()) || (j < b.length && !b[j].isDigit())) {
                val ac = if (i < a.length && !a[i].isDigit()) order(a[i]) else 0
                val bc = if (j < b.length && !b[j].isDigit()) order(b[j]) else 0
                if (ac != bc) return ac - bc
                if (i < a.length && !a[i].isDigit()) i++
                if (j < b.length && !b[j].isDigit()) j++
                if (ac == 0 && bc == 0) break
            }
            var x = 0L; var y = 0L
            while (i < a.length && a[i].isDigit()) { x = x * 10 + (a[i] - '0'); i++ }
            while (j < b.length && b[j].isDigit()) { y = y * 10 + (b[j] - '0'); j++ }
            if (x != y) return if (x < y) -1 else 1
        }
        return 0
    }

    fun compare(a: V, b: V): Int {
        if (a.epoch != b.epoch) return a.epoch - b.epoch
        val up = cmpPart(a.upstream, b.upstream)
        if (up != 0) return up
        return cmpPart(a.revision, b.revision)
    }

    fun compare(a: String, b: String): Int = compare(parse(a), parse(b))
}

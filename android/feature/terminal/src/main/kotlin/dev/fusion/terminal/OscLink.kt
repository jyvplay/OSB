// SPDX-License-Identifier: Apache-2.0
package dev.fusion.terminal

/**
 * OSC 8 terminal-hyperlink parsing (`ESC ] 8 ; params ; URI ST`).
 *
 * Sits next to [AnsiSgr] (which owns SGR styling only — no OSC
 * handling): nothing parses terminal hyperlinks. [parse] validates one
 * complete sequence, splits the `k=v:k=v` params (empty params legal),
 * and returns the URI plus the hover `id` when present. An empty URI
 * is the documented CLOSE marker, not an error. Emitting, hover
 * behavior, and URI fetching are outside; a parsed link is "text with
 * an address", never a navigation claim.
 *
 * Plain Kotlin, no Android dependency, JVM unit-testable.
 */
object OscLink {
    const val MAX_SEQ = 4_096

    /** A parsed hyperlink: params, URI, and optional hover id. */
    data class Link(val params: Map<String, String>, val uri: String, val id: String?)

    /**
     * Parses one OSC 8 sequence.
     *
     * @throws IllegalArgumentException on a bad prefix/terminator, bad
     * params, or a URI with whitespace/control characters.
     */
    fun parse(seq: String): Link {
        require(seq.length <= MAX_SEQ) { "sequence over cap" }
        require(seq.startsWith("\u001B]8;")) { "bad prefix" }
        val body: String
        if (seq.endsWith("\u0007")) {
            body = seq.substring(4, seq.length - 1)
        } else if (seq.endsWith("\u001B\\")) {
            body = seq.substring(4, seq.length - 2)
        } else {
            throw IllegalArgumentException("bad terminator")
        }
        val semi = body.indexOf(';')
        require(semi >= 0) { "missing param separator" }
        val params = linkedMapOf<String, String>()
        val praw = body.substring(0, semi)
        if (praw.isNotEmpty()) {
            for (kv in praw.split(":")) {
                val eq = kv.indexOf('=')
                require(eq > 0) { "bad param" }
                val k = kv.substring(0, eq)
                require(k.isNotEmpty() && k.all { it.isLetterOrDigit() || it == '-' || it == '_' }) {
                    "bad param key"
                }
                require(k !in params) { "dup param" }
                params[k] = kv.substring(eq + 1)
            }
        }
        val uri = body.substring(semi + 1)
        require(uri.none { it.isWhitespace() || it.code < 0x20 || it.code == 0x7F }) { "bad uri" }
        return Link(params, uri, params["id"])
    }
}

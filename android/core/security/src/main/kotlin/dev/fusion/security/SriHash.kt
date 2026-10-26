package dev.fusion.security

/**
 * Subresource Integrity metadata parsing (W3C SRI: tokens of the form
 * `sha256-<base64>`, `sha384-…`, `sha512-…`; a metadata LIST keeps
 * only the strongest algorithm's tokens — weaker entries are dropped
 * per spec, and matching any one of the strongest set passes).
 *
 * THIS KERNEL NEVER HASHES CONTENT: it parses and selects. The
 * comparison step takes a caller-computed digest (base64) so the
 * crypto stays where the bytes are.
 *
 * Declared narrowings: the three registered algorithms only; options
 * (`?opt`) are parsed off and reported, not interpreted; base64
 * length must match the algorithm's digest size exactly.
 */
object SriHash {
    const val SCOPE = "Parse + strongest-wins selection + caller-digest comparison; this kernel never hashes."

    enum class Alg(val b64Length: Int) { SHA256(44), SHA384(64), SHA512(88) }

    data class Token(val alg: Alg, val digestB64: String, val options: String?)

    fun parseToken(text: String): Token {
        val q = text.indexOf('?')
        val core = if (q >= 0) text.substring(0, q) else text
        val opt = if (q >= 0) text.substring(q + 1) else null
        val dash = core.indexOf('-')
        require(dash > 0) { "SRI token lacks the alg-digest dash" }
        val alg = when (core.substring(0, dash).lowercase()) {
            "sha256" -> Alg.SHA256; "sha384" -> Alg.SHA384; "sha512" -> Alg.SHA512
            else -> throw IllegalArgumentException("algorithm '" + core.substring(0, dash) + "' outside the registered set")
        }
        val b64 = core.substring(dash + 1)
        require(b64.length == alg.b64Length) { "digest length " + b64.length + " wrong for " + alg }
        require(b64.all { it.isLetterOrDigit() || it == '+' || it == '/' || it == '=' }) { "non-base64 character" }
        return Token(alg, b64, opt)
    }

    /** Spec rule: keep only the strongest algorithm's tokens. */
    fun strongest(metadata: List<String>): List<Token> {
        val tokens = metadata.map { parseToken(it) }
        require(tokens.isNotEmpty()) { "empty metadata" }
        val best = tokens.maxOf { it.alg }
        return tokens.filter { it.alg == best }
    }

    /** Pass when the caller-computed digest matches ANY strongest token. */
    fun matches(metadata: List<String>, alg: Alg, digestB64: String): Boolean {
        val strong = strongest(metadata)
        if (strong.first().alg != alg) return false
        return strong.any { it.digestB64 == digestB64 }
    }
}

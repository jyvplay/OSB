package dev.fusion.together

/**
 * Join-code format for shared sessions (stated contract): 8 chars of
 * the Crockford-style alphabet WITHOUT I/L/O/U (re-pinned here, not
 * imported across modules), displayed as XXXX-XXXX; decode is
 * case-insensitive and hyphen-tolerant; identity is the canonical
 * uppercase form.
 *
 * Distinct from PairPin (digits + Damm error detection) and Ulid
 * (26-char ids): this is the human-speakable session namespace, and
 * ITS error story is the confirm-on-both-screens flow, stated here
 * so nobody retrofits a checksum claim.
 */
object SessionCode {
    const val SCOPE = "8-char no-ILOU codes, XXXX-XXXX display; error handling is the confirm flow, NOT a checksum."
    const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
    const val LENGTH = 8

    fun canonical(input: String): String {
        val s = input.replace("-", "").uppercase()
        require(s.length == LENGTH) { "code must be " + LENGTH + " chars, got " + s.length }
        for (c in s) require(c in ALPHABET) { "'" + c + "' not in the code alphabet (I/L/O/U excluded)" }
        return s
    }

    fun display(code: String): String {
        val c = canonical(code)
        return c.substring(0, 4) + "-" + c.substring(4)
    }

    fun matches(a: String, b: String): Boolean = canonical(a) == canonical(b)
}

package dev.fusion.security

/**
 * WebAuthn RP-ID shape and origin-host matching (the registrable-
 * suffix rule: an RP ID is valid for an origin whose host equals it
 * or ends with "." + rpId).
 *
 * HONESTY FIRST: full WebAuthn validity ALSO requires that the RP ID
 * not be a public suffix (needs a PSL — owned elsewhere and NOT
 * consulted here). This kernel checks shape and dot-boundary matching
 * ONLY, and its result type carries `pslChecked = false` so no caller
 * can mistake this for the complete rule.
 *
 * Declared narrowings: lowercase DNS-ish labels, no scheme/port/path
 * in the rpId (refused), no IP-address RP IDs (refused).
 */
object RpIdMatch {
    const val SCOPE = "Shape + dot-boundary suffix match; PSL/public-suffix rule NOT applied here (flagged in result)."

    data class Match(val matches: Boolean, val pslChecked: Boolean = false)

    fun validateRpId(rpId: String): String {
        require(rpId.isNotEmpty() && rpId.length <= 253) { "rpId length" }
        require("://" !in rpId && ':' !in rpId && '/' !in rpId) { "rpId must be a bare host" }
        require(rpId == rpId.lowercase()) { "rpId must be lowercase" }
        val labels = rpId.split('.')
        require(labels.all { it.isNotEmpty() && it.length <= 63 && it.all { c -> c.isLetterOrDigit() || c == '-' } && !it.startsWith('-') && !it.endsWith('-') }) {
            "rpId label malformed"
        }
        require(!labels.all { it.all(Char::isDigit) }) { "IP-address RP IDs refused" }
        return rpId
    }

    fun matches(originHost: String, rpId: String): Match {
        validateRpId(rpId)
        val host = originHost.lowercase()
        val m = host == rpId || host.endsWith(".$rpId")
        return Match(m)
    }
}

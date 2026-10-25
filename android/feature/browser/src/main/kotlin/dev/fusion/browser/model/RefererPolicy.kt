package dev.fusion.browser.model

/**
 * Referrer-Policy decision table (W3C Referrer Policy): given a
 * policy, the request's source and target origins and their schemes,
 * decide whether the Referer header carries the FULL url, the ORIGIN
 * only, or NOTHING.
 *
 * Origins here are (scheme, host, port) triples compared exactly;
 * "downgrade" means an https source to an http target.
 *
 * Declared narrowings: the eight registered policy tokens only;
 * URL-within-origin truncation (stripping path/fragment) is the
 * caller's string job — this kernel returns the decision, not the
 * header bytes.
 */
object RefererPolicy {
    const val SCOPE = "Eight-token decision table only; header serialization is the caller's layer."

    enum class Policy { NO_REFERRER, NO_REFERRER_WHEN_DOWNGRADE, ORIGIN, ORIGIN_WHEN_CROSS_ORIGIN, SAME_ORIGIN, STRICT_ORIGIN, STRICT_ORIGIN_WHEN_CROSS_ORIGIN, UNSAFE_URL }
    enum class Send { FULL, ORIGIN, NOTHING }

    data class Origin(val scheme: String, val host: String, val port: Int) {
        init {
            require(scheme == "http" || scheme == "https") { "scheme '$scheme' outside http(s) scope" }
            require(host.isNotEmpty() && port in 1..65535) { "bad origin" }
        }
    }

    fun parse(token: String): Policy = when (token.trim().lowercase()) {
        "no-referrer" -> Policy.NO_REFERRER
        "no-referrer-when-downgrade" -> Policy.NO_REFERRER_WHEN_DOWNGRADE
        "origin" -> Policy.ORIGIN
        "origin-when-cross-origin" -> Policy.ORIGIN_WHEN_CROSS_ORIGIN
        "same-origin" -> Policy.SAME_ORIGIN
        "strict-origin" -> Policy.STRICT_ORIGIN
        "strict-origin-when-cross-origin" -> Policy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN
        "unsafe-url" -> Policy.UNSAFE_URL
        else -> throw IllegalArgumentException("unknown policy token '$token'")
    }

    fun decide(policy: Policy, from: Origin, to: Origin): Send {
        val sameOrigin = from == to
        val downgrade = from.scheme == "https" && to.scheme == "http"
        return when (policy) {
            Policy.NO_REFERRER -> Send.NOTHING
            Policy.UNSAFE_URL -> Send.FULL
            Policy.NO_REFERRER_WHEN_DOWNGRADE -> if (downgrade) Send.NOTHING else Send.FULL
            Policy.ORIGIN -> Send.ORIGIN
            Policy.ORIGIN_WHEN_CROSS_ORIGIN -> if (sameOrigin) Send.FULL else Send.ORIGIN
            Policy.SAME_ORIGIN -> if (sameOrigin) Send.FULL else Send.NOTHING
            Policy.STRICT_ORIGIN -> if (downgrade) Send.NOTHING else Send.ORIGIN
            Policy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN ->
                if (downgrade) Send.NOTHING else if (sameOrigin) Send.FULL else Send.ORIGIN
        }
    }

    /** The web default when no policy is declared. */
    val DEFAULT = Policy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN
}

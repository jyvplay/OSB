package dev.fusion.castv2

/**
 * CASTV2 namespace-string validation (the `urn:x-cast:` namespace form
 * every CASTV2 channel message carries; CastV2Client holds the
 * well-known constants — this kernel validates the FORM so arbitrary
 * receiver apps' namespaces can be checked before a channel opens).
 *
 * Rules: mandatory `urn:x-cast:` prefix; a non-empty suffix of ASCII
 * letters, digits, `.`, `_`, `-`; no empty dot-segments (`..` or
 * leading/trailing dot); total length capped at 128 (the protocol
 * field is length-prefixed; oversized names are either bugs or abuse).
 *
 * Declared narrowings: string-form validation only — no channel
 * semantics, no payload schemas, no network.
 */
object CastNs {
    const val SCOPE = "urn:x-cast: form validation only; channel semantics and payloads live in CastV2Client."
    const val PREFIX = "urn:x-cast:"
    const val MAX_LENGTH = 128

    enum class WellKnown(val suffix: String) {
        CONNECTION("com.google.cast.tp.connection"),
        HEARTBEAT("com.google.cast.tp.heartbeat"),
        RECEIVER("com.google.cast.receiver"),
        MEDIA("com.google.cast.media")
    }

    fun validate(namespace: String): String {
        require(namespace.length <= MAX_LENGTH) { "namespace exceeds $MAX_LENGTH chars" }
        require(namespace.startsWith(PREFIX)) { "namespace must start with '$PREFIX'" }
        val suffix = namespace.removePrefix(PREFIX)
        require(suffix.isNotEmpty()) { "empty namespace suffix" }
        require(suffix.all { it.isLetterOrDigit() && it.code < 128 || it in "._-" }) {
            "namespace suffix carries an illegal character"
        }
        require(!suffix.startsWith('.') && !suffix.endsWith('.')) { "leading/trailing dot" }
        require(".." !in suffix) { "empty dot-segment" }
        return namespace
    }

    fun wellKnown(namespace: String): WellKnown? {
        val suffix = validate(namespace).removePrefix(PREFIX)
        return WellKnown.values().firstOrNull { it.suffix == suffix }
    }

    fun isValid(namespace: String): Boolean =
        try { validate(namespace); true } catch (_: IllegalArgumentException) { false }
}

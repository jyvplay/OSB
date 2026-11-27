// SPDX-License-Identifier: Apache-2.0
package dev.fusion.privacy

/**
 * R146 — allowlist-first diagnostic export builder (owner:v2.0 SC-08
 * "Telemetry off/local logs/redacted export").
 *
 * A diagnostic record arrives as `key=value` pairs; an export runs only the
 * keys the operator allowlisted, drops everything else **before** any value
 * inspection (so a secret in a non-allowlisted key is never even pattern
 * matched), then scrubs the surviving values with the same deterministic
 * shapes as [LogScrubber] (email, IPv4, UUID, long hex, bearer/token,
 * absolute device paths) so an exported file is safe to attach to a bug
 * report. Output is sorted by key for byte-stable diffs.
 *
 * Declared narrowings: at most [MAX_RECORDS] records; keys `[a-z][a-z0-9_.-]{0,63}`
 * and unique; values <= [MAX_VALUE] UTF-16 units; allowlist <= [MAX_ALLOW]
 * keys; the scrubbed output re-scrubs to itself (idempotence is asserted by
 * the host oracle). Heuristic shapes only — never a leak proof.
 */
object DiagnosticExportAllowlist {
    const val SCOPE =
        "Allowlist-filtered, pattern-scrubbed, key-sorted diagnostic export; local-only, no network, no collection."

    const val MAX_RECORDS = 256
    const val MAX_ALLOW = 128
    const val MAX_VALUE = 1024
    const val MAX_KEY = 64

    private val KEY_RE = Regex("[a-z][a-z0-9_.-]{0,63}")
    private val EMAIL = Regex("[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}")
    private val IPV4 = Regex("(?<!\\d)(?:(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)\\.){3}(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)(?!\\d)")
    private val UUID = Regex("(?i)(?<![0-9a-f])[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}(?![0-9a-f])")
    private val LONG_HEX = Regex("(?<![0-9a-fA-F])[0-9a-fA-F]{32,}(?![0-9a-fA-F])")
    private val BEARER = Regex("(?i)(bearer\\s+)[A-Za-z0-9\\-._~+/=]+")
    private val TOKEN = Regex("(?i)(token=)[^\\s&;]+")
    private val PATH = Regex("/(data|sdcard|storage|system|vendor)/[^\\s:'\"]*")

    fun scrubValue(value: String): String {
        require(value.length <= MAX_VALUE) { "value over $MAX_VALUE units" }
        var out = value
        out = BEARER.replace(out, "$1[secret]")
        out = TOKEN.replace(out, "$1[secret]")
        out = EMAIL.replace(out, "[email]")
        out = IPV4.replace(out, "[ip]")
        out = UUID.replace(out, "[uuid]")
        out = LONG_HEX.replace(out, "[hex]")
        out = PATH.replace(out, "[path]")
        return out
    }

    /**
     * Input: `allow` lane = comma-joined allowlist keys; `export` lane pairs
     * are `key=pct(value)` joined by `,`. Output: scrubbed `key=value` lines
     * sorted by key, joined by newline. Non-allowlisted keys are dropped
     * silently (they are not an error — that is the point).
     */
    fun export(allowSpec: String, recordsSpec: String): String {
        require(recordsSpec.length <= MAX_RECORDS * (MAX_VALUE * 3 + MAX_KEY + 2)) { "record input too large" }
        val records = if (recordsSpec.isEmpty()) emptyList() else recordsSpec.split(',')
        require(records.size <= MAX_RECORDS) { "too many records: $MAX_RECORDS" }
        return export(allowSpec, records.map { record ->
            val eq = record.indexOf('=')
            require(eq >= 1) { "record must be key=value" }
            record.substring(0, eq) to pctDecode(record.substring(eq + 1))
        })
    }

    /** Structured API: values are literal text, not transport-encoded. */
    fun export(allowSpec: String, records: List<Pair<String, String>>): String {
        require(allowSpec.length <= MAX_ALLOW * (MAX_KEY + 1)) { "allowlist too large" }
        val allow = if (allowSpec.isEmpty()) emptyList() else allowSpec.split(',')
        require(allow.size <= MAX_ALLOW) { "allowlist over $MAX_ALLOW keys" }
        val allowSet = HashSet<String>()
        for (k in allow) {
            require(KEY_RE.matches(k)) { "invalid allowlist key" }
            require(allowSet.add(k)) { "duplicate allowlist key" }
        }
        require(records.size <= MAX_RECORDS) { "too many records: $MAX_RECORDS" }
        val seen = HashSet<String>()
        val kept = ArrayList<Pair<String, String>>()
        for ((key, value) in records) {
            require(KEY_RE.matches(key)) { "invalid record key" }
            require(seen.add(key)) { "duplicate record key" }
            require(value.length <= MAX_VALUE) { "value over $MAX_VALUE units" }
            if (key in allowSet) {
                require(value.none { it.code < 32 || it.code == 127 }) { "control character in exported value" }
                kept.add(key to scrubValue(value))
            }
        }
        kept.sortBy { it.first }
        if (kept.isEmpty()) return ""
        return kept.joinToString("\n") { "${it.first}=${it.second}" }
    }

    private fun pctDecode(s: String): String {
        val out = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '%') {
                require(i + 2 < s.length) { "truncated escape in record value" }
                val hi = Character.digit(s[i + 1], 16)
                val lo = Character.digit(s[i + 2], 16)
                require(hi >= 0 && lo >= 0) { "bad escape in record value" }
                out.append(((hi shl 4) or lo).toChar())
                i += 3
            } else {
                out.append(c)
                i++
            }
        }
        return out.toString()
    }
}

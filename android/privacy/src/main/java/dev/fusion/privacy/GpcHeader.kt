package dev.fusion.privacy

/**
 * Global Privacy Control signals (the GPC specification: request
 * header `Sec-GPC: 1` — the ONLY valid value — and the
 * `/.well-known/gpc.json` resource with `gpc` true/false and an
 * optional `lastUpdate` date).
 *
 * Parsing is strict because the signal is legal surface: `Sec-GPC: 0`
 * or any other value is NOT a valid opt-out and MUST NOT be treated
 * as one — this kernel refuses rather than coerces, so policy code
 * can never accidentally manufacture consent state.
 *
 * Declared narrowings: signal syntax only — no legal interpretation,
 * no storage; the well-known check is a MINIMAL field extraction (gpc
 * boolean, lastUpdate string-shape), not a JSON parser.
 */
object GpcHeader {
    const val SCOPE = "Sec-GPC/well-known syntax only; invalid values refuse, never coerce to consent state."
    const val HEADER = "Sec-GPC"

    /** True only for the exact value "1" (the spec's only defined value). */
    fun parseHeader(value: String): Boolean {
        require(value.trim() == "1") { "Sec-GPC '$value' is not the defined value '1' — refused, not coerced" }
        return true
    }

    fun isSignalPresent(headers: Map<String, String>): Boolean {
        val v = headers.entries.firstOrNull { it.key.equals(HEADER, ignoreCase = true) }?.value ?: return false
        return parseHeader(v)
    }

    data class WellKnown(val gpc: Boolean, val lastUpdate: String?)

    fun parseWellKnown(json: String): WellKnown {
        val t = json.trim()
        require(t.startsWith("{") && t.endsWith("}")) { "not a JSON object" }
        val gpc = when {
            Regex("\"gpc\"\\s*:\\s*true").containsMatchIn(t) -> true
            Regex("\"gpc\"\\s*:\\s*false").containsMatchIn(t) -> false
            else -> throw IllegalArgumentException("gpc.json lacks a boolean gpc field")
        }
        val lu = Regex("\"lastUpdate\"\\s*:\\s*\"([0-9]{4}-[0-9]{2}-[0-9]{2})\"").find(t)?.groupValues?.get(1)
        return WellKnown(gpc, lu)
    }
}

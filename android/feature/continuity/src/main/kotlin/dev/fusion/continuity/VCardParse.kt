// SPDX-License-Identifier: Apache-2.0
package dev.fusion.continuity

/**
 * Single-vCard parser (RFC 6350 contact subset).
 *
 * Sits next to [VEventParse] (events) and [VTodoParse] (tasks): handoff
 * syncs contacts too, and nothing parses them. One `BEGIN:VCARD` …
 * `END:VCARD` block yields VERSION (3.0 or 4.0, required), FN (required),
 * N (family;given;middle;prefix;suffix, at most one), TEL (repeatable,
 * optional TYPE params), and EMAIL (repeatable, optional TYPE params).
 * Line folding and `\,`/`\;`/`\\`/`\n` escapes mirror [VTodoParse]. Like
 * its siblings, unknown properties are refused rather than skipped so a
 * "parsed" contact never silently drops fields — and unknown line params
 * are refused for the same reason, with TYPE the only accepted param.
 * PHOTO/ADR/ORG and the rest of vCard are explicitly outside this subset.
 *
 * Plain Kotlin, no Android dependency, JVM unit-testable.
 */
object VCardParse {

    /** A typed contact value (phone or email) with its TYPE labels. */
    data class Field(val types: List<String>, val value: String)

    /** A parsed contact. */
    data class Card(
        val version: String,
        val fullName: String,
        val family: String?,
        val given: String?,
        val phones: List<Field>,
        val emails: List<Field>,
    )

    /**
     * Parses one vCard block.
     *
     * @throws IllegalArgumentException on malformed structure, missing
     * VERSION or FN, unknown properties or params, or bad values.
     */
    fun parse(text: String): Card {
        val lines = unfold(text)
        require(lines.size >= 2) { "short block" }
        require(lines.first() == "BEGIN:VCARD") { "no BEGIN" }
        require(lines.last() == "END:VCARD") { "no END" }
        var version: String? = null
        var fn: String? = null
        var family: String? = null
        var given: String? = null
        var nSeen = false
        val phones = mutableListOf<Field>()
        val emails = mutableListOf<Field>()
        for (line in lines.subList(1, lines.size - 1)) {
            require(':' in line) { "no colon" }
            val head = line.substringBefore(':')
            val value = line.substringAfter(':')
            val parts = head.split(';')
            val name = parts.first().uppercase()
            val types = mutableListOf<String>()
            for (p in parts.drop(1)) {
                val pname = p.substringBefore('=').uppercase()
                require(pname == "TYPE") { "unknown param $pname" }
                val pval = p.substringAfter('=', "")
                require(pval.isNotEmpty()) { "empty TYPE" }
                types.addAll(pval.split(',').map { it.uppercase() })
            }
            require(types.distinct().size == types.size) { "dup TYPE" }
            when (name) {
                "VERSION" -> {
                    require(value == "3.0" || value == "4.0") { "bad VERSION" }
                    version = value
                }
                "FN" -> {
                    require(value.isNotEmpty()) { "empty FN" }
                    fn = unescape(value)
                }
                "N" -> {
                    require(!nSeen) { "dup N" }
                    nSeen = true
                    val bits = value.split(';')
                    require(bits.size <= 5) { "bad N" }
                    family = unescape(bits.getOrElse(0) { "" }).takeIf { it.isNotEmpty() }
                    given = unescape(bits.getOrElse(1) { "" }).takeIf { it.isNotEmpty() }
                }
                "TEL" -> {
                    require(value.isNotEmpty()) { "empty TEL" }
                    phones.add(Field(types, unescape(value)))
                }
                "EMAIL" -> {
                    require(value.isNotEmpty()) { "empty EMAIL" }
                    emails.add(Field(types, unescape(value)))
                }
                else -> throw IllegalArgumentException("unknown property $name")
            }
        }
        return Card(
            version ?: throw IllegalArgumentException("missing VERSION"),
            fn ?: throw IllegalArgumentException("missing FN"),
            family, given, phones, emails,
        )
    }

    private fun unfold(text: String): List<String> {
        val raw = text.split("\r\n", "\n")
        val out = mutableListOf<String>()
        for (line in raw) {
            if (line.startsWith(' ') && out.isNotEmpty()) {
                out[out.lastIndex] = out.last() + line.drop(1)
            } else {
                out += line.trimEnd('\r')
            }
        }
        return out.filter { it.isNotEmpty() }
    }

    private fun unescape(s: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (s[i + 1]) {
                    'n', 'N' -> out.append('\n')
                    ',', ';', '\\' -> out.append(s[i + 1])
                    else -> throw IllegalArgumentException("bad escape")
                }
                i += 2
            } else {
                out.append(c)
                i++
            }
        }
        return out.toString()
    }
}

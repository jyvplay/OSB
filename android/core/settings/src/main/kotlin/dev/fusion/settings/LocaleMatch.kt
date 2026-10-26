// SPDX-License-Identifier: Apache-2.0
package dev.fusion.settings

/** Normalized language tag: lowercase ISO-639 language plus optional uppercase region. */
data class LangTag(val lang: String, val region: String?) {
    override fun toString(): String = if (region == null) lang else "$lang-$region"
}

/**
 * BCP-47-subset locale negotiation. Each supported locale is lang[-REGION];
 * lookup order is exact match, then first same-language entry, then the
 * supported-list default (first entry). Script/extension/variant subtags are
 * refused rather than silently dropped so callers know what was negotiated.
 */
object LocaleMatch {
    const val MAX_SUPPORTED = 128
    private val LANG = Regex("[a-z]{2,3}")
    private val REGION = Regex("[A-Z]{2}")

    fun parse(tag: String): LangTag {
        require(tag.isNotEmpty() && tag.length <= 35 && ' ' !in tag && '_' !in tag)
        val parts = tag.split("-")
        require(parts.size in 1..2 && parts.all { it.isNotEmpty() })
        val lang = parts[0].lowercase()
        require(LANG.matches(lang))
        var region: String? = null
        if (parts.size == 2) {
            region = parts[1].uppercase()
            require(REGION.matches(region))
        }
        return LangTag(lang, region)
    }

    fun match(request: String, supported: List<String>): String? {
        require(supported.size <= MAX_SUPPORTED)
        if (supported.isEmpty()) return null
        val want = parse(request)
        val have = supported.map(::parse)
        have.firstOrNull { it == want }?.let { return it.toString() }
        have.firstOrNull { it.lang == want.lang }?.let { return it.toString() }
        return have[0].toString()
    }
}

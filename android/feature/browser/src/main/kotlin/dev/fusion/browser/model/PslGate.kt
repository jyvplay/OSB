// SPDX-License-Identifier: Apache-2.0
package dev.fusion.browser.model

/**
 * Clean-room Mozilla Public Suffix List (PSL) rule-matching engine completing the R43 `RpIdMatch`
 * and R44 §J queued PSL interface.
 *
 * Implements the official PSL specification (`https://publicsuffix.org/list/`):
 * - Exact rules (`com`, `co.uk`, `gov.uk`),
 * - Wildcard rules (`*.ck`, `*.kawasaki.jp`),
 * - Exception rules (`!www.ck`, `!city.kawasaki.jp`) taking highest precedence over wildcards,
 * - Longest matching rule label count wins otherwise.
 *
 * Runtime-honesty contract: rather than pretending a static mobile library contains a live,
 * auto-updated 10,000-rule ICANN+PRIVATE list, [PslGate] ships an explicitly version-pinned
 * core ICANN multi-level subset ([PINNED_VERSION]) AND refuses unlisted TLDs via a 3-valued
 * [Decision] (`REGISTRABLE`, `PUBLIC_SUFFIX`, `UNLISTED_TLD`) instead of PSL's legacy `*`
 * catch-all, while accepting caller-supplied [RuleSet] snapshots via [parseRuleList].
 */
object PslGate {
    const val SCOPE =
        "Mozilla PSL exact/wildcard/exception priority matcher with 3-valued REGISTRABLE/PUBLIC_SUFFIX/UNLISTED_TLD verdict over version-pinned ICANN subset or caller RuleSet; Punycode IDNA2008 tables live in Punycode/HostCanon."

    const val PINNED_VERSION = "2026-10-icann-core-v1"
    const val MAX_RULES = 32_768

    sealed interface Decision {
        data class Registrable(val registrableDomain: String, val publicSuffix: String) : Decision
        data class PublicSuffixOnly(val publicSuffix: String) : Decision
        data class UnlistedTld(val tld: String) : Decision
    }

    class RuleSet internal constructor(
        val version: String,
        internal val exactRules: Set<String>,
        internal val wildcardBases: Set<String>,
        internal val exceptionRules: Set<String>,
    ) {
        val ruleCount: Int get() = exactRules.size + wildcardBases.size + exceptionRules.size
    }

    val PINNED_ICANN_CORE: RuleSet by lazy {
        parseRuleList(
            version = PINNED_VERSION,
            lines = listOf(
                // Core gTLDs & common ccTLDs
                "com", "org", "net", "edu", "gov", "mil", "int", "io", "dev", "app", "ai", "org",
                "info", "biz", "name", "pro", "xyz", "online", "site", "tech", "cloud", "page",
                "us", "ca", "de", "fr", "nl", "ch", "se", "no", "fi", "dk", "it", "es", "pl", "ie",
                // UK multi-level ICANN rules
                "uk", "co.uk", "org.uk", "me.uk", "net.uk", "ltd.uk", "plc.uk", "gov.uk", "ac.uk", "nhs.uk",
                // Australia
                "au", "com.au", "net.au", "org.au", "edu.au", "gov.au", "asn.au", "id.au",
                // Japan (including official PSL wildcard + city exception structure)
                "jp", "co.jp", "ne.jp", "or.jp", "ac.jp", "ad.jp", "ed.jp", "go.jp", "gr.jp", "lg.jp",
                "*.kawasaki.jp", "!city.kawasaki.jp",
                "*.kitakyushu.jp", "!city.kitakyushu.jp",
                "*.kobe.jp", "!city.kobe.jp",
                "*.nagoya.jp", "!city.nagoya.jp",
                "*.sapporo.jp", "!city.sapporo.jp",
                "*.sendai.jp", "!city.sendai.jp",
                "*.yokohama.jp", "!city.yokohama.jp",
                // Cook Islands (classic PSL spec `*.ck` + `!www.ck` example)
                "*.ck", "!www.ck",
                // Additional multi-level ccTLDs
                "nz", "co.nz", "net.nz", "org.nz", "govt.nz", "ac.nz",
                "br", "com.br", "net.br", "org.br", "gov.br", "edu.br",
                "in", "co.in", "net.in", "org.in", "gov.in", "ac.in",
                "sg", "com.sg", "net.sg", "org.sg", "gov.sg", "edu.sg",
                "hk", "com.hk", "edu.hk", "gov.hk", "idv.hk", "net.hk", "org.hk",
                "kr", "co.kr", "ne.kr", "or.kr", "re.kr", "pe.kr", "go.kr", "ac.kr",
                "mx", "com.mx", "org.mx", "gob.mx", "edu.mx", "net.mx",
                "za", "co.za", "org.za", "net.za", "gov.za", "ac.za",
                "cn", "com.cn", "net.cn", "org.cn", "gov.cn", "edu.cn",
                "tw", "com.tw", "org.tw", "net.tw", "gov.tw", "edu.tw",
            ),
        )
    }

    fun parseRuleList(version: String, lines: Iterable<String>): RuleSet {
        require(version.isNotBlank()) { "RuleSet version must not be blank" }
        val exact = LinkedHashSet<String>()
        val wild = LinkedHashSet<String>()
        val exc = LinkedHashSet<String>()
        var count = 0

        for (raw in lines) {
            val line = raw.substringBefore("//").trim().lowercase()
            if (line.isEmpty()) continue
            count++
            require(count <= MAX_RULES) { "PSL rule count exceeds $MAX_RULES" }
            when {
                line.startsWith("!") -> {
                    val body = line.substring(1)
                    validateLabels(body.split('.'))
                    exc.add(body)
                }
                line.startsWith("*.") -> {
                    val base = line.substring(2)
                    validateLabels(base.split('.'))
                    wild.add(base)
                }
                else -> {
                    require('*' !in line && '!' !in line) { "unsupported PSL rule syntax '$raw'" }
                    validateLabels(line.split('.'))
                    exact.add(line)
                }
            }
        }
        return RuleSet(version, exact, wild, exc)
    }

    fun classify(host: String, rules: RuleSet = PINNED_ICANN_CORE): Decision {
        val clean = host.trim().lowercase()
        require(clean.isNotEmpty() && clean.length <= 253) { "invalid host length '${host.length}'" }
        require(!clean.startsWith(".") && !clean.endsWith(".")) {
            "leading or trailing dot refused in '$host'"
        }
        val labels = clean.split('.')
        validateLabels(labels)

        // Find all matching rules from the right-hand TLD suffix inward.
        // Exception rule (`!foo.bar`) has priority; its effective public suffix is `bar` (strip leftmost label).
        for (startIdx in labels.indices) {
            val candidate = labels.subList(startIdx, labels.size).joinToString(".")
            if (candidate in rules.exceptionRules) {
                val suffixLabels = labels.size - startIdx - 1
                val pubSuffix = labels.takeLast(suffixLabels).joinToString(".")
                val regLabels = suffixLabels + 1
                val regDomain = labels.takeLast(regLabels).joinToString(".")
                return Decision.Registrable(regDomain, pubSuffix)
            }
        }

        var matchedSuffixLabelCount = 0
        for (startIdx in labels.indices) {
            val suffixLen = labels.size - startIdx
            val candidate = labels.subList(startIdx, labels.size).joinToString(".")
            if (candidate in rules.exactRules) {
                if (suffixLen > matchedSuffixLabelCount) matchedSuffixLabelCount = suffixLen
            }
            if (startIdx + 1 < labels.size) {
                val parentBase = labels.subList(startIdx + 1, labels.size).joinToString(".")
                if (parentBase in rules.wildcardBases) {
                    if (suffixLen > matchedSuffixLabelCount) matchedSuffixLabelCount = suffixLen
                }
            }
        }

        if (matchedSuffixLabelCount == 0) {
            return Decision.UnlistedTld(labels.last())
        }
        val pubSuffix = labels.takeLast(matchedSuffixLabelCount).joinToString(".")
        if (labels.size <= matchedSuffixLabelCount) {
            return Decision.PublicSuffixOnly(pubSuffix)
        }
        val regDomain = labels.takeLast(matchedSuffixLabelCount + 1).joinToString(".")
        return Decision.Registrable(regDomain, pubSuffix)
    }

    /** True iff [rpId] is at or above [originHost] AND is a registrable domain (never a bare public suffix or unlisted TLD). */
    fun isValidRpIdForHost(originHost: String, rpId: String, rules: RuleSet = PINNED_ICANN_CORE): Boolean {
        val rpDecision = classify(rpId, rules)
        if (rpDecision !is Decision.Registrable) return false
        val hostDecision = classify(originHost, rules)
        if (hostDecision !is Decision.Registrable) return false
        val cleanHost = originHost.trim().lowercase()
        val cleanRp = rpId.trim().lowercase()
        return cleanHost == cleanRp || cleanHost.endsWith(".$cleanRp")
    }

    private fun validateLabels(labels: List<String>) {
        require(labels.isNotEmpty()) { "empty domain" }
        // Refuse bare numeric IPv4 literals (`127.0.0.1`) in PSL classification
        require(!labels.all { l -> l.isNotEmpty() && l.all { it.isDigit() } }) {
            "IPv4 literal cannot be classified as a domain name"
        }
        for (l in labels) {
            require(l.length in 1..63) { "DNS label '$l' outside 1..63 chars" }
            require(!l.startsWith('-') && !l.endsWith('-')) { "DNS label '$l' starts/ends with hyphen" }
            require(l.all { it in 'a'..'z' || it in '0'..'9' || it == '-' }) {
                "DNS label '$l' contains non-LDH ASCII character"
            }
        }
    }
}

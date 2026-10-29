// SPDX-License-Identifier: Apache-2.0
package dev.fusion.browser

import java.net.URI

/**
 * Clean-room permissive EasyList-subset content & tracker filter engine with `@@` exception rules,
 * `$third-party` / resource-type modifiers, and per-site user disable switches (`PR-07p`).
 */
object EasyListTrackerFilterEngine {
    const val SCOPE =
        "Pure-JVM clean-room content/tracker filter engine supporting ||domain^, @@||exception^, \$third-party/script/image/xmlhttprequest modifiers, and per-site user allowlists (PR-07p)."

    enum class ResourceKind { DOCUMENT, SCRIPT, IMAGE, XHR, SUBDOCUMENT, STYLESHEET, OTHER }

    data class FilterDecision(
        val blocked: Boolean,
        val matchedRule: String?,
        val reason: String,
    )

    internal data class CompiledRule(
        val raw: String,
        val isException: Boolean,
        val domainSuffix: String?,
        val substringToken: String?,
        val thirdPartyOnly: Boolean?, // true = $third-party, false = $~third-party, null = any
        val allowedKinds: Set<ResourceKind>?,
    )

    class RuleSet internal constructor(
        private val blockRules: List<CompiledRule>,
        private val exceptionRules: List<CompiledRule>,
        private val disabledSiteHosts: Set<String>,
    ) {
        fun evaluate(requestUrl: String, topFrameUrl: String, kind: ResourceKind): FilterDecision {
            val reqHost = extractHost(requestUrl) ?: return FilterDecision(false, null, "invalid_or_local_scheme")
            val topHost = extractHost(topFrameUrl).orEmpty()
            if (disabledSiteHosts.any { hostMatchesSuffix(topHost, it) }) {
                return FilterDecision(false, null, "site_shield_disabled_by_user")
            }
            val isThirdParty = topHost.isNotEmpty() && registrableBase(reqHost) != registrableBase(topHost)
            for (ex in exceptionRules) {
                if (matches(ex, requestUrl, reqHost, isThirdParty, kind)) {
                    return FilterDecision(false, ex.raw, "exception_allow_rule")
                }
            }
            for (bl in blockRules) {
                if (matches(bl, requestUrl, reqHost, isThirdParty, kind)) {
                    return FilterDecision(true, bl.raw, "matched_block_rule")
                }
            }
            return FilterDecision(false, null, "no_rule_match")
        }
    }

    fun compile(rawLines: List<String>, disabledSites: Set<String> = emptySet()): RuleSet {
        val blocks = ArrayList<CompiledRule>()
        val exceptions = ArrayList<CompiledRule>()
        for (raw in rawLines) {
            val trimmed = raw.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("!") || trimmed.startsWith("[Adblock")) continue
            val isEx = trimmed.startsWith("@@")
            val body = if (isEx) trimmed.removePrefix("@@") else trimmed
            val dollar = body.indexOf('$')
            val pat = if (dollar >= 0) body.substring(0, dollar) else body
            val opts = if (dollar >= 0) body.substring(dollar + 1).split(',').map { it.trim().lowercase() } else emptyList()

            var tp: Boolean? = null
            val kinds = HashSet<ResourceKind>()
            for (o in opts) {
                when (o) {
                    "third-party", "3p" -> tp = true
                    "~third-party", "1p" -> tp = false
                    "script" -> kinds.add(ResourceKind.SCRIPT)
                    "image" -> kinds.add(ResourceKind.IMAGE)
                    "xmlhttprequest", "xhr" -> kinds.add(ResourceKind.XHR)
                    "subdocument" -> kinds.add(ResourceKind.SUBDOCUMENT)
                    "stylesheet" -> kinds.add(ResourceKind.STYLESHEET)
                }
            }
            val (dom, sub) = if (pat.startsWith("||")) {
                val core = pat.removePrefix("||")
                val sep = core.indexOfFirst { it == '^' || it == '/' }
                if (sep < 0) core.lowercase() to null
                else {
                    val d = core.substring(0, sep).lowercase()
                    val rest = core.substring(sep).removePrefix("^")
                    d to rest.takeIf { it.isNotEmpty() }
                }
            } else null to pat
            val rule = CompiledRule(trimmed, isEx, dom, sub, tp, kinds.takeIf { it.isNotEmpty() })
            if (isEx) exceptions.add(rule) else blocks.add(rule)
        }
        return RuleSet(blocks, exceptions, disabledSites.map { it.trim().lowercase() }.toSet())
    }

    private fun matches(
        r: CompiledRule,
        url: String,
        reqHost: String,
        isThirdParty: Boolean,
        kind: ResourceKind,
    ): Boolean {
        if (r.thirdPartyOnly != null && r.thirdPartyOnly != isThirdParty) return false
        if (r.allowedKinds != null && kind !in r.allowedKinds) return false
        if (r.domainSuffix != null && !hostMatchesSuffix(reqHost, r.domainSuffix)) return false
        if (r.substringToken != null && !url.contains(r.substringToken, ignoreCase = true)) return false
        return true
    }

    private fun hostMatchesSuffix(host: String, suffix: String): Boolean =
        host == suffix || host.endsWith(".$suffix")

    private fun registrableBase(host: String): String {
        val parts = host.split('.')
        return if (parts.size <= 2) host else parts.takeLast(2).joinToString(".")
    }

    private fun extractHost(url: String): String? =
        runCatching { URI(url).host?.lowercase()?.removePrefix("www.")?.let { URI(url).host?.lowercase() } }.getOrNull()
}

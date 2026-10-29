package dev.fusion.browser.model

import java.net.URI
import java.util.Locale

/**
 * Clean-room uBlock/MV3-style Declarative Net Request Domain-Trie Content Blocker &
 * Query-Parameter Tracker Scrubber (Apache-2.0).
 *
 * Supports:
 * - Domain-anchored rules: `||ads.tracker.example^$third-party,script,xmlhttprequest`
 * - Exception allowlist overrides: `@@||cdn.partner.example/sdk.js$domain=news.example`
 * - Query parameter stripping (`$removeparam=utm_source` + built-in `fbclid`, `gclid`, `mc_eid`, `msclkid`)!
 */
object DeclarativeNetRequestTrieEngine {
    const val SCOPE =
        "Reverse-label domain-trie content blocker (||domain^, @@exception, \$third-party, resource types) + \$removeparam URL query scrubber"

    enum class ResourceType { DOCUMENT, SCRIPT, IMAGE, XMLHTTPREQUEST, SUBDOCUMENT, STYLESHEET }

    enum class ActionKind { ALLOW_EXCEPTION, BLOCK, REDIRECT_STRIPPED_PARAMS, PASS }

    data class FilterDecision(
        val action: ActionKind,
        val sanitizedUrl: String,
        val matchedRule: String? = null,
    )

    internal data class CompiledRule(
        val raw: String,
        val isException: Boolean,
        val domainAnchor: String?,
        val pathSubstring: String,
        val thirdPartyOnly: Boolean,
        val allowedResourceTypes: Set<ResourceType>,
        val initiatorDomains: Set<String>,
        val removeParamName: String?,
    )

    internal class DomainTrieNode {
        val children = HashMap<String, DomainTrieNode>()
        val rules = ArrayList<CompiledRule>()
    }

    class Engine internal constructor(
        private val domainTrie: DomainTrieNode,
        private val genericRules: List<CompiledRule>,
        private val stripParams: Set<String>,
    ) {
        fun evaluate(
            requestUrl: String,
            initiatorDocumentUrl: String,
            resourceType: ResourceType,
        ): FilterDecision {
            val reqUri = URI(requestUrl)
            val initUri = URI(initiatorDocumentUrl)
            val reqHost = reqUri.host?.lowercase(Locale.ROOT).orEmpty()
            val initHost = initUri.host?.lowercase(Locale.ROOT).orEmpty()
            val isThirdParty = registrableDomain(reqHost) != registrableDomain(initHost)

            val candidateRules = ArrayList<CompiledRule>()
            candidateRules.addAll(genericRules)
            var node: DomainTrieNode? = domainTrie
            for (label in reqHost.split('.').reversed()) {
                node = node?.children?.get(label) ?: break
                candidateRules.addAll(node.rules)
            }

            // 1. Exception rules (@@) always win over block rules per Adblock Plus / uBlock semantics
            for (r in candidateRules) {
                if (r.isException && r.removeParamName == null && matchesContext(r, requestUrl, initHost, isThirdParty, resourceType)) {
                    return FilterDecision(ActionKind.ALLOW_EXCEPTION, requestUrl, r.raw)
                }
            }
            // 2. Block rules
            for (r in candidateRules) {
                if (!r.isException && r.removeParamName == null && matchesContext(r, requestUrl, initHost, isThirdParty, resourceType)) {
                    return FilterDecision(ActionKind.BLOCK, "", r.raw)
                }
            }
            // 3. Query-parameter scrubbing ($removeparam + built-in tracking params)
            val dynamicStrip = buildSet {
                addAll(stripParams)
                for (r in candidateRules) {
                    if (!r.isException && r.removeParamName != null && matchesContext(r, requestUrl, initHost, isThirdParty, resourceType)) {
                        add(r.removeParamName.lowercase(Locale.ROOT))
                    }
                }
            }
            val cleaned = stripTrackingParams(reqUri, dynamicStrip)
            return if (cleaned != requestUrl) {
                FilterDecision(ActionKind.REDIRECT_STRIPPED_PARAMS, cleaned, "\$removeparam")
            } else {
                FilterDecision(ActionKind.PASS, requestUrl, null)
            }
        }
    }

    private val DEFAULT_TRACKING_PARAMS = setOf("utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content", "fbclid", "gclid", "msclkid", "mc_eid")

    fun compile(filterLines: List<String>, includeDefaultParamScrub: Boolean = true): Engine {
        val root = DomainTrieNode()
        val generic = ArrayList<CompiledRule>()
        for (rawLine in filterLines) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("!") || line.startsWith("[")) continue
            val rule = parseRule(line)
            if (rule.domainAnchor != null) {
                var cur = root
                for (lbl in rule.domainAnchor.split('.').reversed()) {
                    cur = cur.children.getOrPut(lbl) { DomainTrieNode() }
                }
                cur.rules.add(rule)
            } else {
                generic.add(rule)
            }
        }
        return Engine(root, generic, if (includeDefaultParamScrub) DEFAULT_TRACKING_PARAMS else emptySet())
    }

    private fun parseRule(raw: String): CompiledRule {
        var body = raw
        val isExc = body.startsWith("@@")
        if (isExc) body = body.removePrefix("@@")
        val dollar = body.indexOf('$')
        val pattern = if (dollar >= 0) body.substring(0, dollar) else body
        val opts = if (dollar >= 0) body.substring(dollar + 1).split(',') else emptyList()

        var thirdParty = false
        val types = LinkedHashSet<ResourceType>()
        val domains = LinkedHashSet<String>()
        var removeParam: String? = null
        for (o in opts) {
            val opt = o.trim()
            when {
                opt == "third-party" || opt == "3p" -> thirdParty = true
                opt.startsWith("domain=") -> opt.removePrefix("domain=").split('|').forEach { domains.add(it.lowercase(Locale.ROOT)) }
                opt.startsWith("removeparam=") -> removeParam = opt.removePrefix("removeparam=")
                opt == "script" -> types.add(ResourceType.SCRIPT)
                opt == "image" -> types.add(ResourceType.IMAGE)
                opt == "xmlhttprequest" || opt == "xhr" -> types.add(ResourceType.XMLHTTPREQUEST)
                opt == "subdocument" -> types.add(ResourceType.SUBDOCUMENT)
                opt == "document" -> types.add(ResourceType.DOCUMENT)
            }
        }

        var domainAnchor: String? = null
        var pathSub = pattern
        if (pattern.startsWith("||")) {
            val rest = pattern.removePrefix("||")
            val sepIdx = rest.indexOfFirst { it == '^' || it == '/' }
            if (sepIdx >= 0) {
                domainAnchor = rest.substring(0, sepIdx).lowercase(Locale.ROOT)
                pathSub = rest.substring(sepIdx).removePrefix("^")
            } else {
                domainAnchor = rest.lowercase(Locale.ROOT)
                pathSub = ""
            }
        }
        return CompiledRule(raw, isExc, domainAnchor, pathSub, thirdParty, types, domains, removeParam)
    }

    private fun matchesContext(
        r: CompiledRule,
        url: String,
        initHost: String,
        isThirdParty: Boolean,
        type: ResourceType,
    ): Boolean {
        if (r.thirdPartyOnly && !isThirdParty) return false
        if (r.allowedResourceTypes.isNotEmpty() && type !in r.allowedResourceTypes) return false
        if (r.initiatorDomains.isNotEmpty() && r.initiatorDomains.none { initHost == it || initHost.endsWith(".$it") }) return false
        if (r.pathSubstring.isNotEmpty() && !url.contains(r.pathSubstring, ignoreCase = true)) return false
        return true
    }

    private fun stripTrackingParams(uri: URI, blockedLower: Set<String>): String {
        val rawQ = uri.rawQuery ?: return uri.toString()
        val kept = rawQ.split('&').filter { pair ->
            val key = pair.substringBefore('=').lowercase(Locale.ROOT)
            key !in blockedLower
        }
        if (kept.size == rawQ.split('&').size) return uri.toString()
        val base = uri.toString().substringBefore('?').substringBefore('#')
        val frag = uri.rawFragment?.let { "#$it" }.orEmpty()
        return if (kept.isEmpty()) "$base$frag" else "$base?${kept.joinToString("&")}$frag"
    }

    private fun registrableDomain(host: String): String {
        val parts = host.split('.').filter { it.isNotEmpty() }
        return if (parts.size >= 2) parts.takeLast(2).joinToString(".") else host
    }
}

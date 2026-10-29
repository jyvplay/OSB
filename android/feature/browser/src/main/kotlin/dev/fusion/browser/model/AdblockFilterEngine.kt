// SPDX-License-Identifier: Apache-2.0
package dev.fusion.browser.model

import java.util.Locale

/**
 * Clean-room EasyList / uBlock network & cosmetic filter engine (`AdblockFilterEngine`)
 * informed by Brave `adblock-rust` 0.13.2 (`MPL-2.0` design reference, clean-room Kotlin).
 *
 * Supports:
 * - Domain-anchored rules: `||tracker.example.com^` (matches exact domain and any subdomain,
 *   never suffix-spoofed `nottracker.example.com`!).
 * - Exact prefix anchors: `|https://cdn.bad.example/pixel.gif`.
 * - Substring & wildcard `*` + separator `^` matching.
 * - Exception rules: `@@||allow.example.com^$script` (exceptions always override blocking rules).
 * - Resource-type & party modifiers: `$third-party`, `$~third-party`/`$first-party`,
 *   `$script`, `$image`, `$stylesheet`, `$xmlhttprequest`, `$subdocument`, `$domain=a.com|~b.com`.
 * - Cosmetic element-hiding rules: `news.example.com##.sponsored-card` and `#@#` unhide exceptions.
 */
object AdblockFilterEngine {
    const val SCOPE =
        "EasyList/uBlock network + cosmetic filter engine (||domain^ subdomain boundary, @@ exceptions, third-party/resource-type/domain= modifiers, ##/#@# cosmetic selectors); scriptlet +js() injection out of scope."

    enum class ResourceType { DOCUMENT, SCRIPT, IMAGE, STYLESHEET, XMLHTTPREQUEST, SUBDOCUMENT, OTHER }

    data class RequestContext(
        val url: String,
        val sourceDomain: String,
        val resourceType: ResourceType = ResourceType.OTHER,
    )

    data class Decision(
        val blocked: Boolean,
        val matchedRule: String? = null,
        val isException: Boolean = false,
    )

    internal data class NetworkRule(
        val raw: String,
        val isException: Boolean,
        val domainAnchor: String?,
        val exactPrefix: String?,
        val patternRegex: Regex?,
        val thirdPartyOnly: Boolean?,
        val allowedTypes: Set<ResourceType>?,
        val excludedTypes: Set<ResourceType>,
        val includeDomains: Set<String>,
        val excludeDomains: Set<String>,
    )

    internal data class CosmeticRule(
        val isException: Boolean,
        val domains: Set<String>,
        val excludeDomains: Set<String>,
        val selector: String,
    )

    class Engine internal constructor(
        private val blockByDomain: Map<String, List<NetworkRule>>,
        private val genericBlocks: List<NetworkRule>,
        private val allowRules: List<NetworkRule>,
        private val cosmeticRules: List<CosmeticRule>,
    ) {
        fun matchNetwork(req: RequestContext): Decision {
            val reqHost = extractHost(req.url)
            val srcHost = req.sourceDomain.trim().lowercase(Locale.ROOT).trimEnd('.')
            val isThirdParty = reqHost.isNotEmpty() && srcHost.isNotEmpty() && !sameRegistrableFamily(reqHost, srcHost)

            for (allow in allowRules) {
                if (matchesRule(allow, req.url, reqHost, srcHost, isThirdParty, req.resourceType)) {
                    return Decision(blocked = false, matchedRule = allow.raw, isException = true)
                }
            }

            // Fast O(labels) lookup on domain-anchored bucket index before generic fallback.
            if (reqHost.isNotEmpty()) {
                var cursor = reqHost
                while (true) {
                    val bucket = blockByDomain[cursor]
                    if (bucket != null) {
                        for (r in bucket) {
                            if (matchesRule(r, req.url, reqHost, srcHost, isThirdParty, req.resourceType)) {
                                return Decision(blocked = true, matchedRule = r.raw, isException = false)
                            }
                        }
                    }
                    val dot = cursor.indexOf('.')
                    if (dot < 0) break
                    cursor = cursor.substring(dot + 1)
                }
            }

            for (r in genericBlocks) {
                if (matchesRule(r, req.url, reqHost, srcHost, isThirdParty, req.resourceType)) {
                    return Decision(blocked = true, matchedRule = r.raw, isException = false)
                }
            }
            return Decision(blocked = false)
        }

        fun cosmeticSelectorsForDomain(pageDomain: String): Set<String> {
            val host = pageDomain.trim().lowercase(Locale.ROOT).trimEnd('.')
            val active = linkedSetOf<String>()
            val unhidden = linkedSetOf<String>()
            for (c in cosmeticRules) {
                if (c.excludeDomains.any { hostMatchesDomain(host, it) }) continue
                if (c.domains.isNotEmpty() && c.domains.none { hostMatchesDomain(host, it) }) continue
                if (c.isException) unhidden.add(c.selector) else active.add(c.selector)
            }
            active.removeAll(unhidden)
            return active
        }
    }

    fun compile(filterLines: List<String>): Engine {
        val blockByDomain = linkedMapOf<String, MutableList<NetworkRule>>()
        val genericBlocks = mutableListOf<NetworkRule>()
        val allowRules = mutableListOf<NetworkRule>()
        val cosmeticRules = mutableListOf<CosmeticRule>()

        for (rawLine in filterLines) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("!") || line.startsWith("[Adblock")) continue

            val unhideIdx = line.indexOf("#@#")
            val hideIdx = if (unhideIdx < 0) line.indexOf("##") else -1
            if (unhideIdx >= 0 || hideIdx >= 0) {
                val isExc = unhideIdx >= 0
                val splitAt = if (isExc) unhideIdx else hideIdx
                val sepLen = if (isExc) 3 else 2
                val domSpec = line.substring(0, splitAt).trim()
                val selector = line.substring(splitAt + sepLen).trim()
                if (selector.isNotEmpty() && !selector.startsWith("+js(")) {
                    val inc = linkedSetOf<String>()
                    val exc = linkedSetOf<String>()
                    if (domSpec.isNotEmpty()) {
                        for (d in domSpec.split(',')) {
                            val dt = d.trim().lowercase(Locale.ROOT)
                            if (dt.startsWith("~")) exc.add(dt.substring(1))
                            else if (dt.isNotEmpty()) inc.add(dt)
                        }
                    }
                    cosmeticRules.add(CosmeticRule(isExc, inc, exc, selector))
                }
                continue
            }

            parseNetworkRule(line)?.let { nr ->
                if (nr.isException) {
                    allowRules.add(nr)
                } else if (nr.domainAnchor != null) {
                    blockByDomain.getOrPut(nr.domainAnchor) { mutableListOf() }.add(nr)
                } else {
                    genericBlocks.add(nr)
                }
            }
        }
        return Engine(blockByDomain, genericBlocks, allowRules, cosmeticRules)
    }

    private fun parseNetworkRule(raw: String): NetworkRule? {
        var body = raw
        val isExc = body.startsWith("@@")
        if (isExc) body = body.substring(2)

        var thirdParty: Boolean? = null
        var allowedTypes: MutableSet<ResourceType>? = null
        val excludedTypes = linkedSetOf<ResourceType>()
        val incDomains = linkedSetOf<String>()
        val excDomains = linkedSetOf<String>()

        val dollarIdx = body.lastIndexOf('$')
        if (dollarIdx >= 0 && !body.substring(dollarIdx + 1).contains('/')) {
            val opts = body.substring(dollarIdx + 1).split(',')
            body = body.substring(0, dollarIdx)
            for (optRaw in opts) {
                val opt = optRaw.trim().lowercase(Locale.ROOT)
                when {
                    opt == "third-party" || opt == "3p" -> thirdParty = true
                    opt == "~third-party" || opt == "first-party" || opt == "1p" -> thirdParty = false
                    opt.startsWith("domain=") -> {
                        for (d in opt.substring(7).split('|')) {
                            val dt = d.trim()
                            if (dt.startsWith("~")) excDomains.add(dt.substring(1))
                            else if (dt.isNotEmpty()) incDomains.add(dt)
                        }
                    }
                    opt.startsWith("~") -> mapType(opt.substring(1))?.let { excludedTypes.add(it) }
                    else -> mapType(opt)?.let {
                        if (allowedTypes == null) allowedTypes = linkedSetOf()
                        allowedTypes!!.add(it)
                    }
                }
            }
        }

        var domainAnchor: String? = null
        var exactPrefix: String? = null
        var regex: Regex? = null
        val lowerBody = body.lowercase(Locale.ROOT)

        if (lowerBody.startsWith("||")) {
            val rest = lowerBody.substring(2)
            val endHost = rest.indexOfFirst { it == '^' || it == '/' || it == '*' }
            val hostPart = if (endHost < 0) rest else rest.substring(0, endHost)
            if (hostPart.isNotEmpty() && !hostPart.contains('*')) {
                domainAnchor = hostPart
                val tail = if (endHost < 0) "" else rest.substring(endHost)
                if (tail.isNotEmpty() && tail != "^") {
                    regex = patternToRegex("||$rest")
                }
            } else {
                regex = patternToRegex(lowerBody)
            }
        } else if (lowerBody.startsWith("|") && !lowerBody.contains('*') && !lowerBody.contains('^')) {
            exactPrefix = lowerBody.substring(1)
        } else if (lowerBody.isNotEmpty()) {
            regex = patternToRegex(lowerBody)
        }

        return NetworkRule(
            raw = raw,
            isException = isExc,
            domainAnchor = domainAnchor,
            exactPrefix = exactPrefix,
            patternRegex = regex,
            thirdPartyOnly = thirdParty,
            allowedTypes = allowedTypes,
            excludedTypes = excludedTypes,
            includeDomains = incDomains,
            excludeDomains = excDomains,
        )
    }

    private fun matchesRule(
        r: NetworkRule,
        rawUrl: String,
        reqHost: String,
        srcHost: String,
        isThirdParty: Boolean,
        type: ResourceType,
    ): Boolean {
        if (r.thirdPartyOnly != null && r.thirdPartyOnly != isThirdParty) return false
        if (type in r.excludedTypes) return false
        if (r.allowedTypes != null && type !in r.allowedTypes) return false
        if (r.excludeDomains.any { hostMatchesDomain(srcHost, it) }) return false
        if (r.includeDomains.isNotEmpty() && r.includeDomains.none { hostMatchesDomain(srcHost, it) }) return false

        val lowerUrl = rawUrl.lowercase(Locale.ROOT)
        if (r.domainAnchor != null && !hostMatchesDomain(reqHost, r.domainAnchor)) return false
        if (r.exactPrefix != null && !lowerUrl.startsWith(r.exactPrefix)) return false
        if (r.patternRegex != null && !r.patternRegex.containsMatchIn(lowerUrl)) return false
        return true
    }

    private fun patternToRegex(pat: String): Regex {
        val sb = StringBuilder()
        var i = 0
        if (pat.startsWith("||")) {
            sb.append("^[a-z][a-z0-9+.-]*://(?:[^/?#]*\\.)?")
            i = 2
        } else if (pat.startsWith("|")) {
            sb.append('^')
            i = 1
        }
        val endAnchor = pat.endsWith("|") && !pat.endsWith("||")
        val limit = if (endAnchor) pat.length - 1 else pat.length
        while (i < limit) {
            when (val ch = pat[i]) {
                '*' -> sb.append(".*")
                '^' -> sb.append("(?:[^a-z0-9_.-]|$)")
                '.', '+', '?', '$', '{', '}', '(', ')', '[', ']', '\\', '|' -> sb.append('\\').append(ch)
                else -> sb.append(ch)
            }
            i++
        }
        if (endAnchor) sb.append('$')
        return Regex(sb.toString())
    }

    private fun mapType(name: String): ResourceType? = when (name) {
        "script" -> ResourceType.SCRIPT
        "image" -> ResourceType.IMAGE
        "stylesheet", "css" -> ResourceType.STYLESHEET
        "xmlhttprequest", "xhr" -> ResourceType.XMLHTTPREQUEST
        "subdocument", "frame" -> ResourceType.SUBDOCUMENT
        "document", "doc" -> ResourceType.DOCUMENT
        else -> null
    }

    private fun hostMatchesDomain(host: String, domain: String): Boolean =
        host == domain || host.endsWith(".$domain")

    private fun sameRegistrableFamily(h1: String, h2: String): Boolean {
        if (h1 == h2) return true
        val l1 = h1.split('.')
        val l2 = h2.split('.')
        if (l1.size < 2 || l2.size < 2) return h1 == h2
        return l1.takeLast(2) == l2.takeLast(2)
    }

    private fun extractHost(url: String): String {
        val lower = url.trim().lowercase(Locale.ROOT)
        val schemeSep = lower.indexOf("://")
        val start = if (schemeSep >= 0) schemeSep + 3 else 0
        val end = lower.indexOfAny(charArrayOf('/', '?', '#', ':'), startIndex = start)
        return (if (end < 0) lower.substring(start) else lower.substring(start, end)).trimEnd('.')
    }
}

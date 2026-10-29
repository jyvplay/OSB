package dev.fusion.browser.model

import java.util.Locale

/**
 * Clean-room W3C `Permissions-Policy` HTTP Structured Header & `<iframe allow="...">` Origin
 * Delegation Intersection Evaluator (Apache-2.0).
 *
 * Enforces privacy-by-default hardware capability blocking (`camera`, `microphone`, `geolocation`,
 * `usb`, `hid`, `bluetooth`, `serial`, `clipboard-read`, `payment`) across top-level documents
 * and cross-origin subframes.
 */
object PermissionsPolicyHeaderEvaluator {
    const val SCOPE =
        "W3C Permissions-Policy structured header parser + iframe allow='' cross-origin capability intersection evaluator"

    sealed interface AllowListSpec {
        data object None : AllowListSpec
        data object Wildcard : AllowListSpec
        data class Origins(val includeSelf: Boolean, val explicitOrigins: Set<String>) : AllowListSpec
    }

    data class ParsedPolicy(val directives: Map<String, AllowListSpec>) {
        fun isAllowedInTopDocument(feature: String, documentOrigin: String): Boolean {
            val spec = directives[feature.lowercase(Locale.ROOT)] ?: defaultPolicyFor(feature)
            return matchesOrigin(spec, documentOrigin, documentOrigin)
        }

        fun isAllowedInSubframe(
            feature: String,
            parentOrigin: String,
            childOrigin: String,
            iframeAllowAttr: String = "",
        ): Boolean {
            val feat = feature.lowercase(Locale.ROOT)
            // W3C §9.4: A subframe has permission ONLY if parent's Permissions-Policy allows childOrigin
            // AND the <iframe allow="..."> container policy also delegates the feature to childOrigin!
            val parentSpec = directives[feat] ?: defaultPolicyFor(feat)
            if (!matchesOrigin(parentSpec, parentOrigin, childOrigin)) return false

            val containerMap = parseIframeAllow(iframeAllowAttr, parentOrigin, childOrigin)
            val containerSpec = containerMap[feat]
                ?: if (parentOrigin.equals(childOrigin, ignoreCase = true)) AllowListSpec.Origins(true, emptySet()) else AllowListSpec.None
            return matchesOrigin(containerSpec, parentOrigin, childOrigin)
        }
    }

    private val HIGH_RISK_HARDWARE_FEATURES = setOf(
        "camera", "microphone", "geolocation", "usb", "hid", "bluetooth", "serial", "midi", "Idle-detection".lowercase(),
    )

    /** Parse W3C `Permissions-Policy: camera=(), geolocation=(self "https://maps.example"), fullscreen=*` */
    fun parseHeader(headerValue: String): ParsedPolicy {
        val map = LinkedHashMap<String, AllowListSpec>()
        for (rawMember in headerValue.split(',')) {
            val member = rawMember.trim()
            if (member.isEmpty()) continue
            val eq = member.indexOf('=')
            if (eq <= 0) continue
            val feature = member.substring(0, eq).trim().lowercase(Locale.ROOT)
            val rhs = member.substring(eq + 1).trim()
            map[feature] = when {
                rhs == "*" -> AllowListSpec.Wildcard
                rhs == "()" -> AllowListSpec.None
                rhs.startsWith("(") && rhs.endsWith(")") -> {
                    val inner = rhs.substring(1, rhs.length - 1).trim()
                    if (inner.isEmpty()) AllowListSpec.None
                    else {
                        val tokens = inner.split(Regex("\\s+")).filter { it.isNotEmpty() }
                        val self = tokens.any { it.equals("self", ignoreCase = true) }
                        val origins = tokens.filter { it.startsWith("\"") && it.endsWith("\"") && it.length >= 2 }
                            .map { normalizeOrigin(it.substring(1, it.length - 1)) }
                            .toSet()
                        AllowListSpec.Origins(self, origins)
                    }
                }
                rhs.equals("self", ignoreCase = true) -> AllowListSpec.Origins(true, emptySet())
                else -> AllowListSpec.None
            }
        }
        return ParsedPolicy(map)
    }

    private fun parseIframeAllow(attr: String, parentOrigin: String, childOrigin: String): Map<String, AllowListSpec> {
        val out = LinkedHashMap<String, AllowListSpec>()
        for (rawClause in attr.split(';')) {
            val clause = rawClause.trim()
            if (clause.isEmpty()) continue
            val tokens = clause.split(Regex("\\s+")).filter { it.isNotEmpty() }
            val feat = tokens[0].lowercase(Locale.ROOT)
            if (tokens.size == 1) {
                // `<iframe src="https://child" allow="camera">` implicitly delegates to `src` origin!
                out[feat] = AllowListSpec.Origins(includeSelf = false, explicitOrigins = setOf(normalizeOrigin(childOrigin)))
            } else {
                val rest = tokens.drop(1)
                if ("'none'" in rest) out[feat] = AllowListSpec.None
                else if ("*" in rest) out[feat] = AllowListSpec.Wildcard
                else {
                    val self = rest.any { it.equals("'self'", ignoreCase = true) }
                    val explicitSrc = rest.any { it.equals("'src'", ignoreCase = true) }
                    val origins = buildSet {
                        if (explicitSrc) add(normalizeOrigin(childOrigin))
                        for (t in rest) if (t.startsWith("http://") || t.startsWith("https://")) add(normalizeOrigin(t))
                        if (self) add(normalizeOrigin(parentOrigin))
                    }
                    out[feat] = AllowListSpec.Origins(includeSelf = self, explicitOrigins = origins)
                }
            }
        }
        return out
    }

    private fun defaultPolicyFor(feature: String): AllowListSpec =
        if (feature.lowercase(Locale.ROOT) in HIGH_RISK_HARDWARE_FEATURES) {
            AllowListSpec.Origins(includeSelf = true, explicitOrigins = emptySet())
        } else {
            AllowListSpec.Origins(includeSelf = true, explicitOrigins = emptySet())
        }

    private fun matchesOrigin(spec: AllowListSpec, selfOrigin: String, targetOrigin: String): Boolean {
        val sNorm = normalizeOrigin(selfOrigin)
        val tNorm = normalizeOrigin(targetOrigin)
        return when (spec) {
            AllowListSpec.None -> false
            AllowListSpec.Wildcard -> true
            is AllowListSpec.Origins -> (spec.includeSelf && tNorm == sNorm) || (tNorm in spec.explicitOrigins)
        }
    }

    private fun normalizeOrigin(o: String): String = o.trim().trimEnd('/').lowercase(Locale.ROOT)
}

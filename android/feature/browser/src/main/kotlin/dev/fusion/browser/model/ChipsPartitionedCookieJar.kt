package dev.fusion.browser.model

import java.net.URI
import java.util.Locale

/**
 * Clean-room IETF CHIPS (`Cookies Having Independent Partitioned State`) & RFC 6265bis Cookie Jar
 * (Apache-2.0).
 *
 * Enforces:
 * - Double-keyed `(topLevelSite, hostKey)` isolation when `Partitioned` is present so embedded
 *   `cdn.tracker.example` on `news.example` NEVER shares cookies with `cdn.tracker.example` on `shop.example`!
 * - `Partitioned` MUST have `Secure` and `Path=/` or valid `Secure` context,
 * - `__Host-` prefix MUST have `Secure`, `Path=/`, and NO `Domain` attribute,
 * - `__Secure-` prefix MUST originate from `https://` with `Secure`,
 * - Default unpartitioned third-party cookie blocking (`blockUnpartitionedThirdParty = true`).
 */
object ChipsPartitionedCookieJar {
    const val SCOPE =
        "IETF CHIPS Partitioned double-keyed cookie jar + RFC 6265bis __Host-/__Secure-/SameSite invariants + unpartitioned 3P blocking"

    enum class SameSiteMode { STRICT, LAX, NONE }

    enum class StoreOutcome { STORED, REJECTED_INSECURE_PREFIX, REJECTED_INVALID_PARTITIONED, REJECTED_SAMESITE_NONE_INSECURE, REJECTED_UNPARTITIONED_THIRD_PARTY, REJECTED_DOMAIN_MISMATCH }

    data class StoredCookie(
        val name: String,
        val value: String,
        val domain: String,
        val hostOnly: Boolean,
        val path: String,
        val secure: Boolean,
        val httpOnly: Boolean,
        val sameSite: SameSiteMode,
        val partitionedTopSite: String?,
    )

    class Jar(val blockUnpartitionedThirdParty: Boolean = true) {
        private val records = LinkedHashMap<String, StoredCookie>()

        fun setCookie(
            requestUrl: String,
            topLevelDocumentUrl: String,
            setCookieHeader: String,
        ): StoreOutcome {
            val req = URI(requestUrl)
            val top = URI(topLevelDocumentUrl)
            val reqHost = req.host?.lowercase(Locale.ROOT) ?: return StoreOutcome.REJECTED_DOMAIN_MISMATCH
            val isHttps = req.scheme.equals("https", ignoreCase = true)
            val reqSite = registrableSite(req)
            val topSite = registrableSite(top)
            val isThirdParty = reqSite != topSite

            val parts = setCookieHeader.split(';').map { it.trim() }.filter { it.isNotEmpty() }
            if (parts.isEmpty() || '=' !in parts[0]) return StoreOutcome.REJECTED_DOMAIN_MISMATCH
            val eq = parts[0].indexOf('=')
            val name = parts[0].substring(0, eq).trim()
            val value = parts[0].substring(eq + 1).trim()

            var explicitDomain: String? = null
            var path = "/"
            var secure = false
            var httpOnly = false
            var partitioned = false
            var sameSite = SameSiteMode.LAX

            for (attr in parts.drop(1)) {
                val kv = attr.split('=', limit = 2)
                val k = kv[0].trim().lowercase(Locale.ROOT)
                val v = kv.getOrNull(1)?.trim().orEmpty()
                when (k) {
                    "domain" -> explicitDomain = v.trimStart('.').lowercase(Locale.ROOT)
                    "path" -> if (v.startsWith("/")) path = v
                    "secure" -> secure = true
                    "httponly" -> httpOnly = true
                    "partitioned" -> partitioned = true
                    "samesite" -> sameSite = when (v.lowercase(Locale.ROOT)) {
                        "strict" -> SameSiteMode.STRICT
                        "none" -> SameSiteMode.NONE
                        else -> SameSiteMode.LAX
                    }
                }
            }

            // RFC 6265bis __Host- & __Secure- prefix checks
            if (name.startsWith("__Host-")) {
                if (!isHttps || !secure || explicitDomain != null || path != "/") {
                    return StoreOutcome.REJECTED_INSECURE_PREFIX
                }
            }
            if (name.startsWith("__Secure-")) {
                if (!isHttps || !secure) return StoreOutcome.REJECTED_INSECURE_PREFIX
            }
            if (sameSite == SameSiteMode.NONE && (!secure || !isHttps)) {
                return StoreOutcome.REJECTED_SAMESITE_NONE_INSECURE
            }
            if (partitioned && (!secure || !isHttps)) {
                return StoreOutcome.REJECTED_INVALID_PARTITIONED
            }
            if (explicitDomain != null && reqHost != explicitDomain && !reqHost.endsWith(".$explicitDomain")) {
                return StoreOutcome.REJECTED_DOMAIN_MISMATCH
            }
            if (isThirdParty && !partitioned && blockUnpartitionedThirdParty) {
                return StoreOutcome.REJECTED_UNPARTITIONED_THIRD_PARTY
            }

            val effDomain = explicitDomain ?: reqHost
            val partKey = if (partitioned) topSite else "*"
            val storageKey = "$partKey|$effDomain|$path|$name"
            records[storageKey] = StoredCookie(
                name = name,
                value = value,
                domain = effDomain,
                hostOnly = explicitDomain == null,
                path = path,
                secure = secure,
                httpOnly = httpOnly,
                sameSite = sameSite,
                partitionedTopSite = if (partitioned) topSite else null,
            )
            return StoreOutcome.STORED
        }

        fun cookieHeaderForRequest(
            requestUrl: String,
            topLevelDocumentUrl: String,
            isTopLevelNavigation: Boolean = false,
            forJavaScriptDocumentCookie: Boolean = false,
        ): String {
            val req = URI(requestUrl)
            val top = URI(topLevelDocumentUrl)
            val reqHost = req.host?.lowercase(Locale.ROOT).orEmpty()
            val reqPath = req.path?.ifEmpty { "/" } ?: "/"
            val isHttps = req.scheme.equals("https", ignoreCase = true)
            val sameSiteContext = registrableSite(req) == registrableSite(top)
            val topSite = registrableSite(top)

            return records.values.filter { c ->
                if (c.secure && !isHttps) return@filter false
                if (forJavaScriptDocumentCookie && c.httpOnly) return@filter false
                val hostMatch = if (c.hostOnly) reqHost == c.domain else (reqHost == c.domain || reqHost.endsWith(".${c.domain}"))
                if (!hostMatch) return@filter false
                if (!reqPath.startsWith(c.path)) return@filter false
                if (c.partitionedTopSite != null && c.partitionedTopSite != topSite) return@filter false
                if (!sameSiteContext) {
                    if (c.partitionedTopSite == null && blockUnpartitionedThirdParty) return@filter false
                    when (c.sameSite) {
                        SameSiteMode.STRICT -> return@filter false
                        SameSiteMode.LAX -> if (!isTopLevelNavigation) return@filter false
                        SameSiteMode.NONE -> {}
                    }
                }
                true
            }.joinToString("; ") { "${it.name}=${it.value}" }
        }

        fun wipeAll(): Int {
            val n = records.size
            records.clear()
            return n
        }
    }

    fun registrableSite(uri: URI): String {
        val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: "https"
        val host = uri.host?.lowercase(Locale.ROOT) ?: ""
        val labels = host.split('.').filter { it.isNotEmpty() }
        val etldPlus1 = if (labels.size >= 2) labels.takeLast(2).joinToString(".") else host
        return "$scheme://$etldPlus1"
    }
}

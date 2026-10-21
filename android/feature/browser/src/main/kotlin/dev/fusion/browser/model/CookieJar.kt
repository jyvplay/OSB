// SPDX-License-Identifier: Apache-2.0
package dev.fusion.browser.model

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * R28 EXECUTED host kernel. RFC 6265-subset cookie jar: Set-Cookie parsing
 * (Domain/Path/Max-Age/Expires/Secure/HttpOnly/SameSite), domain+path+secure
 * matching, expiry eviction and per-host/total caps. No network, no storage;
 * the caller persists whatever it chooses.
 */
class CookieJar private constructor(private val cookies: List<Cookie>) {
    data class Cookie(
        val name: String, val value: String, val domain: String, val hostOnly: Boolean,
        val path: String, val secure: Boolean, val httpOnly: Boolean,
        val sameSite: String, val expiresAt: Long, val createdAt: Long,
    )

    companion object {
        const val MAX_COOKIES = 512
        const val MAX_PER_HOST = 64
        private val TOKEN = Regex("[!#\$%&'*+\\-.0-9A-Z^_`a-z|~]+")
        private val SAME_SITES = setOf("Lax", "Strict", "None")
        private val DATE_FORMATS = listOf(
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US),
            DateTimeFormatter.ofPattern("EEEE, dd-MMM-yy HH:mm:ss zzz", Locale.US),
            DateTimeFormatter.ofPattern("EEE MMM d HH:mm:ss yyyy", Locale.US),
        )

        fun empty(): CookieJar = CookieJar(emptyList())
        fun stored(jar: CookieJar): List<Cookie> = jar.cookies

        private fun parseDate(raw: String): Long? {
            for (f in DATE_FORMATS) {
                try {
                    return f.parse(raw.trim()) { temporal ->
                        var year = temporal.get(java.time.temporal.ChronoField.YEAR)
                        if (year < 100) year += if (year >= 70) 1900 else 2000
                        java.time.LocalDateTime.of(year, temporal.get(java.time.temporal.ChronoField.MONTH_OF_YEAR),
                            temporal.get(java.time.temporal.ChronoField.DAY_OF_MONTH),
                            temporal.get(java.time.temporal.ChronoField.HOUR_OF_DAY),
                            temporal.get(java.time.temporal.ChronoField.MINUTE_OF_HOUR),
                            temporal.get(java.time.temporal.ChronoField.SECOND_OF_MINUTE))
                            .toInstant(ZoneOffset.UTC).epochSecond
                    }
                } catch (_: Exception) { /* next format */ }
            }
            return null
        }

        private fun domainMatches(cookieDomain: String, host: String, hostOnly: Boolean): Boolean {
            if (hostOnly) return host == cookieDomain
            return host == cookieDomain || host.endsWith(".$cookieDomain")
        }

        private fun pathMatches(cookiePath: String, reqPath: String): Boolean {
            if (reqPath == cookiePath) return true
            if (reqPath.startsWith(cookiePath)) {
                if (cookiePath.endsWith("/")) return true
                if (reqPath[cookiePath.length] == '/') return true
            }
            return false
        }
    }

    /**
     * Store one Set-Cookie header received from [host] at [nowEpoch]. Unknown
     * attributes are ignored; a Max-Age <= 0 or past Expires DELETES the cookie.
     */
    fun store(header: String, host: String, defaultPath: String, nowEpoch: Long): CookieJar {
        require(header.length in 3..4096) { "Set-Cookie header must be 3..4096 chars" }
        require(host.length in 1..253 && host.all { it.isLetterOrDigit() || it == '.' || it == '-' }) {
            "Host must be 1..253 letters/digits/dots/dashes"
        }
        require(defaultPath.startsWith("/") && defaultPath.length in 1..1024) { "Default path must be absolute" }
        require(nowEpoch in 0..4102444800L) { "nowEpoch out of range" }
        val parts = header.split(';').map { it.trim() }.filter { it.isNotEmpty() }
        require(parts.isNotEmpty() && '=' in parts[0]) { "Set-Cookie needs a name=value pair" }
        val name = parts[0].substringBefore('=').trim()
        val value = parts[0].substringAfter('=').trim().removeSurrounding("\"")
        require(TOKEN.matches(name)) { "Cookie name must be an RFC 6265 token" }
        require(value.length <= 1024 && value.all { it.code in 0x21..0x7e && it != ';' && it != ',' }) {
            "Cookie value must be bounded printable ASCII without ; or ,"
        }
        var domain: String? = null
        var hostOnly = true
        var path: String? = null
        var secure = false
        var httpOnly = false
        var sameSite = "Lax"
        var maxAge: Long? = null
        var expires: Long? = null
        for (attr in parts.drop(1)) {
            val k = attr.substringBefore('=').trim().lowercase(Locale.ROOT)
            val v = if ('=' in attr) attr.substringAfter('=').trim() else ""
            when (k) {
                "domain" -> {
                    val d = v.trimStart('.').lowercase(Locale.ROOT)
                    require(d.isNotEmpty() && (host == d || host.endsWith(".$d"))) {
                        "Domain attribute must match the request host"
                    }
                    domain = d
                    hostOnly = false
                }
                "path" -> if (v.startsWith("/")) path = v.take(1024)
                "secure" -> secure = true
                "httponly" -> httpOnly = true
                "samesite" -> {
                    val canon = v.lowercase(Locale.ROOT).replaceFirstChar { it.uppercase(Locale.ROOT) }
                    if (canon in SAME_SITES) sameSite = canon
                }
                "max-age" -> maxAge = v.toLongOrNull()
                "expires" -> parseDate(v)?.let { expires = it }
            }
        }
        if (sameSite == "None" && !secure) sameSite = "Lax" // reject insecure None, keep the cookie usable
        val resolvedDomain = (domain ?: host.lowercase(Locale.ROOT))
        val key = Triple(name, resolvedDomain, path ?: defaultPath)
        var next = cookies.filterNot { Triple(it.name, it.domain, it.path) == key }
        val delete = (maxAge != null && maxAge <= 0) || (maxAge == null && expires != null && expires <= nowEpoch)
        if (!delete) {
            val exp = maxAge?.let { (nowEpoch + it).coerceAtMost(4102444800L) } ?: expires ?: 4102444800L
            require(exp > nowEpoch) { "Cookie already expired" }
            next = next + Cookie(name, value, resolvedDomain, hostOnly, path ?: defaultPath,
                secure, httpOnly, sameSite, exp, nowEpoch)
            val hostCount = next.count { domainMatches(it.domain, resolvedDomain, false) }
            if (hostCount > MAX_PER_HOST) {
                val victim = next.filter { domainMatches(it.domain, resolvedDomain, false) }.minBy { it.createdAt }
                next = next - victim
            }
            while (next.size > MAX_COOKIES) next = next - next.minBy { it.createdAt }
        }
        return CookieJar(next)
    }

    /** Cookie header value for a request, or "" when nothing matches. */
    fun headerFor(host: String, reqPath: String, isSecure: Boolean, nowEpoch: Long): String {
        require(nowEpoch in 0..4102444800L) { "nowEpoch out of range" }
        val path = if (reqPath.startsWith("/")) reqPath else "/$reqPath"
        return cookies
            .filter { it.expiresAt > nowEpoch }
            .filter { domainMatches(it.domain, host.lowercase(Locale.ROOT), it.hostOnly) }
            .filter { pathMatches(it.path, path) }
            .filter { !it.secure || isSecure }
            .sortedBy { it.createdAt }
            .joinToString("; ") { "${it.name}=${it.value}" }
    }

    /** Drop expired cookies. */
    fun prune(nowEpoch: Long): CookieJar {
        require(nowEpoch in 0..4102444800L) { "nowEpoch out of range" }
        return CookieJar(cookies.filter { it.expiresAt > nowEpoch })
    }
}

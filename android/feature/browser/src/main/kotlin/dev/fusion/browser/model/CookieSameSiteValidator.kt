// SPDX-License-Identifier: Apache-2.0
package dev.fusion.browser.model

/**
 * Bounded SameSite cookie attribute validator. Validates cookie SameSite
 * attributes (Strict, Lax, None) and verifies that SameSite=None cookies
 * also have the Secure attribute set, per RFC 6265bis. Rejects invalid
 * values, overly long names/values, and None-without-Secure combinations.
 * Does not interact with the actual cookie store — validation is pure.
 *
 * Declared narrowings: at most [MAX_COOKIES] cookies; cookie names at most
 * [MAX_NAME_LEN] chars; values at most [MAX_VALUE_LEN] chars; domains at
 * most [MAX_DOMAIN_LEN] chars; only Strict/Lax/None SameSite values
 * admitted; empty names are refused.
 */
class CookieSameSiteValidator private constructor(
    private val cookies: LinkedHashMap<String, CookieInfo>
) {
    companion object {
        const val MAX_COOKIES = 1024
        const val MAX_NAME_LEN = 256
        const val MAX_VALUE_LEN = 4096
        const val MAX_DOMAIN_LEN = 256
        private val NAME_RE = Regex("[^\x00-\x20\\\",;\\\\]+")
        private val SAMESITE = setOf("Strict", "Lax", "None")

        data class CookieInfo(
            val name: String,
            val value: String,
            val domain: String?,
            val sameSite: String?,
            val secure: Boolean
        )

        data class ValidationResult(
            val valid: Boolean,
            val reason: String?
        )

        fun empty(): CookieSameSiteValidator = CookieSameSiteValidator(LinkedHashMap())
    }

    fun validate(name: String, value: String, domain: String?,
                 sameSite: String?, secure: Boolean): ValidationResult {
        require(name.isNotEmpty() && name.length <= MAX_NAME_LEN) { "invalid cookie name" }
        require(NAME_RE.matches(name)) { "cookie name contains invalid chars" }
        require(value.length <= MAX_VALUE_LEN) { "cookie value too long" }
        require(domain == null || domain.length <= MAX_DOMAIN_LEN) { "domain too long" }
        require(sameSite == null || SAMESITE.contains(sameSite)) {
            "invalid SameSite: $sameSite"
        }

        // SameSite=None requires Secure
        if (sameSite == "None" && !secure) {
            return ValidationResult(false, "SameSite=None requires Secure")
        }

        return ValidationResult(true, null)
    }

    fun recordCookie(name: String, value: String, domain: String?,
                     sameSite: String?, secure: Boolean) {
        require(cookies.size < MAX_COOKIES || cookies.containsKey(name)) {
            "too many cookies: $MAX_COOKIES"
        }
        cookies[name] = CookieInfo(name, value, domain, sameSite, secure)
    }

    fun cookieCount(): Int = cookies.size
    fun removeCookie(name: String): Boolean = cookies.remove(name) != null
    fun clear() = cookies.clear()
}

package dev.fusion.security

/**
 * otpauth:// provisioning-URI parser (the Google Authenticator
 * Key-Uri-Format, the de-facto standard all TOTP apps consume).
 *
 * Shape: otpauth://TYPE/LABEL?PARAMS where TYPE is totp or hotp; LABEL
 * is percent-encoded `Issuer:account` or just `account`; secret is
 * REQUIRED and must be RFC 4648 base32 (A-Z, 2-7, optional trailing
 * '='); digits 6..8; totp period 15..120 (declared sanity window);
 * hotp requires a counter. If both the label prefix and the issuer
 * parameter exist they MUST agree.
 *
 * THIS KERNEL NEVER COMPUTES CODES — TotpCode (:feature:vault) owns
 * code generation; this is intake validation only, so a malformed or
 * hostile provisioning QR refuses before any secret is stored.
 *
 * Declared narrowings: algorithm values SHA1/SHA256/SHA512 only;
 * unknown query parameters refuse loudly (an intake surface must not
 * silently drop fields it does not understand).
 */
object OtpauthUri {
    const val SCOPE = "Provisioning-URI validation only; codes are computed elsewhere; unknown params refused."

    data class Otp(
        val hotp: Boolean, val issuer: String?, val account: String,
        val secretBase32: String, val algorithm: String, val digits: Int,
        val period: Int?, val counter: Long?
    )

    private fun pctDecode(s: String): String {
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                c == '%' -> {
                    require(i + 2 < s.length) { "truncated percent escape" }
                    val hex = s.substring(i + 1, i + 3)
                    require(hex.all { it.isDigit() || it.uppercaseChar() in 'A'..'F' }) { "bad escape %$hex" }
                    sb.append(hex.toInt(16).toChar()); i += 3
                }
                c == '+' -> { sb.append(' '); i++ }
                else -> { sb.append(c); i++ }
            }
        }
        return sb.toString()
    }

    fun parse(uri: String): Otp {
        require(uri.startsWith("otpauth://")) { "not an otpauth URI" }
        val rest = uri.removePrefix("otpauth://")
        val slash = rest.indexOf('/')
        require(slash > 0) { "missing type/label separator" }
        val type = rest.substring(0, slash).lowercase()
        require(type == "totp" || type == "hotp") { "type '$type' is neither totp nor hotp" }
        val q = rest.indexOf('?')
        require(q > slash) { "missing query (secret is required)" }
        // Split the RAW label before percent-decoding: an escaped %3A must
        // stay inside its half, never become a split point (first-execution
        // attack this round caught decode-then-split doing exactly that).
        val rawLabel = rest.substring(slash + 1, q)
        val labelIssuer = rawLabel.substringBefore(':', "").ifEmpty { null }?.let { pctDecode(it).trim() }
        val account = pctDecode(rawLabel.substringAfter(':')).trim()
        require(account.isNotEmpty()) { "empty account in label" }

        val params = HashMap<String, String>()
        for (pair in rest.substring(q + 1).split('&')) {
            val k = pair.substringBefore('=').lowercase()
            val v = pctDecode(pair.substringAfter('=', ""))
            require(k in setOf("secret", "issuer", "algorithm", "digits", "period", "counter")) {
                "unknown parameter '$k' refused (intake must not silently drop fields)"
            }
            require(params.put(k, v) == null) { "duplicate parameter '$k'" }
        }
        val secret = params["secret"] ?: throw IllegalArgumentException("secret parameter is required")
        val secretBody = secret.trimEnd('=')
        require(secretBody.isNotEmpty() && secretBody.all { it in 'A'..'Z' || it in '2'..'7' }) {
            "secret is not RFC 4648 base32 (A-Z, 2-7)"
        }
        val paramIssuer = params["issuer"]?.trim()
        if (labelIssuer != null && paramIssuer != null) {
            require(labelIssuer == paramIssuer) { "label issuer '$labelIssuer' disagrees with issuer parameter '$paramIssuer'" }
        }
        val algorithm = (params["algorithm"] ?: "SHA1").uppercase()
        require(algorithm in setOf("SHA1", "SHA256", "SHA512")) { "algorithm '$algorithm' outside declared set" }
        val digits = (params["digits"] ?: "6").toIntOrNull() ?: throw IllegalArgumentException("digits not numeric")
        require(digits in 6..8) { "digits $digits outside 6..8" }
        val counter = params["counter"]?.let { it.toLongOrNull() ?: throw IllegalArgumentException("counter not numeric") }
        val period = if (type == "totp") {
            val p = (params["period"] ?: "30").toIntOrNull() ?: throw IllegalArgumentException("period not numeric")
            require(p in 15..120) { "period $p outside declared 15..120 sanity window" }
            p
        } else null
        if (type == "hotp") {
            require(counter != null && counter >= 0) { "hotp requires a non-negative counter" }
            require(params["period"] == null) { "period is a totp parameter" }
        }
        return Otp(type == "hotp", paramIssuer ?: labelIssuer, account, secret, algorithm, digits, period, counter)
    }
}

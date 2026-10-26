package dev.fusion.security

/**
 * JWS compact-serialization structural splitter (RFC 7515 §7.1 / RFC 7519).
 *
 * THIS KERNEL NEVER VERIFIES A SIGNATURE. Its entire job is transport
 * syntax: exactly three dot-separated base64url (RFC 4648 §5, unpadded)
 * segments, a decodable JOSE header, and extraction of `alg`/`typ`. The
 * result carries `signatureChecked = false` as a constant — code
 * downstream can never mistake a split for a validation. `alg: none` is
 * surfaced as [algNone] so callers can refuse it (RFC 8725 §2.1 requires
 * rejecting unsecured JWTs unless explicitly anticipated).
 *
 * Declared narrowings: flat JOSE header fields with unescaped string
 * values only — any backslash escape in the header is refused loudly
 * rather than mis-parsed; nested JSON, JWE (5 segments) and detached
 * payloads are out of scope.
 */
object JwtSplit {
    const val SCOPE = "Structural split + header field extraction ONLY; signatures are never checked here."

    data class Jws(
        val headerJson: String, val alg: String, val typ: String?,
        val payload: ByteArray, val signature: ByteArray
    ) {
        val signatureChecked: Boolean get() = false
        val algNone: Boolean get() = alg.equals("none", ignoreCase = true)
    }

    private const val B64URL = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

    fun b64urlDecode(s: String): ByteArray {
        require(s.isNotEmpty()) { "empty segment" }
        require(s.none { it == '=' }) { "padding '=' is not allowed in JWS segments" }
        s.forEach { require(it in B64URL) { "'$it' is not a base64url character" } }
        require(s.length % 4 != 1) { "impossible base64url length ${s.length}" }
        val out = ArrayList<Byte>(s.length * 3 / 4)
        var acc = 0
        var bits = 0
        for (c in s) {
            acc = (acc shl 6) or B64URL.indexOf(c); bits += 6
            if (bits >= 8) { out.add(((acc shr (bits - 8)) and 0xFF).toByte()); bits -= 8 }
        }
        require((acc and ((1 shl bits) - 1)) == 0) { "non-zero trailing bits" }
        return out.toByteArray()
    }

    private fun headerString(json: String, key: String): String? {
        val marker = "\"$key\""
        var i = json.indexOf(marker)
        if (i < 0) return null
        i += marker.length
        while (i < json.length && json[i].isWhitespace()) i++
        require(i < json.length && json[i] == ':') { "malformed header near $key" }
        i++
        while (i < json.length && json[i].isWhitespace()) i++
        require(i < json.length && json[i] == '"') { "$key value is not a string" }
        val end = json.indexOf('"', i + 1)
        require(end > i) { "unterminated $key value" }
        val value = json.substring(i + 1, end)
        require('\\' !in value) { "escaped header values are out of scope and refused" }
        return value
    }

    fun split(token: String): Jws {
        val parts = token.split('.')
        require(parts.size == 3) { "JWS compact form needs exactly 3 segments, got ${parts.size} (JWE is out of scope)" }
        val headerBytes = b64urlDecode(parts[0])
        val headerJson = String(headerBytes, Charsets.UTF_8)
        require(headerJson.startsWith("{") && headerJson.endsWith("}")) { "JOSE header is not a JSON object" }
        require('\\' !in headerJson) { "escaped JOSE headers are out of scope and refused" }
        val alg = headerString(headerJson, "alg") ?: throw IllegalArgumentException("JOSE header lacks alg")
        val typ = headerString(headerJson, "typ")
        // The signature segment of alg:none tokens is empty per RFC 7519 §6.1 — allow empty ONLY then.
        val sig = if (parts[2].isEmpty()) {
            require(alg.equals("none", true)) { "empty signature with alg '$alg'" }
            ByteArray(0)
        } else b64urlDecode(parts[2])
        return Jws(headerJson, alg, typ, b64urlDecode(parts[1]), sig)
    }
}

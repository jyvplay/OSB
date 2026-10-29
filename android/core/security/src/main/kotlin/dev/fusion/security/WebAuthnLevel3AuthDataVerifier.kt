// SPDX-License-Identifier: Apache-2.0
package dev.fusion.security

import java.security.MessageDigest

/**
 * Clean-room W3C WebAuthn Level 3 §6.1 `authenticatorData` verifier for the Vault passkey surface.
 *
 * Enforced invariants (incl. the `Neziahtech/streampay#322` adversarial findings):
 * 1. `rpIdHash` **must** equal `SHA-256(rpId)` — a mismatch means the assertion was minted for another origin.
 * 2. `BS = 1` with `BE = 0` (`flags & 0x10 != 0 && flags & 0x08 == 0`) is forbidden.
 * 3. When `AT = 0` and `ED = 0`, the structure must be **exactly 37 bytes** — trailing bytes are unauthenticated.
 * 4. `signCount` must strictly increase per credential, else a cloned authenticator is reported.
 * 5. `BE = 0 → BS = 0`; `UP` must be set for a user-presence-required flow.
 */
object WebAuthnLevel3AuthDataVerifier {
    const val FLAG_UP = 0x01
    const val FLAG_UV = 0x04
    const val FLAG_BE = 0x08
    const val FLAG_BS = 0x10
    const val FLAG_AT = 0x40
    const val FLAG_ED = 0x80

    enum class Failure {
        TOO_SHORT,
        RP_ID_HASH_MISMATCH,
        BACKUP_STATE_WITHOUT_ELIGIBILITY,
        TRAILING_BYTES_WITHOUT_AT_OR_ED,
        USER_PRESENCE_MISSING,
        USER_VERIFICATION_MISSING,
        CLONED_AUTHENTICATOR,
        MALFORMED_ATTESTED_CREDENTIAL,
    }

    data class AuthData(
        val rpIdHash: ByteArray,
        val flags: Int,
        val signCount: Long,
        val aaguid: ByteArray?,
        val credentialId: ByteArray?,
        val cosePublicKey: ByteArray?,
        val extensionsRaw: ByteArray?,
    ) {
        val userPresent: Boolean get() = flags and FLAG_UP != 0
        val userVerified: Boolean get() = flags and FLAG_UV != 0
        val backupEligible: Boolean get() = flags and FLAG_BE != 0
        val backupState: Boolean get() = flags and FLAG_BS != 0
        val attestedCredentialIncluded: Boolean get() = flags and FLAG_AT != 0
        val extensionDataIncluded: Boolean get() = flags and FLAG_ED != 0
        fun toBytes(): ByteArray = buildList {
            addAll(rpIdHash.toList())
            add(flags.toByte())
            for (i in 3 downTo 0) add(((signCount ushr (i * 8)) and 0xFF).toByte())
            aaguid?.let { addAll(it.toList()) }
            credentialId?.let {
                add(((it.size ushr 8) and 0xFF).toByte())
                add((it.size and 0xFF).toByte())
                addAll(it.toList())
            }
            cosePublicKey?.let { addAll(it.toList()) }
            extensionsRaw?.let { addAll(it.toList()) }
        }.toByteArray()
    }

    data class Verdict(val ok: Boolean, val failure: Failure?, val detail: String, val authData: AuthData?)

    fun rpIdHash(rpId: String): ByteArray = MessageDigest.getInstance("SHA-256").digest(rpId.toByteArray(Charsets.UTF_8))

    fun build(
        rpId: String,
        flags: Int,
        signCount: Long,
        aaguid: ByteArray? = null,
        credentialId: ByteArray? = null,
        cosePublicKey: ByteArray? = null,
        extensions: ByteArray? = null,
    ): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        out.write(rpIdHash(rpId))
        out.write(flags)
        for (i in 3 downTo 0) out.write(((signCount ushr (i * 8)) and 0xFF).toInt())
        if (aaguid != null) {
            require(aaguid.size == 16) { "aaguid must be 16 bytes" }
            out.write(aaguid)
        }
        if (credentialId != null) {
            require(credentialId.size in 1..1023) { "credentialId length must be 1..1023" }
            out.write((credentialId.size ushr 8) and 0xFF)
            out.write(credentialId.size and 0xFF)
            out.write(credentialId)
        }
        cosePublicKey?.let { out.write(it) }
        extensions?.let { out.write(it) }
        return out.toByteArray()
    }

    fun parse(bytes: ByteArray): AuthData? {
        if (bytes.size < 37) return null
        val rp = bytes.copyOfRange(0, 32)
        val flags = bytes[32].toInt() and 0xFF
        var count = 0L
        for (i in 0 until 4) count = (count shl 8) or (bytes[33 + i].toLong() and 0xFFL)
        var p = 37
        var aaguid: ByteArray? = null
        var credId: ByteArray? = null
        var cose: ByteArray? = null
        var ext: ByteArray? = null
        if (flags and FLAG_AT != 0) {
            if (p + 18 > bytes.size) return null
            aaguid = bytes.copyOfRange(p, p + 16); p += 16
            val credLen = ((bytes[p].toInt() and 0xFF) shl 8) or (bytes[p + 1].toInt() and 0xFF)
            p += 2
            if (p + credLen > bytes.size) return null
            credId = bytes.copyOfRange(p, p + credLen); p += credLen
            // COSE_Key: CBOR map (0xA0..0xBF initial byte). Walk one CBOR item.
            if (p >= bytes.size) return null
            val start = p
            p = skipCborItem(bytes, p)
            if (p > bytes.size) return null
            cose = bytes.copyOfRange(start, p)
        }
        if (flags and FLAG_ED != 0) {
            if (p >= bytes.size) return null
            val start = p
            p = skipCborItem(bytes, p)
            if (p > bytes.size) return null
            ext = bytes.copyOfRange(start, p)
        }
        if (p != bytes.size) return null
        return AuthData(rp, flags, count, aaguid, credId, cose, ext)
    }

    private fun skipCborItem(b: ByteArray, off: Int): Int {
        if (off >= b.size) return b.size + 1
        val initial = b[off].toInt() and 0xFF
        val major = initial ushr 5
        val info = initial and 0x1F
        var p = off + 1
        val argLen = when {
            info < 24 -> 0
            info == 24 -> 1
            info == 25 -> 2
            info == 26 -> 4
            info == 27 -> 8
            else -> return b.size + 1
        }
        // For info < 24 the argument value IS `info` (CBOR RFC 8949 §3.1); otherwise it follows big-endian.
        var arg = if (info < 24) info.toLong() else 0L
        if (info >= 24) {
            for (i in 0 until argLen) arg = (arg shl 8) or (b[p + i].toLong() and 0xFFL)
            p += argLen
        }
        return when (major) {
            0, 1, 7 -> p
            2, 3 -> p + arg.toInt()
            4 -> {
                var q = p
                repeat(arg.toInt()) {
                    q = skipCborItem(b, q)
                    q = skipCborItem(b, q)
                }
                q
            }
            5 -> {
                var q = p
                repeat(arg.toInt()) {
                    q = skipCborItem(b, q)
                    q = skipCborItem(b, q)
                }
                q
            }
            else -> b.size + 1
        }
    }

    fun verify(
        bytes: ByteArray,
        rpId: String,
        previousSignCount: Long? = null,
        requireUserVerification: Boolean = false,
    ): Verdict {
        if (bytes.size < 37) {
            return Verdict(false, Failure.TOO_SHORT, "authenticatorData is ${bytes.size} B (< 37)", null)
        }
        val flags = bytes[32].toInt() and 0xFF
        if (flags and FLAG_BS != 0 && flags and FLAG_BE == 0) {
            return Verdict(false, Failure.BACKUP_STATE_WITHOUT_ELIGIBILITY, "BS=1 while BE=0 violates WebAuthn L3 §6.1", null)
        }
        if (flags and FLAG_AT == 0 && flags and FLAG_ED == 0 && bytes.size != 37) {
            return Verdict(
                false,
                Failure.TRAILING_BYTES_WITHOUT_AT_OR_ED,
                "AT=0 and ED=0 requires exactly 37 B, got ${bytes.size} B",
                null,
            )
        }
        val parsed = parse(bytes)
            ?: return Verdict(false, Failure.MALFORMED_ATTESTED_CREDENTIAL, "attested credential / extension CBOR is malformed", null)
        val expected = rpIdHash(rpId)
        if (!expected.contentEquals(parsed.rpIdHash)) {
            return Verdict(false, Failure.RP_ID_HASH_MISMATCH, "rpIdHash is not SHA-256($rpId)", parsed)
        }
        if (flags and FLAG_UP == 0) {
            return Verdict(false, Failure.USER_PRESENCE_MISSING, "UP flag clear", parsed)
        }
        if (requireUserVerification && flags and FLAG_UV == 0) {
            return Verdict(false, Failure.USER_VERIFICATION_MISSING, "UV flag clear but user verification required", parsed)
        }
        if (previousSignCount != null && previousSignCount > 0 && parsed.signCount <= previousSignCount) {
            return Verdict(
                false,
                Failure.CLONED_AUTHENTICATOR,
                "signCount ${parsed.signCount} did not increase past $previousSignCount",
                parsed,
            )
        }
        return Verdict(true, null, "signCount=${parsed.signCount} uv=${parsed.userVerified} be=${parsed.backupEligible} bs=${parsed.backupState}", parsed)
    }

    /** RFC 6238 TOTP (SHA-1/256/512) for the same Vault entry, with ±1 step clock-skew tolerance. */
    fun totp(secret: ByteArray, unixSeconds: Long, periodSeconds: Int = 30, digits: Int = 6, algorithm: String = "SHA1"): String {
        require(periodSeconds in 1..300 && digits in 6..8) { "invalid TOTP parameters" }
        val counter = unixSeconds / periodSeconds
        val mac = javax.crypto.Mac.getInstance("Hmac$algorithm")
        mac.init(javax.crypto.spec.SecretKeySpec(secret, "Hmac$algorithm"))
        val msg = ByteArray(8) { i -> ((counter ushr ((7 - i) * 8)) and 0xFF).toByte() }
        val hash = mac.doFinal(msg)
        val offset = hash[hash.size - 1].toInt() and 0x0F
        val binary = ((hash[offset].toInt() and 0x7F) shl 24) or
            ((hash[offset + 1].toInt() and 0xFF) shl 16) or
            ((hash[offset + 2].toInt() and 0xFF) shl 8) or
            (hash[offset + 3].toInt() and 0xFF)
        val otp = binary % pow10(digits)
        return otp.toString().padStart(digits, '0')
    }

    private fun pow10(n: Int): Int {
        var v = 1
        repeat(n) { v *= 10 }
        return v
    }

    fun totpMatches(secret: ByteArray, code: String, unixSeconds: Long, periodSeconds: Int = 30, digits: Int = 6, algorithm: String = "SHA1"): Boolean {
        for (skew in -1..1) {
            if (totp(secret, unixSeconds + skew.toLong() * periodSeconds, periodSeconds, digits, algorithm) == code) return true
        }
        return false
    }
}

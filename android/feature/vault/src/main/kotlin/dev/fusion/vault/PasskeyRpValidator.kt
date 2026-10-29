// SPDX-License-Identifier: Apache-2.0
package dev.fusion.vault

import java.net.URI
import java.security.MessageDigest

/**
 * Clean-room W3C **WebAuthn Level 3 Relying Party (`rpId`) & `authenticatorData` Verifier**.
 *
 * Enforces:
 * - Origin HTTPS requirement (with `http://localhost` dev exception only if explicitly enabled);
 * - Domain-boundary `rpId` suffix matching (`login.example.com` may assert `rpId = "example.com"`,
 *   never `evil-example.com`, never bare TLD `com`, never IPv4 literals);
 * - `37+` byte `authenticatorData` binary layout (`rpIdHash[32] || flags[1] || signCount[4]`),
 *   verifying `SHA-256(rpId) == rpIdHash` in constant-scan loop, User Presence (`UP = 0x01`),
 *   optional User Verification (`UV = 0x04`), Backup Eligibility/State (`BE = 0x08`, `BS = 0x10`
 *   consistency: `BS=1` with `BE=0` is forbidden per WebAuthn L3 §6.1!), and monotonic `signCount`.
 */
object PasskeyRpValidator {
    const val SCOPE =
        "Clean-room W3C WebAuthn Level 3 rpId domain-boundary & authenticatorData (rpIdHash, UP/UV/BE/BS flags, monotonic signCount) verifier."

    data class AuthDataInspection(
        val valid: Boolean,
        val userPresent: Boolean,
        val userVerified: Boolean,
        val backupEligible: Boolean,
        val backupState: Boolean,
        val signCount: Long,
        val failureReason: String? = null,
    )

    fun isRpIdAllowedForOrigin(originUrl: String, rpId: String, allowLocalhostHttp: Boolean = false): Boolean {
        val uri = runCatching { URI(originUrl) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase() ?: return false
        val host = uri.host?.lowercase()?.trimEnd('.') ?: return false
        val cleanRp = rpId.trim().lowercase().trimEnd('.')
        if (cleanRp.isEmpty() || ':' in cleanRp || '/' in cleanRp) return false
        if (host.matches(Regex("\\d+\\.\\d+\\.\\d+\\.\\d+"))) return false
        val isLocal = host == "localhost"
        if (scheme != "https" && !(allowLocalhostHttp && scheme == "http" && isLocal)) return false
        if (!isLocal && '.' !in cleanRp) return false // forbid single-label TLD rpId like "com"
        return host == cleanRp || host.endsWith(".$cleanRp")
    }

    fun verifyAuthenticatorData(
        authData: ByteArray,
        expectedRpId: String,
        requireUserVerification: Boolean = true,
        previousSignCount: Long = 0L,
    ): AuthDataInspection {
        if (authData.size < 37) {
            return AuthDataInspection(false, false, false, false, false, 0L, "authenticatorData shorter than 37 bytes")
        }
        val expectedHash = MessageDigest.getInstance("SHA-256")
            .digest(expectedRpId.trim().lowercase().toByteArray(Charsets.UTF_8))
        var hashDiff = 0
        for (i in 0 until 32) {
            hashDiff = hashDiff or ((authData[i].toInt() and 0xFF) xor (expectedHash[i].toInt() and 0xFF))
        }
        val flags = authData[32].toInt() and 0xFF
        val up = (flags and 0x01) != 0
        val uv = (flags and 0x04) != 0
        val be = (flags and 0x08) != 0
        val bs = (flags and 0x10) != 0
        val signCount = ((authData[33].toLong() and 0xFFL) shl 24) or
            ((authData[34].toLong() and 0xFFL) shl 16) or
            ((authData[35].toLong() and 0xFFL) shl 8) or
            (authData[36].toLong() and 0xFFL)

        if (hashDiff != 0) return AuthDataInspection(false, up, uv, be, bs, signCount, "rpIdHash mismatch")
        if (!up) return AuthDataInspection(false, up, uv, be, bs, signCount, "User Presence (UP) bit 0 not set")
        if (requireUserVerification && !uv) {
            return AuthDataInspection(false, up, uv, be, bs, signCount, "User Verification (UV) bit 2 required")
        }
        if (bs && !be) {
            return AuthDataInspection(false, up, uv, be, bs, signCount, "WebAuthn L3 §6.1 invalid state: BS=1 while BE=0")
        }
        if ((previousSignCount > 0L || signCount > 0L) && signCount <= previousSignCount && previousSignCount > 0L) {
            return AuthDataInspection(false, up, uv, be, bs, signCount, "cloned authenticator: non-monotonic signCount $signCount <= $previousSignCount")
        }
        return AuthDataInspection(true, up, uv, be, bs, signCount, null)
    }
}

// SPDX-License-Identifier: Apache-2.0
package dev.fusion.model

import java.nio.charset.StandardCharsets
import java.security.PublicKey
import java.security.Signature

/**
 * Clean-room One-Time Purchase, Ed25519 Offline License Key, 14-Day Trial, and Refund-Safe
 * Entitlement State Machine (`BL-01`..`BL-05`).
 *
 * Guarantees:
 * - `BL-01`: Transparent one-time `$19.99` (`1999` cents) Core Desktop unlock + optional `$14.99`
 *   (`1499` cents) Pro Pack; zero recurring subscriptions.
 * - `BL-02` & `BL-03`: Offline grace cache (`<= 30 days`) + standalone Ed25519 signed Pro license
 *   receipt verification (`sku|licenseId|issuedMs|deviceBindingOrWildcard`).
 * - `BL-04`: Full 14-day trial (`14 * 86_400_000 ms`), transitioning cleanly to nag-free read-only
 *   Files + Find (zero modal popups or countdown timers!).
 * - `BL-05`: Refund/revocation downgrades entitlement tier ONLY — `wipeUserData` is unconditionally
 *   `false` (`check(!wipeUserData)` invariant!).
 */
object BillingEntitlementLedger {
    const val CORE_ONE_TIME_PRICE_CENTS = 1_999
    const val PRO_PACK_PRICE_CENTS = 1_499
    const val TRIAL_DURATION_MS = 14L * 24L * 3_600_000L
    const val OFFLINE_CACHE_GRACE_MS = 30L * 24L * 3_600_000L

    enum class Tier {
        TRIAL_FULL_14D,
        READ_ONLY_FILES_AND_FIND_NAG_FREE,
        CORE_UNLOCKED,
        PRO_UNLOCKED,
    }

    data class EntitlementEvaluation(
        val tier: Tier,
        val canMutateWorkspace: Boolean,
        val proCapabilitiesActive: Boolean,
        val showNagBanner: Boolean, // Always false per BL-04!
        val wipeUserDataOnRevoke: Boolean, // Always false per BL-05!
    ) {
        init {
            check(!showNagBanner) { "BL-04 violation: nag banners are forbidden" }
            check(!wipeUserDataOnRevoke) { "BL-05 violation: revocation must never wipe user data" }
        }
    }

    fun evaluate(
        nowMs: Long,
        firstInstallMs: Long,
        corePurchased: Boolean,
        proPurchased: Boolean,
        purchaseRevokedOrRefunded: Boolean,
        lastVerifiedReceiptMs: Long?,
        standaloneEd25519ProVerified: Boolean = false,
    ): EntitlementEvaluation {
        if (standaloneEd25519ProVerified && !purchaseRevokedOrRefunded) {
            return EntitlementEvaluation(Tier.PRO_UNLOCKED, true, true, showNagBanner = false, wipeUserDataOnRevoke = false)
        }
        val cacheFresh = lastVerifiedReceiptMs != null && (nowMs - lastVerifiedReceiptMs) <= OFFLINE_CACHE_GRACE_MS
        if (!purchaseRevokedOrRefunded && cacheFresh) {
            if (proPurchased) {
                return EntitlementEvaluation(Tier.PRO_UNLOCKED, true, true, showNagBanner = false, wipeUserDataOnRevoke = false)
            }
            if (corePurchased) {
                return EntitlementEvaluation(Tier.CORE_UNLOCKED, true, false, showNagBanner = false, wipeUserDataOnRevoke = false)
            }
        }
        val inTrial = !purchaseRevokedOrRefunded && (nowMs >= firstInstallMs) && (nowMs - firstInstallMs < TRIAL_DURATION_MS)
        return if (inTrial) {
            EntitlementEvaluation(Tier.TRIAL_FULL_14D, true, false, showNagBanner = false, wipeUserDataOnRevoke = false)
        } else {
            EntitlementEvaluation(Tier.READ_ONLY_FILES_AND_FIND_NAG_FREE, false, false, showNagBanner = false, wipeUserDataOnRevoke = false)
        }
    }

    fun verifyEd25519LicenseToken(
        payload: String,
        signatureBytes: ByteArray,
        issuerPublicKey: PublicKey,
        expectedDeviceHashOrNull: String? = null,
    ): Boolean {
        // Canonical payload: "FUSION-PRO-V1|<licenseId>|<issuedEpochMs>|<deviceHashOr*>"
        val parts = payload.split('|')
        if (parts.size != 4 || parts[0] != "FUSION-PRO-V1" || parts[1].isBlank()) return false
        val boundDevice = parts[3]
        if (boundDevice != "*" && expectedDeviceHashOrNull != null && boundDevice != expectedDeviceHashOrNull) {
            return false
        }
        val sig = Signature.getInstance("Ed25519")
        sig.initVerify(issuerPublicKey)
        sig.update(payload.toByteArray(StandardCharsets.UTF_8))
        return runCatching { sig.verify(signatureBytes) }.getOrDefault(false)
    }
}

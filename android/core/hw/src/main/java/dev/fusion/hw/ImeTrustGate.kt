// SPDX-License-Identifier: Apache-2.0
package dev.fusion.hw

import java.util.Locale

/**
 * Clean-room Input Method Editor (`IME`) keylogger trust & network-capability gate (`ImeTrustGate`).
 *
 * A third-party Android soft keyboard (`InputMethodService`) receives every character typed into
 * standard `EditText`/`BasicTextField` views — and if it holds `android.permission.INTERNET` (or stages
 * telemetry to shared storage), it acts as a full keylogger!
 *
 * [ImeTrustGate] evaluates the active IME descriptor before rendering any secret/private input surface:
 * - For `FieldSensitivity.SECRET_PIN_OR_PASSPHRASE`: ALWAYS forces `InputMode.IN_APP_SCRAMBLED_PAD`
 *   if the active IME is non-system, holds `INTERNET`, lacks `isDirectBootAware`, or is unpinned!
 * - Even for `FieldSensitivity.PRIVATE_DOCUMENT`: forces `EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING`
 *   (`incognitoKeyboard = true`) and warns/switches when an `INTERNET`-capable third-party IME is active.
 */
object ImeTrustGate {
    const val SCOPE =
        "Active Android IME keylogger policy gate (evaluates system-partition flag, INTERNET permission, cert pin, incognito IME_FLAG_NO_PERSONALIZED_LEARNING, in-app scrambled pad fallback); kernel Binder IPC inspection out of scope."

    enum class FieldSensitivity {
        PUBLIC_SEARCH,
        PRIVATE_DOCUMENT,
        SECRET_PIN_OR_PASSPHRASE,
    }

    enum class InputMode {
        ALLOW_SYSTEM_IME_INCOGNITO,
        WARN_UNTRUSTED_IME_INCOGNITO,
        FORCE_IN_APP_SCRAMBLED_PAD,
    }

    data class ImeDescriptor(
        val packageName: String,
        val isSystemApp: Boolean,
        val requestsInternetPermission: Boolean,
        val isDirectBootAware: Boolean,
        val signingCertSha256Hex: String? = null,
    )

    data class GateVerdict(
        val mode: InputMode,
        val requireNoPersonalizedLearning: Boolean,
        val disableExtractUi: Boolean,
        val reason: String,
    )

    fun evaluate(
        ime: ImeDescriptor,
        sensitivity: FieldSensitivity,
        trustedSystemCertPinsHex: Set<String> = emptySet(),
    ): GateVerdict {
        val normCert = ime.signingCertSha256Hex?.lowercase(Locale.ROOT)
        val normPins = trustedSystemCertPinsHex.map { it.lowercase(Locale.ROOT) }.toSet()
        val pinVerified = normPins.isEmpty() || (normCert != null && normCert in normPins)

        return when (sensitivity) {
            FieldSensitivity.SECRET_PIN_OR_PASSPHRASE -> {
                when {
                    !ime.isSystemApp -> GateVerdict(
                        mode = InputMode.FORCE_IN_APP_SCRAMBLED_PAD,
                        requireNoPersonalizedLearning = true,
                        disableExtractUi = true,
                        reason = "Third-party IME '${ime.packageName}' blocked on secret field",
                    )
                    ime.requestsInternetPermission -> GateVerdict(
                        mode = InputMode.FORCE_IN_APP_SCRAMBLED_PAD,
                        requireNoPersonalizedLearning = true,
                        disableExtractUi = true,
                        reason = "Network-capable IME '${ime.packageName}' blocked on secret field",
                    )
                    !ime.isDirectBootAware || !pinVerified -> GateVerdict(
                        mode = InputMode.FORCE_IN_APP_SCRAMBLED_PAD,
                        requireNoPersonalizedLearning = true,
                        disableExtractUi = true,
                        reason = "Unverified or non-DirectBoot IME '${ime.packageName}' blocked on secret field",
                    )
                    else -> GateVerdict(
                        mode = InputMode.ALLOW_SYSTEM_IME_INCOGNITO,
                        requireNoPersonalizedLearning = true,
                        disableExtractUi = true,
                        reason = "Offline verified system IME permitted in incognito mode",
                    )
                }
            }
            FieldSensitivity.PRIVATE_DOCUMENT -> {
                if (!ime.isSystemApp || ime.requestsInternetPermission || !pinVerified) {
                    GateVerdict(
                        mode = InputMode.WARN_UNTRUSTED_IME_INCOGNITO,
                        requireNoPersonalizedLearning = true,
                        disableExtractUi = true,
                        reason = "IME '${ime.packageName}' may exfiltrate learned n-grams; forcing incognito + warning",
                    )
                } else {
                    GateVerdict(
                        mode = InputMode.ALLOW_SYSTEM_IME_INCOGNITO,
                        requireNoPersonalizedLearning = true,
                        disableExtractUi = false,
                        reason = "Verified offline system IME in incognito mode",
                    )
                }
            }
            FieldSensitivity.PUBLIC_SEARCH -> GateVerdict(
                mode = InputMode.ALLOW_SYSTEM_IME_INCOGNITO,
                requireNoPersonalizedLearning = true,
                disableExtractUi = false,
                reason = "Incognito learning suppression enforced by default",
            )
        }
    }
}

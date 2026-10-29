// SPDX-License-Identifier: Apache-2.0
package dev.fusion.security

/**
 * Clean-room IME Personalized-Learning / Dictionary Leak Firewall & Background Clipboard Access
 * Quarantine (`EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING` = `0x01000000` policy model).
 */
object ImeIncognitoClipboardFirewall {
    const val SCOPE =
        "Pure-JVM IME incognito flag enforcer, untrusted third-party IME warning gate on sensitive surfaces, and background clipboard access firewall."

    const val IME_FLAG_NO_PERSONALIZED_LEARNING = 0x01000000
    const val TYPE_TEXT_VARIATION_PASSWORD = 0x00000081

    enum class SurfaceSensitivity { PUBLIC_NOTE, PRIVATE_BROWSER, VAULT_SECRET, TAILS_SESSION }

    data class ImePolicyResult(
        val imeOptionsMask: Int,
        val allowThirdPartyNetworkedIme: Boolean,
        val fallbackToBuiltInSecureKeypad: Boolean,
        val reason: String,
    )

    fun evaluateImeSurface(
        surface: SurfaceSensitivity,
        currentImeHasInternetPermission: Boolean,
        currentImeIsSystemPreinstalled: Boolean,
    ): ImePolicyResult = when (surface) {
        SurfaceSensitivity.VAULT_SECRET -> ImePolicyResult(
            imeOptionsMask = IME_FLAG_NO_PERSONALIZED_LEARNING,
            allowThirdPartyNetworkedIme = false,
            fallbackToBuiltInSecureKeypad = currentImeHasInternetPermission || !currentImeIsSystemPreinstalled,
            reason = "Vault secrets enforce incognito IME and switch to built-in CSPRNG keypad when active IME holds INTERNET",
        )
        SurfaceSensitivity.TAILS_SESSION, SurfaceSensitivity.PRIVATE_BROWSER -> ImePolicyResult(
            imeOptionsMask = IME_FLAG_NO_PERSONALIZED_LEARNING,
            allowThirdPartyNetworkedIme = false,
            fallbackToBuiltInSecureKeypad = currentImeHasInternetPermission,
            reason = "Amnesic/private surfaces forbid IME dictionary learning and networked third-party IMEs",
        )
        SurfaceSensitivity.PUBLIC_NOTE -> ImePolicyResult(
            imeOptionsMask = IME_FLAG_NO_PERSONALIZED_LEARNING,
            allowThirdPartyNetworkedIme = !currentImeHasInternetPermission,
            fallbackToBuiltInSecureKeypad = false,
            reason = "Default Fusion privacy disables IME cloud/personalized dictionary telemetry",
        )
    }

    fun allowClipboardRead(
        readerHasWindowFocus: Boolean,
        isExplicitUserPasteGesture: Boolean,
        clipAgeMillis: Long,
        maxSensitiveTtlMillis: Long = 30_000L,
    ): Boolean = readerHasWindowFocus && isExplicitUserPasteGesture && clipAgeMillis in 0L..maxSensitiveTtlMillis
}

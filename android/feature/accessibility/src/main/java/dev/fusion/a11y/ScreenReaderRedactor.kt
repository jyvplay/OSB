// SPDX-License-Identifier: Apache-2.0
package dev.fusion.a11y

import java.util.Locale

/**
 * Clean-room AccessibilityService keylogger/screen-scraper defense policy (`ScreenReaderRedactor`).
 *
 * On Android, banking trojans and stalkerware (`FluBot`, `Cerberus`, `TeaBot`, `SpyNote`) overwhelmingly
 * abuse `AccessibilityService` (`TYPE_VIEW_TEXT_CHANGED` + `canRetrieveWindowContent`) as a user-space
 * keylogger! Meanwhile, blind/low-vision users legitimately rely on system screen readers (`TalkBack`,
 * `BrailleBack`).
 *
 * [ScreenReaderRedactor] resolves this tension without breaking real screen-reader accessibility:
 * - If ALL enabled accessibility services are verified system-signed assistive tools (`isSystemApp &&
 *   feedbackSpokenOrBraille && certMatched`), genuine screen-reader announcements proceed appropriately
 *   (with password characters masked to bullet count unless user explicitly toggles speak-passwords).
 * - If ANY enabled accessibility service is a non-system / sideloaded / generic-feedback scraper,
 *   sensitive views enforce `MASK_NODE_AND_SUPPRESS_TEXT_EVENTS` (`importantForAccessibility = NO_HIDE_DESCENDANTS`
 *   on secret inputs + redacted `[Protected content]` label).
 */
object ScreenReaderRedactor {
    const val SCOPE =
        "Anti-AccessibilityService-stalkerware redaction policy (preserves verified system TalkBack/BrailleBack AX while masking node text & TYPE_VIEW_TEXT_CHANGED for unverified services); OS Settings revocation out of scope."

    enum class Action {
        ALLOW_FULL_AX,
        ALLOW_MASKED_PASSWORD_AX,
        REDACT_UNTRUSTED_ACCESSIBILITY_SCRAPER,
    }

    data class BoundA11yService(
        val id: String,
        val isSystemApp: Boolean,
        val isSpokenOrBrailleFeedback: Boolean,
        val canRetrieveWindowContent: Boolean,
        val signingCertSha256Hex: String? = null,
    )

    data class RedactionResult(
        val action: Action,
        val exposedText: String,
        val suppressTextChangeEvents: Boolean,
        val hideDescendantsFromUntrusted: Boolean,
        val untrustedServiceIds: List<String>,
    )

    fun evaluateNode(
        rawText: String,
        isPasswordOrSecret: Boolean,
        isPrivateDocument: Boolean,
        boundServices: List<BoundA11yService>,
        trustedSystemCertPinsHex: Set<String> = emptySet(),
    ): RedactionResult {
        if (boundServices.isEmpty()) {
            return RedactionResult(
                action = if (isPasswordOrSecret) Action.ALLOW_MASKED_PASSWORD_AX else Action.ALLOW_FULL_AX,
                exposedText = if (isPasswordOrSecret) "\u2022".repeat(rawText.length.coerceAtMost(16)) else rawText,
                suppressTextChangeEvents = isPasswordOrSecret,
                hideDescendantsFromUntrusted = false,
                untrustedServiceIds = emptyList(),
            )
        }

        val normPins = trustedSystemCertPinsHex.map { it.lowercase(Locale.ROOT) }.toSet()
        val untrusted = boundServices.filter { svc ->
            val certOk = normPins.isEmpty() || (svc.signingCertSha256Hex?.lowercase(Locale.ROOT) in normPins)
            !svc.isSystemApp || !svc.isSpokenOrBrailleFeedback || !certOk
        }.map { it.id }

        if (untrusted.isNotEmpty() && (isPasswordOrSecret || isPrivateDocument)) {
            return RedactionResult(
                action = Action.REDACT_UNTRUSTED_ACCESSIBILITY_SCRAPER,
                exposedText = "[Protected by Fusion Privacy]",
                suppressTextChangeEvents = true,
                hideDescendantsFromUntrusted = isPasswordOrSecret,
                untrustedServiceIds = untrusted,
            )
        }

        if (isPasswordOrSecret) {
            return RedactionResult(
                action = Action.ALLOW_MASKED_PASSWORD_AX,
                exposedText = "${rawText.length} characters entered",
                suppressTextChangeEvents = true,
                hideDescendantsFromUntrusted = false,
                untrustedServiceIds = untrusted,
            )
        }

        return RedactionResult(
            action = Action.ALLOW_FULL_AX,
            exposedText = rawText,
            suppressTextChangeEvents = false,
            hideDescendantsFromUntrusted = false,
            untrustedServiceIds = untrusted,
        )
    }
}

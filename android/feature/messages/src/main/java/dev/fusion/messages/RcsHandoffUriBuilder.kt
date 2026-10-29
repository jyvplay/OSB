// SPDX-License-Identifier: Apache-2.0
package dev.fusion.messages

import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Clean-room System RCS/SMS Intent Handoff Builder (`MS-04`) & User-Owned BlueBubbles Server
 * Preflight Validator (`MS-01`).
 *
 * Enforces owner spec boundaries strictly:
 * - `MS-04`: RCS is NEVER claimed as an internal third-party carrier stack; all RCS/MMS compose
 *   requests sanitize recipients to E.164/digits and construct a standard `smsto:` intent URI for
 *   handoff to the device's system messaging app.
 * - `MS-01`: BlueBubbles bridge requires an HTTPS URL to a user-owned Mac + non-blank password +
 *   explicit acknowledgment that the user supplies their own Mac & Apple ID (no hosted relay!).
 */
object RcsHandoffUriBuilder {
    data class SystemSmsHandoff(
        val schemeUri: String,
        val sanitizedRecipients: List<String>,
        val bodyPreview: String,
        val usesSystemMessagingAppOnly: Boolean = true,
    )

    fun buildSystemHandoff(recipients: List<String>, draftBody: String = ""): SystemSmsHandoff {
        require(recipients.isNotEmpty()) { "at least one recipient required" }
        val cleaned = recipients.map { raw ->
            val stripped = raw.trim().filter { it.isDigit() || it == '+' }
            require(stripped.count { it.isDigit() } in 3..15) { "invalid phone/RCS recipient '$raw'" }
            require(stripped.indexOf('+') <= 0) { "misplaced '+' in recipient '$raw'" }
            stripped
        }
        val joined = cleaned.joinToString(";")
        val query = if (draftBody.isEmpty()) {
            ""
        } else {
            "?body=" + URLEncoder.encode(draftBody.take(2_000), StandardCharsets.UTF_8)
        }
        return SystemSmsHandoff(
            schemeUri = "smsto:$joined$query",
            sanitizedRecipients = cleaned,
            bodyPreview = draftBody.take(120),
            usesSystemMessagingAppOnly = true,
        )
    }

    data class BlueBubblesPreflight(
        val valid: Boolean,
        val canonicalHttpsOrigin: String?,
        val rejectionReason: String?,
    )

    fun validateUserOwnedBlueBubblesServer(
        serverUrl: String,
        serverPassword: String,
        userConfirmedOwnMacAndAppleId: Boolean,
    ): BlueBubblesPreflight {
        if (!userConfirmedOwnMacAndAppleId) {
            return BlueBubblesPreflight(false, null, "MS-01 requires explicit user confirmation of user-owned Mac and Apple ID.")
        }
        if (serverPassword.trim().length < 8) {
            return BlueBubblesPreflight(false, null, "BlueBubbles server password must be at least 8 characters.")
        }
        val uri = runCatching { URI(serverUrl.trim()) }.getOrNull()
            ?: return BlueBubblesPreflight(false, null, "Malformed server URL.")
        val scheme = uri.scheme?.lowercase()
        val host = uri.host?.lowercase()
        if (scheme != "https" || host.isNullOrBlank()) {
            return BlueBubblesPreflight(false, null, "TLS HTTPS endpoint required for BlueBubbles bridge.")
        }
        val port = if (uri.port == -1 || uri.port == 443) "" else ":${uri.port}"
        return BlueBubblesPreflight(true, "https://$host$port", null)
    }
}

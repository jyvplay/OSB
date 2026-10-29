// SPDX-License-Identifier: Apache-2.0
package dev.fusion.commands

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Clean-room Workspace Preset Switcher (`AU-02`), Opt-In Local Assistant Visible-Tool-Call Ledger
 * with Zero Idle Process (`AU-04`), and HMAC-Scoped Third-Party Automation `Intent` Gate (`AU-05`).
 */
object ThirdPartyIntentCommandGate {
    data class WorkspacePreset(
        val presetId: String,
        val title: String,
        val moduleIdsToOpen: List<String>,
        val layoutMode: String, // "SIDE_BY_SIDE", "QUAD_GRID", "WRITER_FOCUS"
    ) {
        init {
            require(presetId.isNotBlank() && title.isNotBlank() && moduleIdsToOpen.size in 1..6) {
                "invalid WorkspacePreset '$presetId'"
            }
        }
    }

    val DEFAULT_PRESETS: List<WorkspacePreset> = listOf(
        WorkspacePreset("preset_office", "Office & Calc", listOf("docs", "calc", "files"), "SIDE_BY_SIDE"),
        WorkspacePreset("preset_research", "Research & Notes", listOf("browser", "notes", "pdf-preview"), "SIDE_BY_SIDE"),
        WorkspacePreset("preset_dev", "Terminal & Files", listOf("terminal", "files"), "SIDE_BY_SIDE"),
    )

    data class VisibleToolCallRecord(
        val callId: String,
        val toolName: String,
        val renderedArgsSummary: String,
        val userApproved: Boolean,
        val daemonTerminatedOnIdle: Boolean = true,
    ) {
        init {
            require(daemonTerminatedOnIdle) { "AU-04 violation: local assistant must never leave an idle background daemon" }
        }
    }

    private val ALLOWED_EXTERNAL_ACTIONS = setOf(
        "dev.fusion.intent.SWITCH_PRESET",
        "dev.fusion.intent.OPEN_MODULE",
        "dev.fusion.intent.RUN_ROUTINE",
    )

    data class IntentVerdict(val allowed: Boolean, val reason: String)

    fun mintScopedCapabilityMac(
        secretKey: ByteArray,
        callerPackage: String,
        action: String,
        targetId: String,
        expiresEpochMs: Long,
    ): String {
        require(secretKey.size >= 32) { "automation HMAC key must be >= 32 bytes" }
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secretKey, "HmacSHA256"))
        val canonical = "$callerPackage|$action|$targetId|$expiresEpochMs"
        return mac.doFinal(canonical.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    fun verifyIncomingAutomationIntent(
        automationEnabledByUser: Boolean,
        secretKey: ByteArray,
        callerPackage: String,
        action: String,
        targetId: String,
        expiresEpochMs: Long,
        providedMacHex: String,
        nowEpochMs: Long,
    ): IntentVerdict {
        if (!automationEnabledByUser) {
            return IntentVerdict(false, "third-party Intent automation is disabled by default")
        }
        if (action !in ALLOWED_EXTERNAL_ACTIONS) {
            return IntentVerdict(false, "action '$action' is outside the public AU-05 allowlist")
        }
        if (targetId.lowercase() in setOf("vault", "privacy", "security")) {
            return IntentVerdict(false, "Vault/Privacy modules are unconditionally barred from external Intent automation")
        }
        if (nowEpochMs > expiresEpochMs) {
            return IntentVerdict(false, "capability token expired at $expiresEpochMs")
        }
        val expectedHex = mintScopedCapabilityMac(secretKey, callerPackage, action, targetId, expiresEpochMs)
        val ok = MessageDigest.isEqual(
            expectedHex.toByteArray(StandardCharsets.US_ASCII),
            providedMacHex.trim().lowercase().toByteArray(StandardCharsets.US_ASCII),
        )
        return if (ok) IntentVerdict(true, "verified") else IntentVerdict(false, "HMAC token signature mismatch")
    }
}

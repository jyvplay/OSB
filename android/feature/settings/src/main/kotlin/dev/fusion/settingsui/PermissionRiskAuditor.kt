// SPDX-License-Identifier: Apache-2.0
package dev.fusion.settingsui

/**
 * Dangerous Android **Permission-Combination Toxicity Scorer & Remediation Planner**.
 *
 * Audits app permission sets for stalkerware / credential-harvesting / keylogging combinations
 * (e.g. `INTERNET + RECORD_AUDIO`, `BIND_ACCESSIBILITY_SERVICE + SYSTEM_ALERT_WINDOW`,
 * `READ_SMS + INTERNET`, `READ_MEDIA_IMAGES + ACCESS_FINE_LOCATION`).
 */
object PermissionRiskAuditor {
    const val SCOPE =
        "Android permission-combination toxicity scorer (0..100) detecting exfiltration, accessibility keylogging, and acoustic/location stalking chains."

    enum class Severity { LOW, MODERATE, HIGH, CRITICAL }

    data class AuditReport(
        val riskScore: Int,
        val severity: Severity,
        val toxicCombinations: List<String>,
        val remediations: List<String>,
    )

    fun audit(grantedPermissions: Set<String>): AuditReport {
        val perms = grantedPermissions.map { it.substringAfterLast('.').uppercase() }.toSet()
        var score = 0
        val combos = ArrayList<String>()
        val fixes = ArrayList<String>()

        if ("BIND_ACCESSIBILITY_SERVICE" in perms) {
            score += 45
            combos.add("ACCESSIBILITY_INPUT_OBSERVATION")
            fixes.add("Revoke AccessibilityService unless verified assistive technology")
        }
        if ("SYSTEM_ALERT_WINDOW" in perms && ("BIND_ACCESSIBILITY_SERVICE" in perms || "RECORD_AUDIO" in perms)) {
            score += 30
            combos.add("TAPJACKING_OVERLAY_COMBO")
            fixes.add("Disable Draw-Over-Other-Apps (SYSTEM_ALERT_WINDOW)")
        }
        if ("INTERNET" in perms && "RECORD_AUDIO" in perms) {
            score += 25
            combos.add("ACOUSTIC_EXFILTRATION_CHAIN")
            fixes.add("Gate microphone access with AcousticKeystrokeShield during text/Vault focus")
        }
        if ("INTERNET" in perms && ("READ_SMS" in perms || "RECEIVE_SMS" in perms)) {
            score += 35
            combos.add("OTP_SMS_INTERCEPTION_EXFIL")
            fixes.add("Revoke SMS read permission; migrate 2FA to hardware/Vault TOTP or Passkey")
        }
        if ("INTERNET" in perms && ("READ_CONTACTS" in perms || "ACCESS_FINE_LOCATION" in perms)) {
            score += 20
            combos.add("SOCIAL_OR_GEOLOCATION_TELEMETRY_EXFIL")
            fixes.add("Switch to one-time photo/contact picker or coarse/no location")
        }
        val clamped = score.coerceIn(0, 100)
        val sev = when {
            clamped >= 70 -> Severity.CRITICAL
            clamped >= 40 -> Severity.HIGH
            clamped >= 20 -> Severity.MODERATE
            else -> Severity.LOW
        }
        return AuditReport(clamped, sev, combos, fixes)
    }
}

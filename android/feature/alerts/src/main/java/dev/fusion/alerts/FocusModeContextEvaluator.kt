// SPDX-License-Identifier: Apache-2.0
package dev.fusion.alerts

/**
 * Clean-room Focus Mode Evaluator by Workspace / Time-of-Day / Foreground-App / External-Display
 * with Explicit Human-Readable Suppression Reason (`NC-02`, `NC-04`).
 */
object FocusModeContextEvaluator {
    const val SCOPE =
        "Pure-JVM multi-dimension Focus Mode notification gate (workspace, minute-of-day window, foreground app, presentation display) with explicit diagnostic reason strings (NC-02, NC-04)."

    data class FocusRule(
        val id: String,
        val label: String,
        val activeWorkspaces: Set<String> = emptySet(),
        val startMinuteOfDay: Int? = null, // 0..1439, supports overnight wrap (e.g. 1320..420)
        val endMinuteOfDay: Int? = null,
        val triggerForegroundApps: Set<String> = emptySet(),
        val triggerOnExternalPresentation: Boolean = false,
        val allowPrioritySenders: Set<String> = emptySet(),
    )

    data class DeliveryDecision(
        val deliverInterruptively: Boolean,
        val activeFocusId: String?,
        val diagnosticReason: String,
    )

    fun evaluate(
        rules: List<FocusRule>,
        workspaceId: String,
        minuteOfDay: Int,
        foregroundPkg: String,
        externalPresentationConnected: Boolean,
        senderPkg: String,
        isOngoingCallOrAlarm: Boolean = false,
    ): DeliveryDecision {
        require(minuteOfDay in 0..1439) { "minuteOfDay outside 0..1439" }
        if (isOngoingCallOrAlarm) {
            return DeliveryDecision(true, null, "DELIVERED_CRITICAL_CALL_OR_ALARM_BYPASS")
        }
        for (r in rules) {
            val wsHit = workspaceId in r.activeWorkspaces
            val timeHit = r.startMinuteOfDay != null && r.endMinuteOfDay != null &&
                inWindow(minuteOfDay, r.startMinuteOfDay, r.endMinuteOfDay)
            val appHit = foregroundPkg in r.triggerForegroundApps
            val dispHit = r.triggerOnExternalPresentation && externalPresentationConnected
            if (wsHit || timeHit || appHit || dispHit) {
                if (senderPkg in r.allowPrioritySenders) {
                    return DeliveryDecision(true, r.id, "DELIVERED_PRIORITY_ALLOWLIST_IN_${r.id.uppercase()}")
                }
                val trigger = when {
                    dispHit -> "external_presentation_display"
                    wsHit -> "workspace=$workspaceId"
                    appHit -> "foreground_app=$foregroundPkg"
                    else -> "schedule_window"
                }
                return DeliveryDecision(false, r.id, "SUPPRESSED_BY_FOCUS_${r.id.uppercase()}($trigger)")
            }
        }
        return DeliveryDecision(true, null, "DELIVERED_NO_ACTIVE_FOCUS_RULE")
    }

    private fun inWindow(min: Int, start: Int, end: Int): Boolean =
        if (start <= end) min in start..end else (min >= start || min <= end)
}

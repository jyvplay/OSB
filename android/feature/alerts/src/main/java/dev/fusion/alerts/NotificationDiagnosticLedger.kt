// SPDX-License-Identifier: Apache-2.0
package dev.fusion.alerts

/**
 * Clean-room Notification Drop-Reason Diagnostics (`NC-04`), Quick-Panel Tile State Reducer (`NC-03`),
 * and Non-Focus-Stealing Volume/Brightness/Layout OSD Overlay Model (`NC-06`).
 */
object NotificationDiagnosticLedger {
    enum class Disposition {
        STORED_IN_CENTER,
        SKIPPED_SECRET_VISIBILITY,
        SKIPPED_ONGOING_FOREGROUND,
        SKIPPED_GROUP_SUMMARY,
        SUPPRESSED_BY_FOCUS_MODE,
    }

    data class IngestSummary(
        val totalEvaluated: Int,
        val countsByDisposition: Map<Disposition, Int>,
        val listenerConnected: Boolean,
    )

    fun classifyNotification(
        isVisibilitySecret: Boolean,
        isOngoing: Boolean,
        isGroupSummary: Boolean,
        suppressedByFocusProfile: Boolean,
    ): Disposition = when {
        isVisibilitySecret -> Disposition.SKIPPED_SECRET_VISIBILITY // Never store VISIBILITY_SECRET!
        isOngoing -> Disposition.SKIPPED_ONGOING_FOREGROUND
        isGroupSummary -> Disposition.SKIPPED_GROUP_SUMMARY
        suppressedByFocusProfile -> Disposition.SUPPRESSED_BY_FOCUS_MODE
        else -> Disposition.STORED_IN_CENTER
    }

    fun summarizeDispositions(items: List<Disposition>, listenerConnected: Boolean): IngestSummary =
        IngestSummary(
            totalEvaluated = items.size,
            countsByDisposition = Disposition.entries.associateWith { d -> items.count { it == d } },
            listenerConnected = listenerConnected,
        )

    enum class OsdChannel { AUDIO_VOLUME, DISPLAY_BRIGHTNESS, KEYBOARD_LAYOUT, WORKSPACE_SWITCH }

    data class OsdOverlayState(
        val channel: OsdChannel,
        val levelPercentOrNull: Int?,
        val label: String,
        val visibleUntilEpochMs: Long,
        val stealsInputFocus: Boolean = false,
    ) {
        init {
            check(!stealsInputFocus) { "NC-06 invariant: OSD overlay must never steal keyboard/pointer focus" }
        }

        fun isVisibleAt(nowEpochMs: Long): Boolean = nowEpochMs < visibleUntilEpochMs
    }

    fun triggerOsd(
        channel: OsdChannel,
        levelPercentOrNull: Int?,
        label: String,
        nowEpochMs: Long,
        dwellMs: Long = 1_800L,
    ): OsdOverlayState = OsdOverlayState(
        channel = channel,
        levelPercentOrNull = levelPercentOrNull?.coerceIn(0, 100),
        label = label,
        visibleUntilEpochMs = nowEpochMs + dwellMs.coerceIn(500L, 5_000L),
        stealsInputFocus = false,
    )
}

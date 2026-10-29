// SPDX-License-Identifier: Apache-2.0
package dev.fusion.hw

/**
 * Hardware Shield `#30` — **LPDDR4X/LPDDR5 DRAM Rowhammer Bank-Conflict Activation Anomaly Monitor**.
 *
 * Evaluates last-level-cache (`LLC`) miss rate (`misses / tREFI 32 ms` refresh window) and row-buffer
 * conflict ratio (`ACT` commands per memory instruction) against JEDEC Target Row Refresh (**TRR** /
 * **RFM**) thresholds to quarantine hammering sandboxes/processes before bit-flips occur!
 */
object DramRowhammerRefreshMonitor {
    const val SCOPE =
        "Hardware Shield #30: LPDDR4X/5 DRAM Rowhammer LLC-miss/tREFI activation rate estimator and sandbox quarantine advisor."

    enum class MitigationAction { NOMINAL, THROTTLE_AND_RELOCATE_KEY_PAGES, TERMINATE_SANDBOX_IMMEDIATELY }

    data class RowhammerAssessment(
        val estimatedActivationsPerTrefw: Long,
        val rowConflictRatio: Double,
        val action: MitigationAction,
    )

    fun assessWindow(
        llcMissesIn32MsWindow: Long,
        totalMemoryInstructionsIn32Ms: Long,
        dramRowBufferMissFraction: Double,
        highTempAbove75C: Boolean = false,
    ): RowhammerAssessment {
        require(llcMissesIn32MsWindow >= 0L && totalMemoryInstructionsIn32Ms >= 0L)
        val conflictFrac = dramRowBufferMissFraction.coerceIn(0.0, 1.0)
        // At >75C LPDDR refresh interval halves (effective hammer budget tightens by 1.5x)
        val tempMultiplier = if (highTempAbove75C) 1.5 else 1.0
        val effectiveActs = (llcMissesIn32MsWindow * conflictFrac * tempMultiplier).toLong()
        val overallConflictRatio = if (totalMemoryInstructionsIn32Ms == 0L) 0.0
        else llcMissesIn32MsWindow.toDouble() / totalMemoryInstructionsIn32Ms.toDouble()

        val action = when {
            effectiveActs >= 80_000L && overallConflictRatio >= 0.45 ->
                MitigationAction.TERMINATE_SANDBOX_IMMEDIATELY
            effectiveActs >= 25_000L && overallConflictRatio >= 0.20 ->
                MitigationAction.THROTTLE_AND_RELOCATE_KEY_PAGES
            else -> MitigationAction.NOMINAL
        }
        return RowhammerAssessment(effectiveActs, overallConflictRatio, action)
    }
}

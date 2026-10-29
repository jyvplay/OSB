// SPDX-License-Identifier: Apache-2.0
package dev.fusion.commands

/**
 * Pure-JVM **Reversible Batch Command Transaction Journal** for `:core:commands`, grouping
 * multi-file / multi-cell / Recall-purge operations into atomic compensating command bundles
 * with guaranteed LIFO rollback order!
 */
object ReversibleBatchCommandJournal {
    const val SCOPE =
        "Atomic multi-step command transaction journal with LIFO inverse rollback and human-readable Undo summary labels."

    data class AtomicStep(
        val stepId: String,
        val forwardDescription: String,
        val inverseCommandPayload: String,
    )

    data class CommittedTransaction(
        val transactionId: String,
        val summaryLabel: String,
        val committedSteps: List<AtomicStep>,
    ) {
        fun rollbackPlanLifo(): List<AtomicStep> = committedSteps.asReversed()
    }

    fun commitBatch(transactionId: String, verb: String, steps: List<AtomicStep>): CommittedTransaction {
        require(transactionId.isNotBlank() && verb.isNotBlank() && steps.isNotEmpty()) {
            "Non-empty transactionId, verb, and steps required"
        }
        val label = if (steps.size == 1) "$verb (${steps[0].forwardDescription})"
        else "$verb (${steps.size} items)"
        return CommittedTransaction(transactionId, label, steps.toList())
    }
}

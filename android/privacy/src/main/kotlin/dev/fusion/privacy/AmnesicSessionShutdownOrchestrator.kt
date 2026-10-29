package dev.fusion.privacy

/**
 * Clean-room Tails 7.15-Inspired Ordered Amnesic Session Teardown Orchestrator (Apache-2.0).
 *
 * Executes strict dependency-ordered volatile state erasure (`BROWSER_COOKIES_DOM` -> `CLIPBOARD` ->
 * `SEARCH_INDEX_WAL` -> `VAULT_MASTER_KEYS` -> `TOR_CIRCUITS_VANGUARDS` -> `FINAL_RESIDUE_AUDIT`)
 * and refuses to report `CLEAN_SEALED` if any stage fails or leaves non-zero key residue!
 */
object AmnesicSessionShutdownOrchestrator {
    const val SCOPE =
        "Tails 7.15-style dependency-ordered amnesic session teardown state machine + zero-residue post-condition verifier"

    enum class WipeStage {
        BROWSER_COOKIES_DOM,
        CLIPBOARD_SENSITIVE_BUFFER,
        SEARCH_INDEX_WAL_TRUNCATE,
        VAULT_MASTER_KEYS_ZEROIZE,
        TOR_CIRCUITS_VANGUARDS_PURGE,
        FINAL_RESIDUE_AUDIT,
    }

    data class StageReceipt(
        val stage: WipeStage,
        val itemsPurged: Int,
        val succeeded: Boolean,
        val failureReason: String? = null,
    )

    data class ShutdownCertificate(
        val cleanSealed: Boolean,
        val orderedReceipts: List<StageReceipt>,
        val totalPurgedArtifacts: Int,
        val unzeroedKeyBytesRemaining: Int,
    )

    fun executeTeardown(
        secretBuffers: List<ByteArray>,
        stageHooks: Map<WipeStage, () -> Int> = emptyMap(),
    ): ShutdownCertificate {
        val receipts = ArrayList<StageReceipt>()
        val sequence = listOf(
            WipeStage.BROWSER_COOKIES_DOM,
            WipeStage.CLIPBOARD_SENSITIVE_BUFFER,
            WipeStage.SEARCH_INDEX_WAL_TRUNCATE,
            WipeStage.VAULT_MASTER_KEYS_ZEROIZE,
            WipeStage.TOR_CIRCUITS_VANGUARDS_PURGE,
        )

        for (stage in sequence) {
            try {
                var count = stageHooks[stage]?.invoke() ?: 0
                if (stage == WipeStage.VAULT_MASTER_KEYS_ZEROIZE) {
                    for (buf in secretBuffers) {
                        buf.fill(0)
                        count += buf.size
                    }
                }
                receipts.add(StageReceipt(stage, count, true))
            } catch (t: Throwable) {
                receipts.add(StageReceipt(stage, 0, false, t.message ?: t::class.simpleName))
            }
        }

        val unzeroed = secretBuffers.sumOf { buf -> buf.count { it.toInt() != 0 } }
        val auditOk = unzeroed == 0 && receipts.all { it.succeeded }
        receipts.add(
            StageReceipt(
                stage = WipeStage.FINAL_RESIDUE_AUDIT,
                itemsPurged = 0,
                succeeded = auditOk,
                failureReason = if (auditOk) null else "RESIDUE_OR_STAGE_FAILURE(unzeroed=$unzeroed)",
            ),
        )
        return ShutdownCertificate(
            cleanSealed = auditOk,
            orderedReceipts = receipts,
            totalPurgedArtifacts = receipts.sumOf { it.itemsPurged },
            unzeroedKeyBytesRemaining = unzeroed,
        )
    }
}

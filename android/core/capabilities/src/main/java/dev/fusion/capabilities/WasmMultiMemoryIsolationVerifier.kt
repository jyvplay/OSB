// SPDX-License-Identifier: Apache-2.0
package dev.fusion.capabilities

/**
 * Static **WebAssembly 3.0 Multi-Memory Capability Isolation Verifier** auditing compiled Wasm
 * modules to verify which linear memory indices (`memIdx in 0..7`) each guest function touches
 * via `0x40`-flagged load/store, `memory.size`, `memory.grow`, `memory.init`, `memory.fill`, or
 * cross-memory `0xFC 0x0A memory.copy dstMem srcMem`!
 */
object WasmMultiMemoryIsolationVerifier {
    const val SCOPE =
        "Wasm 3.0 Multi-Memory static capability isolator auditing touched memory indices and forbidding unauthorized cross-memory copies."

    data class MultiMemoryAudit(
        val declaredMemoryCount: Int,
        val totalMaxPages: Int,
        val touchedMemoryIndices: Set<Int>,
        val crossMemoryEdges: Set<Pair<Int, Int>>, // (srcMem -> dstMem)
        val isolationCompliant: Boolean,
        val violationReason: String?,
    )

    fun auditPolicy(
        declaredMemoryInitialPages: List<Int>,
        declaredMemoryMaxPages: List<Int?>,
        touchedMemories: Set<Int>,
        copyEdgesSrcToDst: Set<Pair<Int, Int>>,
        privilegedVaultMemoryIndices: Set<Int>,
        maxAggregatePagesBudget: Int = 64,
    ): MultiMemoryAudit {
        val count = declaredMemoryInitialPages.size
        if (count !in 1..WasmSandbox.MAX_MEMORIES_PER_MODULE || declaredMemoryMaxPages.size != count) {
            return MultiMemoryAudit(count, 0, touchedMemories, copyEdgesSrcToDst, false, "Invalid memory count $count")
        }
        val totalPages = declaredMemoryInitialPages.mapIndexed { idx, initP ->
            maxOf(initP, declaredMemoryMaxPages[idx] ?: initP)
        }.sum()
        if (totalPages > maxAggregatePagesBudget) {
            return MultiMemoryAudit(count, totalPages, touchedMemories, copyEdgesSrcToDst, false, "Total pages $totalPages > budget $maxAggregatePagesBudget")
        }
        if (touchedMemories.any { it !in 0 until count }) {
            return MultiMemoryAudit(count, totalPages, touchedMemories, copyEdgesSrcToDst, false, "Out-of-bounds memory index referenced")
        }
        val forbiddenTouch = touchedMemories intersect privilegedVaultMemoryIndices
        if (forbiddenTouch.isNotEmpty()) {
            return MultiMemoryAudit(count, totalPages, touchedMemories, copyEdgesSrcToDst, false, "Untrusted guest accessed privileged memory $forbiddenTouch")
        }
        val exfilEdge = copyEdgesSrcToDst.firstOrNull { (src, dst) -> src in privilegedVaultMemoryIndices && dst !in privilegedVaultMemoryIndices }
        if (exfilEdge != null) {
            return MultiMemoryAudit(count, totalPages, touchedMemories, copyEdgesSrcToDst, false, "Blocked privileged->public memory.copy $exfilEdge")
        }
        return MultiMemoryAudit(count, totalPages, touchedMemories, copyEdgesSrcToDst, true, null)
    }
}

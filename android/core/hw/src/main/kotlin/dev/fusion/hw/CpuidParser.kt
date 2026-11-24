// SPDX-License-Identifier: Apache-2.0
package dev.fusion.hw

/**
 * Bounded CPUID leaf parser. Decodes the leaf/sub-leaf register layout
 * returned by a CPUID invocation: EAX, EBX, ECX, EDX for a given leaf and
 * sub-leaf. Extracts the vendor string (leaf 0), processor brand string
 * (leaves 0x80000002–0x80000004), and selected feature flags from leaf 1
 * and extended leaf 0x80000001.
 *
 * Declared narrowings: at most [MAX_LEAVES] leaves; at most [MAX_SUB_LEAVES]
 * sub-leaves per leaf; vendor and brand strings are ASCII-only and capped
 * at [MAX_STRING_LEN] chars; feature flags are reported by bit index only —
 * no semantic interpretation (SSE, AVX, VMX, etc.) is assigned here. The
 * parser does not issue the CPUID instruction; raw register values are
 * supplied by the caller (the kernel/ABI layer performs the actual call).
 */
class CpuidParser private constructor(
    private val leaves: LinkedHashMap<Int, LinkedHashMap<Int, CpuidLeaf>>
) {
    companion object {
        const val MAX_LEAVES = 64
        const val MAX_SUB_LEAVES = 16
        const val MAX_STRING_LEN = 64

        data class CpuidLeaf(val eax: Int, val ebx: Int, val ecx: Int, val edx: Int)

        fun empty(): CpuidParser = CpuidParser(LinkedHashMap())
    }

    fun record(leaf: Int, subLeaf: Int, eax: Int, ebx: Int, ecx: Int, edx: Int) {
        require(leaves.size < MAX_LEAVES || leaves.containsKey(leaf)) { "too many leaves" }
        val sub = leaves.getOrPut(leaf) { LinkedHashMap() }
        require(sub.size < MAX_SUB_LEAVES || sub.containsKey(subLeaf)) { "too many sub-leaves for leaf $leaf" }
        sub[subLeaf] = CpuidLeaf(eax, ebx, ecx, edx)
    }

    fun vendorString(): String? {
        val leaf0 = leaves[0] ?: return null
        val ebx = leaf0[0]?.ebx ?: return null
        val edx = leaf0[0]?.edx ?: return null
        val ecx = leaf0[0]?.ecx ?: return null
        return asciiString(intToBytes(ebx) + intToBytes(edx) + intToBytes(ecx))
    }

    fun brandString(): String? {
        val parts = mutableListOf<String>()
        for (leaf in listOf(0x80000002, 0x80000003, 0x80000004)) {
            val sub = leaves[leaf] ?: return if (parts.isNotEmpty()) parts.joinToString("") else null
            val regs = sub[0] ?: return if (parts.isNotEmpty()) parts.joinToString("") else null
            parts.add(asciiString(intToBytes(regs.eax) + intToBytes(regs.ebx) + intToBytes(regs.ecx) + intToBytes(regs.edx)))
        }
        return parts.joinToString("").trim { it <= ' ' }
    }

    fun featureFlag(leaf: Int, subLeaf: Int, bit: Int): Boolean {
        val sub = leaves[leaf] ?: return false
        val regs = sub[subLeaf] ?: return false
        val word = when (bit) {
            0..31 -> regs.eax
            32..63 -> regs.ebx
            64..95 -> regs.ecx
            96..127 -> regs.edx
            else -> return false
        }
        return (word shr (bit % 32)) and 1 == 1
    }

    fun maxLeaf(): Int? = leaves.keys.maxOrNull()
    fun leafCount(): Int = leaves.size

    private fun intToBytes(v: Int): ByteArray =
        byteArrayOf((v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte(), ((v shr 16) and 0xFF).toByte(), ((v shr 24) and 0xFF).toByte())

    private fun asciiString(bytes: ByteArray): String {
        val sb = StringBuilder()
        for (b in bytes) {
            val c = (b.toInt() and 0xFF).toChar()
            if (c in 0x20..0x7E) sb.append(c) else break
        }
        return sb.toString()
    }
}

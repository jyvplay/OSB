// SPDX-License-Identifier: Apache-2.0
package dev.fusion.nativefmt

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Clean-room **ELF64 Binary Header & `PT_LOAD` 16 KB Page-Alignment Compliance Auditor**.
 *
 * Android 15, 16, and 17 (`API 35–37`) mandate that every ARM64 (`EM_AARCH64 = 183`) and x86_64
 * (`EM_X86_64 = 62`) `.so` shared library have all `PT_LOAD` (`p_type == 1`) segments aligned to
 * **`p_align >= 16384` (`0x4000`)** AND **`(p_vaddr - p_offset) % 16384 == 0`** (otherwise Package
 * Manager rejects the APK on 16 KB kernel devices with `INSTALL_FAILED_INVALID_APK`)!
 * Also detects dangerous `PF_W | PF_X` (`W^X` violation) executable+writable load segments!
 */
object Elf16KbPageAudit {
    const val SCOPE =
        "Clean-room ELF64 little-endian header & PT_LOAD segment auditor enforcing Android 15-17 16 KB (0x4000) page alignment and W^X (no PF_W|PF_X) compliance."

    const val PAGE_16KB = 16_384L
    const val PT_LOAD = 1
    const val EM_AARCH64 = 183
    const val EM_X86_64 = 62
    private const val PF_X = 0x1
    private const val PF_W = 0x2

    data class LoadSegmentReport(
        val index: Int,
        val pOffset: Long,
        val pVaddr: Long,
        val pAlign: Long,
        val flags: Int,
        val aligned16Kb: Boolean,
        val congruent16Kb: Boolean,
        val violatesWx: Boolean,
    )

    data class AuditResult(
        val machine: Int,
        val loadSegments: List<LoadSegmentReport>,
        val compliant16KbAndWx: Boolean,
        val violations: List<String>,
    )

    fun auditElf64(bytes: ByteArray): AuditResult {
        require(bytes.size >= 64) { "ELF64 header requires >= 64 bytes, got ${bytes.size}" }
        require(
            bytes[0] == 0x7F.toByte() && bytes[1] == 'E'.code.toByte() &&
                bytes[2] == 'L'.code.toByte() && bytes[3] == 'F'.code.toByte(),
        ) { "invalid ELF magic (expected \\x7FELF)" }
        require(bytes[4].toInt() == 2) { "expected 64-bit ELFCLASS64 (2), got ${bytes[4].toInt()}" }
        require(bytes[5].toInt() == 1) { "expected little-endian ELFDATA2LSB (1), got ${bytes[5].toInt()}" }

        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val machine = buf.getShort(18).toInt() and 0xFFFF
        val phOff = buf.getLong(32)
        val phEntSize = buf.getShort(54).toInt() and 0xFFFF
        val phNum = buf.getShort(56).toInt() and 0xFFFF
        require(phEntSize >= 56 && phNum in 1..256 && phOff >= 64L && phOff + phNum.toLong() * phEntSize <= bytes.size) {
            "invalid or truncated ELF64 program header table"
        }

        val loads = ArrayList<LoadSegmentReport>()
        val violations = ArrayList<String>()
        for (i in 0 until phNum) {
            val base = (phOff + i.toLong() * phEntSize).toInt()
            val pType = buf.getInt(base)
            if (pType != PT_LOAD) continue
            val pFlags = buf.getInt(base + 4)
            val pOffset = buf.getLong(base + 8)
            val pVaddr = buf.getLong(base + 16)
            val pAlign = buf.getLong(base + 48)

            val aligned = pAlign >= PAGE_16KB && (pAlign and (pAlign - 1L)) == 0L
            val congruent = Math.floorMod(pVaddr - pOffset, PAGE_16KB) == 0L
            val wx = (pFlags and (PF_W or PF_X)) == (PF_W or PF_X)
            if (!aligned) violations.add("PT_LOAD[$i] p_align=$pAlign < 16384 or non-power-of-two")
            if (!congruent) violations.add("PT_LOAD[$i] (p_vaddr - p_offset) mod 16384 != 0")
            if (wx) violations.add("PT_LOAD[$i] violates W^X (both PF_W and PF_X set)")
            loads.add(LoadSegmentReport(i, pOffset, pVaddr, pAlign, pFlags, aligned, congruent, wx))
        }
        if (loads.isEmpty()) violations.add("ELF64 contains zero PT_LOAD segments")
        return AuditResult(machine, loads, violations.isEmpty(), violations)
    }

    /** Synthesizes a minimal valid ELF64 header + `PT_LOAD` table for deterministic contract testing. */
    fun buildMinimalElf64ForTest(machine: Int = EM_AARCH64, pAlign: Long = PAGE_16KB, pOffset: Long = 0L, pVaddr: Long = 0L, pFlags: Int = 0x5): ByteArray {
        val out = ByteArray(64 + 56)
        val b = ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN)
        out[0] = 0x7F; out[1] = 'E'.code.toByte(); out[2] = 'L'.code.toByte(); out[3] = 'F'.code.toByte()
        out[4] = 2; out[5] = 1; out[6] = 1
        b.putShort(16, 3) // ET_DYN (.so)
        b.putShort(18, machine.toShort())
        b.putInt(20, 1)
        b.putLong(32, 64L) // e_phoff
        b.putShort(52, 64) // e_ehsize
        b.putShort(54, 56) // e_phentsize
        b.putShort(56, 1)  // e_phnum
        // PT_LOAD at offset 64:
        b.putInt(64, PT_LOAD)
        b.putInt(68, pFlags)
        b.putLong(72, pOffset)
        b.putLong(80, pVaddr)
        b.putLong(88, pVaddr)
        b.putLong(96, 120L)
        b.putLong(104, 120L)
        b.putLong(112, pAlign)
        return out
    }
}

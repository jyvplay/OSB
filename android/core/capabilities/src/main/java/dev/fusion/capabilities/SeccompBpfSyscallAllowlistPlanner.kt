// SPDX-License-Identifier: Apache-2.0
package dev.fusion.capabilities

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Linux `seccomp-bpf` classic `sock_filter` (`8-byte {code, jt, jf, k}`) program synthesizer &
 * deterministic VM simulator enforcing `AUDIT_ARCH_AARCH64` (`0xC00000B7`) + strict syscall
 * allowlist (`SECCOMP_RET_ALLOW` vs `SECCOMP_RET_KILL_PROCESS` / `SECCOMP_RET_ERRNO`) for helper processes.
 */
object SeccompBpfSyscallAllowlistPlanner {
    const val SCOPE =
        "Linux seccomp-bpf sock_filter 8-byte instruction compiler and simulator enforcing AUDIT_ARCH_AARCH64 syscall allowlists."

    const val AUDIT_ARCH_AARCH64 = 0xC00000B7.toInt()
    const val SECCOMP_RET_KILL_PROCESS = 0x80000000.toInt()
    const val SECCOMP_RET_ERRNO_EPERM = 0x00050001
    const val SECCOMP_RET_ALLOW = 0x7FFF0000

    data class SockFilterInsn(val code: Int, val jt: Int, val jf: Int, val k: Int)

    fun compileAarch64Allowlist(allowedSyscallNumbers: Set<Int>, killOnArchMismatch: Boolean = true): List<SockFilterInsn> {
        require(allowedSyscallNumbers.isNotEmpty())
        val sorted = allowedSyscallNumbers.sorted()
        val prog = ArrayList<SockFilterInsn>()
        // 0: LD [4] (offsetof(seccomp_data, arch))
        prog.add(SockFilterInsn(0x20, 0, 0, 4))
        // 1: JEQ AUDIT_ARCH_AARCH64 -> +1, else -> RET KILL
        prog.add(SockFilterInsn(0x15, 1, 0, AUDIT_ARCH_AARCH64))
        prog.add(SockFilterInsn(0x06, 0, 0, if (killOnArchMismatch) SECCOMP_RET_KILL_PROCESS else SECCOMP_RET_ERRNO_EPERM))
        // 3: LD [0] (offsetof(seccomp_data, nr))
        prog.add(SockFilterInsn(0x20, 0, 0, 0))
        val n = sorted.size
        for ((i, nr) in sorted.withIndex()) {
            val distToAllow = (n - i) // allow sits right after deny!
            prog.add(SockFilterInsn(0x15, distToAllow, 0, nr))
        }
        // Default deny: ERRNO(EPERM)
        prog.add(SockFilterInsn(0x06, 0, 0, SECCOMP_RET_ERRNO_EPERM))
        // Allow return
        prog.add(SockFilterInsn(0x06, 0, 0, SECCOMP_RET_ALLOW))
        return prog
    }

    fun encodeBinaryProgram(insns: List<SockFilterInsn>): ByteArray {
        val buf = ByteBuffer.allocate(insns.size * 8).order(ByteOrder.LITTLE_ENDIAN)
        for (ins in insns) {
            buf.putShort(ins.code.toShort())
            buf.put(ins.jt.toByte())
            buf.put(ins.jf.toByte())
            buf.putInt(ins.k)
        }
        return buf.array()
    }

    fun simulate(insns: List<SockFilterInsn>, arch: Int, syscallNr: Int): Int {
        var pc = 0
        var acc = 0
        while (pc in insns.indices) {
            val ins = insns[pc]
            when (ins.code) {
                0x20 -> { acc = if (ins.k == 4) arch else if (ins.k == 0) syscallNr else 0; pc++ }
                0x15 -> pc += 1 + (if (acc == ins.k) ins.jt else ins.jf)
                0x06 -> return ins.k
                else -> error("Unsupported BPF opcode 0x${ins.code.toString(16)}")
            }
        }
        return SECCOMP_RET_KILL_PROCESS
    }
}

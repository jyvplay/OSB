// SPDX-License-Identifier: Apache-2.0
package dev.fusion.capabilities

/**
 * Static binary bytecode verifier and assembler helper for **W3C WebAssembly 3.0 Tail-Call**
 * opcodes (`0x12` `return_call <funcidx>` and `0x13` `return_call_indirect <typeidx> <tableidx>`).
 *
 * Verifies section structure and enforces the Wasm 3.0 tail-call typing rule: every `return_call`
 * target function and every `return_call_indirect` expected signature MUST have the exact same
 * return-type vector as the enclosing caller function.
 */
object WasmTailCallVerifier {
    const val SCOPE =
        "W3C WebAssembly 3.0 tail-call static module verifier & bytecode builder for 0x12 return_call and 0x13 return_call_indirect."

    data class TailCallAudit(
        val valid: Boolean,
        val returnCallCount: Int,
        val returnCallIndirectCount: Int,
        val issues: List<String>,
    )

    fun audit(wasmBytes: ByteArray): TailCallAudit {
        val mod = runCatching { WasmSandbox.decode(wasmBytes) }.getOrElse {
            return TailCallAudit(false, 0, 0, listOf("DECODE_ERROR:${it.message}"))
        }
        var rcCount = 0
        var rciCount = 0
        val issues = ArrayList<String>()

        mod.funcs.forEachIndexed { fnIdx, fn ->
            val callerResults = mod.types[fn.typeIdx].resultTypes
            val code = fn.code
            var pc = 0
            while (pc < code.size) {
                val op = code[pc].toInt() and 0xFF
                pc++
                when (op) {
                    0x02, 0x03, 0x04 -> pc++
                    0x0C, 0x0D, 0x10, 0x20, 0x21, 0x22, 0x23, 0x24, 0x25, 0x26, 0xD2 -> pc = skipU32Leb(code, pc)
                    0x11, 0x28, 0x2A, 0x2B, 0x2D, 0x36, 0x38, 0x39, 0x3A -> pc = skipU32Leb(code, skipU32Leb(code, pc))
                    0x12 -> {
                        val (calleeIdx, next) = readU32Leb(code, pc); pc = next
                        rcCount++
                        if (calleeIdx !in mod.funcs.indices) {
                            issues.add("FUNC_$fnIdx:INVALID_RETURN_CALL_TARGET_$calleeIdx")
                        } else {
                            val calleeResults = mod.types[mod.funcs[calleeIdx].typeIdx].resultTypes
                            if (calleeResults != callerResults) {
                                issues.add("FUNC_$fnIdx:RETURN_CALL_RESULT_MISMATCH_${callerResults}_VS_${calleeResults}")
                            }
                        }
                    }
                    0x13 -> {
                        val (typeIdx, n1) = readU32Leb(code, pc)
                        val (tblIdx, n2) = readU32Leb(code, n1); pc = n2
                        rciCount++
                        if (tblIdx != 0) issues.add("FUNC_$fnIdx:UNSUPPORTED_TABLE_$tblIdx")
                        if (typeIdx !in mod.types.indices) {
                            issues.add("FUNC_$fnIdx:INVALID_TYPE_$typeIdx")
                        } else if (mod.types[typeIdx].resultTypes != callerResults) {
                            issues.add("FUNC_$fnIdx:RETURN_CALL_INDIRECT_RESULT_MISMATCH")
                        }
                    }
                    0x3F, 0x40, 0xD0 -> pc++
                    0x41 -> pc = skipS32Leb(code, pc)
                    0x43 -> pc += 4
                    0x44 -> pc += 8
                    0xFC -> {
                        val (sub, n1) = readU32Leb(code, pc)
                        pc = when (sub) {
                            0, 1, 2, 3 -> n1
                            8 -> skipU32Leb(code, n1) + 1
                            9, 13, 15, 16, 17 -> skipU32Leb(code, n1)
                            10 -> n1 + 2
                            11 -> n1 + 1
                            12, 14 -> skipU32Leb(code, skipU32Leb(code, n1))
                            else -> n1
                        }
                    }
                    0xFD -> {
                        val (simd, n1) = readU32Leb(code, pc)
                        pc = when (simd) {
                            0, 9, 11 -> skipU32Leb(code, skipU32Leb(code, n1))
                            12, 13 -> n1 + 16
                            21, 23, 27, 28, 31, 32 -> n1 + 1
                            else -> n1
                        }
                    }
                }
            }
        }
        return TailCallAudit(issues.isEmpty(), rcCount, rciCount, issues)
    }

    /**
     * Builds a real Wasm 3.0 binary exporting `sum_tail(n, acc)` using opcode `0x12` (`return_call 0`):
     * computes `acc + (1 + 2 + ... + n)` in `O(1)` call stack frames even for `n = 100_000` with `maxCallDepth = 2`!
     */
    fun buildTailRecursiveSumModule(): ByteArray {
        // func 0: (param i32 i32) (result i32)
        // local.get 0; i32.eqz; if (result i32) local.get 1 else local.get 0; i32.const 1; i32.sub; local.get 1; local.get 0; i32.add; return_call 0 end; end
        val body = byteArrayOf(
            0x00, // 0 extra locals
            0x20, 0x00, // local.get 0 (n)
            0x45,       // i32.eqz
            0x04, 0x7F, // if (result i32)
            0x20, 0x01, //   local.get 1 (acc)
            0x05,       // else
            0x20, 0x00, //   local.get 0 (n)
            0x41, 0x01, //   i32.const 1
            0x6B,       //   i32.sub (n - 1)
            0x20, 0x01, //   local.get 1 (acc)
            0x20, 0x00, //   local.get 0 (n)
            0x6A,       //   i32.add (acc + n)
            0x12, 0x00, //   return_call 0  <-- Wasm 3.0 tail call!
            0x0B,       // end if
            0x0B,       // end func
        )
        val typeSec = byteArrayOf(0x01, 0x60, 0x02, 0x7F, 0x7F, 0x01, 0x7F)
        val funcSec = byteArrayOf(0x01, 0x00)
        val exportName = "sum_tail".toByteArray(Charsets.UTF_8)
        val exportSec = byteArrayOf(0x01, exportName.size.toByte()) + exportName + byteArrayOf(0x00, 0x00)
        val codeSec = byteArrayOf(0x01, body.size.toByte()) + body

        fun sec(id: Int, payload: ByteArray): ByteArray = byteArrayOf(id.toByte(), payload.size.toByte()) + payload
        return byteArrayOf(0x00, 0x61, 0x73, 0x6D, 0x01, 0x00, 0x00, 0x00) +
            sec(1, typeSec) + sec(3, funcSec) + sec(7, exportSec) + sec(10, codeSec)
    }

    private fun readU32Leb(src: ByteArray, start: Int): Pair<Int, Int> {
        var res = 0; var shift = 0; var p = start
        while (true) {
            val b = src[p++].toInt() and 0xFF
            res = res or ((b and 0x7F) shl shift)
            if ((b and 0x80) == 0) return res to p
            shift += 7
        }
    }

    private fun skipU32Leb(src: ByteArray, start: Int): Int = readU32Leb(src, start).second
    private fun skipS32Leb(src: ByteArray, start: Int): Int {
        var p = start
        while ((src[p++].toInt() and 0x80) != 0) {}
        return p
    }
}

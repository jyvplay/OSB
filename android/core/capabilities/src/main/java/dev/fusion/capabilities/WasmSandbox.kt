// SPDX-License-Identifier: Apache-2.0
package dev.fusion.capabilities

/**
 * Clean-room pure-JVM WebAssembly 1.0 (`\0asm\x01\x00\x00\x00`, W3C WebAssembly Core Spec)
 * deterministic, gas-metered, memory-bounded plugin sandbox.
 *
 * Architectural rationale (inspired by `Chicory 1.7.5` / Bytecode Alliance `Endive`, `Apache-2.0`):
 * - Zero JNI / zero `.so` native libraries -> inherently immune to Android 15/16 16 KB ELF page
 *   alignment rejections (`INSTALL_FAILED_INVALID_APK`) and W^X JIT SELinux denials.
 * - Zero ambient host authority (WASI/filesystem/socket imports refused unless explicitly bound,
 *   and this pure kernel admits zero host imports by default).
 * - Deterministic `fuelBudget` gas metering halts infinite loops (`loop br 0 end`) after exact
 *   instruction steps; `maxMemoryPages` (`64 KiB` per page) + explicit bounds checks trap OOB
 *   linear-memory accesses cleanly without crashing the JVM.
 */
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sqrt
import kotlin.math.truncate
import kotlin.math.withSign

object WasmSandbox {
    const val SCOPE =
        "Pure-JVM W3C WebAssembly 1.0 + Wasm 2.0 deterministic i32/f32/f64, reference-table & 0xFD v128 128-bit SIMD interpreter (Sections 1-12 incl. Table/Global/Element/Data/DataCount active+passive, LEB128, structured block/loop/if/br/br_if/call/call_indirect/return, table.get/table.set/table.size/table.grow/table.fill/table.copy/elem.drop, bounds-checked linear memory + 0xFC memory.init/data.drop/memory.copy/memory.fill + 0xFD v128.load/store/const/shuffle/splat/extract/replace/bitwise/popcnt/i32x4/i16x8-dot/f32x4 + i32.extend8_s/extend16_s + f32/f64 IEEE-754 arithmetic/conversions/saturating trunc, deterministic fuel gas meter); host imports and threads refused."

    const val PAGE_BYTES = 65_536
    const val MAX_MODULE_BYTES = 1_048_576
    const val HARD_MAX_PAGES = 16 // 1 MiB cap per sandbox instance
    const val MAX_MEMORIES_PER_MODULE = 8
    const val HARD_MAX_CALL_DEPTH = 64
    const val HARD_MAX_TABLE_ELEMENTS = 4_096

    private const val TYPE_I32 = 0x7F
    private const val TYPE_F32 = 0x7D
    private const val TYPE_F64 = 0x7C
    const val TYPE_V128 = 0x7B
    private const val REFTYPE_FUNCREF = 0x70
    private const val BLOCK_VOID = 0x40

    enum class TrapKind {
        OUT_OF_FUEL,
        OUT_OF_BOUNDS_MEMORY,
        INTEGER_DIVIDE_BY_ZERO,
        INTEGER_OVERFLOW,
        CALL_STACK_EXHAUSTED,
        UNREACHABLE,
        UNDEFINED_ELEMENT,
        INDIRECT_CALL_TYPE_MISMATCH,
        INVALID_CONVERSION_TO_INTEGER,
    }

    class WasmTrapException(val kind: TrapKind, message: String) : RuntimeException("$kind: $message")

    data class FuncType(
        val paramCount: Int,
        val resultCount: Int,
        val paramTypes: List<Int> = List(paramCount) { TYPE_I32 },
        val resultTypes: List<Int> = List(resultCount) { TYPE_I32 },
    )
    data class FuncBody(val typeIdx: Int, val localCount: Int, val code: ByteArray)
    data class GlobalDecl(val valType: Int, val mutable: Boolean, val initBits: Int, val initBits64: Long = initBits.toLong())
    data class MemoryDecl(val minPages: Int, val maxPages: Int?)
    data class DataSegment(val isPassive: Boolean, val activeOffset: Int, val bytes: ByteArray, val memoryIndex: Int = 0)
    data class ElementSegment(val offset: Int, val funcIndices: IntArray, val isPassive: Boolean = false)
    data class ExecutionResult(
        val returnValues: List<Int>,
        val fuelUsed: Long,
        val fuelRemaining: Long,
    )

    data class V128(val lo: Long, val hi: Long) {
        fun toBytes(): ByteArray {
            val b = ByteArray(16)
            for (i in 0..7) b[i] = ((lo ushr (i * 8)) and 0xFFL).toByte()
            for (i in 0..7) b[8 + i] = ((hi ushr (i * 8)) and 0xFFL).toByte()
            return b
        }

        fun laneI8(idx: Int): Int {
            require(idx in 0..15)
            val raw = if (idx < 8) ((lo ushr (idx * 8)) and 0xFFL).toInt() else ((hi ushr ((idx - 8) * 8)) and 0xFFL).toInt()
            return raw.toByte().toInt()
        }

        fun laneU8(idx: Int): Int = laneI8(idx) and 0xFF

        fun laneI16(idx: Int): Int {
            require(idx in 0..7)
            val raw = if (idx < 4) ((lo ushr (idx * 16)) and 0xFFFFL).toInt() else ((hi ushr ((idx - 4) * 16)) and 0xFFFFL).toInt()
            return raw.toShort().toInt()
        }

        fun laneI32(idx: Int): Int {
            require(idx in 0..3)
            return when (idx) {
                0 -> lo.toInt()
                1 -> (lo ushr 32).toInt()
                2 -> hi.toInt()
                else -> (hi ushr 32).toInt()
            }
        }

        fun lanesI32(): List<Int> = listOf(laneI32(0), laneI32(1), laneI32(2), laneI32(3))

        companion object {
            val ZERO = V128(0L, 0L)

            fun fromBytes(b: ByteArray, offset: Int = 0): V128 {
                var l = 0L
                var h = 0L
                for (i in 0..7) l = l or ((b[offset + i].toLong() and 0xFFL) shl (i * 8))
                for (i in 0..7) h = h or ((b[offset + 8 + i].toLong() and 0xFFL) shl (i * 8))
                return V128(l, h)
            }

            fun fromI32x4(l0: Int, l1: Int, l2: Int, l3: Int): V128 {
                val lo = (l0.toLong() and 0xFFFF_FFFFL) or ((l1.toLong() and 0xFFFF_FFFFL) shl 32)
                val hi = (l2.toLong() and 0xFFFF_FFFFL) or ((l3.toLong() and 0xFFFF_FFFFL) shl 32)
                return V128(lo, hi)
            }
        }
    }

    data class V128ExecutionResult(
        val value: V128,
        val fuelUsed: Long,
        val fuelRemaining: Long,
    )

    class Module internal constructor(
        val types: List<FuncType>,
        val funcs: List<FuncBody>,
        val initialPages: Int,
        val declaredMaxPages: Int?,
        val exports: Map<String, Pair<Int, Int>>, // name -> (kind: 0=func, 1=table, 2=mem, 3=global, index)
        val tableInitialSize: Int = 0,
        val tableMaxSize: Int? = null,
        val globals: List<GlobalDecl> = emptyList(),
        val elements: List<ElementSegment> = emptyList(),
        val dataSegments: List<DataSegment> = emptyList(),
        val memoryDecls: List<MemoryDecl> = listOf(MemoryDecl(initialPages, declaredMaxPages)),
    ) {
        fun instantiate(maxMemoryPages: Int = 4): Instance {
            require(maxMemoryPages in 1..HARD_MAX_PAGES) {
                "maxMemoryPages $maxMemoryPages outside 1..$HARD_MAX_PAGES"
            }
            for ((idx, m) in memoryDecls.withIndex()) {
                require(m.minPages <= maxMemoryPages) {
                    "module memory[$idx] initial (${m.minPages} pages) exceeds sandbox ceiling ($maxMemoryPages pages)"
                }
            }
            val effectiveMax = declaredMaxPages?.coerceAtMost(maxMemoryPages) ?: maxMemoryPages
            return Instance(this, initialPages, effectiveMax, maxMemoryPages)
        }
    }

    class Instance internal constructor(
        private val module: Module,
        initialPages: Int,
        private val maxPages: Int,
        private val sandboxMaxPages: Int = maxPages,
    ) {
        private val memories: Array<ByteArray> = if (module.memoryDecls.isEmpty()) {
            arrayOf(ByteArray(initialPages * PAGE_BYTES))
        } else {
            Array(module.memoryDecls.size) { idx ->
                val pages = if (idx == 0) initialPages else module.memoryDecls[idx].minPages
                ByteArray(pages * PAGE_BYTES)
            }
        }
        private val memoryMaxPages: IntArray = if (module.memoryDecls.isEmpty()) {
            intArrayOf(maxPages)
        } else {
            IntArray(module.memoryDecls.size) { idx ->
                if (idx == 0) maxPages else (module.memoryDecls[idx].maxPages?.coerceAtMost(sandboxMaxPages) ?: sandboxMaxPages)
            }
        }
        private var memory: ByteArray
            get() = memories[0]
            set(value) { memories[0] = value }
        private val globals: LongArray = LongArray(module.globals.size) { module.globals[it].initBits64 }
        private var table: IntArray = IntArray(module.tableInitialSize) { -1 }
        private val tableMax: Int = (module.tableMaxSize ?: HARD_MAX_TABLE_ELEMENTS).coerceAtMost(HARD_MAX_TABLE_ELEMENTS)
        private val passiveElems: Array<IntArray?> = Array(module.elements.size) { i ->
            if (module.elements[i].isPassive) module.elements[i].funcIndices.copyOf() else null
        }
        private val passiveData: Array<ByteArray?> = Array(module.dataSegments.size) { i ->
            if (module.dataSegments[i].isPassive) module.dataSegments[i].bytes.copyOf() else null
        }

        init {
            for (el in module.elements) {
                if (!el.isPassive) {
                    if (el.offset < 0 || el.offset.toLong() + el.funcIndices.size > table.size) {
                        throw WasmTrapException(TrapKind.UNDEFINED_ELEMENT, "element segment [${el.offset}..${el.offset + el.funcIndices.size}) > table(${table.size})")
                    }
                    for (i in el.funcIndices.indices) table[el.offset + i] = el.funcIndices[i]
                }
            }
            for (seg in module.dataSegments) {
                if (!seg.isPassive) {
                    require(seg.memoryIndex in memories.indices) { "active data segment memory index ${seg.memoryIndex} out of range" }
                    val targetMem = memories[seg.memoryIndex]
                    if (seg.activeOffset < 0 || seg.activeOffset.toLong() + seg.bytes.size > targetMem.size) {
                        throw WasmTrapException(TrapKind.OUT_OF_BOUNDS_MEMORY, "active data segment [${seg.activeOffset}..${seg.activeOffset + seg.bytes.size}) > memory[${seg.memoryIndex}](${targetMem.size})")
                    }
                    if (seg.bytes.isNotEmpty()) System.arraycopy(seg.bytes, 0, targetMem, seg.activeOffset, seg.bytes.size)
                }
            }
        }

        fun readGlobalBits(exportName: String): Int {
            val exp = module.exports[exportName] ?: throw IllegalArgumentException("unknown export '$exportName'")
            require(exp.first == 3) { "export '$exportName' is not a global (kind=${exp.first})" }
            return globals[exp.second].toInt()
        }

        fun readGlobalF64(exportName: String): Double {
            val exp = module.exports[exportName] ?: throw IllegalArgumentException("unknown export '$exportName'")
            require(exp.first == 3) { "export '$exportName' is not a global (kind=${exp.first})" }
            return Double.fromBits(globals[exp.second])
        }

        fun tableSize(): Int = table.size

        fun readTableSlot(slot: Int): Int {
            require(slot in table.indices) { "table slot $slot outside 0..${table.lastIndex}" }
            return table[slot]
        }

        fun invokeF32(
            exportName: String,
            args: List<Float> = emptyList(),
            fuelBudget: Long = 50_000L,
            maxCallDepth: Int = 32,
        ): Float {
            val res = invoke(exportName, args.map { it.toRawBits() }, fuelBudget, maxCallDepth)
            require(res.returnValues.size == 1) { "expected 1 f32 return value from '$exportName'" }
            return Float.fromBits(res.returnValues[0])
        }

        fun invokeF64(
            exportName: String,
            args: List<Double> = emptyList(),
            fuelBudget: Long = 50_000L,
            maxCallDepth: Int = 32,
        ): Double {
            require(fuelBudget in 1L..5_000_000L) { "fuelBudget $fuelBudget outside 1..5_000_000" }
            require(maxCallDepth in 1..HARD_MAX_CALL_DEPTH) { "maxCallDepth $maxCallDepth outside 1..$HARD_MAX_CALL_DEPTH" }
            val exp = module.exports[exportName] ?: throw IllegalArgumentException("unknown export '$exportName'")
            require(exp.first == 0) { "export '$exportName' is not a function (kind=${exp.first})" }
            val fn = module.funcs[exp.second]
            val ft = module.types[fn.typeIdx]
            require(args.size == ft.paramCount) { "export '$exportName' expects ${ft.paramCount} f64 args, got ${args.size}" }
            val state = ExecState(fuelBudget, maxCallDepth)
            val res = execFunc(exp.second, LongArray(args.size) { args[it].toRawBits() }, 0, state)
            require(res.size == 1) { "expected 1 f64 return value from '$exportName'" }
            return Double.fromBits(res[0])
        }

        fun memoryCount(): Int = memories.size

        fun memoryPages(memIdx: Int = 0): Int {
            require(memIdx in memories.indices) { "memory index $memIdx outside 0..${memories.lastIndex}" }
            return memories[memIdx].size / PAGE_BYTES
        }

        fun writeMemory(offset: Int, bytes: ByteArray, memIdx: Int = 0) {
            require(memIdx in memories.indices) { "memory index $memIdx outside 0..${memories.lastIndex}" }
            val m = memories[memIdx]
            if (offset < 0 || offset.toLong() + bytes.size > m.size) {
                throw WasmTrapException(TrapKind.OUT_OF_BOUNDS_MEMORY, "host write [$offset..${offset + bytes.size}) > ${m.size}")
            }
            System.arraycopy(bytes, 0, m, offset, bytes.size)
        }

        fun readMemory(offset: Int, length: Int, memIdx: Int = 0): ByteArray {
            require(memIdx in memories.indices) { "memory index $memIdx outside 0..${memories.lastIndex}" }
            require(length >= 0) { "negative read length" }
            val m = memories[memIdx]
            if (offset < 0 || offset.toLong() + length > m.size) {
                throw WasmTrapException(TrapKind.OUT_OF_BOUNDS_MEMORY, "host read [$offset..${offset + length}) > ${m.size}")
            }
            return m.copyOfRange(offset, offset + length)
        }

        fun invoke(
            exportName: String,
            args: List<Int> = emptyList(),
            fuelBudget: Long = 50_000L,
            maxCallDepth: Int = 32,
        ): ExecutionResult {
            require(fuelBudget in 1L..5_000_000L) { "fuelBudget $fuelBudget outside 1..5_000_000" }
            require(maxCallDepth in 1..HARD_MAX_CALL_DEPTH) { "maxCallDepth $maxCallDepth outside 1..$HARD_MAX_CALL_DEPTH" }
            val exp = module.exports[exportName]
                ?: throw IllegalArgumentException("unknown export '$exportName'")
            require(exp.first == 0) { "export '$exportName' is not a function (kind=${exp.first})" }
            val fn = module.funcs[exp.second]
            val ft = module.types[fn.typeIdx]
            require(args.size == ft.paramCount) {
                "export '$exportName' expects ${ft.paramCount} i32 args, got ${args.size}"
            }
            val state = ExecState(fuelBudget, maxCallDepth)
            val res = execFunc(exp.second, LongArray(args.size) { args[it].toLong() }, 0, state)
            return ExecutionResult(res.map { it.toInt() }, fuelBudget - state.fuel, state.fuel)
        }

        private class ExecState(var fuel: Long, val maxCallDepth: Int) {
            val v128Pool = ArrayList<V128>().apply { add(V128.ZERO) } // handle 0L == zero-initialized v128 local!
            fun allocV128(v: V128): Long {
                if (v.lo == 0L && v.hi == 0L) return 0L
                val id = v128Pool.size
                v128Pool.add(v)
                return id.toLong()
            }
            fun readV128(handle: Long): V128 = v128Pool.getOrElse(handle.toInt()) { V128.ZERO }
            fun charge(cost: Long = 1L) {
                if (fuel < cost) throw WasmTrapException(TrapKind.OUT_OF_FUEL, "fuel budget exhausted")
                fuel -= cost
            }
        }

        fun invokeV128(
            exportName: String,
            i32Args: List<Int> = emptyList(),
            v128Args: List<V128> = emptyList(),
            fuelBudget: Long = 50_000L,
            maxCallDepth: Int = 32,
        ): V128ExecutionResult {
            require(fuelBudget in 1L..5_000_000L) { "fuelBudget $fuelBudget outside 1..5_000_000" }
            require(maxCallDepth in 1..HARD_MAX_CALL_DEPTH) { "maxCallDepth $maxCallDepth outside 1..$HARD_MAX_CALL_DEPTH" }
            val exp = module.exports[exportName]
                ?: throw IllegalArgumentException("unknown export '$exportName'")
            require(exp.first == 0) { "export '$exportName' is not a function" }
            val fn = module.funcs[exp.second]
            val ft = module.types[fn.typeIdx]
            require(i32Args.size + v128Args.size == ft.paramCount) {
                "export '$exportName' expects ${ft.paramCount} args"
            }
            val state = ExecState(fuelBudget, maxCallDepth)
            val packedArgs = LongArray(ft.paramCount)
            var iIdx = 0
            var vIdx = 0
            for (pIdx in 0 until ft.paramCount) {
                packedArgs[pIdx] = if (ft.paramTypes[pIdx] == TYPE_V128) {
                    state.allocV128(v128Args[vIdx++])
                } else {
                    i32Args[iIdx++].toLong()
                }
            }
            val res = execFunc(exp.second, packedArgs, 0, state)
            val outV = if (res.isNotEmpty()) state.readV128(res[0]) else V128.ZERO
            return V128ExecutionResult(outV, fuelBudget - state.fuel, state.fuel)
        }

        private data class CtrlFrame(
            val opcode: Int, // 0x02=block, 0x03=loop, 0x04=if
            val startPc: Int, // for loop, branch target is startPc (right after loop header)
            val elsePc: Int,
            val endPc: Int, // pc of matching 0x0B `end`
            val arity: Int, // 0 or 1
            val stackBase: Int,
        )

        private fun execFunc(funcIdx: Int, args: LongArray, depth: Int, state: ExecState): LongArray {
            if (depth >= state.maxCallDepth) {
                throw WasmTrapException(TrapKind.CALL_STACK_EXHAUSTED, "call depth $depth >= ${state.maxCallDepth}")
            }
            var curFuncIdx = funcIdx
            var curArgs = args
            tailLoop@ while (true) {
            val fn = module.funcs[curFuncIdx]
            val ft = module.types[fn.typeIdx]
            val locals = LongArray(ft.paramCount + fn.localCount)
            System.arraycopy(curArgs, 0, locals, 0, curArgs.size)

            val code = fn.code
            val blockMap = precomputeStructuredBlocks(code)
            val valStack = LongArray(1024)
            var sp = 0
            val ctrlStack = ArrayList<CtrlFrame>()

            fun push64(v: Long) {
                require(sp < valStack.size) { "Wasm operand stack overflow" }
                valStack[sp++] = v
            }

            fun pop64(): Long {
                require(sp > 0) { "Wasm operand stack underflow" }
                return valStack[--sp]
            }

            fun push(v: Int) = push64(v.toLong())
            fun pop(): Int = pop64().toInt()
            fun pushF64(d: Double) = push64(d.toRawBits())
            fun popF64(): Double = Double.fromBits(pop64())
            fun pushV128(v: V128) = push64(state.allocV128(v))
            fun popV128(): V128 = state.readV128(pop64())

            // Implicit function-level block frame
            ctrlStack.add(
                CtrlFrame(
                    opcode = 0x02,
                    startPc = 0,
                    elsePc = -1,
                    endPc = code.lastIndex,
                    arity = ft.resultCount,
                    stackBase = 0,
                ),
            )

            var pc = 0
            fun branchTo(labelDepth: Int): Int {
                require(labelDepth in 0 until ctrlStack.size) { "invalid branch depth $labelDepth" }
                val targetIdx = ctrlStack.lastIndex - labelDepth
                val frame = ctrlStack[targetIdx]
                if (frame.opcode == 0x03) {
                    // Loop branch jumps back to start of loop body (0-arity continuation in MVP for void/i32 loops)
                    sp = frame.stackBase
                    while (ctrlStack.lastIndex > targetIdx) ctrlStack.removeAt(ctrlStack.lastIndex)
                    return frame.startPc
                } else {
                    val carried = if (frame.arity == 1) pop64() else 0L
                    sp = frame.stackBase
                    if (frame.arity == 1) push64(carried)
                    while (ctrlStack.lastIndex >= targetIdx) ctrlStack.removeAt(ctrlStack.lastIndex)
                    return frame.endPc + 1
                }
            }

            while (pc < code.size && ctrlStack.isNotEmpty()) {
                state.charge(1L)
                val op = code[pc].toInt() and 0xFF
                pc++
                when (op) {
                    0x00 -> throw WasmTrapException(TrapKind.UNREACHABLE, "unreachable instruction at pc=${pc - 1}")
                    0x01 -> {} // nop
                    0x02, 0x03 -> { // block, loop
                        val bt = code[pc].toInt() and 0xFF; pc++
                        val arity = blockArity(bt)
                        val info = blockMap[pc - 2] ?: throw IllegalArgumentException("unmapped block at ${pc - 2}")
                        ctrlStack.add(CtrlFrame(op, pc, -1, info.second, arity, sp))
                    }
                    0x04 -> { // if
                        val bt = code[pc].toInt() and 0xFF; pc++
                        val arity = blockArity(bt)
                        val info = blockMap[pc - 2] ?: throw IllegalArgumentException("unmapped if at ${pc - 2}")
                        val cond = pop()
                        ctrlStack.add(CtrlFrame(0x04, pc, info.first, info.second, arity, sp))
                        if (cond == 0) {
                            pc = if (info.first >= 0) info.first + 1 else info.second + 1
                            if (info.first < 0) ctrlStack.removeAt(ctrlStack.lastIndex)
                        }
                    }
                    0x05 -> { // else (reached from end of true-branch: skip to end)
                        val top = ctrlStack.removeAt(ctrlStack.lastIndex)
                        pc = top.endPc + 1
                    }
                    0x0B -> { // end
                        val top = ctrlStack.removeAt(ctrlStack.lastIndex)
                        val carried = if (top.arity == 1 && sp > top.stackBase) pop64() else 0L
                        sp = top.stackBase
                        if (top.arity == 1) push64(carried)
                    }
                    0x0C -> { // br
                        val (lbl, next) = readU32Leb(code, pc); pc = next
                        pc = branchTo(lbl)
                    }
                    0x0D -> { // br_if
                        val (lbl, next) = readU32Leb(code, pc); pc = next
                        val c = pop()
                        if (c != 0) pc = branchTo(lbl)
                    }
                    0x0F -> { // return
                        return if (ft.resultCount == 1) longArrayOf(pop64()) else LongArray(0)
                    }
                    0x10 -> { // call
                        val (calleeIdx, next) = readU32Leb(code, pc); pc = next
                        require(calleeIdx in module.funcs.indices) { "invalid call index $calleeIdx" }
                        val cft = module.types[module.funcs[calleeIdx].typeIdx]
                        val callArgs = LongArray(cft.paramCount)
                        for (i in cft.paramCount - 1 downTo 0) callArgs[i] = pop64()
                        val ret = execFunc(calleeIdx, callArgs, depth + 1, state)
                        for (rv in ret) push64(rv)
                    }
                    0x11 -> { // call_indirect (typeIdx, tableIdx)
                        val (expectedTypeIdx, n1) = readU32Leb(code, pc)
                        val (tblIdx, n2) = readU32Leb(code, n1); pc = n2
                        require(tblIdx == 0) { "only table 0 supported in call_indirect" }
                        require(expectedTypeIdx in module.types.indices) { "invalid call_indirect type index $expectedTypeIdx" }
                        val elemIdx = pop()
                        if (elemIdx !in table.indices) {
                            throw WasmTrapException(TrapKind.UNDEFINED_ELEMENT, "call_indirect index $elemIdx outside table(${table.size})")
                        }
                        val calleeIdx = table[elemIdx]
                        if (calleeIdx < 0) {
                            throw WasmTrapException(TrapKind.UNDEFINED_ELEMENT, "uninitialized table slot $elemIdx")
                        }
                        val expectedFt = module.types[expectedTypeIdx]
                        val actualFt = module.types[module.funcs[calleeIdx].typeIdx]
                        if (expectedFt != actualFt) {
                            throw WasmTrapException(
                                TrapKind.INDIRECT_CALL_TYPE_MISMATCH,
                                "call_indirect expected $expectedFt but slot $elemIdx has $actualFt",
                            )
                        }
                        val callArgs = LongArray(expectedFt.paramCount)
                        for (i in expectedFt.paramCount - 1 downTo 0) callArgs[i] = pop64()
                        val ret = execFunc(calleeIdx, callArgs, depth + 1, state)
                        for (rv in ret) push64(rv)
                    }
                    0x12 -> { // return_call <funcIdx> (Wasm 3.0 O(1) stack-frame-reusing tail call!)
                        val (calleeIdx, next) = readU32Leb(code, pc); pc = next
                        require(calleeIdx in module.funcs.indices) { "invalid return_call index $calleeIdx" }
                        val cft = module.types[module.funcs[calleeIdx].typeIdx]
                        require(cft.resultTypes == ft.resultTypes) {
                            "return_call result signature mismatch: caller=${ft.resultTypes} callee=${cft.resultTypes}"
                        }
                        val callArgs = LongArray(cft.paramCount)
                        for (i in cft.paramCount - 1 downTo 0) callArgs[i] = pop64()
                        curFuncIdx = calleeIdx
                        curArgs = callArgs
                        continue@tailLoop
                    }
                    0x13 -> { // return_call_indirect <typeIdx> <tableIdx> (Wasm 3.0 O(1) indirect tail call!)
                        val (expectedTypeIdx, n1) = readU32Leb(code, pc)
                        val (tblIdx, n2) = readU32Leb(code, n1); pc = n2
                        require(tblIdx == 0) { "only table 0 supported in return_call_indirect" }
                        require(expectedTypeIdx in module.types.indices) { "invalid return_call_indirect type index $expectedTypeIdx" }
                        val elemIdx = pop()
                        if (elemIdx !in table.indices) {
                            throw WasmTrapException(TrapKind.UNDEFINED_ELEMENT, "return_call_indirect index $elemIdx outside table(${table.size})")
                        }
                        val calleeIdx = table[elemIdx]
                        if (calleeIdx < 0) {
                            throw WasmTrapException(TrapKind.UNDEFINED_ELEMENT, "uninitialized table slot $elemIdx")
                        }
                        val expectedFt = module.types[expectedTypeIdx]
                        val actualFt = module.types[module.funcs[calleeIdx].typeIdx]
                        if (expectedFt != actualFt) {
                            throw WasmTrapException(
                                TrapKind.INDIRECT_CALL_TYPE_MISMATCH,
                                "return_call_indirect expected $expectedFt but slot $elemIdx has $actualFt",
                            )
                        }
                        require(expectedFt.resultTypes == ft.resultTypes) {
                            "return_call_indirect result signature mismatch: caller=${ft.resultTypes} callee=${expectedFt.resultTypes}"
                        }
                        val callArgs = LongArray(expectedFt.paramCount)
                        for (i in expectedFt.paramCount - 1 downTo 0) callArgs[i] = pop64()
                        curFuncIdx = calleeIdx
                        curArgs = callArgs
                        continue@tailLoop
                    }
                    0x1A -> { pop64() } // drop
                    0x1B -> { // select
                        val c = pop(); val v2 = pop64(); val v1 = pop64()
                        push64(if (c != 0) v1 else v2)
                    }
                    0x20 -> { // local.get
                        val (idx, next) = readU32Leb(code, pc); pc = next
                        push64(locals[idx])
                    }
                    0x21 -> { // local.set
                        val (idx, next) = readU32Leb(code, pc); pc = next
                        locals[idx] = pop64()
                    }
                    0x22 -> { // local.tee
                        val (idx, next) = readU32Leb(code, pc); pc = next
                        val v = pop64(); locals[idx] = v; push64(v)
                    }
                    0x23 -> { // global.get
                        val (idx, next) = readU32Leb(code, pc); pc = next
                        require(idx in globals.indices) { "invalid global.get index $idx" }
                        push64(globals[idx])
                    }
                    0x24 -> { // global.set
                        val (idx, next) = readU32Leb(code, pc); pc = next
                        require(idx in globals.indices && module.globals[idx].mutable) { "cannot mutate const or unknown global $idx" }
                        globals[idx] = pop64()
                    }
                    0x25 -> { // table.get (Wasm 2.0 reference-types)
                        val (tblIdx, next) = readU32Leb(code, pc); pc = next
                        require(tblIdx == 0) { "only table 0 supported in table.get" }
                        val slot = pop()
                        if (slot !in table.indices) {
                            throw WasmTrapException(TrapKind.UNDEFINED_ELEMENT, "table.get($slot) outside table(${table.size})")
                        }
                        push(table[slot])
                    }
                    0x26 -> { // table.set (Wasm 2.0 reference-types)
                        val (tblIdx, next) = readU32Leb(code, pc); pc = next
                        require(tblIdx == 0) { "only table 0 supported in table.set" }
                        val refVal = pop()
                        val slot = pop()
                        if (slot !in table.indices) {
                            throw WasmTrapException(TrapKind.UNDEFINED_ELEMENT, "table.set($slot) outside table(${table.size})")
                        }
                        require(refVal == -1 || refVal in module.funcs.indices) { "invalid funcref $refVal in table.set" }
                        table[slot] = refVal
                    }
                    0x28, 0x2A -> { // i32.load / f32.load (Wasm 3.0 multi-memory 0x40 memarg supported!)
                        val (alignFlags, n1) = readU32Leb(code, pc)
                        val (memIdx, nMem) = if ((alignFlags and 0x40) != 0) readU32Leb(code, n1) else 0 to n1
                        val (offset, n2) = readU32Leb(code, nMem); pc = n2
                        require(memIdx in memories.indices) { "invalid memory index $memIdx" }
                        val targetMem = memories[memIdx]
                        val base = pop()
                        val ea = (base.toLong() and 0xFFFF_FFFFL) + (offset.toLong() and 0xFFFF_FFFFL)
                        if (ea < 0L || ea + 4L > targetMem.size) {
                            throw WasmTrapException(TrapKind.OUT_OF_BOUNDS_MEMORY, "i32.load at $ea (size=${targetMem.size})")
                        }
                        val p = ea.toInt()
                        val v = (targetMem[p].toInt() and 0xFF) or
                            ((targetMem[p + 1].toInt() and 0xFF) shl 8) or
                            ((targetMem[p + 2].toInt() and 0xFF) shl 16) or
                            ((targetMem[p + 3].toInt() and 0xFF) shl 24)
                        push(v)
                    }
                    0x2D -> { // i32.load8_u
                        val (alignFlags, n1) = readU32Leb(code, pc)
                        val (memIdx, nMem) = if ((alignFlags and 0x40) != 0) readU32Leb(code, n1) else 0 to n1
                        val (offset, n2) = readU32Leb(code, nMem); pc = n2
                        require(memIdx in memories.indices) { "invalid memory index $memIdx" }
                        val targetMem = memories[memIdx]
                        val base = pop()
                        val ea = (base.toLong() and 0xFFFF_FFFFL) + (offset.toLong() and 0xFFFF_FFFFL)
                        if (ea < 0L || ea + 1L > targetMem.size) {
                            throw WasmTrapException(TrapKind.OUT_OF_BOUNDS_MEMORY, "i32.load8_u at $ea")
                        }
                        push(targetMem[ea.toInt()].toInt() and 0xFF)
                    }
                    0x36, 0x38 -> { // i32.store / f32.store (Wasm 3.0 multi-memory 0x40 memarg supported!)
                        val (alignFlags, n1) = readU32Leb(code, pc)
                        val (memIdx, nMem) = if ((alignFlags and 0x40) != 0) readU32Leb(code, n1) else 0 to n1
                        val (offset, n2) = readU32Leb(code, nMem); pc = n2
                        require(memIdx in memories.indices) { "invalid memory index $memIdx" }
                        val targetMem = memories[memIdx]
                        val v = pop(); val base = pop()
                        val ea = (base.toLong() and 0xFFFF_FFFFL) + (offset.toLong() and 0xFFFF_FFFFL)
                        if (ea < 0L || ea + 4L > targetMem.size) {
                            throw WasmTrapException(TrapKind.OUT_OF_BOUNDS_MEMORY, "i32.store at $ea (size=${targetMem.size})")
                        }
                        val p = ea.toInt()
                        targetMem[p] = (v and 0xFF).toByte()
                        targetMem[p + 1] = ((v ushr 8) and 0xFF).toByte()
                        targetMem[p + 2] = ((v ushr 16) and 0xFF).toByte()
                        targetMem[p + 3] = ((v ushr 24) and 0xFF).toByte()
                    }
                    0x2B -> { // f64.load (8-byte little-endian)
                        val (alignFlags, n1) = readU32Leb(code, pc)
                        val (memIdx, nMem) = if ((alignFlags and 0x40) != 0) readU32Leb(code, n1) else 0 to n1
                        val (offset, n2) = readU32Leb(code, nMem); pc = n2
                        require(memIdx in memories.indices) { "invalid memory index $memIdx" }
                        val targetMem = memories[memIdx]
                        val base = pop()
                        val ea = (base.toLong() and 0xFFFF_FFFFL) + (offset.toLong() and 0xFFFF_FFFFL)
                        if (ea < 0L || ea + 8L > targetMem.size) {
                            throw WasmTrapException(TrapKind.OUT_OF_BOUNDS_MEMORY, "f64.load at $ea (size=${targetMem.size})")
                        }
                        val p = ea.toInt()
                        var bits = 0L
                        for (bIdx in 0..7) bits = bits or ((targetMem[p + bIdx].toLong() and 0xFFL) shl (bIdx * 8))
                        push64(bits)
                    }
                    0x39 -> { // f64.store (8-byte little-endian)
                        val (alignFlags, n1) = readU32Leb(code, pc)
                        val (memIdx, nMem) = if ((alignFlags and 0x40) != 0) readU32Leb(code, n1) else 0 to n1
                        val (offset, n2) = readU32Leb(code, nMem); pc = n2
                        require(memIdx in memories.indices) { "invalid memory index $memIdx" }
                        val targetMem = memories[memIdx]
                        val bits = pop64(); val base = pop()
                        val ea = (base.toLong() and 0xFFFF_FFFFL) + (offset.toLong() and 0xFFFF_FFFFL)
                        if (ea < 0L || ea + 8L > targetMem.size) {
                            throw WasmTrapException(TrapKind.OUT_OF_BOUNDS_MEMORY, "f64.store at $ea (size=${targetMem.size})")
                        }
                        val p = ea.toInt()
                        for (bIdx in 0..7) targetMem[p + bIdx] = ((bits ushr (bIdx * 8)) and 0xFFL).toByte()
                    }
                    0x3A -> { // i32.store8
                        val (alignFlags, n1) = readU32Leb(code, pc)
                        val (memIdx, nMem) = if ((alignFlags and 0x40) != 0) readU32Leb(code, n1) else 0 to n1
                        val (offset, n2) = readU32Leb(code, nMem); pc = n2
                        require(memIdx in memories.indices) { "invalid memory index $memIdx" }
                        val targetMem = memories[memIdx]
                        val v = pop(); val base = pop()
                        val ea = (base.toLong() and 0xFFFF_FFFFL) + (offset.toLong() and 0xFFFF_FFFFL)
                        if (ea < 0L || ea + 1L > targetMem.size) {
                            throw WasmTrapException(TrapKind.OUT_OF_BOUNDS_MEMORY, "i32.store8 at $ea")
                        }
                        targetMem[ea.toInt()] = (v and 0xFF).toByte()
                    }
                    0x3F -> { // memory.size <memIdx>
                        val (memIdx, nextPc) = readU32Leb(code, pc); pc = nextPc
                        require(memIdx in memories.indices) { "invalid memory.size index $memIdx" }
                        push(memories[memIdx].size / PAGE_BYTES)
                    }
                    0x40 -> { // memory.grow <memIdx>
                        val (memIdx, nextPc) = readU32Leb(code, pc); pc = nextPc
                        require(memIdx in memories.indices) { "invalid memory.grow index $memIdx" }
                        val delta = pop()
                        val curPages = memories[memIdx].size / PAGE_BYTES
                        if (delta < 0 || curPages.toLong() + delta > memoryMaxPages[memIdx]) {
                            push(-1)
                        } else {
                            if (delta > 0) memories[memIdx] = memories[memIdx].copyOf((curPages + delta) * PAGE_BYTES)
                            push(curPages)
                        }
                    }
                    0x41 -> { // i32.const
                        val (v, next) = readS32Leb(code, pc); pc = next
                        push(v)
                    }
                    0x43 -> { // f32.const (4 little-endian IEEE-754 bytes)
                        require(pc + 4 <= code.size) { "truncated f32.const" }
                        val bits = (code[pc].toInt() and 0xFF) or
                            ((code[pc + 1].toInt() and 0xFF) shl 8) or
                            ((code[pc + 2].toInt() and 0xFF) shl 16) or
                            ((code[pc + 3].toInt() and 0xFF) shl 24)
                        pc += 4
                        push(bits)
                    }
                    0x44 -> { // f64.const (8 little-endian IEEE-754 bytes)
                        require(pc + 8 <= code.size) { "truncated f64.const" }
                        var bits = 0L
                        for (bIdx in 0..7) bits = bits or ((code[pc + bIdx].toLong() and 0xFFL) shl (bIdx * 8))
                        pc += 8
                        push64(bits)
                    }
                    0xD0 -> { // ref.null (reftype)
                        val rt = code[pc++].toInt() and 0xFF
                        require(rt == REFTYPE_FUNCREF) { "only funcref 0x70 supported in ref.null" }
                        push(-1)
                    }
                    0xD2 -> { // ref.func (funcIdx)
                        val (fnIdx, next) = readU32Leb(code, pc); pc = next
                        require(fnIdx in module.funcs.indices) { "invalid ref.func index $fnIdx" }
                        push(fnIdx)
                    }
                    0x45 -> push(if (pop() == 0) 1 else 0) // i32.eqz
                    0x46 -> { val b = pop(); val a = pop(); push(if (a == b) 1 else 0) } // eq
                    0x47 -> { val b = pop(); val a = pop(); push(if (a != b) 1 else 0) } // ne
                    0x48 -> { val b = pop(); val a = pop(); push(if (a < b) 1 else 0) } // lt_s
                    0x49 -> { val b = pop(); val a = pop(); push(if (Integer.compareUnsigned(a, b) < 0) 1 else 0) } // lt_u
                    0x4A -> { val b = pop(); val a = pop(); push(if (a > b) 1 else 0) } // gt_s
                    0x4B -> { val b = pop(); val a = pop(); push(if (Integer.compareUnsigned(a, b) > 0) 1 else 0) } // gt_u
                    0x4C -> { val b = pop(); val a = pop(); push(if (a <= b) 1 else 0) } // le_s
                    0x4D -> { val b = pop(); val a = pop(); push(if (Integer.compareUnsigned(a, b) <= 0) 1 else 0) } // le_u
                    0x4E -> { val b = pop(); val a = pop(); push(if (a >= b) 1 else 0) } // ge_s
                    0x4F -> { val b = pop(); val a = pop(); push(if (Integer.compareUnsigned(a, b) >= 0) 1 else 0) } // ge_u
                    0x6A -> { val b = pop(); val a = pop(); push(a + b) } // add
                    0x6B -> { val b = pop(); val a = pop(); push(a - b) } // sub
                    0x6C -> { val b = pop(); val a = pop(); push(a * b) } // mul
                    0x6D -> { // div_s
                        val b = pop(); val a = pop()
                        if (b == 0) throw WasmTrapException(TrapKind.INTEGER_DIVIDE_BY_ZERO, "i32.div_s by 0")
                        if (a == Int.MIN_VALUE && b == -1) throw WasmTrapException(TrapKind.INTEGER_OVERFLOW, "INT_MIN / -1")
                        push(a / b)
                    }
                    0x6E -> { // div_u
                        val b = pop(); val a = pop()
                        if (b == 0) throw WasmTrapException(TrapKind.INTEGER_DIVIDE_BY_ZERO, "i32.div_u by 0")
                        push(Integer.divideUnsigned(a, b))
                    }
                    0x6F -> { // rem_s
                        val b = pop(); val a = pop()
                        if (b == 0) throw WasmTrapException(TrapKind.INTEGER_DIVIDE_BY_ZERO, "i32.rem_s by 0")
                        push(if (a == Int.MIN_VALUE && b == -1) 0 else a % b)
                    }
                    0x70 -> { // rem_u
                        val b = pop(); val a = pop()
                        if (b == 0) throw WasmTrapException(TrapKind.INTEGER_DIVIDE_BY_ZERO, "i32.rem_u by 0")
                        push(Integer.remainderUnsigned(a, b))
                    }
                    0x71 -> { val b = pop(); val a = pop(); push(a and b) } // and
                    0x72 -> { val b = pop(); val a = pop(); push(a or b) } // or
                    0x73 -> { val b = pop(); val a = pop(); push(a xor b) } // xor
                    0x74 -> { val b = pop(); val a = pop(); push(a shl (b and 31)) } // shl
                    0x75 -> { val b = pop(); val a = pop(); push(a shr (b and 31)) } // shr_s
                    0x76 -> { val b = pop(); val a = pop(); push(a ushr (b and 31)) } // shr_u
                    0x77 -> { val b = pop(); val a = pop(); push(Integer.rotateLeft(a, b and 31)) } // rotl
                    0x78 -> { val b = pop(); val a = pop(); push(Integer.rotateRight(a, b and 31)) } // rotr
                    0xC0 -> push(pop().toByte().toInt()) // Wasm 2.0 i32.extend8_s
                    0xC1 -> push(pop().toShort().toInt()) // Wasm 2.0 i32.extend16_s
                    // IEEE-754 f32 comparisons, unary/binary arithmetic, conversions & reinterprets
                    0x5B -> { val b = Float.fromBits(pop()); val a = Float.fromBits(pop()); push(if (a == b) 1 else 0) } // f32.eq
                    0x5C -> { val b = Float.fromBits(pop()); val a = Float.fromBits(pop()); push(if (a != b) 1 else 0) } // f32.ne
                    0x5D -> { val b = Float.fromBits(pop()); val a = Float.fromBits(pop()); push(if (a < b) 1 else 0) } // f32.lt
                    0x5E -> { val b = Float.fromBits(pop()); val a = Float.fromBits(pop()); push(if (a > b) 1 else 0) } // f32.gt
                    0x5F -> { val b = Float.fromBits(pop()); val a = Float.fromBits(pop()); push(if (a <= b) 1 else 0) } // f32.le
                    0x60 -> { val b = Float.fromBits(pop()); val a = Float.fromBits(pop()); push(if (a >= b) 1 else 0) } // f32.ge
                    0x8B -> push(abs(Float.fromBits(pop())).toRawBits()) // f32.abs
                    0x8C -> push((-Float.fromBits(pop())).toRawBits()) // f32.neg
                    0x8D -> push(ceil(Float.fromBits(pop())).toRawBits()) // f32.ceil
                    0x8E -> push(floor(Float.fromBits(pop())).toRawBits()) // f32.floor
                    0x90 -> push(round(Float.fromBits(pop())).toRawBits()) // f32.nearest (IEEE-754 round-half-to-even!)
                    0x91 -> push(sqrt(Float.fromBits(pop())).toRawBits()) // f32.sqrt
                    0x92 -> { val b = Float.fromBits(pop()); val a = Float.fromBits(pop()); push((a + b).toRawBits()) } // f32.add
                    0x93 -> { val b = Float.fromBits(pop()); val a = Float.fromBits(pop()); push((a - b).toRawBits()) } // f32.sub
                    0x94 -> { val b = Float.fromBits(pop()); val a = Float.fromBits(pop()); push((a * b).toRawBits()) } // f32.mul
                    0x95 -> { val b = Float.fromBits(pop()); val a = Float.fromBits(pop()); push((a / b).toRawBits()) } // f32.div
                    0x96 -> { val b = Float.fromBits(pop()); val a = Float.fromBits(pop()); push(min(a, b).toRawBits()) } // f32.min
                    0x97 -> { val b = Float.fromBits(pop()); val a = Float.fromBits(pop()); push(max(a, b).toRawBits()) } // f32.max
                    0xA8 -> { // i32.trunc_f32_s (trapping)
                        val f = Float.fromBits(pop())
                        if (f.isNaN()) throw WasmTrapException(TrapKind.INVALID_CONVERSION_TO_INTEGER, "i32.trunc_f32_s(NaN)")
                        if (f >= 2147483648.0f || f < -2147483648.0f) {
                            throw WasmTrapException(TrapKind.INTEGER_OVERFLOW, "i32.trunc_f32_s($f) overflow")
                        }
                        push(f.toInt())
                    }
                    0xB2 -> push(pop().toFloat().toRawBits()) // f32.convert_i32_s
                    0xB3 -> push((pop().toLong() and 0xFFFF_FFFFL).toFloat().toRawBits()) // f32.convert_i32_u
                    0xBC, 0xBE -> {} // i32.reinterpret_f32 / f32.reinterpret_i32 (no-op on 32-bit raw-bits stack!)
                    // IEEE-754 f64 comparisons, unary/binary arithmetic, conversions
                    0x61 -> { val b = popF64(); val a = popF64(); push(if (a == b) 1 else 0) } // f64.eq
                    0x62 -> { val b = popF64(); val a = popF64(); push(if (a != b) 1 else 0) } // f64.ne
                    0x63 -> { val b = popF64(); val a = popF64(); push(if (a < b) 1 else 0) } // f64.lt
                    0x64 -> { val b = popF64(); val a = popF64(); push(if (a > b) 1 else 0) } // f64.gt
                    0x65 -> { val b = popF64(); val a = popF64(); push(if (a <= b) 1 else 0) } // f64.le
                    0x66 -> { val b = popF64(); val a = popF64(); push(if (a >= b) 1 else 0) } // f64.ge
                    0x99 -> pushF64(abs(popF64())) // f64.abs
                    0x9A -> pushF64(-popF64()) // f64.neg
                    0x9B -> pushF64(ceil(popF64())) // f64.ceil
                    0x9C -> pushF64(floor(popF64())) // f64.floor
                    0x9D -> pushF64(truncate(popF64())) // f64.trunc
                    0x9E -> pushF64(round(popF64())) // f64.nearest
                    0x9F -> pushF64(sqrt(popF64())) // f64.sqrt
                    0xA0 -> { val b = popF64(); val a = popF64(); pushF64(a + b) } // f64.add
                    0xA1 -> { val b = popF64(); val a = popF64(); pushF64(a - b) } // f64.sub
                    0xA2 -> { val b = popF64(); val a = popF64(); pushF64(a * b) } // f64.mul
                    0xA3 -> { val b = popF64(); val a = popF64(); pushF64(a / b) } // f64.div
                    0xA4 -> { val b = popF64(); val a = popF64(); pushF64(min(a, b)) } // f64.min
                    0xA5 -> { val b = popF64(); val a = popF64(); pushF64(max(a, b)) } // f64.max
                    0xA6 -> { val b = popF64(); val a = popF64(); pushF64(a.withSign(b)) } // f64.copysign
                    0xAA -> { // i32.trunc_f64_s
                        val d = popF64()
                        if (d.isNaN()) throw WasmTrapException(TrapKind.INVALID_CONVERSION_TO_INTEGER, "i32.trunc_f64_s(NaN)")
                        if (d >= 2147483648.0 || d <= -2147483649.0) {
                            throw WasmTrapException(TrapKind.INTEGER_OVERFLOW, "i32.trunc_f64_s($d) overflow")
                        }
                        push(d.toInt())
                    }
                    0xAB -> { // i32.trunc_f64_u
                        val d = popF64()
                        if (d.isNaN()) throw WasmTrapException(TrapKind.INVALID_CONVERSION_TO_INTEGER, "i32.trunc_f64_u(NaN)")
                        if (d >= 4294967296.0 || d <= -1.0) {
                            throw WasmTrapException(TrapKind.INTEGER_OVERFLOW, "i32.trunc_f64_u($d) overflow")
                        }
                        push(d.toLong().toInt())
                    }
                    0xB6 -> push(popF64().toFloat().toRawBits()) // f32.demote_f64
                    0xB7 -> pushF64(pop().toDouble()) // f64.convert_i32_s
                    0xB8 -> pushF64((pop().toLong() and 0xFFFF_FFFFL).toDouble()) // f64.convert_i32_u
                    0xBB -> pushF64(Float.fromBits(pop()).toDouble()) // f64.promote_f32
                    0xFC -> { // Wasm 2.0 0xFC saturating trunc + bulk-memory + table instructions
                        val (subOp, nextPc) = readU32Leb(code, pc); pc = nextPc
                        when (subOp) {
                            0 -> { // i32.trunc_sat_f32_s
                                val f = Float.fromBits(pop())
                                val v = when {
                                    f.isNaN() -> 0
                                    f >= 2147483648.0f -> Int.MAX_VALUE
                                    f <= -2147483648.0f -> Int.MIN_VALUE
                                    else -> f.toInt()
                                }
                                push(v)
                            }
                            1 -> { // i32.trunc_sat_f32_u
                                val f = Float.fromBits(pop())
                                val v = when {
                                    f.isNaN() || f <= 0.0f -> 0
                                    f >= 4294967296.0f -> -1 // 0xFFFF_FFFFu
                                    else -> f.toLong().toInt()
                                }
                                push(v)
                            }
                            2 -> { // i32.trunc_sat_f64_s
                                val d = popF64()
                                val v = when {
                                    d.isNaN() -> 0
                                    d >= 2147483647.0 -> Int.MAX_VALUE
                                    d <= -2147483648.0 -> Int.MIN_VALUE
                                    else -> d.toInt()
                                }
                                push(v)
                            }
                            3 -> { // i32.trunc_sat_f64_u
                                val d = popF64()
                                val v = when {
                                    d.isNaN() || d <= 0.0 -> 0
                                    d >= 4294967295.0 -> -1
                                    else -> d.toLong().toInt()
                                }
                                push(v)
                            }
                            8 -> { // 0x08 memory.init (dataIdx, memIdx)
                                val (dataIdx, n1) = readU32Leb(code, pc)
                                val (memIdx, n2) = readU32Leb(code, n1); pc = n2
                                require(dataIdx in passiveData.indices && memIdx in memories.indices) {
                                    "invalid memory.init indices (seg=$dataIdx, mem=$memIdx)"
                                }
                                val targetMem = memories[memIdx]
                                val len = pop()
                                val src = pop()
                                val dst = pop()
                                val segBytes = passiveData[dataIdx] ?: ByteArray(0)
                                if (len < 0 || src < 0 || dst < 0 ||
                                    src.toLong() + len > segBytes.size ||
                                    dst.toLong() + len > targetMem.size
                                ) {
                                    throw WasmTrapException(
                                        TrapKind.OUT_OF_BOUNDS_MEMORY,
                                        "memory.init(seg=$dataIdx, mem=$memIdx, dst=$dst, src=$src, len=$len) out of bounds",
                                    )
                                }
                                if (len > 0) System.arraycopy(segBytes, src, targetMem, dst, len)
                            }
                            9 -> { // 0x09 data.drop (dataIdx)
                                val (dataIdx, n1) = readU32Leb(code, pc); pc = n1
                                require(dataIdx in passiveData.indices) { "invalid data.drop segment index $dataIdx" }
                                passiveData[dataIdx] = null
                            }
                            10 -> { // 0x0A memory.copy (dstMemIdx, srcMemIdx — Wasm 3.0 multi-memory!)
                                val (dstMemIdx, n1) = readU32Leb(code, pc)
                                val (srcMemIdx, n2) = readU32Leb(code, n1); pc = n2
                                require(dstMemIdx in memories.indices && srcMemIdx in memories.indices) {
                                    "invalid memory.copy indices (dstMem=$dstMemIdx, srcMem=$srcMemIdx)"
                                }
                                val dstMem = memories[dstMemIdx]
                                val srcMem = memories[srcMemIdx]
                                val len = pop()
                                val src = pop()
                                val dst = pop()
                                if (len < 0 || src < 0 || dst < 0 ||
                                    src.toLong() + len > srcMem.size || dst.toLong() + len > dstMem.size
                                ) {
                                    throw WasmTrapException(
                                        TrapKind.OUT_OF_BOUNDS_MEMORY,
                                        "memory.copy(dstMem=$dstMemIdx@$dst, srcMem=$srcMemIdx@$src, len=$len) out of bounds",
                                    )
                                }
                                if (len > 0) System.arraycopy(srcMem, src, dstMem, dst, len) // overlap-safe memmove in JVM!
                            }
                            11 -> { // 0x0B memory.fill (memIdx)
                                val (memIdx, n1) = readU32Leb(code, pc); pc = n1
                                require(memIdx in memories.indices) { "invalid memory.fill index $memIdx" }
                                val targetMem = memories[memIdx]
                                val len = pop()
                                val byteVal = (pop() and 0xFF).toByte()
                                val dst = pop()
                                if (len < 0 || dst < 0 || dst.toLong() + len > targetMem.size) {
                                    throw WasmTrapException(
                                        TrapKind.OUT_OF_BOUNDS_MEMORY,
                                        "memory.fill(mem=$memIdx, dst=$dst, len=$len) out of bounds (${targetMem.size})",
                                    )
                                }
                                if (len > 0) targetMem.fill(byteVal, dst, dst + len)
                            }
                            12 -> { // 0x0C table.init (elemIdx, tableIdx=0)
                                val (elemIdx, n1) = readU32Leb(code, pc)
                                val (tblIdx, n2) = readU32Leb(code, n1); pc = n2
                                require(tblIdx == 0 && elemIdx in passiveElems.indices) { "invalid table.init indices" }
                                val n = pop(); val src = pop(); val dst = pop()
                                val seg = passiveElems[elemIdx] ?: IntArray(0)
                                if (n < 0 || src < 0 || dst < 0 || src.toLong() + n > seg.size || dst.toLong() + n > table.size) {
                                    throw WasmTrapException(TrapKind.UNDEFINED_ELEMENT, "table.init out of bounds")
                                }
                                if (n > 0) System.arraycopy(seg, src, table, dst, n)
                            }
                            13 -> { // 0x0D elem.drop (elemIdx)
                                val (elemIdx, n1) = readU32Leb(code, pc); pc = n1
                                require(elemIdx in passiveElems.indices) { "invalid elem.drop index $elemIdx" }
                                passiveElems[elemIdx] = null
                            }
                            14 -> { // 0x0E table.copy (dstTable=0, srcTable=0)
                                val (dstTbl, n1) = readU32Leb(code, pc)
                                val (srcTbl, n2) = readU32Leb(code, n1); pc = n2
                                require(dstTbl == 0 && srcTbl == 0) { "only table 0 supported in table.copy" }
                                val n = pop(); val src = pop(); val dst = pop()
                                if (n < 0 || src < 0 || dst < 0 || src.toLong() + n > table.size || dst.toLong() + n > table.size) {
                                    throw WasmTrapException(TrapKind.UNDEFINED_ELEMENT, "table.copy out of bounds")
                                }
                                if (n > 0) System.arraycopy(table, src, table, dst, n)
                            }
                            15 -> { // 0x0F table.grow (tableIdx=0)
                                val (tblIdx, n1) = readU32Leb(code, pc); pc = n1
                                require(tblIdx == 0) { "only table 0 supported in table.grow" }
                                val delta = pop()
                                val initRef = pop()
                                val oldSz = table.size
                                if (delta < 0 || oldSz.toLong() + delta > tableMax) {
                                    push(-1)
                                } else {
                                    if (delta > 0) {
                                        val grown = table.copyOf(oldSz + delta)
                                        for (i in oldSz until oldSz + delta) grown[i] = initRef
                                        table = grown
                                    }
                                    push(oldSz)
                                }
                            }
                            16 -> { // 0x10 table.size (tableIdx=0)
                                val (tblIdx, n1) = readU32Leb(code, pc); pc = n1
                                require(tblIdx == 0) { "only table 0 supported in table.size" }
                                push(table.size)
                            }
                            17 -> { // 0x11 table.fill (tableIdx=0)
                                val (tblIdx, n1) = readU32Leb(code, pc); pc = n1
                                require(tblIdx == 0) { "only table 0 supported in table.fill" }
                                val n = pop(); val refVal = pop(); val dst = pop()
                                if (n < 0 || dst < 0 || dst.toLong() + n > table.size) {
                                    throw WasmTrapException(TrapKind.UNDEFINED_ELEMENT, "table.fill out of bounds")
                                }
                                for (i in dst until dst + n) table[i] = refVal
                            }
                            else -> throw IllegalArgumentException("unsupported 0xFC Wasm sub-opcode $subOp")
                        }
                    }
                    0xFD -> { // Wasm 2.0 0xFD 128-bit packed SIMD instructions
                        val (simdOp, nextPc) = readU32Leb(code, pc); pc = nextPc
                        when (simdOp) {
                            0 -> { // v128.load (align:u32, offset:u32)
                                val (_, n1) = readU32Leb(code, pc)
                                val (offset, n2) = readU32Leb(code, n1); pc = n2
                                val base = pop()
                                val ea = (base.toLong() and 0xFFFF_FFFFL) + (offset.toLong() and 0xFFFF_FFFFL)
                                if (ea < 0L || ea + 16L > memory.size) {
                                    throw WasmTrapException(TrapKind.OUT_OF_BOUNDS_MEMORY, "v128.load at $ea (size=${memory.size})")
                                }
                                pushV128(V128.fromBytes(memory, ea.toInt()))
                            }
                            9 -> { // v128.load32_splat (align:u32, offset:u32)
                                val (_, n1) = readU32Leb(code, pc)
                                val (offset, n2) = readU32Leb(code, n1); pc = n2
                                val base = pop()
                                val ea = (base.toLong() and 0xFFFF_FFFFL) + (offset.toLong() and 0xFFFF_FFFFL)
                                if (ea < 0L || ea + 4L > memory.size) {
                                    throw WasmTrapException(TrapKind.OUT_OF_BOUNDS_MEMORY, "v128.load32_splat at $ea")
                                }
                                val p = ea.toInt()
                                val word = (memory[p].toInt() and 0xFF) or
                                    ((memory[p + 1].toInt() and 0xFF) shl 8) or
                                    ((memory[p + 2].toInt() and 0xFF) shl 16) or
                                    ((memory[p + 3].toInt() and 0xFF) shl 24)
                                pushV128(V128.fromI32x4(word, word, word, word))
                            }
                            11 -> { // v128.store (align:u32, offset:u32)
                                val (_, n1) = readU32Leb(code, pc)
                                val (offset, n2) = readU32Leb(code, n1); pc = n2
                                val v = popV128(); val base = pop()
                                val ea = (base.toLong() and 0xFFFF_FFFFL) + (offset.toLong() and 0xFFFF_FFFFL)
                                if (ea < 0L || ea + 16L > memory.size) {
                                    throw WasmTrapException(TrapKind.OUT_OF_BOUNDS_MEMORY, "v128.store at $ea (size=${memory.size})")
                                }
                                val bytes16 = v.toBytes()
                                System.arraycopy(bytes16, 0, memory, ea.toInt(), 16)
                            }
                            12 -> { // v128.const (16 immediate bytes)
                                require(pc + 16 <= code.size) { "truncated v128.const" }
                                val v = V128.fromBytes(code, pc)
                                pc += 16
                                pushV128(v)
                            }
                            13 -> { // i8x16.shuffle (16 lane indices 0..31)
                                require(pc + 16 <= code.size) { "truncated i8x16.shuffle" }
                                val b = popV128().toBytes()
                                val a = popV128().toBytes()
                                val out = ByteArray(16)
                                for (i in 0..15) {
                                    val lane = code[pc + i].toInt() and 0xFF
                                    require(lane in 0..31) { "i8x16.shuffle lane $lane outside 0..31" }
                                    out[i] = if (lane < 16) a[lane] else b[lane - 16]
                                }
                                pc += 16
                                pushV128(V128.fromBytes(out))
                            }
                            15 -> { // i8x16.splat
                                val b = (pop() and 0xFF).toByte()
                                pushV128(V128.fromBytes(ByteArray(16) { b }))
                            }
                            16 -> { // i16x8.splat
                                val s = pop() and 0xFFFF
                                val word = s or (s shl 16)
                                pushV128(V128.fromI32x4(word, word, word, word))
                            }
                            17 -> { // i32x4.splat
                                val x = pop()
                                pushV128(V128.fromI32x4(x, x, x, x))
                            }
                            27 -> { // i32x4.extract_lane
                                val lane = code[pc++].toInt() and 0xFF
                                require(lane in 0..3) { "i32x4.extract_lane $lane outside 0..3" }
                                push(popV128().laneI32(lane))
                            }
                            28 -> { // i32x4.replace_lane
                                val lane = code[pc++].toInt() and 0xFF
                                require(lane in 0..3) { "i32x4.replace_lane $lane outside 0..3" }
                                val repl = pop()
                                val v = popV128()
                                val l = IntArray(4) { v.laneI32(it) }
                                l[lane] = repl
                                pushV128(V128.fromI32x4(l[0], l[1], l[2], l[3]))
                            }
                            77 -> { // v128.not
                                val v = popV128()
                                pushV128(V128(v.lo.inv(), v.hi.inv()))
                            }
                            78 -> { val b = popV128(); val a = popV128(); pushV128(V128(a.lo and b.lo, a.hi and b.hi)) } // v128.and
                            79 -> { val b = popV128(); val a = popV128(); pushV128(V128(a.lo and b.lo.inv(), a.hi and b.hi.inv())) } // v128.andnot
                            80 -> { val b = popV128(); val a = popV128(); pushV128(V128(a.lo or b.lo, a.hi or b.hi)) } // v128.or
                            81 -> { val b = popV128(); val a = popV128(); pushV128(V128(a.lo xor b.lo, a.hi xor b.hi)) } // v128.xor
                            83 -> { val v = popV128(); push(if (v.lo != 0L || v.hi != 0L) 1 else 0) } // v128.any_true
                            98 -> { // i8x16.popcnt
                                val v = popV128()
                                val out = ByteArray(16) { idx -> Integer.bitCount(v.laneU8(idx)).toByte() }
                                pushV128(V128.fromBytes(out))
                            }
                            160 -> { // i32x4.abs
                                val v = popV128()
                                fun abs32(x: Int): Int = if (x == Int.MIN_VALUE) Int.MIN_VALUE else abs(x)
                                pushV128(V128.fromI32x4(abs32(v.laneI32(0)), abs32(v.laneI32(1)), abs32(v.laneI32(2)), abs32(v.laneI32(3))))
                            }
                            161 -> { // i32x4.neg
                                val v = popV128()
                                pushV128(V128.fromI32x4(-v.laneI32(0), -v.laneI32(1), -v.laneI32(2), -v.laneI32(3)))
                            }
                            174 -> { // i32x4.add
                                val b = popV128(); val a = popV128()
                                pushV128(V128.fromI32x4(a.laneI32(0) + b.laneI32(0), a.laneI32(1) + b.laneI32(1), a.laneI32(2) + b.laneI32(2), a.laneI32(3) + b.laneI32(3)))
                            }
                            177 -> { // i32x4.sub
                                val b = popV128(); val a = popV128()
                                pushV128(V128.fromI32x4(a.laneI32(0) - b.laneI32(0), a.laneI32(1) - b.laneI32(1), a.laneI32(2) - b.laneI32(2), a.laneI32(3) - b.laneI32(3)))
                            }
                            181 -> { // i32x4.mul
                                val b = popV128(); val a = popV128()
                                pushV128(V128.fromI32x4(a.laneI32(0) * b.laneI32(0), a.laneI32(1) * b.laneI32(1), a.laneI32(2) * b.laneI32(2), a.laneI32(3) * b.laneI32(3)))
                            }
                            186 -> { // i32x4.dot_i16x8_s (pairwise signed 16-bit multiply-accumulate into 4 i32 lanes!)
                                val b = popV128(); val a = popV128()
                                val l = IntArray(4) { k ->
                                    a.laneI16(2 * k) * b.laneI16(2 * k) + a.laneI16(2 * k + 1) * b.laneI16(2 * k + 1)
                                }
                                pushV128(V128.fromI32x4(l[0], l[1], l[2], l[3]))
                            }
                            228 -> { // f32x4.add
                                val b = popV128(); val a = popV128()
                                val l = IntArray(4) { k -> (Float.fromBits(a.laneI32(k)) + Float.fromBits(b.laneI32(k))).toRawBits() }
                                pushV128(V128.fromI32x4(l[0], l[1], l[2], l[3]))
                            }
                            230 -> { // f32x4.mul
                                val b = popV128(); val a = popV128()
                                val l = IntArray(4) { k -> (Float.fromBits(a.laneI32(k)) * Float.fromBits(b.laneI32(k))).toRawBits() }
                                pushV128(V128.fromI32x4(l[0], l[1], l[2], l[3]))
                            }
                            else -> throw IllegalArgumentException("unsupported 0xFD Wasm SIMD sub-opcode $simdOp")
                        }
                    }
                    else -> throw IllegalArgumentException("unsupported Wasm opcode 0x${op.toString(16)} at pc=${pc - 1}")
                }
            }
            return if (ft.resultCount == 1) longArrayOf(pop64()) else LongArray(0)
            }
        }
    }

    fun decode(bytes: ByteArray): Module {
        require(bytes.size in 8..MAX_MODULE_BYTES) { "Wasm binary size ${bytes.size} outside 8..$MAX_MODULE_BYTES" }
        require(
            bytes[0] == 0x00.toByte() && bytes[1] == 0x61.toByte() &&
                bytes[2] == 0x73.toByte() && bytes[3] == 0x6D.toByte(),
        ) { "invalid Wasm magic header (expected \\0asm)" }
        require(
            bytes[4] == 0x01.toByte() && bytes[5] == 0x00.toByte() &&
                bytes[6] == 0x00.toByte() && bytes[7] == 0x00.toByte(),
        ) { "unsupported Wasm binary version (expected v1)" }

        var pos = 8
        var lastSectionId = 0
        val types = ArrayList<FuncType>()
        val funcTypeIndices = ArrayList<Int>()
        var initialPages = 1
        var maxPages: Int? = null
        val memoryDecls = ArrayList<MemoryDecl>()
        var tableInitialSize = 0
        var tableMaxSize: Int? = null
        val globals = ArrayList<GlobalDecl>()
        val elements = ArrayList<ElementSegment>()
        val dataSegments = ArrayList<DataSegment>()
        var declaredDataCount: Int? = null
        val exports = LinkedHashMap<String, Pair<Int, Int>>()
        val bodies = ArrayList<Pair<Int, ByteArray>>()

        fun isSupportedValType(t: Int): Boolean =
            t == TYPE_I32 || t == TYPE_F32 || t == TYPE_F64 || t == TYPE_V128 || t == REFTYPE_FUNCREF

        fun readConstInitExpr64(src: ByteArray, startPos: Int): Pair<Long, Int> {
            val initOp = src[startPos].toInt() and 0xFF
            return when (initOp) {
                0x41 -> { // i32.const
                    val (v, n1) = readS32Leb(src, startPos + 1)
                    require((src[n1].toInt() and 0xFF) == 0x0B) { "const expr must terminate with 0x0B" }
                    v.toLong() to (n1 + 1)
                }
                0x43 -> { // f32.const
                    val p = startPos + 1
                    val bits = (src[p].toInt() and 0xFF) or
                        ((src[p + 1].toInt() and 0xFF) shl 8) or
                        ((src[p + 2].toInt() and 0xFF) shl 16) or
                        ((src[p + 3].toInt() and 0xFF) shl 24)
                    require((src[p + 4].toInt() and 0xFF) == 0x0B) { "const expr must terminate with 0x0B" }
                    bits.toLong() to (p + 5)
                }
                0x44 -> { // f64.const
                    val p = startPos + 1
                    var bits = 0L
                    for (bIdx in 0..7) bits = bits or ((src[p + bIdx].toLong() and 0xFFL) shl (bIdx * 8))
                    require((src[p + 8].toInt() and 0xFF) == 0x0B) { "const expr must terminate with 0x0B" }
                    bits to (p + 9)
                }
                else -> throw IllegalArgumentException("unsupported constant initializer opcode 0x${initOp.toString(16)}")
            }
        }

        fun readConstInitExpr(src: ByteArray, startPos: Int): Pair<Int, Int> {
            val (v64, next) = readConstInitExpr64(src, startPos)
            return v64.toInt() to next
        }

        while (pos < bytes.size) {
            val secId = bytes[pos++].toInt() and 0xFF
            val (secLen, nextPos) = readU32Leb(bytes, pos)
            pos = nextPos
            require(secLen >= 0 && pos + secLen <= bytes.size) { "truncated Wasm section $secId" }
            if (secId != 0) {
                // Wasm 2.0 §5.5.2 section order: ... Element(9)->9, DataCount(12)->10, Code(10)->11, Data(11)->12
                val orderKey = if (secId == 12) 10 else if (secId >= 10) secId + 1 else secId
                require(orderKey > lastSectionId) { "out-of-order or duplicate Wasm section $secId" }
                lastSectionId = orderKey
            }
            val secEnd = pos + secLen
            when (secId) {
                0 -> {} // custom section: skipped safely
                1 -> { // Type section
                    val (cnt, p0) = readU32Leb(bytes, pos); var p = p0
                    repeat(cnt) {
                        require((bytes[p++].toInt() and 0xFF) == 0x60) { "expected functype 0x60" }
                        val (pCnt, p1) = readU32Leb(bytes, p); p = p1
                        val pTypes = List(pCnt) {
                            val pt = bytes[p++].toInt() and 0xFF
                            require(isSupportedValType(pt)) { "only i32/f32 params supported (got 0x${pt.toString(16)})" }
                            pt
                        }
                        val (rCnt, p2) = readU32Leb(bytes, p); p = p2
                        require(rCnt in 0..1) { "multi-value returns ($rCnt) out of scope" }
                        val rTypes = List(rCnt) {
                            val rt = bytes[p++].toInt() and 0xFF
                            require(isSupportedValType(rt)) { "only i32/f32 results supported (got 0x${rt.toString(16)})" }
                            rt
                        }
                        types.add(FuncType(pCnt, rCnt, pTypes, rTypes))
                    }
                    require(p == secEnd) { "Type section size mismatch" }
                }
                2 -> { // Import section — zero ambient authority!
                    val (cnt, _) = readU32Leb(bytes, pos)
                    require(cnt == 0) { "ambient host imports ($cnt) refused by capability sandbox" }
                }
                3 -> { // Function section
                    val (cnt, p0) = readU32Leb(bytes, pos); var p = p0
                    repeat(cnt) {
                        val (tIdx, p1) = readU32Leb(bytes, p); p = p1
                        require(tIdx in types.indices) { "invalid type index $tIdx" }
                        funcTypeIndices.add(tIdx)
                    }
                    require(p == secEnd) { "Function section size mismatch" }
                }
                4 -> { // Table section (funcref indirect dispatch table)
                    val (cnt, p0) = readU32Leb(bytes, pos); var p = p0
                    require(cnt == 1) { "expected at most 1 table, got $cnt" }
                    val elemType = bytes[p++].toInt() and 0xFF
                    require(elemType == REFTYPE_FUNCREF) { "only funcref (0x70) table supported" }
                    val flags = bytes[p++].toInt() and 0xFF
                    require(flags == 0 || flags == 1) { "unsupported table limits flag $flags" }
                    val (minT, p1) = readU32Leb(bytes, p); p = p1
                    require(minT in 0..HARD_MAX_TABLE_ELEMENTS) { "table size $minT exceeds $HARD_MAX_TABLE_ELEMENTS" }
                    tableInitialSize = minT
                    if (flags == 1) {
                        val (maxT, p2) = readU32Leb(bytes, p); p = p2
                        require(maxT >= minT) { "table max ($maxT) < min ($minT)" }
                        tableMaxSize = maxT
                    }
                    require(p == secEnd) { "Table section size mismatch" }
                }
                5 -> { // Memory section (Wasm 3.0 multi-memory up to 8 linear memories)
                    val (cnt, p0) = readU32Leb(bytes, pos); var p = p0
                    require(cnt in 1..8) { "expected 1..8 memories in Wasm 3.0 sandbox, got $cnt" }
                    repeat(cnt) { idx ->
                        val flags = bytes[p++].toInt() and 0xFF
                        require(flags == 0 || flags == 1) { "unsupported memory limits flag $flags" }
                        val (minP, p1) = readU32Leb(bytes, p); p = p1
                        require(minP in 0..HARD_MAX_PAGES) { "initial memory $minP exceeds $HARD_MAX_PAGES pages" }
                        var mMax: Int? = null
                        if (flags == 1) {
                            val (maxP, p2) = readU32Leb(bytes, p); p = p2
                            require(maxP >= minP) { "memory max ($maxP) < min ($minP)" }
                            mMax = maxP
                        }
                        memoryDecls.add(MemoryDecl(minP, mMax))
                        if (idx == 0) {
                            initialPages = minP
                            maxPages = mMax
                        }
                    }
                    require(p == secEnd) { "Memory section size mismatch" }
                }
                6 -> { // Global section
                    val (cnt, p0) = readU32Leb(bytes, pos); var p = p0
                    repeat(cnt) {
                        val vt = bytes[p++].toInt() and 0xFF
                        require(isSupportedValType(vt)) { "only i32/f32/f64/funcref globals supported" }
                        val mutByte = bytes[p++].toInt() and 0xFF
                        require(mutByte == 0 || mutByte == 1) { "invalid global mutability $mutByte" }
                        val (initBits64, pNext) = readConstInitExpr64(bytes, p); p = pNext
                        globals.add(GlobalDecl(vt, mutByte == 1, initBits64.toInt(), initBits64))
                    }
                    require(p == secEnd) { "Global section size mismatch" }
                }
                7 -> { // Export section
                    val (cnt, p0) = readU32Leb(bytes, pos); var p = p0
                    repeat(cnt) {
                        val (nameLen, p1) = readU32Leb(bytes, p); p = p1
                        val name = String(bytes, p, nameLen, Charsets.UTF_8); p += nameLen
                        val kind = bytes[p++].toInt() and 0xFF
                        val (idx, p2) = readU32Leb(bytes, p); p = p2
                        require(name !in exports) { "duplicate export '$name'" }
                        exports[name] = kind to idx
                    }
                    require(p == secEnd) { "Export section size mismatch" }
                }
                9 -> { // Element section (active mode=0 + passive mode=1 Wasm 2.0 segments)
                    val (cnt, p0) = readU32Leb(bytes, pos); var p = p0
                    repeat(cnt) {
                        val (mode, p1) = readU32Leb(bytes, p); p = p1
                        when (mode) {
                            0 -> {
                                val (offsetVal, p2) = readConstInitExpr(bytes, p); p = p2
                                val (fnCnt, p3) = readU32Leb(bytes, p); p = p3
                                val fnIndices = IntArray(fnCnt)
                                for (i in 0 until fnCnt) {
                                    val (fIdx, pNext) = readU32Leb(bytes, p); p = pNext
                                    require(fIdx in funcTypeIndices.indices) { "element references unknown func $fIdx" }
                                    fnIndices[i] = fIdx
                                }
                                elements.add(ElementSegment(offsetVal, fnIndices, isPassive = false))
                            }
                            1 -> { // passive element segment: elemkind (0x00) + vec(funcidx)
                                val elemKind = bytes[p++].toInt() and 0xFF
                                require(elemKind == 0x00) { "expected elemkind 0x00 in passive element segment" }
                                val (fnCnt, p2) = readU32Leb(bytes, p); p = p2
                                val fnIndices = IntArray(fnCnt)
                                for (i in 0 until fnCnt) {
                                    val (fIdx, pNext) = readU32Leb(bytes, p); p = pNext
                                    require(fIdx in funcTypeIndices.indices) { "passive element references unknown func $fIdx" }
                                    fnIndices[i] = fIdx
                                }
                                elements.add(ElementSegment(0, fnIndices, isPassive = true))
                            }
                            else -> throw IllegalArgumentException("unsupported element segment mode $mode")
                        }
                    }
                    require(p == secEnd) { "Element section size mismatch" }
                }
                12 -> { // DataCount section (Wasm 2.0 bulk-memory validation)
                    val (dc, p1) = readU32Leb(bytes, pos)
                    require(p1 == secEnd) { "DataCount section size mismatch" }
                    declaredDataCount = dc
                }
                10 -> { // Code section
                    val (cnt, p0) = readU32Leb(bytes, pos); var p = p0
                    require(cnt == funcTypeIndices.size) { "Code count ($cnt) != Function count (${funcTypeIndices.size})" }
                    repeat(cnt) {
                        val (bodySize, p1) = readU32Leb(bytes, p); p = p1
                        val bodyEnd = p + bodySize
                        val (vecCnt, p2) = readU32Leb(bytes, p); var bp = p2
                        var totalLocals = 0
                        repeat(vecCnt) {
                            val (nLoc, nNext) = readU32Leb(bytes, bp)
                            val lType = bytes[nNext].toInt() and 0xFF
                            bp = nNext + 1
                            require(isSupportedValType(lType)) { "only i32/f32 locals supported" }
                            totalLocals += nLoc
                            require(totalLocals <= 512) { "too many locals ($totalLocals)" }
                        }
                        val instrBytes = bytes.copyOfRange(bp, bodyEnd)
                        require(instrBytes.isNotEmpty() && instrBytes.last() == 0x0B.toByte()) {
                            "function body must terminate with 0x0B (end)"
                        }
                        bodies.add(totalLocals to instrBytes)
                        p = bodyEnd
                    }
                    require(p == secEnd) { "Code section size mismatch" }
                }
                11 -> { // Data section (active mode=0 + passive mode=1 segments)
                    val (cnt, p0) = readU32Leb(bytes, pos); var p = p0
                    if (declaredDataCount != null) {
                        require(cnt == declaredDataCount) { "Data section count ($cnt) != DataCount ($declaredDataCount)" }
                    }
                    repeat(cnt) {
                        val (mode, p1) = readU32Leb(bytes, p); p = p1
                        when (mode) {
                            0 -> { // active memory 0
                                val (off, p2) = readConstInitExpr(bytes, p); p = p2
                                val (dLen, p3) = readU32Leb(bytes, p); p = p3
                                dataSegments.add(DataSegment(false, off, bytes.copyOfRange(p, p + dLen), 0))
                                p += dLen
                            }
                            1 -> { // passive (for memory.init / data.drop!)
                                val (dLen, p2) = readU32Leb(bytes, p); p = p2
                                dataSegments.add(DataSegment(true, 0, bytes.copyOfRange(p, p + dLen), 0))
                                p += dLen
                            }
                            2 -> { // active explicit memoryIndex (Wasm 3.0 multi-memory!)
                                val (memIdx, pMem) = readU32Leb(bytes, p)
                                val (off, p2) = readConstInitExpr(bytes, pMem); p = p2
                                val (dLen, p3) = readU32Leb(bytes, p); p = p3
                                dataSegments.add(DataSegment(false, off, bytes.copyOfRange(p, p + dLen), memIdx))
                                p += dLen
                            }
                            else -> throw IllegalArgumentException("unsupported data segment mode $mode")
                        }
                    }
                    require(p == secEnd) { "Data section size mismatch" }
                }
                else -> throw IllegalArgumentException("unsupported Wasm section id $secId")
            }
            pos = secEnd
        }
        require(bodies.size == funcTypeIndices.size) { "missing Code section for declared functions" }
        val funcs = funcTypeIndices.mapIndexed { i, tIdx -> FuncBody(tIdx, bodies[i].first, bodies[i].second) }
        val finalMemories = if (memoryDecls.isEmpty()) listOf(MemoryDecl(initialPages, maxPages)) else memoryDecls
        return Module(types, funcs, initialPages, maxPages, exports, tableInitialSize, tableMaxSize, globals, elements, dataSegments, finalMemories)
    }

    private fun blockArity(bt: Int): Int = when (bt) {
        BLOCK_VOID -> 0
        TYPE_I32, TYPE_F32, TYPE_F64, TYPE_V128, REFTYPE_FUNCREF -> 1
        else -> throw IllegalArgumentException("unsupported blocktype 0x${bt.toString(16)}")
    }

    private fun precomputeStructuredBlocks(code: ByteArray): Map<Int, Pair<Int, Int>> {
        // Maps pc of 0x02/0x03/0x04 -> (elsePc or -1, endPc)
        val out = HashMap<Int, Pair<Int, Int>>()
        val stack = ArrayList<IntArray>() // [openPc, elsePc]
        var pc = 0
        while (pc < code.size) {
            val op = code[pc].toInt() and 0xFF
            val opPc = pc
            pc++
            when (op) {
                0x02, 0x03, 0x04 -> {
                    pc++ // blocktype byte
                    stack.add(intArrayOf(opPc, -1))
                }
                0x05 -> {
                    require(stack.isNotEmpty()) { "unmatched else at $opPc" }
                    stack.last()[1] = opPc
                }
                0x0B -> {
                    if (stack.isNotEmpty()) {
                        val open = stack.removeAt(stack.lastIndex)
                        out[open[0]] = open[1] to opPc
                    }
                }
                0x0C, 0x0D, 0x10, 0x12, 0x20, 0x21, 0x22, 0x23, 0x24, 0x25, 0x26, 0xD2 -> pc = readU32Leb(code, pc).second
                0x11, 0x13 -> {
                    val n1 = readU32Leb(code, pc).second
                    pc = readU32Leb(code, n1).second
                }
                0x28, 0x2A, 0x2B, 0x2D, 0x36, 0x38, 0x39, 0x3A -> {
                    val (alignFlags, n1) = readU32Leb(code, pc)
                    val nMem = if ((alignFlags and 0x40) != 0) readU32Leb(code, n1).second else n1
                    pc = readU32Leb(code, nMem).second
                }
                0x3F, 0x40 -> pc = readU32Leb(code, pc).second
                0xD0 -> pc++
                0x41 -> pc = readS32Leb(code, pc).second
                0x43 -> pc += 4 // f32.const 4 raw bytes (MUST NOT misparse 0x0B inside float bytes as structured end!)
                0x44 -> pc += 8 // f64.const 8 raw bytes (MUST NOT misparse 0x0B inside double bytes as structured end!)
                0xFC -> {
                    val (subOp, nextPc) = readU32Leb(code, pc)
                    pc = when (subOp) {
                        0, 1, 2, 3 -> nextPc // trunc_sat_f32/f64_s/u
                        8, 10, 12, 14 -> readU32Leb(code, readU32Leb(code, nextPc).second).second // memory.init/copy, table.init/copy
                        9, 11, 13, 15, 16, 17 -> readU32Leb(code, nextPc).second // data.drop, memory.fill, elem.drop, table.grow/size/fill
                        else -> throw IllegalArgumentException("unsupported 0xFC Wasm sub-opcode $subOp in block scan")
                    }
                }
                0xFD -> {
                    val (simdOp, nextPc) = readU32Leb(code, pc)
                    pc = when (simdOp) {
                        0, 9, 11 -> readU32Leb(code, readU32Leb(code, nextPc).second).second // memarg align + offset
                        12, 13 -> nextPc + 16 // v128.const / i8x16.shuffle 16 raw bytes (MUST NOT match 0x0B inside vector!)
                        27, 28 -> nextPc + 1 // lane index immediate byte
                        15, 16, 17, 77, 78, 79, 80, 81, 83, 98, 160, 161, 174, 177, 181, 186, 228, 230 -> nextPc
                        else -> throw IllegalArgumentException("unsupported 0xFD Wasm SIMD sub-opcode $simdOp in block scan")
                    }
                }
            }
        }
        require(stack.isEmpty()) { "unclosed structured control block in Wasm function" }
        return out
    }

    private fun readU32Leb(buf: ByteArray, start: Int): Pair<Int, Int> {
        var result = 0
        var shift = 0
        var p = start
        while (true) {
            require(p < buf.size && shift < 35) { "malformed u32 LEB128 at $start" }
            val b = buf[p++].toInt() and 0xFF
            result = result or ((b and 0x7F) shl shift)
            if ((b and 0x80) == 0) return result to p
            shift += 7
        }
    }

    private fun readS32Leb(buf: ByteArray, start: Int): Pair<Int, Int> {
        var result = 0
        var shift = 0
        var p = start
        var b: Int
        do {
            require(p < buf.size && shift < 35) { "malformed s32 LEB128 at $start" }
            b = buf[p++].toInt() and 0xFF
            result = result or ((b and 0x7F) shl shift)
            shift += 7
        } while ((b and 0x80) != 0)
        if (shift < 32 && (b and 0x40) != 0) {
            result = result or (-1 shl shift)
        }
        return result to p
    }
}

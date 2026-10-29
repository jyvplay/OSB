// SPDX-License-Identifier: Apache-2.0
package dev.fusion.capabilities

/**
 * Capability-Scoped WASI Virtual Pipe & Deterministic Multi-Stage Wasm Pipeline Runner.
 *
 * Connects pure-JVM [WasmSandbox] modules via bounded in-memory byte buffers (`stdinSlice ->
 * Wasm linear memory -> stdoutSlice`) with **zero host OS file-descriptor or socket authority**!
 */
object WasiVirtualPipe {
    const val SCOPE =
        "Capability-scoped bounded in-memory virtual pipe & multi-stage WasmSandbox pipeline runner (zero host OS fd/socket authority)."

    const val MAX_PIPE_BYTES = 262_144

    class BoundedRingPipe(val capacityBytes: Int = 65_536) {
        init {
            require(capacityBytes in 16..MAX_PIPE_BYTES) { "pipe capacity $capacityBytes outside 16..$MAX_PIPE_BYTES" }
        }

        private val buf = ByteArray(capacityBytes)
        private var head = 0
        private var tail = 0
        private var count = 0

        fun available(): Int = count
        fun freeSpace(): Int = capacityBytes - count

        fun write(src: ByteArray): Int {
            require(src.size <= freeSpace()) { "virtual pipe overflow: writing ${src.size}B with ${freeSpace()}B free" }
            for (b in src) {
                buf[tail] = b
                tail = (tail + 1) % capacityBytes
                count++
            }
            return src.size
        }

        fun read(maxBytes: Int): ByteArray {
            require(maxBytes >= 0) { "negative read length" }
            val n = minOf(maxBytes, count)
            val out = ByteArray(n)
            for (i in 0 until n) {
                out[i] = buf[head]
                head = (head + 1) % capacityBytes
                count--
            }
            return out
        }
    }

    data class StageSpec(
        val wasmBytes: ByteArray,
        val exportFunc: String = "transform",
        val inputOffset: Int = 0,
        val outputOffset: Int = 4096,
        val fuelBudget: Long = 50_000L,
    )

    /**
     * Executes a deterministic pipeline `stage_0 | stage_1 | ... | stage_{k-1}` where each stage
     * receives `(inputOffset, inputLen, outputOffset)` and returns `outputLen`.
     */
    fun runPipeline(initialInput: ByteArray, stages: List<StageSpec>): Pair<ByteArray, Long> {
        require(stages.isNotEmpty() && stages.size <= 16) { "stages count outside 1..16" }
        var payload = initialInput.copyOf()
        var totalFuel = 0L
        for (stage in stages) {
            require(payload.size <= MAX_PIPE_BYTES) { "stage payload exceeds $MAX_PIPE_BYTES" }
            val inst = WasmSandbox.decode(stage.wasmBytes).instantiate(maxMemoryPages = 2)
            inst.writeMemory(stage.inputOffset, payload)
            val res = inst.invoke(
                stage.exportFunc,
                listOf(stage.inputOffset, payload.size, stage.outputOffset),
                fuelBudget = stage.fuelBudget,
            )
            totalFuel += res.fuelUsed
            val outLen = res.returnValues.firstOrNull() ?: 0
            require(outLen in 0..MAX_PIPE_BYTES) { "Wasm stage returned invalid output length $outLen" }
            payload = inst.readMemory(stage.outputOffset, outLen)
        }
        return payload to totalFuel
    }
}

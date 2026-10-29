package dev.fusion.capabilities

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/**
 * Clean-room W3C WebAssembly Component Model Canonical ABI Lift/Lower Memory Codec (Apache-2.0).
 *
 * Safely lowers and lifts UTF-8 `string` `(ptr, byteLen)` descriptors, `list<s32>`, and
 * `result<string, u32>` discriminated unions across guest linear memory with strict alignment,
 * out-of-bounds refusal, and malformed UTF-8 surrogate rejection.
 */
object WitComponentCanonicalAbiCodec {
    const val SCOPE =
        "WebAssembly Component Model Canonical ABI lift/lower codec (UTF-8 string ptr+len, list<s32>, result<T,E> discriminant, bounds & UTF-8 checks)"

    data class LoweredSlice(val ptr: Int, val len: Int, val nextBumpOffset: Int)

    sealed interface WitResult<out T, out E> {
        data class Ok<T>(val value: T) : WitResult<T, Nothing>
        data class Err<E>(val error: E) : WitResult<Nothing, E>
    }

    fun lowerUtf8String(memory: ByteArray, bumpOffset: Int, value: String): LoweredSlice {
        val utf8 = value.toByteArray(StandardCharsets.UTF_8)
        val alignedPtr = alignTo(bumpOffset, 1)
        require(alignedPtr >= 0 && alignedPtr.toLong() + utf8.size <= memory.size) {
            "Canonical ABI string lower out of linear memory bounds (${alignedPtr + utf8.size} > ${memory.size})"
        }
        System.arraycopy(utf8, 0, memory, alignedPtr, utf8.size)
        return LoweredSlice(alignedPtr, utf8.size, alignedPtr + utf8.size)
    }

    fun liftUtf8String(memory: ByteArray, ptr: Int, byteLen: Int): String {
        require(ptr >= 0 && byteLen >= 0 && ptr.toLong() + byteLen <= memory.size) {
            "Canonical ABI string lift out of bounds (ptr=$ptr len=$byteLen cap=${memory.size})"
        }
        val decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            decoder.decode(ByteBuffer.wrap(memory, ptr, byteLen)).toString()
        } catch (e: CharacterCodingException) {
            throw IllegalArgumentException("Canonical ABI invalid UTF-8 sequence at ptr=$ptr: ${e.message}", e)
        }
    }

    fun lowerInt32List(memory: ByteArray, bumpOffset: Int, items: List<Int>): LoweredSlice {
        val alignedPtr = alignTo(bumpOffset, 4)
        val byteLen = items.size * 4
        require(alignedPtr >= 0 && alignedPtr.toLong() + byteLen <= memory.size) {
            "Canonical ABI list<s32> lower out of bounds"
        }
        val bb = ByteBuffer.wrap(memory, alignedPtr, byteLen).order(ByteOrder.LITTLE_ENDIAN)
        for (v in items) bb.putInt(v)
        return LoweredSlice(alignedPtr, items.size, alignedPtr + byteLen)
    }

    fun liftInt32List(memory: ByteArray, ptr: Int, elemCount: Int): List<Int> {
        require(ptr >= 0 && elemCount >= 0 && ptr % 4 == 0 && ptr.toLong() + elemCount.toLong() * 4L <= memory.size) {
            "Canonical ABI list<s32> lift misaligned or out of bounds (ptr=$ptr count=$elemCount)"
        }
        val bb = ByteBuffer.wrap(memory, ptr, elemCount * 4).order(ByteOrder.LITTLE_ENDIAN)
        return List(elemCount) { bb.int }
    }

    /**
     * Encodes `result<string, u32>` at 4-byte aligned [recordOffset]:
     * - byte `0`: discriminant (`0` = `ok`, `1` = `err`)
     * - bytes `4..7`: `ok.ptr` or `err.code`
     * - bytes `8..11`: `ok.len` (0 on `err`)
     */
    fun lowerResultStringOrCode(
        memory: ByteArray,
        recordOffset: Int,
        payloadBumpOffset: Int,
        value: WitResult<String, Int>,
    ): Int {
        require(recordOffset >= 0 && recordOffset % 4 == 0 && recordOffset + 12 <= memory.size) {
            "Canonical ABI result record misaligned or out of bounds"
        }
        val bb = ByteBuffer.wrap(memory, recordOffset, 12).order(ByteOrder.LITTLE_ENDIAN)
        return when (value) {
            is WitResult.Ok -> {
                val slice = lowerUtf8String(memory, payloadBumpOffset, value.value)
                bb.put(0.toByte()).put(0).put(0).put(0)
                bb.putInt(slice.ptr).putInt(slice.len)
                slice.nextBumpOffset
            }
            is WitResult.Err -> {
                bb.put(1.toByte()).put(0).put(0).put(0)
                bb.putInt(value.error).putInt(0)
                payloadBumpOffset
            }
        }
    }

    fun liftResultStringOrCode(memory: ByteArray, recordOffset: Int): WitResult<String, Int> {
        require(recordOffset >= 0 && recordOffset % 4 == 0 && recordOffset + 12 <= memory.size) {
            "Canonical ABI result record lift misaligned or out of bounds"
        }
        val bb = ByteBuffer.wrap(memory, recordOffset, 12).order(ByteOrder.LITTLE_ENDIAN)
        val disc = bb.get().toInt() and 0xFF
        bb.position(4)
        val w1 = bb.int
        val w2 = bb.int
        return when (disc) {
            0 -> WitResult.Ok(liftUtf8String(memory, w1, w2))
            1 -> WitResult.Err(w1)
            else -> throw IllegalArgumentException("Invalid WIT result discriminant $disc (expected 0 or 1)")
        }
    }

    private fun alignTo(offset: Int, align: Int): Int {
        val mask = align - 1
        return (offset + mask) and mask.inv()
    }
}

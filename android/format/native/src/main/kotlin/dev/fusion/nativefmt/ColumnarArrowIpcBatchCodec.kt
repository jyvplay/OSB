package dev.fusion.nativefmt

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

/**
 * Clean-room 64-byte-aligned Columnar IPC RecordBatch Codec (Apache Arrow memory-layout inspired,
 * Apache-2.0) supporting nullable `INT64`, `FLOAT64`, and variable-width `UTF8` string columns
 * with validity bitmaps and zero-row-deserialization single-column projection.
 */
object ColumnarArrowIpcBatchCodec {
    const val SCOPE =
        "64-byte-aligned columnar IPC RecordBatch codec (validity bitmaps + Int64/Float64/Utf8 offset+data buffers + single-column projection)"

    private val MAGIC = byteArrayOf('F'.code.toByte(), 'A'.code.toByte(), 'R'.code.toByte(), 'W'.code.toByte(), 0x01, 0x00, 0x00, 0x00)

    sealed interface ColumnVector {
        val name: String
        val rowCount: Int

        data class Int64Col(override val name: String, val values: List<Long?>) : ColumnVector {
            override val rowCount: Int get() = values.size
        }
        data class Float64Col(override val name: String, val values: List<Double?>) : ColumnVector {
            override val rowCount: Int get() = values.size
        }
        data class Utf8Col(override val name: String, val values: List<String?>) : ColumnVector {
            override val rowCount: Int get() = values.size
        }
    }

    fun encodeBatch(columns: List<ColumnVector>): ByteArray {
        require(columns.isNotEmpty()) { "Batch requires >= 1 column" }
        val rows = columns[0].rowCount
        require(columns.all { it.rowCount == rows }) { "All columns in RecordBatch must share identical rowCount=$rows" }

        val out = java.io.ByteArrayOutputStream()
        out.write(MAGIC)
        val hdr = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        hdr.putInt(rows).putInt(columns.size)
        out.write(hdr.array())

        for (col in columns) {
            val nameBytes = col.name.toByteArray(StandardCharsets.UTF_8)
            val typeTag: Byte = when (col) {
                is ColumnVector.Int64Col -> 1
                is ColumnVector.Float64Col -> 2
                is ColumnVector.Utf8Col -> 3
            }
            val meta = ByteBuffer.allocate(5 + nameBytes.size).order(ByteOrder.LITTLE_ENDIAN)
            meta.put(typeTag).putInt(nameBytes.size).put(nameBytes)
            out.write(meta.array())
            padTo64(out)

            val bitmapBytes = ((rows + 7) / 8).coerceAtLeast(1)
            val bitmap = ByteArray(bitmapBytes)
            when (col) {
                is ColumnVector.Int64Col -> {
                    val buf = ByteBuffer.allocate(rows * 8).order(ByteOrder.LITTLE_ENDIAN)
                    for (i in 0 until rows) {
                        val v = col.values[i]
                        if (v != null) {
                            bitmap[i ushr 3] = (bitmap[i ushr 3].toInt() or (1 shl (i and 7))).toByte()
                            buf.putLong(v)
                        } else {
                            buf.putLong(0L)
                        }
                    }
                    out.write(bitmap); padTo64(out)
                    out.write(buf.array()); padTo64(out)
                }
                is ColumnVector.Float64Col -> {
                    val buf = ByteBuffer.allocate(rows * 8).order(ByteOrder.LITTLE_ENDIAN)
                    for (i in 0 until rows) {
                        val v = col.values[i]
                        if (v != null) {
                            bitmap[i ushr 3] = (bitmap[i ushr 3].toInt() or (1 shl (i and 7))).toByte()
                            buf.putDouble(v)
                        } else {
                            buf.putDouble(0.0)
                        }
                    }
                    out.write(bitmap); padTo64(out)
                    out.write(buf.array()); padTo64(out)
                }
                is ColumnVector.Utf8Col -> {
                    val offsets = ByteBuffer.allocate((rows + 1) * 4).order(ByteOrder.LITTLE_ENDIAN)
                    val dataOut = java.io.ByteArrayOutputStream()
                    offsets.putInt(0)
                    for (i in 0 until rows) {
                        val s = col.values[i]
                        if (s != null) {
                            bitmap[i ushr 3] = (bitmap[i ushr 3].toInt() or (1 shl (i and 7))).toByte()
                            dataOut.write(s.toByteArray(StandardCharsets.UTF_8))
                        }
                        offsets.putInt(dataOut.size())
                    }
                    val strData = dataOut.toByteArray()
                    out.write(bitmap); padTo64(out)
                    out.write(offsets.array()); padTo64(out)
                    val lenPrefix = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(strData.size).array()
                    out.write(lenPrefix)
                    out.write(strData); padTo64(out)
                }
            }
        }
        return out.toByteArray()
    }

    fun decodeBatch(bytes: ByteArray, projectedNames: Set<String>? = null): List<ColumnVector> {
        require(bytes.size >= 16) { "Truncated columnar IPC batch" }
        for (i in MAGIC.indices) require(bytes[i] == MAGIC[i]) { "Invalid FARW magic header" }
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        bb.position(8)
        val rows = bb.int
        val colCount = bb.int
        require(rows in 0..1_000_000 && colCount in 1..1_024) { "Out-of-bounds batch dimensions" }

        val out = ArrayList<ColumnVector>()
        val bitmapBytes = ((rows + 7) / 8).coerceAtLeast(1)
        for (c in 0 until colCount) {
            val typeTag = bb.get().toInt()
            val nameLen = bb.int
            val nameBuf = ByteArray(nameLen)
            bb.get(nameBuf)
            val name = String(nameBuf, StandardCharsets.UTF_8)
            bb.position(align64(bb.position()))

            val bitmap = ByteArray(bitmapBytes)
            bb.get(bitmap)
            bb.position(align64(bb.position()))
            val keep = projectedNames == null || name in projectedNames

            fun isValid(r: Int): Boolean = ((bitmap[r ushr 3].toInt() ushr (r and 7)) and 1) == 1

            when (typeTag) {
                1 -> {
                    if (keep) {
                        val vals = List(rows) { r -> val v = bb.long; if (isValid(r)) v else null }
                        out.add(ColumnVector.Int64Col(name, vals))
                    } else {
                        bb.position(bb.position() + rows * 8)
                    }
                    bb.position(align64(bb.position()))
                }
                2 -> {
                    if (keep) {
                        val vals = List(rows) { r -> val v = bb.double; if (isValid(r)) v else null }
                        out.add(ColumnVector.Float64Col(name, vals))
                    } else {
                        bb.position(bb.position() + rows * 8)
                    }
                    bb.position(align64(bb.position()))
                }
                3 -> {
                    val offs = IntArray(rows + 1) { bb.int }
                    bb.position(align64(bb.position()))
                    val dataLen = bb.int
                    val dataStart = bb.position()
                    if (keep) {
                        val vals = List(rows) { r ->
                            if (!isValid(r)) null
                            else String(bytes, dataStart + offs[r], offs[r + 1] - offs[r], StandardCharsets.UTF_8)
                        }
                        out.add(ColumnVector.Utf8Col(name, vals))
                    }
                    bb.position(align64(dataStart + dataLen))
                }
                else -> throw IllegalArgumentException("Unknown column typeTag=$typeTag")
            }
        }
        return out
    }

    private fun align64(pos: Int): Int = (pos + 63) and 63.inv()
    private fun padTo64(out: java.io.ByteArrayOutputStream) {
        val pad = align64(out.size()) - out.size()
        if (pad > 0) out.write(ByteArray(pad))
    }
}

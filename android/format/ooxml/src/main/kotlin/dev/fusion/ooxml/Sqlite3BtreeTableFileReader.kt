// SPDX-License-Identifier: Apache-2.0
package dev.fusion.ooxml

/**
 * Clean-room SQLite 3 database file-format writer & reader for spreadsheet export/import.
 *
 * Implements the exact on-disk contract from `sqlite.org/fileformat2.html`:
 * - 100-byte header: magic `"SQLite format 3\u0000"`, big-endian `u16` page size at `16`, page count at `28`,
 *   schema cookie at `40`, schema format `4` at `44`, text encoding `1` (UTF-8) at `56`, version-valid-for at `92`.
 * - **SQLite big-endian varint** (1–9 bytes; bytes 1–8 carry 7 bits, byte 9 carries all 8 bits) — this is
 *   NOT protobuf LEB128 and must not be decoded little-endian.
 * - Leaf table page `0x0d` (`8`-byte header: type, first-freeblock, cell count, cell-content start, fragmented)
 *   and interior table page `0x05` (`12`-byte header with 4-byte `rightChild`).
 * - Record serial types `0` NULL, `1..6` int 8/16/24/32/48/64, `7` float64, `8`/`9` constants `0`/`1`,
 *   `N>=12` even BLOB, `N>=13` odd TEXT.
 */
object Sqlite3BtreeTableFileReader {
    val MAGIC = byteArrayOf(
        'S'.code.toByte(), 'Q'.code.toByte(), 'L'.code.toByte(), 'i'.code.toByte(), 't'.code.toByte(),
        'e'.code.toByte(), ' '.code.toByte(), 'f'.code.toByte(), 'o'.code.toByte(), 'r'.code.toByte(),
        'm'.code.toByte(), 'a'.code.toByte(), 't'.code.toByte(), ' '.code.toByte(), '3'.code.toByte(), 0,
    )

    const val PAGE_LEAF_TABLE = 0x0D
    const val PAGE_INTERIOR_TABLE = 0x05

    data class Column(val name: String, val kind: ColumnKind)
    enum class ColumnKind { INTEGER, TEXT, REAL }

    sealed interface Value {
        data class IntV(val v: Long) : Value
        data class TextV(val v: String) : Value
        data class RealV(val v: Double) : Value
        data object NullV : Value
        fun display(): String = when (this) {
            is IntV -> v.toString()
            is TextV -> v
            is RealV -> v.toString()
            NullV -> ""
        }
    }

    data class Table(val name: String, val columns: List<Column>, val rows: List<List<Value>>)

    data class Header(
        val pageSize: Int,
        val writeFormat: Int,
        val readFormat: Int,
        val reservedPerPage: Int,
        val pageCount: Int,
        val freelistTrunk: Int,
        val freelistCount: Int,
        val schemaCookie: Long,
        val schemaFormat: Int,
        val textEncoding: Int,
        val userVersion: Long,
        val applicationId: Long,
        val versionValidFor: Long,
        val sqliteVersionNumber: Long,
    )

    data class Integrity(val ok: Boolean, val reasons: List<String>, val leafPages: Int, val interiorPages: Int)

    fun encodeVarint(value: Long): ByteArray {
        require(value >= 0L) { "varint is unsigned" }
        if (value <= 0x7FL) return byteArrayOf(value.toByte())
        val bits = 64 - java.lang.Long.numberOfLeadingZeros(value)
        val n = if (bits <= 56) (bits + 6) / 7 else 9
        val out = ByteArray(n)
        if (n == 9) {
            // SQLite 9-byte form: bytes 1..8 carry 7 bits each, byte 9 carries a full 8 bits.
            val hi = value ushr 8
            for (i in 0 until 8) {
                out[i] = (((hi ushr (7 * (7 - i))) and 0x7FL) or 0x80L).toByte()
            }
            out[8] = (value and 0xFFL).toByte()
        } else {
            for (i in 0 until n) {
                val chunk = (value ushr (7 * (n - 1 - i))) and 0x7FL
                out[i] = (if (i < n - 1) chunk or 0x80L else chunk).toByte()
            }
        }
        return out
    }

    fun decodeVarint(bytes: ByteArray, start: Int): Pair<Long, Int> {
        var result = 0L
        var p = start
        for (i in 0 until 9) {
            require(p < bytes.size) { "truncated varint at $start" }
            val b = bytes[p++].toInt() and 0xFF
            if (i == 8) {
                result = (result shl 8) or b.toLong()
                return result to p
            }
            result = (result shl 7) or (b and 0x7F).toLong()
            if ((b and 0x80) == 0) return result to p
        }
        return result to p
    }

    private fun putU16(buf: ByteArray, off: Int, v: Int) {
        buf[off] = ((v ushr 8) and 0xFF).toByte()
        buf[off + 1] = (v and 0xFF).toByte()
    }

    private fun putU32(buf: ByteArray, off: Int, v: Long) {
        buf[off] = ((v ushr 24) and 0xFF).toByte()
        buf[off + 1] = ((v ushr 16) and 0xFF).toByte()
        buf[off + 2] = ((v ushr 8) and 0xFF).toByte()
        buf[off + 3] = (v and 0xFF).toByte()
    }

    private fun u16(buf: ByteArray, off: Int): Int =
        ((buf[off].toInt() and 0xFF) shl 8) or (buf[off + 1].toInt() and 0xFF)

    private fun u32(buf: ByteArray, off: Int): Long =
        ((buf[off].toLong() and 0xFFL) shl 24) or
            ((buf[off + 1].toLong() and 0xFFL) shl 16) or
            ((buf[off + 2].toLong() and 0xFFL) shl 8) or
            (buf[off + 3].toLong() and 0xFFL)

    private fun serialTypeFor(v: Value): Pair<Long, ByteArray> = when (v) {
        is Value.NullV -> 0L to ByteArray(0)
        is Value.IntV -> {
            val x = v.v
            when {
                x == 0L -> 8L to ByteArray(0)
                x == 1L -> 9L to ByteArray(0)
                x >= -128 && x <= 127 -> 1L to byteArrayOf(x.toByte())
                x >= -32768 && x <= 32767 -> 2L to byteArrayOf((x ushr 8).toByte(), x.toByte())
                x >= -8_388_608 && x <= 8_388_607 -> 3L to byteArrayOf((x ushr 16).toByte(), (x ushr 8).toByte(), x.toByte())
                x >= Int.MIN_VALUE && x <= Int.MAX_VALUE -> 4L to byteArrayOf((x ushr 24).toByte(), (x ushr 16).toByte(), (x ushr 8).toByte(), x.toByte())
                x >= -140_737_488_355_328L && x <= 140_737_488_355_327L -> 5L to ByteArray(6) { i -> (x ushr ((5 - i) * 8)).toByte() }
                else -> 6L to ByteArray(8) { i -> (x ushr ((7 - i) * 8)).toByte() }
            }
        }
        is Value.RealV -> {
            val bits = java.lang.Double.doubleToRawLongBits(v.v)
            7L to ByteArray(8) { i -> (bits ushr ((7 - i) * 8)).toByte() }
        }
        is Value.TextV -> {
            val b = v.v.toByteArray(Charsets.UTF_8)
            (13L + 2L * b.size) to b
        }
    }

    private fun encodeRecord(values: List<Value>): ByteArray {
        val serials = values.map { serialTypeFor(it) }
        val bodySize = serials.sumOf { it.second.size }
        // Header length includes its own varint; iterate to a fixed point.
        var headerLen = 1
        while (true) {
            var sum = encodeVarint(headerLen.toLong()).size
            for ((st, _) in serials) sum += encodeVarint(st).size
            if (sum == headerLen) break
            headerLen = sum
        }
        val out = ArrayList<Byte>()
        encodeVarint(headerLen.toLong()).forEach { out.add(it) }
        for ((st, _) in serials) encodeVarint(st).forEach { out.add(it) }
        for ((_, bytes) in serials) bytes.forEach { out.add(it) }
        require(out.size == headerLen + bodySize) { "record length mismatch" }
        return out.toByteArray()
    }

    fun writeSingleFileTable(pageSize: Int = 4096, table: Table): ByteArray {
        require(pageSize in 512..65536 && (pageSize and (pageSize - 1)) == 0) { "pageSize must be a power of two in 512..65536" }
        val cols = table.columns
        require(table.rows.all { it.size == cols.size }) { "row width mismatch" }
        val sql = "CREATE TABLE " + table.name.replace("\"", "\"\"") +
            " (" + cols.joinToString(", ") { "\"" + it.name.replace("\"", "\"\"") + "\" " +
            when (it.kind) { ColumnKind.INTEGER -> "INTEGER"; ColumnKind.TEXT -> "TEXT"; ColumnKind.REAL -> "REAL" } } + ")"

        val page1 = ByteArray(pageSize)
        val page2 = ByteArray(pageSize)

        // --- Page 1 header ---
        System.arraycopy(MAGIC, 0, page1, 0, 16)
        putU16(page1, 16, if (pageSize == 65536) 1 else pageSize)
        page1[18] = 1 // write format: legacy journal
        page1[19] = 1 // read format
        page1[20] = 0 // reserved bytes per page
        page1[21] = 64
        page1[22] = 32
        page1[23] = 32
        putU32(page1, 24, 1) // file change counter
        putU32(page1, 28, 2) // database size in pages
        putU32(page1, 32, 0) // first freelist trunk page
        putU32(page1, 36, 0) // total freelist pages
        putU32(page1, 40, 1) // schema cookie
        putU32(page1, 44, 4) // schema format number
        putU32(page1, 48, 0) // default page cache size
        putU32(page1, 52, 0) // largest root b-tree page (auto-vacuum off)
        putU32(page1, 56, 1) // text encoding: UTF-8
        putU32(page1, 60, 0) // user version
        putU32(page1, 64, 0) // incremental vacuum
        putU32(page1, 68, 0) // application id
        // 72..91 reserved expansion (zeros)
        putU32(page1, 92, 1) // version-valid-for
        putU32(page1, 96, 3_045_000) // SQLite version number

        fun buildLeafPage(page: ByteArray, headerOffset: Int, cells: List<ByteArray>): Boolean {
            val contentStart = mutableListOf<Int>()
            var cursor = page.size
            for (cell in cells) {
                cursor -= cell.size
                if (cursor <= headerOffset + 8 + cells.size * 2) return false
                System.arraycopy(cell, 0, page, cursor, cell.size)
                contentStart.add(cursor)
            }
            // Cells must appear in ascending rowid order -> pointers ascending by offset == descending placement
            page[headerOffset] = PAGE_LEAF_TABLE.toByte()
            putU16(page, headerOffset + 1, 0)
            putU16(page, headerOffset + 3, cells.size)
            putU16(page, headerOffset + 5, if (cells.isEmpty()) 0 else cursor)
            page[headerOffset + 7] = 0
            for (i in cells.indices) {
                putU16(page, headerOffset + 8 + i * 2, contentStart[i])
            }
            return true
        }

        // sqlite_master row (root page 2)
        val masterValues = listOf(
            Value.TextV("table"),
            Value.TextV(table.name),
            Value.TextV(table.name),
            Value.IntV(2),
            Value.TextV(sql),
        )
        val masterRecord = encodeRecord(masterValues)
        val masterCell = encodeVarint(masterRecord.size.toLong()) + encodeVarint(1L) + masterRecord
        require(buildLeafPage(page1, 100, listOf(masterCell))) { "sqlite_master cell does not fit page 1" }

        val rowCells = ArrayList<ByteArray>()
        for ((idx, row) in table.rows.withIndex()) {
            val rec = encodeRecord(row)
            rowCells.add(encodeVarint(rec.size.toLong()) + encodeVarint((idx + 1).toLong()) + rec)
        }
        require(buildLeafPage(page2, 0, rowCells)) { "table rows do not fit a single ${pageSize}-byte leaf page" }
        return page1 + page2
    }

    fun readHeader(db: ByteArray): Header {
        require(db.size >= 100) { "file shorter than the 100-byte SQLite header" }
        for (i in 0 until 16) require(db[i] == MAGIC[i]) { "bad SQLite magic at byte $i" }
        val rawPageSize = u16(db, 16)
        val pageSize = if (rawPageSize == 1) 65536 else rawPageSize
        require(pageSize in 512..65536 && (pageSize and (pageSize - 1)) == 0) { "invalid page size $pageSize" }
        return Header(
            pageSize = pageSize,
            writeFormat = db[18].toInt(),
            readFormat = db[19].toInt(),
            reservedPerPage = db[20].toInt(),
            pageCount = u32(db, 28).toInt(),
            freelistTrunk = u32(db, 32).toInt(),
            freelistCount = u32(db, 36).toInt(),
            schemaCookie = u32(db, 40),
            schemaFormat = u32(db, 44).toInt(),
            textEncoding = u32(db, 56).toInt(),
            userVersion = u32(db, 60),
            applicationId = u32(db, 68),
            versionValidFor = u32(db, 92),
            sqliteVersionNumber = u32(db, 96),
        )
    }

    private fun decodeRecord(bytes: ByteArray, start: Int, payloadLen: Int): List<Value> {
        val (headerLen, afterHeaderLen) = decodeVarint(bytes, start)
        val headerEnd = start + headerLen.toInt()
        require(headerEnd <= start + payloadLen) { "record header overruns payload" }
        var p = afterHeaderLen
        val serials = ArrayList<Long>()
        while (p < headerEnd) {
            val (st, np) = decodeVarint(bytes, p)
            serials.add(st)
            p = np
        }
        var body = headerEnd
        val out = ArrayList<Value>(serials.size)
        for (st in serials) {
            out.add(
                when {
                    st == 0L -> Value.NullV
                    st == 8L -> Value.IntV(0)
                    st == 9L -> Value.IntV(1)
                    st in 1L..6L -> {
                        val n = when (st) { 1L -> 1; 2L -> 2; 3L -> 3; 4L -> 4; 5L -> 6; else -> 8 }
                        var v = 0L
                        val neg = (bytes[body].toInt() and 0x80) != 0
                        for (i in 0 until n) v = (v shl 8) or (bytes[body + i].toLong() and 0xFFL)
                        if (neg && n < 8) v = v or (-1L shl (n * 8))
                        body += n
                        Value.IntV(v)
                    }
                    st == 7L -> {
                        var bits = 0L
                        for (i in 0 until 8) bits = (bits shl 8) or (bytes[body + i].toLong() and 0xFFL)
                        body += 8
                        Value.RealV(java.lang.Double.longBitsToDouble(bits))
                    }
                    st >= 13L && st % 2 == 1L -> {
                        val n = ((st - 13) / 2).toInt()
                        val s = String(bytes, body, n, Charsets.UTF_8)
                        body += n
                        Value.TextV(s)
                    }
                    st >= 12L && st % 2 == 0L -> {
                        val n = ((st - 12) / 2).toInt()
                        body += n
                        Value.TextV("<blob:$n>")
                    }
                    else -> Value.NullV
                },
            )
        }
        return out
    }

    fun readTableRows(db: ByteArray, pageNumber: Int = 2): List<List<Value>> {
        val header = readHeader(db)
        val start = (pageNumber - 1) * header.pageSize
        require(start + header.pageSize <= db.size) { "page $pageNumber out of bounds" }
        val page = db.copyOfRange(start, start + header.pageSize)
        require(page[0].toInt() and 0xFF == PAGE_LEAF_TABLE) { "page $pageNumber is not a 0x0d leaf table page" }
        val nCells = u16(page, 3)
        val out = ArrayList<List<Value>>()
        for (i in 0 until nCells) {
            val ptr = u16(page, 8 + i * 2)
            val (payloadLen, afterLen) = decodeVarint(page, ptr)
            val (_, afterRowId) = decodeVarint(page, afterLen)
            out.add(decodeRecord(page, afterRowId, payloadLen.toInt()))
        }
        return out
    }

    fun verifyIntegrity(db: ByteArray): Integrity {
        val reasons = ArrayList<String>()
        val header = try { readHeader(db) } catch (e: IllegalArgumentException) {
            return Integrity(false, listOf(e.message ?: "bad header"), 0, 0)
        }
        if (db.size % header.pageSize != 0) reasons.add("file size ${db.size} is not a multiple of page size ${header.pageSize}")
        if (header.schemaFormat != 4) reasons.add("schema format ${header.schemaFormat} != 4")
        if (header.textEncoding !in 1..3) reasons.add("invalid text encoding ${header.textEncoding}")
        if (header.freelistCount != 0 || header.freelistTrunk != 0) reasons.add("freelist not empty")
        var leaf = 0
        var interior = 0
        val pages = db.size / header.pageSize
        for (p in 1..pages) {
            val off = (p - 1) * header.pageSize + (if (p == 1) 100 else 0)
            if (off >= db.size) break
            when (db[off].toInt() and 0xFF) {
                PAGE_LEAF_TABLE -> {
                    leaf++
                    val headerRel = if (p == 1) 100 else 0
                    val nCells = u16(db, off + 3)
                    val contentStart = u16(db, off + 5)
                    if (nCells > 0 && contentStart < headerRel + 8 + nCells * 2) {
                        reasons.add("page $p cell content start $contentStart overlaps pointer array")
                    }
                    var prev = Int.MAX_VALUE
                    for (i in 0 until nCells) {
                        val ptr = u16(db, off + 8 + i * 2)
                        if (ptr < contentStart || ptr >= header.pageSize) reasons.add("page $p cell $i pointer $ptr out of range")
                        // Higher rowids are stored at lower offsets, so the pointer array must strictly descend.
                        if (ptr >= prev) reasons.add("page $p cell pointers are not strictly descending (rowid order)")
                        prev = ptr
                    }
                }
                PAGE_INTERIOR_TABLE -> {
                    interior++
                    val nCells = u16(db, off + 3)
                    for (i in 0 until nCells) {
                        val ptr = u16(db, off + 12 + i * 2)
                        if (ptr >= header.pageSize) reasons.add("page $p interior cell pointer $ptr out of range")
                    }
                    if (u32(db, off + 8).toInt() !in 1..maxOf(1, pages)) reasons.add("page $p rightChild out of range")
                }
                else -> reasons.add("page $p has unknown page type ${db[off].toInt() and 0xFF}")
            }
        }
        if (leaf == 0) reasons.add("no leaf table page found")
        return Integrity(reasons.isEmpty(), reasons, leaf, interior)
    }
}

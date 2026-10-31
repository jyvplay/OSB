// SPDX-License-Identifier: Apache-2.0
package dev.fusion.ooxml

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.util.Collections
import java.util.Locale

/**
 * A deliberately small, single-sheet, local authoring surface. This is NOT an XLSX importer,
 * an Excel-compatible calculation engine, or a replacement for the blocked Office sandbox.
 * The explicit type prevents text such as "=1+1" from becoming a formula accidentally.
 */
object OfficeSheetDraft {
    const val MAX_ROWS = 100
    const val MAX_COLUMNS = 26
    const val MAX_CELLS = 256
    const val MAX_FORMULAS = 64
    const val MAX_CELL_BYTES = 4_096
    const val MAX_FORMULA_CHARS = 512
    private const val MAX_FORMULA_REFERENCES = 32
    private const val MAX_TOTAL_TEXT_BYTES = 256 * 1024

    enum class Kind { TEXT, NUMBER, BOOLEAN, FORMULA }
    data class Cell(val kind: Kind, val input: String)

    /** Own the map rather than exposing the caller's mutable collection. */
    class Draft private constructor(val revision: Long, cells: Map<String, Cell>) {
        val cells: Map<String, Cell> = Collections.unmodifiableMap(LinkedHashMap(cells))
        override fun equals(other: Any?): Boolean = other is Draft && revision == other.revision && cells == other.cells
        override fun hashCode(): Int = 31 * revision.hashCode() + cells.hashCode()
        override fun toString(): String = "SheetDraft(revision=$revision, cells=${cells.size})"

        companion object {
            internal fun owned(revision: Long, cells: Map<String, Cell>): Draft = Draft(revision, cells)
        }
    }

    data class Calculated(val draft: Draft, val values: Map<String, FormulaEval.CellValue>)

    fun empty(): Draft = Draft.owned(0, emptyMap())

    fun address(raw: String): String {
        // Do not silently admit absolute markers, whitespace inside a ref, or other sheets.
        val normalized = raw.trim().uppercase(Locale.ROOT)
        require(Regex("[A-Z](?:[1-9]|[1-9][0-9]|100)").matches(normalized)) {
            "Use a cell from A1 to Z100"
        }
        return normalized
    }

    /** Evaluate and validate the whole candidate before it is made visible or persisted. */
    fun replace(draft: Draft, rawAddress: String, cell: Cell?): Calculated {
        val ref = address(rawAddress)
        if (draft.cells[ref] == cell) return calculate(draft)
        require(draft.revision < Long.MAX_VALUE) { "Sheet revision exhausted" }
        val next = LinkedHashMap(draft.cells)
        if (cell == null) next.remove(ref) else next[ref] = cell
        return restore(draft.revision + 1, next)
    }

    /** Used by the recovery codec; failure leaves existing recovery bytes untouched. */
    fun restore(revision: Long, cells: Map<String, Cell>): Calculated {
        require(revision >= 0) { "Invalid sheet revision" }
        require(cells.size <= MAX_CELLS) { "A draft can contain at most $MAX_CELLS populated cells" }
        val normalized = LinkedHashMap<String, Cell>()
        var totalBytes = 0
        var formulaCount = 0
        for ((ref, cell) in cells) {
            val key = address(ref)
            require(key == ref && key !in normalized) { "Noncanonical or duplicate cell address" }
            require(cell.input.length <= MAX_CELL_BYTES) { "Cell $key exceeds $MAX_CELL_BYTES characters" }
            val bytes = utf8(cell.input)
            require(bytes.size <= MAX_CELL_BYTES) { "Cell $key exceeds $MAX_CELL_BYTES UTF-8 bytes" }
            totalBytes += bytes.size
            require(totalBytes <= MAX_TOTAL_TEXT_BYTES) { "Sheet input budget exceeded" }
            validateXml(cell.input)
            when (cell.kind) {
                Kind.TEXT -> Unit
                Kind.NUMBER -> {
                    require(cell.input == cell.input.trim() && NUMBER.matches(cell.input)) { "Use a decimal number with optional exponent" }
                    require(cell.input.toDoubleOrNull()?.isFinite() == true) { "Number must be finite" }
                }
                Kind.BOOLEAN -> require(cell.input == "TRUE" || cell.input == "FALSE") { "Use TRUE or FALSE" }
                Kind.FORMULA -> { formulaCount++; validateFormula(cell.input) }
            }
            normalized[key] = cell
        }
        require(formulaCount <= MAX_FORMULAS) { "A draft can contain at most $MAX_FORMULAS formulas" }
        val draft = Draft.owned(revision, normalized)
        return calculate(draft)
    }

    fun calculate(draft: Draft): Calculated {
        val values = FormulaEval.evaluateSheet(inputs(draft)).values
        return Calculated(draft, Collections.unmodifiableMap(LinkedHashMap(values)))
    }

    /** XLSX only; the generated file is a copy and is never used to overwrite a source. */
    fun xlsx(draft: Draft): ByteArray = FormulaXlsx.write("Sheet1", inputs(draft))

    private fun inputs(draft: Draft): Map<String, FormulaEval.CellInput> = draft.cells.mapValues { (_, cell) ->
        when (cell.kind) {
            Kind.TEXT -> FormulaEval.CellInput.TextLiteral(cell.input)
            Kind.NUMBER -> FormulaEval.CellInput.NumberLiteral(cell.input.toDouble())
            Kind.BOOLEAN -> FormulaEval.CellInput.BoolLiteral(cell.input == "TRUE")
            Kind.FORMULA -> FormulaEval.CellInput.Formula(cell.input)
        }
    }

    private val NUMBER = Regex("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?")
    private val NUMBER_TOKEN = Regex("(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?")
    private val REFERENCE = Regex("\\$?[A-Za-z]\\$?(?:100|[1-9][0-9]?)")
    private val IDENTIFIER = Regex("[A-Za-z_][A-Za-z0-9_.]*")
    // CONCAT and '&' remain withheld because chained formulas can otherwise amplify text
    // exponentially without a derived-value budget. ROUND uses a bounded decimal scale.
    private val FUNCTIONS = setOf("SUM", "AVERAGE", "MIN", "MAX", "COUNT", "COUNTA", "IF", "IFERROR", "AND", "OR", "NOT", "ABS", "ROUND", "MOD", "INT", "LEN")

    /**
     * Admission is narrower than FormulaEval: no external/3-D links, named expressions,
     * dynamic arrays, volatile functions, unbounded generators, or references outside the UI.
     * FormulaEval is still responsible for parsing and cycle checking after lexical admission.
     */
    private fun validateFormula(raw: String) {
        require(raw.startsWith('=') && raw.length in 2..MAX_FORMULA_CHARS) { "Formula must start with = and be at most $MAX_FORMULA_CHARS characters" }
        var i = 1
        var references = 0
        while (i < raw.length) {
            val c = raw[i]
            when {
                c == ' ' || c == '\t' || c == '\n' || c == '\r' -> i++
                c == '"' -> {
                    i++
                    var closed = false
                    while (i < raw.length) {
                        if (raw[i++] == '"') {
                            if (i < raw.length && raw[i] == '"') i++ else { closed = true; break }
                        }
                    }
                    require(closed) { "Unclosed formula string" }
                }
                c in '0'..'9' || c == '.' -> {
                    val match = NUMBER_TOKEN.find(raw, i)?.takeIf { it.range.first == i }
                        ?: throw IllegalArgumentException("Malformed formula number")
                    require(match.value.toDoubleOrNull()?.isFinite() == true) { "Formula number must be finite" }
                    i = match.range.last + 1
                }
                c == '$' || c in 'A'..'Z' || c in 'a'..'z' || c == '_' -> {
                    val reference = REFERENCE.find(raw, i)?.takeIf { it.range.first == i }
                    val end = reference?.range?.last?.plus(1) ?: i
                    // A1foo and A1000 are not a reference followed by a second token.
                    val wholeRef = reference != null && (end == raw.length || !(raw[end].isLetterOrDigit() || raw[end] == '_' || raw[end] == '$' || raw[end] == '.'))
                    if (wholeRef) {
                        address(reference!!.value.replace("$", ""))
                        references++
                        require(references <= MAX_FORMULA_REFERENCES) { "Too many references in one formula" }
                        i = end
                    } else {
                        val match = IDENTIFIER.find(raw, i)?.takeIf { it.range.first == i }
                            ?: throw IllegalArgumentException("Invalid formula reference")
                        val word = match.value.uppercase(Locale.ROOT)
                        i = match.range.last + 1
                        var after = i
                        while (after < raw.length && raw[after].isWhitespace()) after++
                        require(if (after < raw.length && raw[after] == '(') word in FUNCTIONS else word == "TRUE" || word == "FALSE") {
                            "Unsupported function or name: $word"
                        }
                    }
                }
                c in "+-*/^=<>():,%" -> i++
                else -> throw IllegalArgumentException("Unsupported formula character: $c")
            }
        }
    }

    internal fun utf8(text: String): ByteArray {
        val encoded = Charsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(text))
        return ByteArray(encoded.remaining()).also { encoded.get(it) }
    }

    internal fun validateXml(text: String) {
        var index = 0
        while (index < text.length) {
            val c = text[index++]
            if (c.isHighSurrogate()) {
                require(index < text.length && text[index].isLowSurrogate()) { "Unpaired surrogate" }
                index++
            } else {
                require(!c.isLowSurrogate() && (c == '\t' || c == '\n' || c == '\r' || c.code in 0x20..0xD7FF || c.code in 0xE000..0xFFFD)) {
                    "Text contains a character not permitted in XML 1.0"
                }
            }
        }
    }
}

/** Checksummed, bounded private recovery, not an interchange format or authenticated storage. */
object OfficeSheetCodec {
    private const val MAGIC = 0x46534831 // FSH1
    private const val VERSION = 1
    const val MAX_FRAME = 512 * 1024

    fun write(draft: OfficeSheetDraft.Draft, output: OutputStream) {
        val payload = ByteArrayOutputStream()
        DataOutputStream(payload).use { out ->
            out.writeInt(VERSION)
            out.writeLong(draft.revision)
            out.writeInt(draft.cells.size)
            for ((ref, cell) in draft.cells.toSortedMap()) {
                writeText(out, ref)
                out.writeByte(cell.kind.ordinal)
                writeText(out, cell.input)
            }
        }
        val bytes = payload.toByteArray()
        require(bytes.size + 40 <= MAX_FRAME) { "Sheet checkpoint too large" }
        val out = DataOutputStream(output)
        out.writeInt(MAGIC)
        out.writeInt(bytes.size)
        out.write(bytes)
        out.write(MessageDigest.getInstance("SHA-256").digest(bytes))
        out.flush()
    }

    fun read(input: InputStream): OfficeSheetDraft.Draft {
        val frame = input.readNBytes(MAX_FRAME + 1)
        require(frame.size in 40..MAX_FRAME) { "Sheet checkpoint size invalid" }
        val data = DataInputStream(ByteArrayInputStream(frame))
        require(data.readInt() == MAGIC) { "Unknown sheet checkpoint" }
        val size = data.readInt()
        require(size >= 16 && size == frame.size - 40) { "Sheet checkpoint length mismatch" }
        val payload = ByteArray(size)
        data.readFully(payload)
        val digest = ByteArray(32)
        data.readFully(digest)
        require(MessageDigest.isEqual(digest, MessageDigest.getInstance("SHA-256").digest(payload))) { "Sheet checkpoint checksum mismatch" }
        val body = DataInputStream(ByteArrayInputStream(payload))
        require(body.readInt() == VERSION) { "Unknown sheet checkpoint version" }
        val revision = body.readLong()
        val count = body.readInt()
        require(count in 0..OfficeSheetDraft.MAX_CELLS) { "Sheet checkpoint cell count invalid" }
        val cells = LinkedHashMap<String, OfficeSheetDraft.Cell>()
        repeat(count) {
            val ref = readText(body, 8)
            val kind = OfficeSheetDraft.Kind.entries.getOrNull(body.readUnsignedByte())
                ?: throw IllegalArgumentException("Unknown cell type")
            val text = readText(body, OfficeSheetDraft.MAX_CELL_BYTES)
            require(cells.put(ref, OfficeSheetDraft.Cell(kind, text)) == null) { "Duplicate cell" }
        }
        require(body.available() == 0) { "Trailing sheet checkpoint data" }
        return OfficeSheetDraft.restore(revision, cells).draft
    }

    private fun writeText(out: DataOutputStream, text: String) {
        val bytes = OfficeSheetDraft.utf8(text)
        out.writeInt(bytes.size)
        out.write(bytes)
    }

    private fun readText(input: DataInputStream, limit: Int): String {
        val size = input.readInt()
        require(size in 0..minOf(limit, input.available())) { "Invalid sheet checkpoint string length" }
        val bytes = ByteArray(size)
        input.readFully(bytes)
        return Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
    }
}

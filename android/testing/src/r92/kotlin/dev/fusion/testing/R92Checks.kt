// SPDX-License-Identifier: Apache-2.0
package dev.fusion.testing

import dev.fusion.ooxml.XlsxProtection
import dev.fusion.ooxml.XlsxStyledBook
import java.io.File

/**
 * R92 spreadsheet contracts: worksheet protection (`<sheetProtection>`, its fifteen action flags and the
 * legacy 16-bit password hash) and per-cell `<protection>` inside an `xf`.
 *
 * Host JVM only. The probe writes real `.xlsx` packages plus three intent tables — `protections.tsv`,
 * `cell_protection.tsv` and `password_hashes.tsv`. Independent witnesses:
 * `tools/check_r92_sheet_protection.py` (raw OPC + openpyxl's `SheetProtection`/`Protection` models) and
 * `tools/check_r92_password_hash.py` (every hash recomputed with openpyxl's own implementation). No Excel,
 * LibreOffice or Android run: nothing here says a cell was protected against editing.
 */
object R92Checks {
    private var assertions = 0

    private inline fun check(ok: Boolean, message: () -> String) {
        assertions++
        require(ok) { "FAILED: " + message() }
    }

    private fun text(bytes: ByteArray): String = String(bytes, Charsets.ISO_8859_1)

    private fun part(bytes: ByteArray, entry: String): String {
        var found: String? = null
        java.util.zip.ZipInputStream(java.io.ByteArrayInputStream(bytes)).use { input ->
            while (found == null) {
                val current = input.nextEntry ?: break
                val payload = input.readBytes()
                if (current.name == entry) found = text(payload)
            }
        }
        return found ?: error("missing OPC part $entry")
    }

    private fun cell(ref: String, value: String, style: XlsxStyledBook.CellStyle = XlsxStyledBook.CellStyle()) =
        XlsxStyledBook.StyledCell(ref, XlsxStyledBook.StyledVal.Txt(value), style)

    /** Everything blocked except column formatting, plus the two selection flags inverted from the default. */
    private val POLICY: Set<XlsxProtection.Action> =
        (XlsxProtection.DEFAULT_BLOCKED - XlsxProtection.Action.FORMAT_COLUMNS) +
            XlsxProtection.Action.SELECT_LOCKED_CELLS

    private val PROTECTED = XlsxProtection.SheetProtection.ofPassword("secret").copy(blocked = POLICY)
    private val OPEN = XlsxProtection.SheetProtection(blocked = emptySet())

    private fun writeIntent(folder: File, entries: List<Triple<String, String, XlsxProtection.SheetProtection?>>) {
        val lines = ArrayList<String>()
        for ((file, sheet, protection) in entries) {
            val flags = XlsxProtection.Action.values().map {
                if (protection != null && it in protection.blocked) "1" else "0"
            }
            lines.add(
                (listOf(file, sheet, if (protection == null) "0" else "1", protection?.passwordHash ?: "-") + flags)
                    .joinToString("\t"),
            )
        }
        File(folder, "protections.tsv").writeText(lines.joinToString("\n", postfix = "\n"), Charsets.UTF_8)
    }

    private fun writeCellIntent(folder: File, entries: List<List<String>>) {
        File(folder, "cell_protection.tsv").writeText(
            entries.joinToString("\n", postfix = "\n") { it.joinToString("\t") }, Charsets.UTF_8,
        )
    }

    private fun passwordCorpus(): List<String> {
        val passwords = LinkedHashSet<String>()
        passwords.add("")
        for (code in 32..126) {
            val ch = code.toChar()
            passwords.add(ch.toString())
            passwords.add("a$ch")
            passwords.add("ab$ch")
        }
        passwords.add("secret")
        passwords.add("Password1")
        passwords.add("pass word")
        passwords.add("tab\there")
        passwords.add("quote\"'`")
        passwords.add("\u00fcn\u00efc\u00f8d\u00e9")
        passwords.add("\u03a9\u2248\u00e7\u221a")
        passwords.add("x".repeat(15))
        passwords.add("y".repeat(14))
        passwords.add("0123456789abcde")
        passwords.add("\uFFFF")
        for (n in 1..15) passwords.add("\uFFFF".repeat(n))
        return passwords.toList()
    }

    private fun writeHashes(folder: File, passwords: List<String>) {
        val lines = passwords.map { it.replace("\t", "\\t").replace("\n", "\\n") + "\t" + XlsxProtection.hash(it) }
        File(folder, "password_hashes.tsv").writeText(lines.joinToString("\n", postfix = "\n"), Charsets.UTF_8)
    }

    private fun refuses(name: String, block: () -> Unit) {
        assertions++
        val threw = try {
            block()
            false
        } catch (expected: IllegalArgumentException) {
            check(expected.message?.isNotBlank() == true) { "$name refused without a message" }
            true
        }
        require(threw) { "FAILED: $name was accepted" }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val folder = File(args.firstOrNull() ?: "testing/build/r92/artifacts")
        folder.mkdirs()

        // 1. No protection anywhere: the sheet and styles parts stay structurally identical to R91.
        val plain = XlsxStyledBook.write(
            XlsxStyledBook.WorkbookSpec(
                sheets = listOf(XlsxStyledBook.StyledSheet("Plain", listOf(cell("A1", "open")))),
            ),
        )
        File(folder, "prot-plain.xlsx").writeBytes(plain)
        val plainSheet = part(plain, "xl/worksheets/sheet1.xml")
        check(!plainSheet.contains("sheetProtection")) { "an unprotected sheet emitted sheetProtection" }
        check(plainSheet.endsWith("</sheetData></worksheet>")) { "sheet tail changed: $plainSheet" }
        check(!part(plain, "xl/styles.xml").contains("<protection")) { "an unprotected style emitted <protection>" }

        // 2. A protected sheet with a mixed policy, and cells that opt out of locking.
        val editable = XlsxStyledBook.CellStyle(locked = false)
        val hidden = XlsxStyledBook.CellStyle(hiddenFormula = true)
        val both = XlsxStyledBook.CellStyle(locked = false, hiddenFormula = true)
        val protectedBook = XlsxStyledBook.write(
            XlsxStyledBook.WorkbookSpec(
                sheets = listOf(
                    XlsxStyledBook.StyledSheet(
                        name = "Locked",
                        cells = listOf(
                            cell("A1", "default-locked"),
                            cell("A2", "editable", editable),
                            cell("A3", "hidden-formula", hidden),
                            cell("A4", "both", both),
                            cell("A5", "editable again", editable),
                        ),
                        protection = PROTECTED,
                    ),
                ),
            ),
        )
        File(folder, "prot.xlsx").writeBytes(protectedBook)
        val protectedSheet = part(protectedBook, "xl/worksheets/sheet1.xml")
        val styles = part(protectedBook, "xl/styles.xml")
        check(protectedSheet.contains("""<sheetProtection password="${XlsxProtection.hash("secret")}" sheet="1"""")) {
            "the password hash or sheet flag is wrong: $protectedSheet"
        }
        check(protectedSheet.indexOf("<sheetProtection") > protectedSheet.indexOf("</sheetData>")) {
            "sheetProtection must follow sheetData"
        }
        check(protectedSheet.indexOf("<sheetProtection") < protectedSheet.indexOf("</worksheet>")) {
            "sheetProtection must sit inside the worksheet"
        }
        check(protectedSheet.contains(""" formatColumns="0"""")) { "the allowed action was written as blocked" }
        check(protectedSheet.contains(""" selectLockedCells="1"""")) {
            "a schema-default-allowed action was not written as blocked"
        }
        check(styles.contains(""" applyProtection="1"""")) { "applyProtection was not written" }
        check(styles.contains("""<protection locked="0"/>""")) { "the unlocked cell style is missing <protection>" }
        check(styles.contains("""<protection hidden="1"/>""")) { "the hidden-formula style is missing <protection>" }
        check(styles.contains("""<protection locked="0" hidden="1"/>""")) { "combined protection is wrong" }
        // A5 reuses A2's style: four distinct xf records for five cells, so protection still interns.
        check(Regex("""<cellXfs count="(\d+)">""").find(styles)?.groupValues?.get(1) == "4") {
            "expected 4 xf records, got ${Regex(""".*cellXfs count="(\d+)".*""").find(styles)?.groupValues?.get(1)}"
        }

        // 3. Protection without a password writes no password attribute at all.
        val openBook = XlsxStyledBook.write(
            XlsxStyledBook.WorkbookSpec(
                sheets = listOf(XlsxStyledBook.StyledSheet("Open", listOf(cell("A1", "x")), protection = OPEN)),
            ),
        )
        File(folder, "prot-nopw.xlsx").writeBytes(openBook)
        val openSheet = part(openBook, "xl/worksheets/sheet1.xml")
        check(!openSheet.contains("password=")) { "a password-free protection wrote a password" }
        check(openSheet.contains("""<sheetProtection sheet="1"""")) { "protection without a password was dropped" }
        check(openSheet.contains(""" formatCells="0"""")) { "an empty blocked set still blocks formatting" }

        // 4. Round-trip: hash and every action flag survive this code's own reader.
        val readBook = XlsxStyledBook.read(protectedBook)
        check(readBook.sheets[0].protection == PROTECTED) {
            "protection round-trip changed the record: ${readBook.sheets[0].protection}"
        }
        val readStyles = readBook.sheets[0].cells.map { it.style }
        check(readStyles[1].locked == false) { "locked=false did not read back: ${readStyles[1]}" }
        check(readStyles[2].hiddenFormula == true) { "hidden=true did not read back: ${readStyles[2]}" }
        check(readStyles[3].locked == false && readStyles[3].hiddenFormula == true) { "combined protection" }
        check(readStyles[0].locked == null && readStyles[0].hiddenFormula == null) {
            "a default cell invented a protection value: ${readStyles[0]}"
        }
        check(XlsxStyledBook.read(plain).sheets[0].protection == null) { "an unprotected sheet read back protected" }
        check(XlsxStyledBook.read(openBook).sheets[0].protection == OPEN) { "password-free round-trip" }

        // 5. Refusals: each would produce a hash that no other implementation would agree with.
        refuses("a 16-character password") { XlsxProtection.hash("x".repeat(16)) }
        refuses("an astral-character password") { XlsxProtection.hash("a\uD834\uDD1E") }
        refuses("a lower-case hash") { XlsxProtection.SheetProtection(passwordHash = "abcd") }
        refuses("a non-hex hash") { XlsxProtection.SheetProtection(passwordHash = "ZZZZ") }
        refuses("an over-long hash") { XlsxProtection.SheetProtection(passwordHash = "012345678") }

        // 6. Hash corpus and the padding boundary it must contain.
        val passwords = passwordCorpus()
        writeHashes(folder, passwords)
        val hashes = passwords.map { XlsxProtection.hash(it) }
        check(hashes.all { it.length == 4 }) { "a hash is not four hex digits: ${hashes.filter { it.length != 4 }}" }
        check(hashes.all { it.matches(Regex("[0-9A-F]{4}")) }) { "a hash is not upper-case hex" }
        // The mask keeps the value in 16 bits, so the spelling is exactly four digits; whether a value below
        // 0x1000 can occur at all is left to the corpus rather than asserted from a hand argument (an earlier
        // "always >= 0x8000" claim was wrong and the corpus refuted it within one run).
        check(hashes.all { it.toInt(16) in 0..0xFFFF }) { "a hash escaped the 16-bit mask" }
        check(hashes.toSet().size >= passwords.size * 3 / 4) {
            "the corpus collapses under the hash (${hashes.toSet().size} distinct of ${passwords.size})"
        }
        check(hashes.toSet().size > passwords.size / 2) { "the hash collapses the corpus; the corpus is too uniform" }

        // 7. Artifacts for the independent readers.
        writeIntent(
            folder,
            listOf(
                Triple("prot.xlsx", "Locked", PROTECTED),
                Triple("prot-nopw.xlsx", "Open", OPEN),
                Triple("prot-plain.xlsx", "Plain", null),
            ),
        )
        writeCellIntent(
            folder,
            listOf(
                listOf("prot.xlsx", "Locked", "A1", "-", "-"),
                listOf("prot.xlsx", "Locked", "A2", "false", "-"),
                listOf("prot.xlsx", "Locked", "A3", "-", "true"),
                listOf("prot.xlsx", "Locked", "A4", "false", "true"),
                listOf("prot.xlsx", "Locked", "A5", "false", "-"),
            ),
        )

        println("R92_OFFICE_OK assertions=$assertions workbooks=3 passwords=${passwords.size} actions=${XlsxProtection.Action.values().size}")
    }
}

// SPDX-License-Identifier: Apache-2.0
package dev.fusion.testing

import dev.fusion.ooxml.CsvTable
import dev.fusion.ooxml.LiteralXlsxImport
import java.io.File
import java.security.MessageDigest

/** Separate JVM per implementation; hashes a canonical public data-only frame, not an Office rendering. */
object R60XlsxProbe {
    private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun inspect(bytes: ByteArray): String {
        val original = bytes.copyOf()
        val table = try { LiteralXlsxImport.read(bytes) } catch (_: Exception) {
            check(bytes.contentEquals(original)) { "refused input was mutated" }
            return "REFUSED"
        }
        check(bytes.contentEquals(original)) { "accepted input was mutated" }
        val frame = table.frame()
        check(CsvTable.readFrame(frame).frame().contentEquals(frame))
        if (table.rows.isNotEmpty()) {
            var blocked = false
            try { (table.rows as MutableList<List<String>>).clear() } catch (_: Exception) { blocked = true }
            check(blocked && table.frame().contentEquals(frame)) { "mutable outer table" }
            blocked = false
            try { (table.rows[0] as MutableList<String>)[0] = "changed" } catch (_: Exception) { blocked = true }
            check(blocked && table.frame().contentEquals(frame)) { "mutable inner row" }
        }
        return "OK\t${digest(frame)}"
    }

    @JvmStatic fun main(args: Array<String>) {
        if (args.firstOrNull() == "--bench") {
            require(args.size == 4)
            val input = File(args[1]).readBytes()
            val warmups = args[2].toInt()
            val measured = args[3].toInt()
            val expected = inspect(input)
            require(expected.startsWith("OK\t"))
            val times = ArrayList<Long>()
            repeat(warmups + measured) { index ->
                val start = System.nanoTime()
                val table = LiteralXlsxImport.read(input)
                val duration = System.nanoTime() - start
                check("OK\t${digest(table.frame())}" == expected)
                if (index >= warmups) times.add(duration)
            }
            println("BENCH\t" + times.joinToString(","))
            return
        }
        require(args.isEmpty())
        generateSequence(::readLine).forEach { path ->
            println(inspect(File(path).readBytes()))
        }
    }
}

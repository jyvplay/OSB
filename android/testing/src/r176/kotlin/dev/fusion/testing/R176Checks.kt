// SPDX-License-Identifier: Apache-2.0
package dev.fusion.testing

import dev.fusion.security.diagnostics.DiagnosticSaveWorkflow
import dev.fusion.security.diagnostics.DiagnosticArchiveInspector
import dev.fusion.security.diagnostics.DiagnosticExportSession
import dev.fusion.model.SemVer
import dev.fusion.model.VersionRange
import dev.fusion.settings.SettingsCatalog
import dev.fusion.calc.Units
import java.nio.file.Files
import java.nio.file.Path
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Real JVM and filesystem protocol experiment. No Android/runtime substitutes. */
object R176Checks {
    private var assertions = 0
    private fun yes(value: Boolean) { check(value); assertions++ }
    private fun refuses(action: () -> Unit) {
        try { action() } catch (_: IllegalArgumentException) { assertions++; return }
        error("expected refusal")
    }
    private fun prepared(): DiagnosticSaveWorkflow = DiagnosticSaveWorkflow().apply {
        prepare("app.version,operator.note", listOf("app.version" to "2.1", "operator.note" to "dev@example.org"))
    }
    private fun ticket(flow: DiagnosticSaveWorkflow): DiagnosticSaveWorkflow.Ticket =
        flow.beginSave(requireNotNull(flow.snapshot().sha256))

    private class FileDestination(val path: Path) : DiagnosticSaveWorkflow.Destination {
        var opens = 0
        override fun openOutput(): OutputStream { opens++; return Files.newOutputStream(path) }
        override fun openInput(): InputStream = Files.newInputStream(path)
    }

    @JvmStatic fun main(args: Array<String>) {
        val dir = Path.of(args[0]); Files.createDirectories(dir)
        when (args.getOrNull(1)) {
            "ranges" -> {
                Files.readAllLines(dir.resolve("range-input.tsv")).forEachIndexed { index, line ->
                    val fields = line.split('\t')
                    val result = try { VersionRange.satisfies(fields[0], fields[1]).toString() }
                        catch (_: IllegalArgumentException) { "REF" }
                    println("$index\t$result")
                }
                return
            }
            "decimals" -> {
                Files.readAllLines(dir.resolve("decimal-input.tsv")).forEachIndexed { index, line ->
                    val fields = line.split('\t')
                    val result = try {
                        val value = when (fields[0]) {
                            "add" -> dev.fusion.calc.DecimalMath.add(fields[1], fields[2]).value
                            "subtract" -> dev.fusion.calc.DecimalMath.subtract(fields[1], fields[2]).value
                            "multiply" -> dev.fusion.calc.DecimalMath.multiply(fields[1], fields[2]).value
                            "divide" -> dev.fusion.calc.DecimalMath.divide(fields[1], fields[2]).value
                            else -> error("unknown operation")
                        }
                        value
                    } catch (_: IllegalArgumentException) { "REF" }
                      catch (_: ArithmeticException) { "REF" }
                    println("$index\t$result")
                }
                return
            }
            "inspect" -> {
                Files.readAllLines(dir.resolve("archives.txt")).forEach { name ->
                    val result = try {
                        val report = DiagnosticArchiveInspector.inspect(dir.resolve(name))
                        "OK:${report.records}:${report.utf8Bytes}:${report.sha256}"
                    } catch (_: Exception) { "REF" }
                    println("$name\t$result")
                }
                return
            }
        }
        val flow = prepared()
        val snapshot = flow.snapshot()
        yes(snapshot.phase == DiagnosticSaveWorkflow.Phase.REVIEW)
        yes(snapshot.review == "app.version=2.1\noperator.note=[email]")
        refuses { flow.beginSave("0".repeat(64)) }
        yes(flow.snapshot() == snapshot)
        val first = ticket(flow)
        refuses { ticket(flow) }
        refuses { flow.prepare("", emptyList()) }
        val other = prepared(); val otherTicket = ticket(other)
        refuses { flow.cancelPicker(otherTicket) } // same digest/sequence, different owner
        flow.cancelPicker(first)
        yes(flow.snapshot().phase == DiagnosticSaveWorkflow.Phase.REVIEW)
        val second = ticket(flow)
        val dest = FileDestination(dir.resolve("saved.zip"))
        refuses { flow.save(first, dest) }
        yes(dest.opens == 0)
        // A brand-new process/session must not accept the previous picker result.
        refuses { DiagnosticSaveWorkflow().save(second, dest) }
        yes(dest.opens == 0)
        yes(flow.save(second, dest))
        yes(dest.opens == 1)
        yes(flow.snapshot().phase == DiagnosticSaveWorkflow.Phase.SAVED)
        yes(flow.snapshot().review == null && flow.snapshot().sha256 == null)
        refuses { flow.save(second, dest) }
        yes(dest.opens == 1)
        val report = DiagnosticArchiveInspector.inspect(dest.path)
        yes(report.records == 2 && report.sha256 == snapshot.sha256)
        Files.writeString(dir.resolve("export-content.txt"), requireNotNull(snapshot.review))
        Files.writeString(dir.resolve("export.sha256"), report.sha256)
        // Invalid new input invalidates old approval before parsing.
        val invalid = prepared()
        refuses { invalid.prepare("bad key", emptyList()) }
        yes(invalid.snapshot().phase == DiagnosticSaveWorkflow.Phase.EMPTY)
        yes(invalid.snapshot().review == null)
        refuses { invalid.beginSave(requireNotNull(snapshot.sha256)) }
        // Discard invalidates all outstanding tickets; late result opens nothing.
        val discarded = prepared(); val lost = ticket(discarded); discarded.discard()
        refuses { discarded.save(lost, dest) }; yes(dest.opens == 1)
        // Provider failure, truncated/corrupt/extra readback, non-progress stream.
        for (mode in listOf("open", "write", "close", "read-open", "truncated", "corrupt", "extra", "no-progress")) {
            val failing = prepared(); val issued = ticket(failing)
            val bytes = ByteArrayOutputStream()
            val destination = object : DiagnosticSaveWorkflow.Destination {
                override fun openOutput(): OutputStream {
                    if (mode == "open") throw java.io.IOException("injected open failure")
                    return object : OutputStream() {
                        override fun write(value: Int) {
                            if (mode == "write") throw java.io.IOException("injected write failure")
                            bytes.write(value)
                        }
                        override fun close() { if (mode == "close") throw java.io.IOException("injected close failure") }
                    }
                }
                override fun openInput(): InputStream {
                    if (mode == "read-open") throw java.io.IOException("injected read open failure")
                    val raw = bytes.toByteArray()
                    return when (mode) {
                        "no-progress" -> object : InputStream() {
                            override fun read() = 0
                            override fun read(b: ByteArray, off: Int, len: Int) = 0
                        }
                        "truncated" -> ByteArrayInputStream(raw.copyOf(raw.size - 1))
                        "corrupt" -> ByteArrayInputStream(raw.apply { this[0] = 0 })
                        "extra" -> ByteArrayInputStream(raw + byteArrayOf(1))
                        else -> ByteArrayInputStream(raw)
                    }
                }
            }
            yes(!failing.save(issued, destination))
            yes(failing.snapshot().phase == DiagnosticSaveWorkflow.Phase.FAILED)
            yes(failing.snapshot().review == null)
            refuses { failing.save(issued, destination) }
        }
        // Actual two-thread race: while the first writer owns the ticket the
        // second call cannot acquire it or open any output destination.
        val race = prepared(); val raceTicket = ticket(race)
        val opened = CountDownLatch(1); val release = CountDownLatch(1)
        val raceDestination = object : DiagnosticSaveWorkflow.Destination {
            private val bytes = ByteArrayOutputStream()
            override fun openOutput(): OutputStream {
                opened.countDown(); check(release.await(5, TimeUnit.SECONDS)); return bytes
            }
            override fun openInput(): InputStream = ByteArrayInputStream(bytes.toByteArray())
        }
        var workerSuccess = false
        val thread = Thread { workerSuccess = race.save(raceTicket, raceDestination) }
        thread.start()
        try {
            yes(opened.await(5, TimeUnit.SECONDS))
            refuses { race.save(raceTicket, dest) }
            refuses { race.discard() }
        } finally { release.countDown(); thread.join(6000) }
        yes(!thread.isAlive && workerSuccess)
        // C1 controls, bidi overrides and isolated surrogate attacks on review.
        for (text in listOf("a\nforged=x", "x\u0085fake=y", "x\u2028fake", "x\u202eexe", "x\u2066", "\ud800", "\udfff")) {
            refuses { DiagnosticExportSession.prepare("note", listOf("note" to text)) }
        }
        val emoji = DiagnosticExportSession.prepare("note", listOf("note" to "hello 😀"))
        yes(emoji.utf8Bytes == 15)
        // Existing calculator and settings APIs compile TOGETHER, not in
        // separate islands that conceal duplicate declarations.
        val catalog = SettingsCatalog(listOf(dev.fusion.settings.SettingDef("mode", "Mode")))
        yes(catalog.decode(catalog.encode(mapOf("mode" to "local"))) == mapOf("mode" to "local"))
        val values = SettingsCatalog.empty(); values.put("shell.mode", "local")
        yes(values.get("shell.mode") == "local")
        refuses { values.put("shell.mode", "x\u0000y") }
        yes(Units.convert(java.math.BigDecimal("1"), "m", "cm").compareTo(java.math.BigDecimal("100")) == 0)
        yes(Units.convert(1.0, "m", "cm").value == 100.0)
        yes("time" in Units.dimensionNames())
        val huge = "9".repeat(200)
        yes(SemVer.parse("1.0.0-$huge") > SemVer.parse("1.0.0-" + "8".repeat(199)))
        yes(VersionRange.satisfies("1.2.3+different", "==1.2.3+build"))
        yes(!VersionRange.satisfies("1.2.2", "^1.2.3"))
        yes(VersionRange.parse("~0.0.3").upperExclusive == SemVer.parse("0.1.0"))
        refuses { VersionRange.parse("^9223372036854775807.0.0") }
        yes(dev.fusion.calc.DecimalMath.add("0.1", "0.2").value == "0.3")
        refuses { dev.fusion.calc.DecimalMath.add("0e-2147483647", "1") }
        refuses { dev.fusion.calc.DecimalMath.multiply("1e1000000", "0") }
        refuses { dev.fusion.calc.DecimalMath.add("1".repeat(513), "1") }
        println("R176_SYSTEM_OK assertions=$assertions real_threads=true real_files=true")
    }
}

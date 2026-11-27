// SPDX-License-Identifier: Apache-2.0
package dev.fusion.tools

import dev.fusion.privacy.DiagnosticExportAllowlist
import dev.fusion.privacy.DiagnosticExportSession
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/** R146 operator tool. Does not collect anything; input must be explicitly supplied.
 * Usage: ALLOWLIST INPUT [SHA256 NEW_OUTPUT.zip]
 * INPUT is UTF-8, one key=literal-value record per line; no percent decoding.
 * First run prints the exact review and digest. A second invocation with that
 * digest publishes only if the freshly read input produces the same review.
 */
object DiagnosticExportCli {
    @JvmStatic fun main(args: Array<String>) {
        try {
            require(args.size == 2 || args.size == 4) { "usage: ALLOWLIST INPUT [SHA256 NEW_OUTPUT.zip]" }
            val input = Path.of(args[1])
            val maxBytes = DiagnosticExportAllowlist.MAX_RECORDS *
                (DiagnosticExportAllowlist.MAX_VALUE * 4 + DiagnosticExportAllowlist.MAX_KEY + 2)
            val raw = Files.newInputStream(input).use { it.readNBytes(maxBytes + 1) }
            require(raw.size <= maxBytes) { "input too large" }
            val decoded = Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(raw)).toString()
            val lines = decoded.removeSuffix("\n").split('\n').let {
                if (it == listOf("")) emptyList() else it
            }
            require(lines.size <= DiagnosticExportAllowlist.MAX_RECORDS) { "too many records" }
            val records = lines.map { line ->
                val eq = line.indexOf('=')
                require(eq >= 1) { "expected key=value records" }
                line.substring(0, eq) to line.substring(eq + 1)
            }
            val review = DiagnosticExportSession.prepare(args[0], records)
            println(DiagnosticExportSession.WARNING)
            println("--- exact diagnostics.txt ---")
            println(review.text)
            println("--- end; UTF-8 bytes=${review.utf8Bytes}; SHA256=${review.sha256} ---")
            if (args.size == 4) {
                require(args[2] == review.sha256) { "input changed or approval digest incorrect" }
                // CREATE_NEW: never overwrite an existing file, including a symlink.
                // Partial new files are removed on failure; existing files are untouched.
                val target = Path.of(args[3])
                val output = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
                try {
                    output.use { review.writeApproved(args[2], it) }
                } catch (failure: Exception) {
                    try { Files.deleteIfExists(target) } catch (_: Exception) { /* preserve original failure */ }
                    throw failure
                }
                println("Archive written. Review the recipient before sharing.")
            }
        } catch (_: Exception) {
            // Do not reflect input records, paths, or provider exception text.
            System.err.println("Export refused or failed. Check bounded key=value input, allowlist, approval and NEW destination.")
            kotlin.system.exitProcess(1)
        }
    }
}

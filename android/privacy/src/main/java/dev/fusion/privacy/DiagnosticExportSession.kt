// SPDX-License-Identifier: Apache-2.0
package dev.fusion.privacy

import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * R146 offline diagnostics review/export workflow. No collectors, network,
 * scratchpad access or disk access. Callers supply bounded records explicitly.
 * Prepare snapshots the scrubbed text; export requires the digest of that exact
 * snapshot, preventing approval of an earlier review from exporting newer data.
 * The caller owns the explicit human confirmation gesture and destination.
 *
 * Pattern scrubbing is heuristic, NOT anonymisation. The review must still be
 * read. A failing output stream can contain a partial ZIP; callers must report
 * failure and discard it. Closing the archive does not close the caller's stream.
 */
object DiagnosticExportSession {
    const val WARNING = "Heuristic redaction only. Review for secrets before sharing. Not anonymous."

    fun prepare(allowSpec: String, records: List<Pair<String, String>>): Review =
        Review(DiagnosticExportAllowlist.export(allowSpec, records))

    class Review internal constructor(val text: String) {
        // Immutable snapshot; byte arrays never leave this object.
        private val bytes = text.toByteArray(Charsets.UTF_8)
        val sha256: String = digest(bytes)
        val utf8Bytes: Int get() = bytes.size

        /** Writes a real two-entry ZIP only after the snapshot digest matches. */
        fun writeApproved(approvedSha256: String, destination: OutputStream) {
            require(approvedSha256 == sha256) { "approval does not match reviewed snapshot" }
            // ZipOutputStream.close() closes its stream; this wrapper preserves
            // caller ownership while still releasing the native deflater.
            val borrowed = object : java.io.FilterOutputStream(destination) {
                override fun close() { flush() }
            }
            ZipOutputStream(borrowed).use { zip ->
                fun entry(name: String, content: ByteArray) {
                    zip.putNextEntry(ZipEntry(name).apply { time = 0L })
                    zip.write(content)
                    zip.closeEntry()
                }
                entry("diagnostics.txt", bytes)
                entry("manifest.txt", ("format=FDE1\nsha256=$sha256\nbytes=${bytes.size}\n" +
                    "warning=$WARNING\n").toByteArray(Charsets.UTF_8))
            }
        }
    }

    private fun digest(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {
            (it.toInt() and 255).toString(16).padStart(2, '0')
        }
}

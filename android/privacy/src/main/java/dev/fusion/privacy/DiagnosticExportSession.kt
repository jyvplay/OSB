// SPDX-License-Identifier: Apache-2.0
package dev.fusion.privacy

/** R146 compatibility facade. Does not collect data or enable a scratchpad export route. */
object DiagnosticExportSession {
    const val WARNING = dev.fusion.security.diagnostics.DiagnosticExportSession.WARNING
    fun prepare(allowSpec: String, records: List<Pair<String, String>>): Review =
        Review(dev.fusion.security.diagnostics.DiagnosticExportSession.prepare(allowSpec, records))
    class Review internal constructor(private val shared: dev.fusion.security.diagnostics.DiagnosticExportSession.Review) {
        val text: String get() = shared.text
        val sha256: String get() = shared.sha256
        val utf8Bytes: Int get() = shared.utf8Bytes
        fun writeApproved(approvedSha256: String, destination: java.io.OutputStream) =
            shared.writeApproved(approvedSha256, destination)
    }
}

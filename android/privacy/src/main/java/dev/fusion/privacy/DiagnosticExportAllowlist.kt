// SPDX-License-Identifier: Apache-2.0
package dev.fusion.privacy

/** R146 API retained. R176 shares the implementation without giving Shell access to scratchpad data. */
object DiagnosticExportAllowlist {
    private val engine = dev.fusion.security.diagnostics.DiagnosticExportAllowlist
    const val SCOPE = dev.fusion.security.diagnostics.DiagnosticExportAllowlist.SCOPE
    const val MAX_RECORDS = dev.fusion.security.diagnostics.DiagnosticExportAllowlist.MAX_RECORDS
    const val MAX_ALLOW = dev.fusion.security.diagnostics.DiagnosticExportAllowlist.MAX_ALLOW
    const val MAX_VALUE = dev.fusion.security.diagnostics.DiagnosticExportAllowlist.MAX_VALUE
    const val MAX_KEY = dev.fusion.security.diagnostics.DiagnosticExportAllowlist.MAX_KEY
    fun scrubValue(value: String): String = engine.scrubValue(value)
    fun export(allowSpec: String, recordsSpec: String): String = engine.export(allowSpec, recordsSpec)
    fun export(allowSpec: String, records: List<Pair<String, String>>): String = engine.export(allowSpec, records)
}

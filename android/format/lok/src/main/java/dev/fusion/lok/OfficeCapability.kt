// SPDX-License-Identifier: Apache-2.0
package dev.fusion.lok

/** Honest support record: flags plus the gates keeping the rest closed. */
data class FormatSupport(val format: String, val view: Boolean, val edit: Boolean, val gates: List<String>)

/**
 * Bounded Office-format capability matrix. View flags name surfaces that
 * exist elsewhere in the tree (PDF preview, plain-text screens); every edit
 * flag is false and every unsupported op carries the open gates quoted from
 * [LokBridge]. Unknown formats yield an explicit unsupported record rather
 * than a refusal, so callers can probe. Format ids normalize to lowercase
 * with one leading dot stripped.
 */
object OfficeCapability {
    const val GATE_ENGINE = "engine payload"
    const val GATE_HOSTING = "Android hosting"
    const val GATE_MEMORY = "memory"
    const val GATE_FIDELITY = "fidelity"
    const val GATE_SECURITY = "security"
    const val GATE_LICENSE = "license"
    private val OFFICE_GATES = listOf(GATE_ENGINE, GATE_HOSTING, GATE_MEMORY, GATE_FIDELITY, GATE_SECURITY, GATE_LICENSE)
    private val MATRIX = mapOf(
        "pdf" to FormatSupport("pdf", true, false, OFFICE_GATES),
        "txt" to FormatSupport("txt", true, false, listOf(GATE_FIDELITY)),
        "csv" to FormatSupport("csv", true, false, listOf(GATE_FIDELITY)),
        "odt" to FormatSupport("odt", false, false, OFFICE_GATES),
        "ods" to FormatSupport("ods", false, false, OFFICE_GATES),
        "odp" to FormatSupport("odp", false, false, OFFICE_GATES),
        "docx" to FormatSupport("docx", false, false, OFFICE_GATES),
        "xlsx" to FormatSupport("xlsx", false, false, OFFICE_GATES),
        "pptx" to FormatSupport("pptx", false, false, OFFICE_GATES),
        "rtf" to FormatSupport("rtf", false, false, OFFICE_GATES),
        "html" to FormatSupport("html", false, false, OFFICE_GATES),
    )

    fun support(format: String): FormatSupport {
        val id = format.trim().trimStart('.').lowercase()
        require(id.length in 1..16 && id.all { it.isLetterOrDigit() }) { "bad format id" }
        return MATRIX[id] ?: FormatSupport(id, false, false, listOf("format not in bounded matrix"))
    }

    /** Formats with any view surface, sorted. */
    fun viewable(): List<String> = MATRIX.values.filter { it.view }.map { it.format }.sorted()
}

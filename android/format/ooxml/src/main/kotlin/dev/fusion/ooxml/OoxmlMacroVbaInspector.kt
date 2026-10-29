// SPDX-License-Identifier: Apache-2.0
package dev.fusion.ooxml

import java.nio.charset.StandardCharsets

/**
 * Clean-room MS-OVBA / OOXML macro quarantine scanner (`FF-17`: Macros disabled by default).
 *
 * Inspects OOXML zip entry manifests and raw `vbaProject.bin` / `xl/macrosheets/` streams:
 * - Detects OLE2 Compound File Binary (`D0 CF 11 E0 A1 B1 1A E1`) `vbaProject.bin` payloads.
 * - Flags auto-execution entrypoints (`AutoOpen`, `AutoExec`, `Document_Open`, `Workbook_Open`)
 *   and dangerous external process/network primitives (`Shell`, `CreateObject`, `WScript.Shell`,
 *   `URLDownloadToFile`, `PowerShell`).
 * - Always returns `executionPermitted = false` by default (`FF-17` fail-closed policy).
 */
object OoxmlMacroVbaInspector {
    private val OLE2_MAGIC = byteArrayOf(
        0xD0.toByte(), 0xCF.toByte(), 0x11.toByte(), 0xE0.toByte(),
        0xA1.toByte(), 0xB1.toByte(), 0x1A.toByte(), 0xE1.toByte(),
    )

    private val AUTO_TRIGGERS = listOf(
        "AutoOpen", "AutoExec", "AutoClose", "Document_Open", "Workbook_Open", "Auto_Open",
    )

    private val SUSPICIOUS_CALLS = listOf(
        "Shell", "WScript.Shell", "CreateObject", "GetObject", "URLDownloadToFile",
        "XMLHTTP", "PowerShell", "Declare Function", "Lib \"kernel32\"",
    )

    enum class ThreatLevel { CLEAN, MACRO_PRESENT_QUARANTINED, HIGH_RISK_AUTOEXEC_QUARANTINED }

    data class InspectionVerdict(
        val threatLevel: ThreatLevel,
        val executionPermitted: Boolean,
        val hasOle2VbaProject: Boolean,
        val detectedMacroEntries: List<String>,
        val autoTriggers: List<String>,
        val suspiciousSymbols: List<String>,
    )

    fun inspectPackage(
        zipEntryNames: Collection<String>,
        vbaProjectBytes: ByteArray? = null,
    ): InspectionVerdict {
        val macroEntries = zipEntryNames.filter { name ->
            val lower = name.lowercase()
            lower.endsWith("vbaproject.bin") ||
                lower.contains("/macrosheets/") ||
                lower.contains("/dialogsheets/") ||
                lower.endsWith("vbadata.xml")
        }.sorted()

        val hasOle2 = vbaProjectBytes != null &&
            vbaProjectBytes.size >= 8 &&
            (0 until 8).all { vbaProjectBytes[it] == OLE2_MAGIC[it] }

        val asciiScan = if (vbaProjectBytes != null) {
            // Scan both ASCII and UTF-16LE stripped text views of OLE2 stream
            val latin = String(vbaProjectBytes, StandardCharsets.ISO_8859_1)
            val strippedNulls = latin.replace("\u0000", "")
            "$latin\n$strippedNulls"
        } else ""

        val triggers = AUTO_TRIGGERS.filter { asciiScan.contains(it, ignoreCase = true) }
        val calls = SUSPICIOUS_CALLS.filter { asciiScan.contains(it, ignoreCase = true) }

        val level = when {
            triggers.isNotEmpty() || calls.isNotEmpty() -> ThreatLevel.HIGH_RISK_AUTOEXEC_QUARANTINED
            macroEntries.isNotEmpty() || hasOle2 -> ThreatLevel.MACRO_PRESENT_QUARANTINED
            else -> ThreatLevel.CLEAN
        }

        return InspectionVerdict(
            threatLevel = level,
            executionPermitted = false, // FF-17: Macros disabled by default; zero execution engine exposed
            hasOle2VbaProject = hasOle2,
            detectedMacroEntries = macroEntries,
            autoTriggers = triggers,
            suspiciousSymbols = calls,
        )
    }
}

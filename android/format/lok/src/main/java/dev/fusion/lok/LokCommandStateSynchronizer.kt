// SPDX-License-Identifier: Apache-2.0
package dev.fusion.lok

/**
 * Pure-JVM clean-room `Apache-2.0` desktop office command & selection synchronization protocol
 * mapper (`UNO`-style `.uno:Bold`, `.uno:InsertTable`, `.uno:NumberFormatCurrency`, `.uno:SlideMasterPage`)
 * bridging host/headless document views without linking any `MPL-2.0` LibreOfficeKit runtime.
 */
object LokCommandStateSynchronizer {
    const val SCOPE =
        "Clean-room Apache-2.0 office command/state delta synchronizer bridging toolbar state payloads across Writer, Calc/Sheets, and Impress/Slides."

    data class CommandStateChange(
        val commandName: String,
        val rawPayload: String,
        val monotonicSeq: Long,
    )

    data class SynchronizedToolbarModel(
        val boldActive: Boolean = false,
        val italicActive: Boolean = false,
        val underlineActive: Boolean = false,
        val activeFontFamily: String = "Inter",
        val activeFontSizePt: Double = 11.0,
        val activeCellFormula: String = "",
        val lastAppliedSeq: Long = 0L,
    )

    fun reduce(initial: SynchronizedToolbarModel, deltas: List<CommandStateChange>): SynchronizedToolbarModel {
        var cur = initial
        for (d in deltas.sortedBy { it.monotonicSeq }) {
            if (d.monotonicSeq < cur.lastAppliedSeq) continue // Reject stale out-of-order callback!
            val p = d.rawPayload.trim()
            cur = when (d.commandName) {
                ".uno:Bold" -> cur.copy(boldActive = p.equals("true", ignoreCase = true), lastAppliedSeq = d.monotonicSeq)
                ".uno:Italic" -> cur.copy(italicActive = p.equals("true", ignoreCase = true), lastAppliedSeq = d.monotonicSeq)
                ".uno:Underline" -> cur.copy(underlineActive = p.equals("true", ignoreCase = true), lastAppliedSeq = d.monotonicSeq)
                ".uno:CharFontName" -> cur.copy(activeFontFamily = p.ifBlank { cur.activeFontFamily }, lastAppliedSeq = d.monotonicSeq)
                ".uno:FontHeight" -> cur.copy(activeFontSizePt = p.toDoubleOrNull()?.coerceIn(6.0, 144.0) ?: cur.activeFontSizePt, lastAppliedSeq = d.monotonicSeq)
                ".uno:EnterString" -> cur.copy(activeCellFormula = p, lastAppliedSeq = d.monotonicSeq)
                else -> cur.copy(lastAppliedSeq = d.monotonicSeq)
            }
        }
        return cur
    }
}

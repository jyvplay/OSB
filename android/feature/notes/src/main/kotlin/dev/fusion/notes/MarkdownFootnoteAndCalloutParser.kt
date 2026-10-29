// SPDX-License-Identifier: Apache-2.0
package dev.fusion.notes

/**
 * GitHub-Flavored / Obsidian-compatible Markdown **Footnote** (`[^id]` + `[^id]: ...`) and
 * **Admonition Callout** (`> [!NOTE]`, `> [!WARNING]`, `> [!TIP]`, `> [!CAUTION]`) parser for
 * Fusion Notes knowledge documents.
 */
object MarkdownFootnoteAndCalloutParser {
    const val SCOPE =
        "Markdown [^id] footnote cross-linker and > [!TYPE] admonition callout block AST extractor for Fusion Notes."

    data class CalloutBlock(val kind: String, val title: String, val bodyLines: List<String>)
    data class ParsedDocument(
        val callouts: List<CalloutBlock>,
        val footnoteOrder: List<String>,
        val footnoteDefinitions: Map<String, String>,
        val unresolvedReferences: List<String>,
    )

    private val CALLOUT_HDR = Regex("^>\\s*\\[!([A-Za-z]+)\\]\\s*(.*)$")
    private val FN_DEF = Regex("^\\[\\^([A-Za-z0-9_-]+)\\]:\\s*(.+)$")
    private val FN_REF = Regex("\\[\\^([A-Za-z0-9_-]+)\\](?!:)")

    fun parse(markdown: String): ParsedDocument {
        val lines = markdown.lines()
        val callouts = ArrayList<CalloutBlock>()
        val defs = LinkedHashMap<String, String>()
        val refOrder = ArrayList<String>()

        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val cm = CALLOUT_HDR.matchEntire(line.trim())
            if (cm != null) {
                val kind = cm.groupValues[1].uppercase()
                val title = cm.groupValues[2].ifBlank { kind.lowercase().replaceFirstChar { it.uppercase() } }
                val body = ArrayList<String>()
                i++
                while (i < lines.size && lines[i].trimStart().startsWith(">")) {
                    body.add(lines[i].trimStart().removePrefix(">").trimStart())
                    i++
                }
                callouts.add(CalloutBlock(kind, title, body))
                continue
            }
            val dm = FN_DEF.matchEntire(line.trim())
            if (dm != null) {
                defs[dm.groupValues[1]] = dm.groupValues[2].trim()
                i++
                continue
            }
            for (rm in FN_REF.findAll(line)) {
                val id = rm.groupValues[1]
                if (id !in refOrder) refOrder.add(id)
            }
            i++
        }
        val unresolved = refOrder.filter { it !in defs }
        return ParsedDocument(callouts, refOrder, defs, unresolved)
    }
}

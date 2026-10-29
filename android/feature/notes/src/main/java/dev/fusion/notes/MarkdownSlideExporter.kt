// SPDX-License-Identifier: Apache-2.0
package dev.fusion.notes

/**
 * Clean-room Markdown-to-Presentation deck exporter for Notes & Presenter mode.
 *
 * Splits Markdown on horizontal slide dividers (`---` outside fenced code blocks), extracts
 * slide titles (`#` / `##`), bullet points, fenced code snippets, and `Note:` speaker notes,
 * and emits deterministic self-contained offline SVG/HTML slide previews (zero external scripts).
 */
object MarkdownSlideExporter {
    const val MAX_SLIDES = 256

    data class SlideModel(
        val slideNumber: Int,
        val title: String,
        val bullets: List<String>,
        val codeBlock: String?,
        val speakerNotes: String,
    )

    fun parseDeck(markdown: String): List<SlideModel> {
        val rawSlides = ArrayList<MutableList<String>>()
        rawSlides.add(ArrayList())
        var inFence = false
        for (line in markdown.replace("\r\n", "\n").split('\n')) {
            if (line.trimStart().startsWith("```")) {
                inFence = !inFence
                rawSlides.last().add(line)
            } else if (!inFence && line.trim() == "---") {
                rawSlides.add(ArrayList())
            } else {
                rawSlides.last().add(line)
            }
        }
        val nonEmpty = rawSlides.filter { lines -> lines.any { it.isNotBlank() } }
        require(nonEmpty.size <= MAX_SLIDES) { "deck exceeds $MAX_SLIDES slides" }

        return nonEmpty.mapIndexed { idx, lines ->
            var title = "Slide ${idx + 1}"
            val bullets = ArrayList<String>()
            val codeBuf = StringBuilder()
            val notesBuf = StringBuilder()
            var fence = false
            var inNotes = false
            for (l in lines) {
                val t = l.trim()
                when {
                    t.startsWith("```") -> {
                        fence = !fence
                    }
                    fence -> {
                        if (codeBuf.isNotEmpty()) codeBuf.append('\n')
                        codeBuf.append(l)
                    }
                    t.startsWith("Note:", ignoreCase = true) -> {
                        inNotes = true
                        val rem = t.substring(5).trim()
                        if (rem.isNotEmpty()) notesBuf.append(rem)
                    }
                    inNotes -> {
                        if (notesBuf.isNotEmpty()) notesBuf.append(' ')
                        notesBuf.append(t)
                    }
                    t.startsWith("# ") || t.startsWith("## ") -> {
                        title = t.trimStart('#').trim()
                    }
                    t.startsWith("- ") || t.startsWith("* ") -> {
                        bullets.add(t.substring(2).trim())
                    }
                    t.isNotEmpty() -> bullets.add(t)
                }
            }
            SlideModel(
                slideNumber = idx + 1,
                title = title,
                bullets = bullets,
                codeBlock = codeBuf.toString().takeIf { it.isNotEmpty() },
                speakerNotes = notesBuf.toString(),
            )
        }
    }

    fun renderOfflineHtmlDeck(slides: List<SlideModel>, deckTitle: String = "Fusion Deck"): String = buildString {
        append("<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"utf-8\">")
        append("<meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'none'; style-src 'unsafe-inline'\">")
        append("<title>").append(esc(deckTitle)).append("</title></head><body>")
        for (s in slides) {
            append("<section class=\"slide\" data-slide=\"${s.slideNumber}\">")
            append("<h1>").append(esc(s.title)).append("</h1>")
            if (s.bullets.isNotEmpty()) {
                append("<ul>")
                for (b in s.bullets) append("<li>").append(esc(b)).append("</li>")
                append("</ul>")
            }
            if (s.codeBlock != null) {
                append("<pre><code>").append(esc(s.codeBlock)).append("</code></pre>")
            }
            if (s.speakerNotes.isNotEmpty()) {
                append("<aside class=\"notes\">").append(esc(s.speakerNotes)).append("</aside>")
            }
            append("</section>")
        }
        append("</body></html>")
    }

    private fun esc(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}

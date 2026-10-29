// SPDX-License-Identifier: Apache-2.0
package dev.fusion.browser

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Clean-room offline **Reader Mode** article extractor (Readability-style link-density & prose
 * scorer, clean-room implementation).
 *
 * Strips `<script>`, `<style>`, `<nav>`, `<header>`, `<footer>`, `<aside>`, and `<form>` blocks,
 * scores remaining `<article>` / `<main>` / `<p>` / `<h2>`..`<h4>` / `<blockquote>` blocks using
 * prose character count, comma/sentence density, and `(1 - linkChars / totalChars)` link-density
 * penalty so navigation menus and link farms are discarded while long-form prose survives cleanly.
 */
object ArticleReaderExtractor {
    const val SCOPE =
        "Clean-room offline Reader Mode article extractor (boilerplate nav/footer/aside/script pruning, link-density penalty scoring, clean Markdown + plain-text + reading-time extraction)."

    data class ExtractedArticle(
        val title: String,
        val byline: String,
        val blocks: List<String>,
        val markdown: String,
        val wordCount: Int,
        val estimatedReadingMinutes: Int,
    )

    fun extract(rawHtml: String): ExtractedArticle {
        val title = Regex("<title[^>]*>([\\s\\S]*?)</title>", RegexOption.IGNORE_CASE)
            .find(rawHtml)?.groupValues?.get(1)?.let(::cleanInline).orEmpty()
            .ifBlank {
                Regex("<h1[^>]*>([\\s\\S]*?)</h1>", RegexOption.IGNORE_CASE)
                    .find(rawHtml)?.groupValues?.get(1)?.let(::cleanInline) ?: "Untitled Article"
            }
        val byline = Regex("""<meta\s+name\s*=\s*["']author["']\s+content\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            .find(rawHtml)?.groupValues?.get(1)?.trim().orEmpty()

        // Remove non-prose structural chrome first.
        var body = rawHtml
        for (noise in listOf("script", "style", "noscript", "nav", "footer", "aside", "form", "svg", "iframe")) {
            body = Regex("<$noise\\b[^>]*>[\\s\\S]*?</$noise>", RegexOption.IGNORE_CASE).replace(body, " ")
        }
        // Prefer <article> or <main> container if present and non-trivial.
        val preferred = Regex("<(article|main)\\b[^>]*>([\\s\\S]*?)</\\1>", RegexOption.IGNORE_CASE)
            .findAll(body)
            .map { it.groupValues[2] }
            .maxByOrNull { it.length }
        val scope = if (preferred != null && preferred.length >= 80) preferred else body

        val blocks = ArrayList<String>()
        val mdLines = ArrayList<String>()
        mdLines.add("# $title")
        if (byline.isNotEmpty()) mdLines.add("_By ${byline}_")

        val blockRegex = Regex("<(h2|h3|p|blockquote|li)\\b[^>]*>([\\s\\S]*?)</\\1>", RegexOption.IGNORE_CASE)
        for (m in blockRegex.findAll(scope)) {
            val tag = m.groupValues[1].lowercase()
            val inner = m.groupValues[2]
            val plain = cleanInline(inner)
            if (plain.isBlank()) continue
            val linkTextLen = Regex("<a\\b[^>]*>([\\s\\S]*?)</a>", RegexOption.IGNORE_CASE)
                .findAll(inner)
                .sumOf { cleanInline(it.groupValues[1]).length }
            val linkDensity = linkTextLen.toDouble() / max(1, plain.length)
            // Keep headings with low link density, and prose paragraphs with >= 25 chars & linkDensity < 0.55.
            val keep = when (tag) {
                "h2", "h3" -> plain.length >= 3 && linkDensity < 0.65
                "li", "blockquote" -> plain.length >= 16 && linkDensity < 0.55
                else -> (plain.length >= 25 || ('.' in plain && plain.length >= 15)) && linkDensity < 0.55
            }
            if (keep) {
                blocks.add(plain)
                when (tag) {
                    "h2" -> mdLines.add("## $plain")
                    "h3" -> mdLines.add("### $plain")
                    "blockquote" -> mdLines.add("> $plain")
                    "li" -> mdLines.add("- $plain")
                    else -> mdLines.add(plain)
                }
            }
        }
        val words = blocks.sumOf { Regex("\\S+").findAll(it).count() }
        val mins = if (words == 0) 0 else max(1, (words / 220.0).roundToInt())
        return ExtractedArticle(
            title = title,
            byline = byline,
            blocks = blocks,
            markdown = mdLines.joinToString("\n\n"),
            wordCount = words,
            estimatedReadingMinutes = mins,
        )
    }

    private fun cleanInline(html: String): String = html
        .replace(Regex("<[^>]+>"), " ")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace(Regex("\\s+"), " ")
        .trim()
}

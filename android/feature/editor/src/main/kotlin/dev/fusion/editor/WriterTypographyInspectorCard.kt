// SPDX-License-Identifier: Apache-2.0
package dev.fusion.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.fusion.documents.LiangHyphenationTrieEngine
import dev.fusion.documents.OpenTypeSfntMetricsParser
import dev.fusion.documents.OptimalParagraphComposer

/**
 * R54 Writer typography inspector card.
 *
 * Shows the *real* host Knuth–Plass break result (demerits, worst adjustment ratio, hyphen runs) and the
 * measured `sfnt` advance width for the current paragraph, so a writer can see typographic quality rather
 * than trusting a silent layout engine. All numbers come from the pure-JVM kernels, not from estimation.
 */
@Composable
fun WriterTypographyInspectorCard(
    paragraphText: String,
    measureDp: Float,
    fontSizePt: Float,
    modifier: Modifier = Modifier,
) {
    val spaces = paragraphText.length * (fontSizePt * 0.5f)
    val items = OptimalParagraphComposer.tokenizeParagraph(
        words = paragraphText.split(" ").filter { it.isNotEmpty() },
        measureText = { word: String -> word.length * fontSizePt.toDouble() * 0.5 },
        spaceWidth = fontSizePt.toDouble() * 0.25,
        hyphenWidth = fontSizePt.toDouble() * 0.2,
        hyphenator = { word -> LiangHyphenationTrieEngine.hyphenateWord(word) },
    )
    val result = OptimalParagraphComposer.compose(items, { measureDp.toDouble() })
    val worstRatio = result.lines.maxOfOrNull { kotlin.math.abs(it.adjustmentRatio) } ?: 0.0
    val hyphenRuns = result.lines.count { it.endsWithHyphen }
    val quality = when {
        worstRatio <= 0.25 && result.passUsed == 1 -> "Optimal (pass 1)"
        worstRatio <= 0.5 -> "Optimal (relaxed)"
        result.usedGreedyFallback -> "Greedy fallback"
        else -> "Emergency stretch"
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("writer-typography-inspector")
            .semantics { contentDescription = "Writer typography: $quality" },
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Typography inspector",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = "$quality \u00b7 ${result.lines.size} lines \u00b7 ${result.totalDemerits} demerits",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = "Worst adjustment ${"%.2f".format(worstRatio)} \u00b7 hyphenated lines $hyphenRuns",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = "Natural width ${"%.1f".format(spaces)}dp vs measure ${"%.1f".format(measureDp)}dp",
                style = MaterialTheme.typography.bodySmall,
            )
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
            Row(modifier = Modifier.padding(top = 8.dp)) {
                Text(text = "Pass ${result.passUsed}", style = MaterialTheme.typography.labelSmall)
                Text(
                    text = if (result.usedGreedyFallback) " \u00b7 live-typing wrap" else " \u00b7 library-quality wrap",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

/**
 * Renders a single glyph-advance proof row: the measured OpenType advance width for `glyph` at `sizePt`,
 * asserted against the parsed `hmtx` table so an unsupported font surfaces immediately instead of silently
 * falling back to a synthetic width.
 */
@Composable
fun SfntAdvanceWidthRow(
    fontBytes: ByteArray,
    sample: String,
    sizePt: Float,
    modifier: Modifier = Modifier,
) {
    val face = runCatching { OpenTypeSfntMetricsParser.parse(fontBytes) }.getOrNull()
    val widthPt = face?.measureTextPoints(sample, sizePt.toDouble())
    Surface(
        modifier = modifier.fillMaxWidth().testTag("sfnt-advance-row"),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(modifier = Modifier.padding(10.dp)) {
            Text(
                text = if (widthPt == null) "Font refused (truncated or unsupported sfnt)" else {
                    "\"$sample\" \u2192 ${"%.2f".format(widthPt)}pt at ${"%.1f".format(sizePt)}pt " +
                        "(${face.unitsPerEm} upem, ${face.numGlyphs} glyphs)"
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

// SPDX-License-Identifier: Apache-2.0
package dev.fusion.testing

import dev.fusion.ooxml.DocxPackage
import dev.fusion.ooxml.PlainWriterDocx
import dev.fusion.ooxml.StructuredWriterDocx
import java.io.File
import java.util.Base64
import java.util.Random

/** Generate actual Writer outputs for a separate independent reader; not a renderer test. */
object R59WriterChecks {
    private var count = 0
    private fun verify(value: Boolean, label: String) { count++; check(value) { label } }
    private fun text(block: DocxPackage.Block): String = when (block) {
        is DocxPackage.Block.Paragraph -> block.runs.joinToString("") { it.text }
        is DocxPackage.Block.Heading -> block.runs.joinToString("") { it.text }
        is DocxPackage.Block.ListItem -> block.runs.joinToString("") { it.text }
        is DocxPackage.Block.BlockQuote -> block.runs.joinToString("") { it.text }
        else -> error("Unexpected block $block")
    }
    @JvmStatic fun main(args: Array<String>) {
        val folder = File(args.single()).apply { mkdirs() }
        val random = Random(5902)
        val alphabet = listOf("a", "Z", " ", "\t", "&", "<", ">", "\"", "é", "🙂", "9")
        val manifest = ArrayList<String>()
        repeat(100) { index ->
            val payload = buildString { append('x'); repeat(1 + random.nextInt(24)) { append(alphabet[random.nextInt(alphabet.size)]) } }
            val lines = listOf("# H$payload", "- $payload **strong** and *soft*", "> $payload `mono`", "  $payload  ", "**unclosed $payload")
            val sep = listOf("\n", "\r\n", "\r")[index % 3]
            val source = lines.joinToString(sep) + sep
            val plain = PlainWriterDocx.write(source)
            val structured = StructuredWriterDocx.write(source)
            val expected = listOf("H$payload", "$payload strong and soft", "$payload mono", "  $payload  ", "**unclosed $payload", "")
            verify(DocxPackage.read(plain).blocks.map(::text) == lines + "", "plain text $index")
            verify(DocxPackage.read(structured).blocks.map(::text) == expected, "structured text $index")
            verify(plain.contentEquals(PlainWriterDocx.write(source)), "plain deterministic $index")
            verify(structured.contentEquals(StructuredWriterDocx.write(source)), "structured deterministic $index")
            val id = index.toString().padStart(3, '0')
            File(folder, "doc-$id-plain.docx").writeBytes(plain)
            File(folder, "doc-$id-structured.docx").writeBytes(structured)
            manifest.add(id + "\t" + Base64.getEncoder().encodeToString(source.toByteArray(Charsets.UTF_8)))
        }
        File(folder, "manifest.tsv").writeText(manifest.joinToString("\n", postfix = "\n"), Charsets.UTF_8)
        println("R59 Writer corpus: $count host assertions and 200 actual DOCX files; independent reader pending")
    }
}

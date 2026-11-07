// SPDX-License-Identifier: Apache-2.0
package dev.fusion.testing

import dev.fusion.ooxml.StructuredWriterDocx
import java.io.File
import java.util.Random

/** Calls only pre-R62 public overloads; compiled unchanged against old and new sources. */
object R62WriterLegacyProbe {
    @JvmStatic fun main(args: Array<String>) {
        val directory = File(args.single()).also { check(it.exists() || it.mkdirs()) }
        val random = Random(62001)
        val tokens = listOf("plain", "~~strike~~", "~~broken", "**bold**", "*italic*", "`code`",
            "~~a **nested** b~~", "~single~", "\\~~escape~~", "α🙂", "", " ")
        repeat(128) { i ->
            val lines = List(1 + random.nextInt(8)) {
                val prefix = listOf("", "1. ", "# ", "- ", "> ")[random.nextInt(5)]
                prefix + List(1 + random.nextInt(4)) { tokens[random.nextInt(tokens.size)] }.joinToString(" ")
            }
            val raw = lines.joinToString("\n")
            val bytes = if (i % 4 == 0) StructuredWriterDocx.write(raw) else StructuredWriterDocx.write(raw, i % 2 == 0)
            File(directory, "doc-${i.toString().padStart(3, '0')}.docx").writeBytes(bytes)
        }
    }
}

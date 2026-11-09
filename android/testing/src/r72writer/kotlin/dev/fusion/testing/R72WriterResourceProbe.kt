// SPDX-License-Identifier: Apache-2.0
package dev.fusion.testing

import dev.fusion.ooxml.PlainWriterDocx

/**
 * Small-heap and boundary observations for the plain DOCX writer.
 * Host-only: the input String already exists before the method is called, so this
 * does not model every allocation an editor or Android runtime may perform.
 */
object R72WriterResourceProbe {
    @JvmStatic fun main(args: Array<String>) {
        require(args.isNotEmpty()) { "Expected boundaries or oversized <chars>" }
        when (args[0]) {
            "boundaries" -> {
                val limit = PlainWriterDocx.MAX_TEXT_BYTES
                val exact = PlainWriterDocx.write("a".repeat(limit))
                check(exact.isNotEmpty()) { "Exact ASCII boundary must be accepted" }
                check(PlainWriterDocx.write("a".repeat(limit - 2) + "\u00e9").isNotEmpty()) {
                    "UTF-8 bytes at the exact limit must be accepted"
                }
                checkRefused("one UTF-8 byte over the limit") {
                    PlainWriterDocx.write("a".repeat(limit - 1) + "\u00e9")
                }
                checkRefused("ASCII over limit") { PlainWriterDocx.write("a".repeat(limit + 1)) }
                // 65,537 UTF-16 units are within the code-unit bound, but 131,074 UTF-8 bytes are not.
                checkRefused("multibyte over byte limit") { PlainWriterDocx.write("\u00e9".repeat(limit / 2 + 1)) }
                checkRefused("unpaired surrogate") { PlainWriterDocx.write("\uD800") }
                checkRefused("XML control character") { PlainWriterDocx.write("a\u0000b") }
                checkRefused("too many paragraphs") { PlainWriterDocx.write("\n".repeat(PlainWriterDocx.MAX_PARAGRAPHS)) }
                println("BOUNDARIES ok")
            }
            "oversized" -> {
                val size = args[1].toInt()
                check(size > PlainWriterDocx.MAX_TEXT_BYTES) { "Oversized probe must exceed the text budget" }
                // The probe's own allocation is reported separately: an OOM here is not
                // evidence about the writer, so the runner treats it as inconclusive.
                val input = try {
                    "a".repeat(size)
                } catch (e: OutOfMemoryError) {
                    println("OUT_OF_MEMORY phase=probe-input")
                    kotlin.system.exitProcess(13)
                }
                try {
                    PlainWriterDocx.write(input)
                    println("ACCEPTED $size")
                } catch (e: OutOfMemoryError) {
                    println("OUT_OF_MEMORY phase=plain-writer")
                    kotlin.system.exitProcess(12)
                } catch (e: IllegalArgumentException) {
                    println("REFUSED phase=plain-writer")
                }
            }
            else -> error("Unknown mode")
        }
    }

    private inline fun checkRefused(label: String, operation: () -> Unit) {
        try {
            operation()
        } catch (e: IllegalArgumentException) {
            return
        }
        error("Expected refusal: $label")
    }
}

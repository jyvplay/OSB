// SPDX-License-Identifier: Apache-2.0
package dev.fusion.testing

import dev.fusion.ooxml.StructuredWriterDocx
import java.io.File
import java.security.MessageDigest

/** Compiled against the pinned old OSB source in a separate host JVM. */
object R60WriterLegacyProbe {
    @JvmStatic fun main(args: Array<String>) {
        require(args.isEmpty())
        generateSequence(::readLine).forEach { path ->
            val bytes = StructuredWriterDocx.write(File(path).readText(Charsets.UTF_8))
            println(MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })
        }
    }
}

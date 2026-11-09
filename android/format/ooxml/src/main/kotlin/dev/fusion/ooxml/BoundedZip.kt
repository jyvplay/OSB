// SPDX-License-Identifier: Apache-2.0
package dev.fusion.ooxml

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

/**
 * One byte budget for every ZIP entry, directory entries included.
 *
 * `ZipInputStream.closeEntry()` skips the remaining bytes of the current entry
 * without passing them to the caller, so a slash-suffixed name can hide an
 * arbitrarily large payload from a loop that only counts files. Every entry is
 * therefore read through the same budget, whatever its name or directory flag.
 * Names must be unique. Only non-directory payloads are returned.
 */
internal object BoundedZip {
    fun read(
        bytes: ByteArray,
        archiveBytes: Int,
        partBytes: Int,
        expandedBytes: Long,
        entries: Int,
        describe: String = "archive",
        validate: (String) -> Unit = {},
    ): Map<String, ByteArray> {
        require(bytes.isNotEmpty() && bytes.size <= archiveBytes) {
            "$describe input ${bytes.size} outside 1..$archiveBytes"
        }
        val out = LinkedHashMap<String, ByteArray>()
        val names = HashSet<String>()
        var total = 0L
        var seen = 0
        ZipInputStream(ByteArrayInputStream(bytes)).use { stream ->
            val buffer = ByteArray(8 * 1024)
            while (true) {
                val entry = stream.nextEntry ?: break
                seen++
                require(seen <= entries) { "$describe has more than $entries entries" }
                val name = entry.name
                require(names.add(name)) { "$describe repeats the ZIP entry name $name" }
                validate(name)
                val payload = if (entry.isDirectory) null else ByteArrayOutputStream()
                var part = 0
                while (true) {
                    val want = minOf(buffer.size.toLong(), partBytes - part + 1L, expandedBytes - total + 1L)
                    require(want > 0L) { "$describe exceeds its byte budget at $name" }
                    val read = stream.read(buffer, 0, want.toInt())
                    if (read < 0) break
                    require(read > 0) { "$describe entry $name made no progress" }
                    part += read
                    total += read
                    require(part <= partBytes && total <= expandedBytes) {
                        "$describe exceeds its byte budget at $name"
                    }
                    payload?.write(buffer, 0, read)
                }
                if (payload != null) out[name] = payload.toByteArray()
            }
        }
        return out
    }
}

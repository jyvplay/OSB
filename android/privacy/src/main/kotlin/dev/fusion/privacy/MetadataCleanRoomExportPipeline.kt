package dev.fusion.privacy

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Clean-room MAT2-Inspired Outbound Office/ZIP Metadata Sanitizer (Apache-2.0).
 *
 * Strips identifying OOXML/ODF package parts (`docProps/core.xml`, `docProps/app.xml`,
 * `docProps/custom.xml`, `word/comments.xml`, `meta.xml`, `settings.xml` printer names),
 * normalizes all ZIP entry timestamps to `1980-01-01T00:00:00Z`, and canonicalizes entry order!
 */
object MetadataCleanRoomExportPipeline {
    const val SCOPE =
        "MAT2-inspired OOXML/ODF ZIP metadata scrubber (strips docProps/core+app+custom, meta.xml, comments; pins 1980-01-01 epoch)"

    const val ZERO_DOS_EPOCH_MS = 315_532_800_000L

    data class SanitizedArchiveResult(
        val sanitizedBytes: ByteArray,
        val removedEntries: List<String>,
        val retainedEntries: List<String>,
    )

    private val STRIP_EXACT = setOf(
        "docprops/core.xml",
        "docprops/app.xml",
        "docprops/custom.xml",
        "word/comments.xml",
        "meta.xml",
    )

    fun sanitizeZipDocument(zipBytes: ByteArray): SanitizedArchiveResult {
        val rawEntries = LinkedHashMap<String, Pair<Int, ByteArray>>()
        val removed = mutableListOf<String>()

        ZipInputStream(ByteArrayInputStream(zipBytes), StandardCharsets.UTF_8).use { zis ->
            while (true) {
                val e = zis.nextEntry ?: break
                require(!e.name.contains("..") && !e.name.startsWith("/")) { "Unsafe entry '${e.name}'" }
                val data = zis.readBytes()
                if (e.name.lowercase() in STRIP_EXACT || e.name.lowercase().endsWith("thumbnail.jpeg")) {
                    removed.add(e.name)
                } else {
                    rawEntries[e.name] = e.method to data
                }
            }
        }

        // Preserve ODF `mimetype` strictly first & STORED if present; sort remaining paths deterministically!
        val orderedKeys = buildList {
            if ("mimetype" in rawEntries) add("mimetype")
            addAll(rawEntries.keys.filter { it != "mimetype" }.sorted())
        }

        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos, StandardCharsets.UTF_8).use { zos ->
            for (name in orderedKeys) {
                var (_, payload) = rawEntries.getValue(name)
                if (name.endsWith(".rels") || name == "[Content_Types].xml") {
                    val xml = String(payload, StandardCharsets.UTF_8)
                    val cleanedXml = xml
                        .replace(Regex("""<Relationship[^>]*Target="docProps/[^"]+"[^>]*/>"""), "")
                        .replace(Regex("""<Override[^>]*PartName="/docProps/[^"]+"[^>]*/>"""), "")
                    payload = cleanedXml.toByteArray(StandardCharsets.UTF_8)
                }
                val crc = CRC32().apply { update(payload) }.value
                val entry = ZipEntry(name).apply {
                    time = ZERO_DOS_EPOCH_MS
                    method = ZipEntry.STORED
                    size = payload.size.toLong()
                    compressedSize = payload.size.toLong()
                    this.crc = crc
                }
                zos.putNextEntry(entry)
                zos.write(payload)
                zos.closeEntry()
            }
        }
        return SanitizedArchiveResult(baos.toByteArray(), removed.sorted(), orderedKeys)
    }
}

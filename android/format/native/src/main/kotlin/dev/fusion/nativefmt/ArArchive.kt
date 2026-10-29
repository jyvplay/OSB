// SPDX-License-Identifier: Apache-2.0
package dev.fusion.nativefmt

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

/**
 * Clean-room Unix `ar` (`!<arch>\n`) archive reader & deterministic zero-epoch writer (`ArArchive`).
 *
 * Powers `:format:native` `.a` static library & `.deb` (`debian-binary` + `control.tar` + `data.tar`)
 * inspection:
 * - Exact 8-byte global magic `!<arch>\n` + 60-byte ASCII member headers (`name[16]`, `mtime[12]`,
 *   `uid[6]`, `gid[6]`, `mode[8]`, `size[10]`, `fmag = "`\n"`), padded to 2-byte even alignment (`\n`).
 * - Deterministic build hygiene (`D` mode): `mtime=0`, `uid=0`, `gid=0`, `mode=100644`.
 * - Supports BSD `#1/<len>` in-band long filenames and GNU `//` string-table long filenames, while
 *   strictly refusing `..` / `/` path traversal!
 */
object ArArchive {
    const val SCOPE =
        "Unix !<arch>\\n ar/.deb archive deterministic writer + BSD #1/ & GNU // long-filename reader with path-traversal refusal; COFF symbol ranlib tables out of scope."

    const val MAGIC = "!<arch>\n"
    const val HEADER_LEN = 60
    const val FMAG = "`\n"
    const val MAX_ARCHIVE_BYTES = 32 * 1024 * 1024

    data class Member(val name: String, val data: ByteArray, val modeOctal: String = "100644") {
        init {
            require(isSafeMemberName(name)) { "Unsafe ar member filename: '$name'" }
        }
    }

    fun writeDeterministic(members: List<Member>): ByteArray {
        val baos = ByteArrayOutputStream()
        baos.write(MAGIC.toByteArray(StandardCharsets.US_ASCII))
        for (m in members) {
            val nameBytes = m.name.toByteArray(StandardCharsets.UTF_8)
            val useBsdExt = nameBytes.size > 15 || m.name.contains(' ')
            if (!useBsdExt) {
                val nameField = ("${m.name}/").padEnd(16, ' ')
                baos.write(formatHeader(nameField, m.modeOctal, m.data.size))
                baos.write(m.data)
                if (m.data.size % 2 != 0) baos.write('\n'.code)
            } else {
                val paddedNameLen = if (nameBytes.size % 2 == 0) nameBytes.size else nameBytes.size + 1
                val namePayload = ByteArray(paddedNameLen).also {
                    System.arraycopy(nameBytes, 0, it, 0, nameBytes.size)
                }
                val totalSize = namePayload.size + m.data.size
                val nameField = "#1/${namePayload.size}".padEnd(16, ' ')
                baos.write(formatHeader(nameField, m.modeOctal, totalSize))
                baos.write(namePayload)
                baos.write(m.data)
                if (totalSize % 2 != 0) baos.write('\n'.code)
            }
        }
        return baos.toByteArray()
    }

    fun read(arBytes: ByteArray): List<Member> {
        require(arBytes.size <= MAX_ARCHIVE_BYTES) { "ar archive exceeds MAX_ARCHIVE_BYTES" }
        require(arBytes.size >= 8 && String(arBytes, 0, 8, StandardCharsets.US_ASCII) == MAGIC) {
            "Invalid Unix ar magic (expected !<arch>\\n)"
        }
        val out = mutableListOf<Member>()
        var gnuNameTable: ByteArray? = null
        var offset = 8
        while (offset < arBytes.size) {
            require(offset + HEADER_LEN <= arBytes.size) { "Truncated 60-byte ar header at $offset" }
            val hdr = String(arBytes, offset, HEADER_LEN, StandardCharsets.US_ASCII)
            require(hdr.substring(58, 60) == FMAG) { "Invalid ar header fmag at $offset" }
            val rawName = hdr.substring(0, 16).trimEnd(' ')
            val mode = hdr.substring(40, 48).trim()
            val size = hdr.substring(48, 58).trim().toIntOrNull()
                ?: throw IllegalArgumentException("Invalid ar member size at $offset")
            require(size >= 0 && offset + HEADER_LEN + size <= arBytes.size) {
                "ar member size ($size) overflows archive bounds"
            }

            var payloadStart = offset + HEADER_LEN
            var payloadLen = size
            offset = payloadStart + size + (size and 1)

            if (rawName == "//") {
                gnuNameTable = arBytes.copyOfRange(payloadStart, payloadStart + payloadLen)
                continue
            }
            if (rawName == "/" || rawName == "/SYM64/") continue // symbol table

            val resolvedName = when {
                rawName.startsWith("#1/") -> {
                    val extLen = rawName.substring(3).toIntOrNull()
                        ?: throw IllegalArgumentException("Invalid BSD #1/ length: $rawName")
                    require(extLen in 1..payloadLen) { "BSD #1/ name length exceeds member size" }
                    val nm = String(arBytes, payloadStart, extLen, StandardCharsets.UTF_8).trimEnd('\u0000')
                    payloadStart += extLen
                    payloadLen -= extLen
                    nm
                }
                rawName.startsWith("/") && gnuNameTable != null -> {
                    val tblOff = rawName.substring(1).toIntOrNull()
                        ?: throw IllegalArgumentException("Invalid GNU /name offset: $rawName")
                    require(tblOff in gnuNameTable.indices) { "GNU // offset out of bounds" }
                    var end = tblOff
                    while (end < gnuNameTable.size && gnuNameTable[end] != '/'.code.toByte() && gnuNameTable[end] != '\n'.code.toByte()) {
                        end++
                    }
                    String(gnuNameTable, tblOff, end - tblOff, StandardCharsets.UTF_8)
                }
                else -> rawName.trimEnd('/')
            }

            require(isSafeMemberName(resolvedName)) { "Unsafe ar member filename: '$resolvedName'" }
            out.add(Member(resolvedName, arBytes.copyOfRange(payloadStart, payloadStart + payloadLen), mode))
        }
        return out
    }

    private fun isSafeMemberName(name: String): Boolean =
        name.isNotEmpty() && !name.contains('/') && !name.contains('\\') && !name.contains('\u0000') && name != "." && name != ".."

    private fun formatHeader(name16: String, modeOctal: String, size: Int): ByteArray {
        val sb = StringBuilder(HEADER_LEN)
        sb.append(name16.take(16).padEnd(16, ' '))
        sb.append("0".padEnd(12, ' ')) // mtime = 0 deterministic
        sb.append("0".padEnd(6, ' '))  // uid = 0
        sb.append("0".padEnd(6, ' '))  // gid = 0
        sb.append(modeOctal.take(8).padEnd(8, ' '))
        sb.append(size.toString().padEnd(10, ' '))
        sb.append(FMAG)
        return sb.toString().toByteArray(StandardCharsets.US_ASCII)
    }
}

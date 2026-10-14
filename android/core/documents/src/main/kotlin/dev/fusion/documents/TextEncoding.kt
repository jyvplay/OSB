package dev.fusion.documents

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/** No statistical encoding guess or lossy replacement. BOM-less legacy encodings require a choice. */
enum class TextEncoding(val charsetName: String, val label: String) {
    UTF8("UTF-8","UTF-8"), UTF8_BOM("UTF-8","UTF-8 with BOM"),
    UTF16_LE("UTF-16LE","UTF-16 LE without BOM"), UTF16_BE("UTF-16BE","UTF-16 BE without BOM"),
    UTF16_LE_BOM("UTF-16LE","UTF-16 LE with BOM"), UTF16_BE_BOM("UTF-16BE","UTF-16 BE with BOM"),
    WINDOWS_1252("windows-1252","Windows-1252")
}
data class DecodedText(val content: String, val encoding: TextEncoding)
object TextEncodingCodec {
    private fun marker(e: TextEncoding): ByteArray = when(e) {
        TextEncoding.UTF8_BOM -> byteArrayOf(0xef.toByte(),0xbb.toByte(),0xbf.toByte())
        TextEncoding.UTF16_LE_BOM -> byteArrayOf(0xff.toByte(),0xfe.toByte())
        TextEncoding.UTF16_BE_BOM -> byteArrayOf(0xfe.toByte(),0xff.toByte())
        else -> byteArrayOf()
    }
    private fun starts(bytes: ByteArray, prefix: ByteArray) = bytes.size>=prefix.size && prefix.indices.all { bytes[it]==prefix[it] }
    fun decode(bytes: ByteArray, explicit: TextEncoding? = null): DecodedText {
        require(bytes.size<=TextBytes.MAX_BYTES) { "Encoded text exceeds 2 MiB" }
        val selected=explicit ?: run {
            require(!starts(bytes,byteArrayOf(0xff.toByte(),0xfe.toByte(),0,0)) &&
                !starts(bytes,byteArrayOf(0,0,0xfe.toByte(),0xff.toByte()))) { "UTF-32 is unsupported; choose a known encoding explicitly" }
            listOf(TextEncoding.UTF8_BOM,TextEncoding.UTF16_LE_BOM,TextEncoding.UTF16_BE_BOM)
                .firstOrNull { starts(bytes,marker(it)) } ?: TextEncoding.UTF8
        }
        val bom=marker(selected)
        require(starts(bytes,bom)) { "Selected BOM-bearing encoding does not match the file" }
        val content=Charset.forName(selected.charsetName).newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes,bom.size,bytes.size-bom.size)).toString()
        TextBytes.encodedSize(content) // Canonical recovery/undo budget is also enforced.
        // Refuse mappings that cannot preserve original bytes exactly; do not normalize newlines/BOMs.
        require(encode(content,selected).contentEquals(bytes)) { "Encoding would not roundtrip exactly" }
        return DecodedText(content,selected)
    }
    fun encode(content: String, encoding: TextEncoding): ByteArray {
        TextBytes.encodedSize(content)
        val encoded=Charset.forName(encoding.charsetName).newEncoder()
            .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
            .encode(CharBuffer.wrap(content))
        val bom=marker(encoding)
        require(encoded.remaining().toLong()+bom.size<=TextBytes.MAX_BYTES) { "Encoded output exceeds 2 MiB; draft retained" }
        return bom+ByteArray(encoded.remaining()).also(encoded::get)
    }
}

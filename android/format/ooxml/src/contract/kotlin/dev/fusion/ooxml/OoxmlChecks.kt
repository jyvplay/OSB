package dev.fusion.ooxml

import java.io.*
import java.util.zip.*

object OoxmlChecks {
    private var checks=0
    private fun expect(ok: Boolean) { checks++;check(ok) { "OOXML guard check $checks failed" } }
    private val word="<w:document><w:p><w:t>café &amp; 漢😀</w:t></w:p></w:document>".toByteArray()
    private fun zip(parts: List<Pair<String,ByteArray>>): ByteArray = ByteArrayOutputStream().also { bytes ->
        ZipOutputStream(bytes).use { z -> parts.forEach { (name,body) -> z.putNextEntry(ZipEntry(name));z.write(body);z.closeEntry() } }
    }.toByteArray()
    private fun expanded(parts: List<Pair<String,Int>>): ByteArray = ByteArrayOutputStream().also { bytes ->
        ZipOutputStream(bytes).use { z ->
            z.putNextEntry(ZipEntry("word/document.xml"));z.write(word);z.closeEntry()
            val block=ByteArray(8192)
            for((name,size) in parts) {
                z.putNextEntry(ZipEntry(name));var left=size
                while(left>0) { val n=minOf(left,block.size);z.write(block,0,n);left-=n };z.closeEntry()
            }
        }
    }.toByteArray()
    private fun extract(bytes: ByteArray)=Ooxml.extractDocxText(ByteArrayInputStream(bytes))
    @JvmStatic fun main(args: Array<String>) {
        expect(extract(zip(listOf("word/document.xml" to word))).getOrThrow()=="café & 漢😀")
        expect(extract(expanded(listOf("ignored.bin" to Ooxml.MAX_PART_BYTES))).isSuccess)
        expect(extract(expanded(listOf("ignored.bin" to Ooxml.MAX_PART_BYTES+1))).isFailure)
        expect(extract(expanded(listOf("ignored/" to Ooxml.MAX_PART_BYTES+1))).isFailure)
        expect(extract(expanded(listOf("one" to Ooxml.MAX_PART_BYTES,"two" to Ooxml.MAX_PART_BYTES))).isFailure)
        expect(extract(zip(listOf("word/document.xml" to ByteArray(Ooxml.MAX_XML_BYTES+1)))).isFailure)
        expect(extract(zip(listOf("word/document.xml" to byteArrayOf(0xc0.toByte(),0x80.toByte())))).isFailure)
        expect(extract(zip(listOf("word/document.xml" to "<!DOCTYPE x SYSTEM 'file:///private'><w:t>x</w:t>".toByteArray()))).isFailure)
        for(name in listOf("../escape","/root","a//b","a/./b","a\\b","a\u0000b","a".repeat(1025)))
            expect(extract(zip(listOf("word/document.xml" to word,name to byteArrayOf()))).isFailure)
        // ZipOutputStream itself rejects duplicate names. Alter equal-length names in both headers after serialization.
        val dup=zip(listOf("word/document.xml" to word,"word/documenx.xml" to word))
        val from="word/documenx.xml".toByteArray();val to="word/document.xml".toByteArray()
        for(i in 0..dup.size-from.size) if(from.indices.all { dup[i+it]==from[it] }) to.copyInto(dup,i)
        expect(extract(dup).isFailure)
        expect(extract(zip(listOf("word/document.xml" to word)+List(Ooxml.MAX_ENTRIES) { "entry$it" to byteArrayOf() })).isFailure)
        expect(extract(zip(listOf("word/document.xml" to "<w:t>".repeat(100000).toByteArray()))).isFailure)
        val sheet="<worksheet><row>"+"<c/>".repeat(Ooxml.MAX_CELLS+1)+"</row></worksheet>"
        expect(Ooxml.extractXlsxSheet(ByteArrayInputStream(zip(listOf("xl/worksheets/sheet1.xml" to sheet.toByteArray())))).isFailure)
        val malformed="<worksheet><row>"+"<c>".repeat(100000)+"</row></worksheet>"
        expect(Ooxml.extractXlsxSheet(ByteArrayInputStream(zip(listOf("xl/worksheets/sheet1.xml" to malformed.toByteArray())))).isFailure)
        val emptyCells="<worksheet><row><c/><c /><c r=\"C1\"/></row></worksheet>"
        expect(Ooxml.extractXlsxSheet(ByteArrayInputStream(zip(listOf("xl/worksheets/sheet1.xml" to emptyCells.toByteArray())))).getOrThrow().rows==listOf(listOf("","","")))
        val worksheet="<worksheet><row><c t=\"s\"><v>0</v></c><c><v>42</v></c></row></worksheet>"
        val shared="<sst><si><t>héllo</t></si></sst>"
        val valid=zip(listOf("xl/worksheets/sheet1.xml" to worksheet.toByteArray(),"xl/sharedStrings.xml" to shared.toByteArray()))
        expect(Ooxml.extractXlsxSheet(ByteArrayInputStream(valid)).getOrThrow().rows==listOf(listOf("héllo","42")))
        val manyShared="<sst>"+"<si><t>x</t></si>".repeat(Ooxml.MAX_SHARED_STRINGS+1)+"</sst>"
        expect(Ooxml.extractXlsxSheet(ByteArrayInputStream(zip(listOf("xl/worksheets/sheet1.xml" to worksheet.toByteArray(),"xl/sharedStrings.xml" to manyShared.toByteArray())))).isFailure)
        val manyRows="<worksheet>"+"<row></row>".repeat(Ooxml.MAX_ROWS+1)+"</worksheet>"
        expect(Ooxml.extractXlsxSheet(ByteArrayInputStream(zip(listOf("xl/worksheets/sheet1.xml" to manyRows.toByteArray())))).isFailure)
        var closed=false
        val zero=object: InputStream() { override fun read()=0;override fun read(b:ByteArray,o:Int,l:Int)=0;override fun close() { closed=true } }
        expect(Ooxml.extractDocxText(zero).isFailure);expect(closed)
        // Stored data with a bad CRC: no partial extracted result may be returned.
        val stored=ByteArrayOutputStream().also { bytes -> ZipOutputStream(bytes).use { z ->
            val crc=CRC32().also { it.update(word) }
            z.putNextEntry(ZipEntry("word/document.xml").apply { method=ZipEntry.STORED;size=word.size.toLong();compressedSize=size;this.crc=crc.value })
            z.write(word);z.closeEntry()
        } }.toByteArray()
        val offset=30+"word/document.xml".length
        stored[offset]=(stored[offset].toInt() xor 1).toByte()
        expect(extract(stored).isFailure)
        println("PASS OOXML container guards: $checks assertions; ignored/directory/aggregate expansion, names/duplicates, strict decoding, cell/row/string bounds, CRC and stalled input")
    }
}

package dev.fusion.testing

import dev.fusion.files.ContentDigest
import dev.fusion.files.ContentReceipt
import dev.fusion.terminal.ShellWords
import dev.fusion.automation.*
import dev.fusion.continuity.*
import dev.fusion.ooxml.*
import java.io.*
import java.math.BigDecimal
import java.nio.file.Files
import java.time.Instant
import java.util.zip.ZipInputStream

object R13Checks {
    private var checks=0
    private fun eq(a:Any?,b:Any?) { checks++;check(a==b) { "check $checks: $a != $b" } }
    private fun refused(f:()->Unit) { checks++;try { f() } catch(_:IllegalArgumentException) { return } catch(_:java.nio.charset.CharacterCodingException) { return };error("Attack accepted $checks") }
    private val now=Instant.parse("2026-10-15T12:00:00Z")
    @JvmStatic fun main(args:Array<String>) {
        val root=Files.createTempDirectory("r13-real-")
        try {
            val file=root.resolve("content");Files.write(file,"abc".toByteArray())
            val digest=Files.newInputStream(file).use { ContentDigest.scan(it,3) }
            eq(ContentReceipt(3,"ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"),digest)
            Files.write(file,"abd".toByteArray());eq(false,Files.newInputStream(file).use { ContentDigest.matches(it,digest) })
            eq(0L,ContentDigest.scan(byteArrayOf().inputStream(),0).bytes)
            refused { ContentDigest.scan("x".byteInputStream(),0) }
            refused { ContentDigest.scan("abc".byteInputStream(),2) }
            refused { ContentDigest.scan(byteArrayOf().inputStream(),Long.MAX_VALUE) }
            refused { ContentDigest.scan(object:InputStream(){ override fun read()=0;override fun read(b:ByteArray,o:Int,l:Int)=0 }) }
            var closed=false
            val input=object:ByteArrayInputStream("abc".toByteArray()){override fun close(){closed=true;super.close()}}
            ContentDigest.scan(input);eq(false,closed);input.close();eq(true,closed)
            eq(listOf("echo","a b","","c d","$"+"HOME","x;y"),ShellWords.parse("echo 'a b' \"\" c\\ d $"+"HOME x;y"))
            eq(emptyList<String>(),ShellWords.parse(" \t\n"))
            eq(listOf("ab c"),ShellWords.parse("a\"b c\""))
            for(s in listOf("'bad","bad\\","\"bad","\u0000","x".repeat(2049),"x ".repeat(65)," ".repeat(4097))) refused { ShellWords.parse(s) }
            eq(64,ShellWords.parse("x ".repeat(64)).size)
            val plan=ActionPlan.parse("workspace 2\nopen calculator\nopen notes")
            val effects=root.resolve("actual-actions")
            val result=plan.execute { action -> Files.writeString(effects,action.toString()+"\n",java.nio.file.StandardOpenOption.CREATE,java.nio.file.StandardOpenOption.APPEND);true }
            eq(3,result.completed);eq(3,Files.readAllLines(effects).size)
            var attempted=0;val stopped=plan.execute { attempted++;attempted!=2 }
            eq(1,stopped.completed);eq(2,attempted)
            eq(0,plan.execute { throw IOException("real callback failure") }.completed)
            for(s in listOf("open beam","open terminal","workspace 0","workspace 4","open  notes","open notes\n","run sh","open notes\r","open notes\n".repeat(17))) refused { ActionPlan.parse(s) }
            eq(16,ActionPlan.parse(List(16){"open files"}.joinToString("\n")).actions().size)
            val event=SharedCalendarEvent("test@fusion",now,now,now.plusSeconds(60),"界🙂".repeat(70),"a,b;c\\d\nEND:VEVENT")
            val encoded=CalendarExport.encode(event)
            val physical=encoded.toString(Charsets.UTF_8).split("\r\n")
            for(line in physical) { checks++;check(line.toByteArray().size<=75) }
            val unfolded=encoded.toString(Charsets.UTF_8).replace("\r\n ","")
            eq(true,unfolded.contains("DESCRIPTION:a\\,b\\;c\\\\d\\nEND:VEVENT"))
            for(e in listOf(event.copy(uid="x\r\nBEGIN:VEVENT"),event.copy(end=now),event.copy(start=now.plusNanos(1)),event.copy(summary="\uD800"),event.copy(description="a\rb"),event.copy(summary="x".repeat(1025)))) refused { CalendarExport.encode(e) }
            val rows=listOf(listOf(LiteralCell.Text("=SUM(A1:A2)"),LiteralCell.Text("α🙂\r\n<&>"),LiteralCell.Number(BigDecimal("12.30")),LiteralCell.Bool(true),LiteralCell.Empty))
            val workbook=LiteralWorkbook.export("Original",rows)
            eq(2,workbook.limitations.size)
            val names=mutableListOf<String>();val contents=mutableMapOf<String,String>()
            ZipInputStream(workbook.bytes.inputStream()).use { z -> while(true){val e=z.nextEntry?:break;names.add(e.name);contents[e.name]=z.readBytes().toString(Charsets.UTF_8)} }
            eq(5,names.size);eq(false,names.any { "vba" in it || "external" in it })
            val xml=contents.getValue("xl/worksheets/sheet1.xml")
            eq(false,xml.contains("<f>"));eq(true,xml.contains("t=\"inlineStr\""));eq(true,xml.contains("&#13;"))
            for(name in listOf("", "a/b", "x".repeat(32),"'bad", "a\nb")) refused { LiteralWorkbook.export(name,rows) }
            for(text in listOf("\u0000","\uD800","_x000D_","x".repeat(32768))) refused { LiteralWorkbook.export("s",listOf(listOf(LiteralCell.Text(text)))) }
            refused { LiteralWorkbook.export("s",List(1001){emptyList()}) }
            refused { LiteralWorkbook.export("s",listOf(List(129){LiteralCell.Empty})) }
            refused { LiteralWorkbook.export("s",List(200){List(128){LiteralCell.Empty}}) }
            refused { LiteralWorkbook.export("s",List(70){listOf(LiteralCell.Text("a".repeat(32767)))}) }
            refused { LiteralWorkbook.export("s",listOf(listOf(LiteralCell.Number(BigDecimal("1234567890123456"))))) }
            refused { LiteralWorkbook.export("s",listOf(listOf(LiteralCell.Number(BigDecimal("1E1000"))))) }
            val random=java.util.Random(130013)
            repeat(200) {
                val bytes=ByteArray(random.nextInt(8193)).also(random::nextBytes)
                eq(bytes.size.toLong(),ContentDigest.scan(bytes.inputStream(),bytes.size.toLong()).bytes)
                val text=(0 until random.nextInt(100)).joinToString(""){listOf("a","界","🙂"," ")[random.nextInt(4)]}
                eq(listOf(text),ShellWords.parse("'$text'"))
            }
            println("R13: $checks assertions PASS; real stream/file/callback effects and bounded serializers. Not full Office/Files/PTY/calendar/automation acceptance.")
        } finally { root.toFile().deleteRecursively() }
    }
}

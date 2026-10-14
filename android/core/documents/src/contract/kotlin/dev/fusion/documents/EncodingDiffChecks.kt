package dev.fusion.documents

import java.io.*
import java.nio.file.Files
import kotlin.random.Random

/** Real codec, migration, file copy/readback and line edit scripts. No Android/provider substitute. */
object EncodingDiffChecks {
    private var checks=0
    private fun expect(ok: Boolean) { checks++;check(ok) { "Encoding/diff check $checks failed" } }
    private fun refuse(block: ()->Unit) { var failed=false;try { block() } catch(_: Exception) { failed=true };expect(failed) }
    private fun checkpoint(s: EditorSession): EditorSession = SessionCodec.read(ByteArrayInputStream(
        ByteArrayOutputStream().also { SessionCodec.write(s,it) }.toByteArray()))
    @JvmStatic fun main(args: Array<String>) {
        val legacy=File(args.single(),"editor-session-v2.bin").inputStream().use(SessionCodec::read)
        expect(legacy.documents.single().encoding==TextEncoding.UTF8)
        expect(legacy.pendingCopy?.encoding==TextEncoding.UTF8 && legacy.copySequence==1L)
        expect(legacy.pendingCopy?.content=="Original α" && legacy.pendingCopy?.stage==CopyStage.AWAITING_PICKER)
        expect(checkpoint(legacy)==legacy)
        expect(CopyProtocol.recover(legacy).pendingCopy?.stage==CopyStage.REVIEW_REQUIRED)
        val root=Files.createTempDirectory("fusion-encoding-check-").toFile()
        try {
            for(e in TextEncoding.entries) {
                val text=if(e==TextEncoding.WINDOWS_1252) "€ café\r\n“exact”" else "α漢😀\r\n\u0000tail\r\n"
                val bytes=TextEncodingCodec.encode(text,e)
                expect(TextEncodingCodec.decode(bytes,e)==DecodedText(text,e))
                if(e in listOf(TextEncoding.UTF8,TextEncoding.UTF8_BOM,TextEncoding.UTF16_LE_BOM,TextEncoding.UTF16_BE_BOM))
                    expect(TextEncodingCodec.decode(bytes)==DecodedText(text,e))
                val doc=TextDocument("doc","native.txt","content://example/source",text,TextBytes.digest("before"),e)
                var session=CopyProtocol.start(EditorSession(listOf(doc),doc.id),doc.id,"00000000-0000-4000-8000-000000000001")
                expect(checkpoint(session)==session)
                val id=requireNotNull(session.pendingCopy?.requestId);val uri="content://example/new-copy"
                session=CopyProtocol.reserve(CopyProtocol.receive(session,id,uri),id)
                val source=File(root,"source").apply { writeBytes(bytes) }
                val destination=File(root,"copy")
                val evidence=VerifiedCopy.write(bytes,{ destination.outputStream() },{ destination.inputStream() },VerifiedCopy.Binding(id,uri))
                expect(destination.readBytes().contentEquals(source.readBytes()))
                if(e!=TextEncoding.UTF8) {
                    val utf8=File(root,"wrong-copy")
                    val wrong=VerifiedCopy.write(TextBytes.encode(text),{ utf8.outputStream() },{ utf8.inputStream() },VerifiedCopy.Binding(id,uri))
                    refuse { CopyProtocol.verified(session,id,wrong) }
                }
                val done=CopyProtocol.verified(session,id,evidence)
                expect(!done.documents.single().dirty && done.documents.single().encoding==e)
                expect(done.copyReceipts.single().digest==TextBytes.digest(bytes))
                expect(checkpoint(done)==done)
                val late=session.copy(documents=listOf(doc.copy(content=text+"later")))
                expect(CopyProtocol.verified(late,id,evidence).documents.single().dirty)
            }
        } finally { root.deleteRecursively() }
        for(b in listOf(byteArrayOf(0xc0.toByte(),0x80.toByte()),byteArrayOf(0xff.toByte()),byteArrayOf(0xef.toByte(),0xbb.toByte())))
            refuse { TextEncodingCodec.decode(b) }
        refuse { TextEncodingCodec.decode(byteArrayOf(0xff.toByte(),0xfe.toByte(),0,0)) } // Ambiguous UTF32 / UTF16 NUL needs explicit choice.
        expect(TextEncodingCodec.decode(byteArrayOf(0xff.toByte(),0xfe.toByte(),0,0),TextEncoding.UTF16_LE_BOM).content=="\u0000")
        refuse { TextEncodingCodec.decode(byteArrayOf(0xff.toByte(),0xfe.toByte(),0),TextEncoding.UTF16_LE_BOM) }
        refuse { TextEncodingCodec.decode(byteArrayOf(0xff.toByte(),0xfe.toByte(),0,0xd8.toByte()),TextEncoding.UTF16_LE_BOM) }
        refuse { TextEncodingCodec.decode(byteArrayOf(0x81.toByte()),TextEncoding.WINDOWS_1252) }
        refuse { TextEncodingCodec.decode(byteArrayOf(1,2),TextEncoding.UTF8_BOM) }
        refuse { TextEncodingCodec.encode("😀",TextEncoding.WINDOWS_1252) }
        refuse { TextEncodingCodec.encode("\uD800",TextEncoding.UTF16_LE) }
        refuse { TextEncodingCodec.encode("a".repeat(TextBytes.MAX_BYTES),TextEncoding.UTF16_LE) }
        refuse { TextEncodingCodec.encode("a".repeat(TextBytes.MAX_BYTES),TextEncoding.UTF8_BOM) }
        expect(TextEncodingCodec.encode("a".repeat(TextBytes.MAX_BYTES),TextEncoding.UTF8).size==TextBytes.MAX_BYTES)
        // A legacy-encoding draft with a newly unrepresentable character MUST remain recoverable.
        val draft=TextDocument("draft","latin.txt",null,"new 😀",TextBytes.digest("new"),TextEncoding.WINDOWS_1252)
        val state=EditorSession(listOf(draft),draft.id)
        expect(checkpoint(state)==state)
        refuse { CopyProtocol.start(state,draft.id,"00000000-0000-4000-8000-000000000001") }
        expect(state.pendingCopy==null && state.documents.single().content=="new 😀")
        val rng=Random(1014)
        val pieces=listOf("a\n","b\r\n","c\r","😀\n","", "tail")
        repeat(1200) {
            val before=List(rng.nextInt(12)) { pieces.random(rng) }.joinToString("")
            val after=List(rng.nextInt(12)) { pieces.random(rng) }.joinToString("")
            val diff=LineDiff.compare(before,after)
            expect(diff.lines.filter { it.change!=LineChange.ADDED }.joinToString("") { it.text }==before)
            expect(diff.lines.filter { it.change!=LineChange.REMOVED }.joinToString("") { it.text }==after)
            expect(diff.added==diff.lines.count { it.change==LineChange.ADDED })
            expect(diff.removed==diff.lines.count { it.change==LineChange.REMOVED })
            expect(LineDiff.compare(before,before).let { it.added==0 && it.removed==0 })
        }
        expect(LineDiff.compare("x\r\n","x\n").let { it.added==1 && it.removed==1 })
        expect(LineDiff.compare("x\n","x").let { it.added==1 && it.removed==1 })
        expect(LineDiff.compare("a\nb\nc\n","a\nx\nc\n").let { it.added==1 && it.removed==1 })
        expect(LineDiff.compare("","").lines.isEmpty())
        refuse { LineDiff.compare("x\n".repeat(2049),"") }
        refuse { LineDiff.compare("x\n".repeat(1000),"x\n".repeat(1000)) }
        expect(LineDiff.compare("x\n".repeat(999),"x\n".repeat(999)).added==0)
        refuse { (LineDiff.compare("a","b").lines as MutableList<DiffLine>).clear() }
        println("PASS encoding + diff: $checks assertions; v2/v3 migration, exact encoded file readback, wrong-encoding evidence refusal, draft retention, line reconstruction/bounds")
    }
}

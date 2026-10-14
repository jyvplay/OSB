package dev.fusion.documents

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Locale
import kotlin.random.Random

/** Executes production code with independent expected strings, exhaustive short inputs and real codecs. */
object EditorChecks {
    private var assertions = 0
    private fun verify(ok: Boolean, why: String) { assertions++; check(ok) { why } }
    private fun rejects(why: String, block: ()->Unit) {
        var rejected = false
        try { block() } catch(_: IllegalArgumentException) { rejected = true }
        verify(rejected,why)
    }
    private fun buffer(text: String, id: String="doc") = EditBuffer.create(id,text)
    fun run(): Int {
        assertions=0
        for(text in listOf("", "ascii", "\uFEFFa\r\nb\rc\n", "\u0000", "é漢😀", "e\u0301", "\uDBFF\uDFFF")) {
            verify(TextBytes.encodedSize(text)==TextBytes.encode(text).size,"strict UTF-8 size matches independent platform encoder")
        }
        for(text in listOf("\uD800","\uDC00","x\uD800a","\uD800\uD800")) rejects("reject malformed Unicode") { TextBytes.encodedSize(text) }
        verify(TextBytes.encodedSize("a".repeat(TextBytes.MAX_BYTES))==TextBytes.MAX_BYTES,"exact byte limit")
        rejects("multi-byte size not character count") { TextBytes.encodedSize("漢".repeat(TextBytes.MAX_BYTES/3+1)) }
        val selection=TextSelection(2).inText("a😀b")
        verify(selection==TextSelection(1),"surrogate interior caret snaps to code-point boundary")
        var edit=buffer("a😀b\r\n").select(TextSelection(1,3))
        val before=edit
        edit=edit.edit("a😁b\r\n",TextSelection(3))
        verify(edit.undo().content==before.content && edit.undo().selection==before.selection,"shared high surrogate is not split in delta")
        verify(edit.undo().redo().content==edit.content,"emoji redo")
        val saved=TextDocument("doc","test.txt",null,"first",TextBytes.digest("first"))
        val pending=PendingCopy("doc","test.txt",null,"frozen")
        val changed=buffer(saved.content).edit("second")
        verify(saved.copy(content=changed.content).dirty,"edit dirty")
        verify(!saved.copy(content=changed.undo().content).dirty,"undo to saved digest clean")
        verify(pending.content=="frozen","editing never changes frozen save payload")
        var bounded=buffer("")
        repeat(150) { bounded=bounded.edit("$it") }
        verify(bounded.undoDepth==100 && bounded.historyTrimmed,"step budget clips oldest steps visibly")
        repeat(100) { bounded=bounded.undo() }
        verify(bounded.content=="49" && bounded.undoDepth==0,"oldest retained undo boundary")
        verify(bounded.redoDepth==100,"redo retains exact chain")
        bounded=bounded.edit("branch")
        verify(bounded.redoDepth==0 && bounded.undo().content=="49","new edit invalidates redo only after actual change")
        val bigA="a".repeat(TextBytes.MAX_BYTES)
        val bigB="b".repeat(TextBytes.MAX_BYTES)
        val big=buffer(bigA).edit(bigB)
        verify(big.historyUnits==EditBuffer.MAX_HISTORY_UNITS && big.undo().content==bigA,"largest single edit always undoable at payload cap")
        verify(big.edit("c").historyUnits<=EditBuffer.MAX_HISTORY_UNITS,"payload trimming respects cap")
        val rng=Random(490401)
        val pieces=listOf("a","é","😀","\r\n","\n","漢","e\u0301","")
        repeat(300) {
            val old=buildString { repeat(rng.nextInt(20)) { append(pieces.random(rng)) } }
            val new=buildString { repeat(rng.nextInt(20)) { append(pieces.random(rng)) } }
            val a=buffer(old).select(TextSelection(old.length,0)); val b=a.edit(new)
            if(old != new) {
                verify(b.undo().content==old && b.undo().selection==a.selection,"random exact undo")
                verify(b.undo().redo().content==new,"random exact redo")
                verify(b.undo().revision>b.revision,"undo never reuses an earlier revision")
            }
            verify(b.historyUnits<=EditBuffer.MAX_HISTORY_UNITS,"random bounded history")
        }
        println("PASS editor deltas: exact Unicode/newline reversal, branch invalidation, saved-state semantics, step/payload limits, 300 varied string pairs")

        fun ranges(text: String,q: String,case: Boolean=true) = LiteralSearch.matches(text,LiteralQuery(q,case))
        verify(ranges("aaa","aa")==listOf(TextSelection(0,2)),"non-overlap semantics")
        verify(ranges("a😀b😀","😀")==listOf(TextSelection(1,3),TextSelection(4,6)),"UTF-16 match offsets")
        verify(ranges("café cafe\u0301","é")==listOf(TextSelection(3,4)),"no silent normalization")
        verify(ranges("Straße STRASSE","strasse",false)==listOf(TextSelection(7,14)),"no expanding ß fold")
        verify(ranges("Σσς","σ",false).size==3,"simple Greek case mapping")
        verify(ranges("\uD801\uDC00\uD801\uDC28","\uD801\uDC28",false).size==2,"supplementary Deseret case mapping")
        val locale=Locale.getDefault()
        try { Locale.setDefault(Locale.forLanguageTag("tr-TR")); verify(ranges("iIİı","i",false).size==4,"simple mapping independent of Turkish default locale") }
        finally { Locale.setDefault(locale) }
        rejects("empty query is not an insertion everywhere") { ranges("abc","") }
        rejects("pattern budget") { ranges("a","x".repeat(1025)) }
        rejects("excess matches never return a partial result") { ranges("a".repeat(10001),"a") }
        verify(ranges("a".repeat(10000),"a").size==10000,"exact result cap")
        verify(ranges("a".repeat(100000)+"b","a".repeat(1023)+"b").single().end==100001,"long repetitive KMP adversary")
        // Exhaustive binary texts/needles versus naive non-overlap indexOf, not KMP.
        fun words(length: Int)=(0 until (1 shl length)).map { bits -> buildString { repeat(length) { append(if(bits and (1 shl it)==0) 'a' else 'b') } } }
        for(n in 0..7) for(text in words(n)) for(m in 1..4) for(needle in words(m)) {
            val expected=mutableListOf<TextSelection>(); var offset=0
            while(true) { val at=text.indexOf(needle,offset); if(at<0) break; expected+=TextSelection(at,at+needle.length); offset=at+needle.length }
            verify(ranges(text,needle)==expected,"exhaustive KMP/indexOf agreement [$text]/[$needle]")
        }
        val found=ranges("aba aba","aba")
        verify(LiteralSearch.next(found,TextSelection(0,3))==TextSelection(4,7),"next excludes current nonempty selection")
        verify(LiteralSearch.next(found,TextSelection(4,7))==TextSelection(0,3),"next wraps")
        verify(LiteralSearch.next(found,TextSelection(0,3),true)==TextSelection(4,7),"previous wraps")
        println("PASS editor literal search: exhaustive short binary oracle, repetitive long input, Unicode/locale/non-overlap semantics and hard limits")

        val source=buffer("one one\r\n😀 one")
        val plan=TextReplacement.plan(source,LiteralQuery("one"),"$1\\n")
        verify(plan.count==3 && plan.apply(source).content=="$1\\n $1\\n\r\n😀 $1\\n","replacement has no regex expansion or newline normalization")
        verify(plan.apply(source).undo().content==source.content,"replace all is one undo step")
        rejects("cross-document plan rejected even for same text/revision") { plan.apply(buffer(source.content,"other")) }
        val aba=source.edit("temporary").undo()
        rejects("ABA text cycle still stale") { plan.apply(aba) }
        verify(aba.content==source.content,"rejected plan preserved reverted text")
        val selected=source.select(TextSelection(0,3))
        verify(TextReplacement.plan(selected,LiteralQuery("one"),"",true).apply(selected).content==" one\r\n😀 one","empty replacement deletes selected match only")
        val selectionPlan=TextReplacement.plan(selected,LiteralQuery("one"),"x",true)
        rejects("selection moved after selected-match confirmation was prepared") { selectionPlan.apply(selected.select(TextSelection(4,7))) }
        verify(selectionPlan.apply(selected.select(TextSelection(3,0))).content=="x one\r\n😀 one","reversed direction of same selected span remains valid")
        verify(plan.apply(source.select(TextSelection(source.content.length))).content==plan.apply(source).content,"replace-all plan does not depend on caret movement")
        rejects("partial match selection not accepted") { TextReplacement.plan(source.select(TextSelection(0,2)),LiteralQuery("one"),"x",true) }
        rejects("unit-count multiplication overflow avoided") { TextReplacement.plan(buffer("a".repeat(10000)),LiteralQuery("a"),bigA) }
        rejects("UTF-8 byte expansion refused before mutation") { TextReplacement.plan(buffer("a".repeat(1000)),LiteralQuery("a"),"漢".repeat(1000)) }
        verify(source.content=="one one\r\n😀 one","planning never mutates source")
        val same=TextReplacement.plan(source,LiteralQuery("one"),"one").apply(source)
        verify(same.revision==source.revision && same.undoDepth==0,"no-op replacement is not a fake undo step")
        val session=EditorSession(listOf(saved.copy(content=plan.apply(source).content)),"doc",pending)
        val bytes=ByteArrayOutputStream().also { SessionCodec.write(session,it) }.toByteArray()
        verify(SessionCodec.read(ByteArrayInputStream(bytes))==session,"existing codec preserves new draft and frozen pending payload")
        println("PASS editor replacement: one-step reversibility, stale/cross-tab/ABA rejection, literal replacement, empty deletion, byte expansion and codec compatibility")

        val text="😀x\r\né\r漢\n"
        for((line,column,offset) in listOf(Triple(1,1,0),Triple(1,2,2),Triple(1,3,3),Triple(2,1,5),Triple(2,2,6),Triple(3,1,7),Triple(4,1,9)))
            verify(TextNavigation.offset(text,line,column)==offset,"CRLF/CR/LF/code-point navigation $line:$column")
        for((line,column) in listOf(0 to 1,1 to 0,5 to 1,1 to 4,2 to 3,Int.MAX_VALUE to 1))
            rejects("out-of-range navigation is not clamped to wrong text") { TextNavigation.offset(text,line,column) }
        verify(TextNavigation.offset("",1,1)==0,"empty document starts at 1:1")
        println("PASS editor navigation: explicit code-point columns, mixed line endings, EOF and invalid coordinate guards")
        println("PASS: $assertions editor assertions. No Android UI, IME, accessibility, provider or frame-time acceptance inferred.")
        return assertions
    }
    @JvmStatic fun main(args: Array<String>) { run() }
}

package dev.fusion.alerts

import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction

data class PublicNotice(val id:String,val title:String,val body:String,val postedMillis:Long)
data class NoticeUpdate(val retained:List<PublicNotice>,val evictedIds:List<String>)
/** Session-only public data ledger. No system notifications read; listener remains disabled.
 * Single-owner API. Caller supplies nonnegative nondecreasing time in milliseconds, not a trusted clock.
 */
class NoticeLedger(private val capacity:Int=256,private val retentionMillis:Long=604800000L) {
    init { require(capacity in 1..256 && retentionMillis in 1..604800000L) }
    private var clock=0L
    private var rows=emptyMap<String,PublicNotice>()
    private fun id(s:String) { require(s.matches(Regex("[A-Za-z0-9_.:@/-]{1,128}"))) }
    private fun time(now:Long) { require(now>=clock) { "Clock rollback" } }
    private fun live(now:Long)=rows.filterValues { now-it.postedMillis<retentionMillis }
    private fun ordered(items:Collection<PublicNotice>)=items.sortedWith(compareByDescending<PublicNotice> { it.postedMillis }.thenBy { it.id })
    private fun commit(now:Long,next:Map<String,PublicNotice>):NoticeUpdate {
        val sorted=ordered(next.values);val kept=sorted.take(capacity)
        val evicted=(rows.keys+next.keys).filter { key -> kept.none { it.id==key } }.sorted()
        clock=now;rows=kept.associateBy { it.id };return NoticeUpdate(kept,evicted)
    }
    fun post(notice:PublicNotice,now:Long):NoticeUpdate {
        time(now);id(notice.id)
        require(notice.postedMillis in 0..now)
        require(notice.title.length<=256 && notice.body.length<=2048 && (notice.title.isNotEmpty() || notice.body.isNotEmpty()))
        for(s in listOf(notice.title,notice.body)) Charsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT).encode(CharBuffer.wrap(s))
        val next=live(now).toMutableMap()
        require(next[notice.id]?.let { it.postedMillis<=notice.postedMillis }!=false) { "Stale replacement" }
        if(now-notice.postedMillis<retentionMillis) next[notice.id]=notice
        return commit(now,next)
    }
    /** Use this for a public→private/secret transition: no private body or caller clock enters this operation. */
    fun redact(id:String):NoticeUpdate { id(id);return commit(clock,rows-id) }
    fun advance(now:Long):NoticeUpdate { time(now);return commit(now,live(now)) }
    fun clear():NoticeUpdate = commit(clock,emptyMap())
}

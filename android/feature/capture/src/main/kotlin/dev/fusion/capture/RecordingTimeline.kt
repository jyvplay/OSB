package dev.fusion.capture

data class RecordingSpan(val fromMillis:Long,val untilMillis:Long)
data class RecordingTime(val elapsedMillis:Long,val activeMillis:Long,val pausedMillis:Long,val stopped:Boolean)
/** Single-owner elapsed-time accounting. Not encoded media timestamps, capture consent or an encoder. */
class RecordingTimeline {
    private var start:Long?=null
    private var active:Long?=null
    private var end:Long?=null
    private var last=0L
    private var total=0L
    private val spans=mutableListOf<RecordingSpan>()
    private fun time(now:Long) { require(now>=last && now>=0) { "Nonmonotonic clock" } }
    fun start(now:Long) { require(start==null);time(now);start=now;active=now;last=now }
    fun pause(now:Long) {
        time(now);require(start!=null && end==null && active!=null && spans.size<1024)
        val from=requireNotNull(active);val next=Math.addExact(total,now-from)
        spans.add(RecordingSpan(from,now));total=next;active=null;last=now
    }
    fun resume(now:Long) { time(now);require(start!=null && end==null && active==null && spans.size<1024);active=now;last=now }
    fun stop(now:Long):RecordingTime {
        time(now);require(start!=null && end==null)
        if(active!=null) pause(now)
        end=now;last=now;return read(now)
    }
    fun read(now:Long):RecordingTime {
        time(now);val beginning=requireNotNull(start) { "Not started" };last=now
        val until=end ?: now
        val elapsed=until-beginning;val working=total+(active?.let { until-it } ?: 0L)
        return RecordingTime(elapsed,working,elapsed-working,end!=null)
    }
    fun completedSpans():List<RecordingSpan> = spans.toList()
}

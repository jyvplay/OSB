package dev.fusion.browser.model

import java.io.*
import java.security.MessageDigest

data class NamedBrowserSession(val name: String,val state: BrowserSessions)

/** Bounded metadata-only format. No cookies, grants, profiles, passwords, renderer state or automatic URL loading. */
object BrowserSessionCodec {
    const val MAX_BYTES=512*1024
    private const val MAGIC=0x46425253 // FBRS
    fun write(session: NamedBrowserSession,output: OutputStream) {
        boundedText(session.name,128);require(session.name.isNotBlank())
        val s=session.state
        val payload=ByteArrayOutputStream().also { bytes -> DataOutputStream(bytes).use { out ->
            out.writeInt(MAGIC);out.writeInt(1);out.writeUTF(session.name);out.writeLong(s.nextId)
            out.writeInt(s.windows.size)
            s.windows.forEach { w ->
                out.writeLong(w.id);out.optional(w.active);out.writeBoolean(w.split!=null)
                w.split?.let { out.writeLong(it.secondary);out.writeUTF(it.axis.name);out.writeInt(it.dividerPermille) }
            }
            out.writeInt(s.tabs.size)
            s.tabs.forEach { t ->
                out.writeLong(t.id);out.writeLong(t.window);out.writeUTF(t.url);out.writeUTF(t.title)
                out.writeBoolean(t.pinned);out.optional(t.group);out.writeBoolean(t.sleepRequested)
            }
            out.writeInt(s.groups.size)
            s.groups.forEach { g -> out.writeLong(g.id);out.writeLong(g.window);out.writeUTF(g.name);out.writeUTF(g.color.name);out.writeBoolean(g.collapsed) }
        } }.toByteArray()
        require(payload.size<=MAX_BYTES)
        DataOutputStream(output).apply { writeInt(payload.size);write(payload);write(MessageDigest.getInstance("SHA-256").digest(payload));flush() }
    }
    fun read(input: InputStream): NamedBrowserSession {
        val source=DataInputStream(input);val length=source.readInt();require(length in 8..MAX_BYTES)
        val payload=ByteArray(length).also(source::readFully);val digest=ByteArray(32).also(source::readFully)
        require(MessageDigest.isEqual(digest,MessageDigest.getInstance("SHA-256").digest(payload))) { "Session checksum mismatch" }
        require(source.read()==-1) { "Trailing session data" }
        return DataInputStream(ByteArrayInputStream(payload)).use { data ->
            require(data.readInt()==MAGIC && data.readInt()==1) { "Unsupported session format" }
            val name=data.readUTF();boundedText(name,128);require(name.isNotBlank())
            val next=data.readLong()
            val windows=List(data.count(1,BrowserSessions.MAX_WINDOWS)) {
                BrowserWindow(data.readLong(),data.optional(),if(data.flag()) BrowserSplit(data.readLong(),SplitAxis.valueOf(data.readUTF()),data.readInt()) else null)
            }
            val tabs=List(data.count(0,BrowserSessions.MAX_TABS)) {
                BrowserTab(data.readLong(),data.readLong(),data.readUTF(),data.readUTF(),data.flag(),data.optional(),data.flag())
            }
            val groups=List(data.count(0,BrowserSessions.MAX_GROUPS)) {
                BrowserGroup(data.readLong(),data.readLong(),data.readUTF(),GroupColor.valueOf(data.readUTF()),data.flag())
            }
            require(data.read()==-1)
            NamedBrowserSession(name,BrowserSessions.admit(windows,tabs,groups,next))
        }
    }
    private fun DataOutputStream.optional(value:Long?) { writeBoolean(value!=null);value?.let(::writeLong) }
    private fun DataInputStream.optional():Long? = if(flag()) readLong() else null
    private fun DataInputStream.flag():Boolean { val value=readUnsignedByte();require(value==0 || value==1);return value==1 }
    private fun DataInputStream.count(min:Int,max:Int):Int=readInt().also { require(it in min..max) }
}

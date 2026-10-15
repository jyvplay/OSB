package dev.fusion.browser.model

import java.io.*
import java.nio.file.Files
import kotlin.random.Random

/** Executes production domain operations and actual serialized files. No WebView/network/device substitution. */
object BrowserSessionChecks {
    private var count=0
    private fun expect(value:Boolean) { count++;check(value) { "Browser session check $count failed" } }
    private fun refused(block:()->Unit) { var failed=false;try { block() } catch(_:Exception) { failed=true };expect(failed) }
    private fun equal(a:BrowserSessions,b:BrowserSessions)=a.tabs==b.tabs && a.windows==b.windows && a.groups==b.groups && a.nextId==b.nextId
    private fun encode(s:BrowserSessions)=ByteArrayOutputStream().also { BrowserSessionCodec.write(NamedBrowserSession("Work α",s),it) }.toByteArray()
    private fun verify(s:BrowserSessions) {
        expect(equal(s,BrowserSessions.admit(s.windows,s.tabs,s.groups,s.nextId)))
        val next=BrowserSessionCodec.read(ByteArrayInputStream(encode(s)))
        expect(next.name=="Work α" && equal(s,next.state))
        expect(s.tabs.size<=64 && s.windows.size in 1..8 && s.groups.size<=16)
    }
    @JvmStatic fun main(args:Array<String>) {
        expect(NavigationTarget.parse("HTTPS://Example.COM:443/a%2Fb?q=%23#frag")=="https://example.com/a%2Fb?q=%23#frag")
        expect(NavigationTarget.origin("https://EXAMPLE.com:443/path?q=private")=="https://example.com")
        expect(NavigationTarget.origin("about:blank")==null)
        expect(NavigationTarget.parse("http://[::1]:80")=="http://[::1]/")
        for(url in listOf("javascript:alert(1)","file:///private","content://docs/1","data:text/html,evil","intent://launch",
            " https://example.com","https://user:pass@example.com/","https://example.com:65536","https://example.com/\n",
            "https://example.com\\@evil.test","https://éxample.com","//example.com","http:example.com","https://example.com/\uD800")) refused { NavigationTarget.parse(url) }
        var s=BrowserSessions.empty().open(1,"https://a.test").open(1,"https://b.test").open(1,"https://c.test")
        val a=s.tabs[0].id;val b=s.tabs[1].id;val c=s.tabs[2].id
        s=s.group(1,setOf(a,c),"Project",GroupColor.BLUE)
        expect(s.orderedTabs(1).map { it.id }==listOf(a,c,b))
        val group=s.groups.single().id
        s=s.editGroup(group,"Renamed",GroupColor.GREEN,true);expect(s.groups.single().collapsed)
        s=s.pin(a,true);expect(s.tabs.first().id==a && s.tabs.first().group==null)
        refused { s.group(1,setOf(a),"Pinned",GroupColor.RED) }
        s=s.select(b).split(1,c,SplitAxis.VERTICAL,200)
        refused { s.requestSleep(c) };refused { s.requestSleep(b) };refused { s.requestSleep(a) }
        s=s.select(c);expect(s.windows.single().split?.secondary==b)
        s=s.unsplit(1).requestSleep(b);expect(s.tabs.single { it.id==b }.sleepRequested)
        s=s.select(b);expect(!s.tabs.single { it.id==b }.sleepRequested)
        s=s.detach(c);val other=s.windows.last().id
        expect(s.tabs.single { it.id==c }.group==null)
        s=s.merge(other,1);expect(s.windows.size==1)
        s=s.open(1,"https://a.test").closeDuplicates(1)
        expect(s.tabs.count { it.url=="https://a.test/" }==1)
        s=s.closeOthers(c);expect(s.tabs.map { it.id }.toSet()==setOf(a,c))
        verify(s)
        val previousNext=s.nextId;s=s.close(s.tabs.map { it.id }.toSet()).open(1,"about:blank")
        expect(s.tabs.single().id==previousNext)
        expect(s.search("BLANK").size==1)
        refused { (s.tabs as MutableList<BrowserTab>).clear() }
        refused { (s.windows as MutableList<BrowserWindow>).clear() }
        val caller=s.tabs.toMutableList();val admitted=BrowserSessions.admit(s.windows,caller,s.groups,s.nextId);caller.clear()
        expect(admitted.tabs.size==1)
        refused { BrowserSessions.admit(s.windows,s.tabs+s.tabs,s.groups,s.nextId) }
        refused { BrowserSessions.admit(listOf(s.windows.single().copy(active=999)),s.tabs,s.groups,s.nextId) }
        refused { s.split(1,s.tabs.single().id,SplitAxis.HORIZONTAL) }
        refused { s.navigate(s.tabs.single().id,"about:blank","\uD800") }
        val exhausted=BrowserSessions.admit(s.windows,s.tabs,s.groups,Long.MAX_VALUE)
        refused { exhausted.open(1,"about:blank") }
        var full=BrowserSessions.empty();repeat(64) { full=full.open(1,"about:blank") }
        refused { full.open(1,"about:blank") };expect(full.tabs.size==64)
        val bytes=encode(s)
        for(index in bytes.indices) {
            refused { BrowserSessionCodec.read(ByteArrayInputStream(bytes.copyOf(index))) }
            val changed=bytes.copyOf();changed[index]=(changed[index].toInt() xor 1).toByte()
            refused { BrowserSessionCodec.read(ByteArrayInputStream(changed)) }
        }
        refused { BrowserSessionCodec.read(ByteArrayInputStream(bytes+byteArrayOf(0))) }
        fun malicious(version:Int,windows:Int,truthy:Boolean=false): ByteArray {
            val payload=ByteArrayOutputStream().also { bytes -> DataOutputStream(bytes).use { out ->
                out.writeInt(0x46425253);out.writeInt(version);out.writeUTF("Untrusted");out.writeLong(2);out.writeInt(windows)
                if(truthy) { out.writeLong(1);out.writeByte(2) }
            } }.toByteArray()
            return ByteArrayOutputStream().also { bytes -> DataOutputStream(bytes).use { out ->
                out.writeInt(payload.size);out.write(payload);out.write(java.security.MessageDigest.getInstance("SHA-256").digest(payload))
            } }.toByteArray()
        }
        for(forged in listOf(malicious(2,1),malicious(1,Int.MAX_VALUE),malicious(1,0),malicious(1,1,true)))
            refused { BrowserSessionCodec.read(ByteArrayInputStream(forged)) }
        val file=Files.createTempFile("browser-session-",".bin").toFile()
        try { file.outputStream().use { BrowserSessionCodec.write(NamedBrowserSession("Work α",s),it) };expect(equal(s,file.inputStream().use(BrowserSessionCodec::read).state)) }
        finally { file.delete() }
        // Varied production operations with invariant/codec checks after every successful transition.
        val rng=Random(1107);s=BrowserSessions.empty()
        repeat(1200) { step ->
            val before=s
            try {
                val tab=s.tabs.randomOrNull(rng)
                s=when(rng.nextInt(12)) {
                    0,1 -> s.open(s.windows.random(rng).id,"https://site${step%17}.test/page#${step%3}")
                    2 -> tab?.let { s.close(setOf(it.id)) } ?: s
                    3 -> tab?.let { s.pin(it.id,!it.pinned) } ?: s
                    4 -> tab?.let { s.select(it.id) } ?: s
                    5 -> tab?.let { s.detach(it.id) } ?: s
                    6 -> if(s.windows.size>1) s.merge(s.windows.last().id,s.windows.first().id) else s
                    7 -> tab?.let { s.reorder(it.id,rng.nextInt(s.orderedTabs(it.window).size)) } ?: s
                    8 -> tab?.let { s.group(it.window,setOf(it.id),"Group$step",GroupColor.RED) } ?: s
                    9 -> tab?.let { s.requestSleep(it.id) } ?: s
                    10 -> tab?.let { s.split(it.window,it.id,SplitAxis.HORIZONTAL,500) } ?: s
                    else -> s.closeDuplicates(s.windows.random(rng).id)
                }
            } catch(_:IllegalArgumentException) { expect(equal(s,before)) }
            verify(s);expect(s.nextId>=before.nextId)
        }
        println("PASS browser sessions: $count assertions; URL admission, immutable tab/group/window operations, split/pinned invariants, exact session bytes and corruption/ID/limit attacks. No renderer, actual tab sleep, drag surface, site permission or profile isolation claim.")
    }
}

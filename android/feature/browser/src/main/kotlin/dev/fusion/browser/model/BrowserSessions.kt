package dev.fusion.browser.model

import java.net.URI
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.util.Collections
import java.util.Locale

/** URL admission only. Not redirect/DNS/TLS/SSRF protection or permission authorization. */
object NavigationTarget {
    fun parse(raw: String): String {
        boundedText(raw,4096)
        require(raw==raw.trim() && raw.none { it.isISOControl() || it=='\\' }) { "Invalid URL characters" }
        if(raw=="about:blank") return raw
        val uri=URI(raw)
        val scheme=uri.scheme?.lowercase(Locale.ROOT)
        require(scheme=="https" || scheme=="http") { "Only explicit HTTP(S) or about:blank navigation is admitted" }
        require(uri.rawUserInfo==null && uri.host!=null && uri.port in -1..65535) { "Invalid host, credentials or port" }
        val host=uri.host.lowercase(Locale.ROOT)
        val port=if(uri.port==-1 || (scheme=="https" && uri.port==443) || (scheme=="http" && uri.port==80)) "" else ":${uri.port}"
        // Preserve raw percent escapes, path, query and fragment. Do not decode into a different URL.
        return "$scheme://$host$port"+(uri.rawPath.ifEmpty { "/" })+
            (uri.rawQuery?.let { "?$it" } ?: "")+(uri.rawFragment?.let { "#$it" } ?: "")
    }
    fun origin(raw: String): String? {
        val value=parse(raw)
        if(value=="about:blank") return null
        val uri=URI(value)
        return uri.scheme+"://"+uri.rawAuthority
    }
}
internal fun boundedText(value: String,limit: Int) {
    require(value.length<=limit)
    val encoded=Charsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(value))
    require(encoded.remaining()<=limit)
}

enum class SplitAxis { HORIZONTAL, VERTICAL }
enum class GroupColor { BLUE, GREEN, AMBER, PURPLE, RED, GRAY }
data class BrowserSplit(val secondary: Long,val axis: SplitAxis,val dividerPermille: Int)
data class BrowserWindow(val id: Long,val active: Long?=null,val split: BrowserSplit?=null)
data class BrowserTab(val id: Long,val window: Long,val url: String,val title: String="",
                      val pinned: Boolean=false,val group: Long?=null,val sleepRequested: Boolean=false)
data class BrowserGroup(val id: Long,val window: Long,val name: String,val color: GroupColor,val collapsed: Boolean=false)

/**
 * Immutable browser session/domain operations. No WebView, actual sleeping renderer, disk save or profile isolation claim.
 * IDs are monotonically allocated across this whole session and never reused after closing a tab/window/group.
 */
class BrowserSessions private constructor(
    val windows: List<BrowserWindow>,val tabs: List<BrowserTab>,val groups: List<BrowserGroup>,val nextId: Long
) {
    companion object {
        const val MAX_TABS=64
        const val MAX_WINDOWS=8
        const val MAX_GROUPS=16
        fun empty()=admit(listOf(BrowserWindow(1)),emptyList(),emptyList(),2)
        fun admit(windows: List<BrowserWindow>,tabs: List<BrowserTab>,groups: List<BrowserGroup>,nextId: Long): BrowserSessions {
            require(windows.size in 1..MAX_WINDOWS && tabs.size<=MAX_TABS && groups.size<=MAX_GROUPS)
            val w=windows.toList();val t=tabs.toList();val g=groups.toList()
            val ids=w.map { it.id }+t.map { it.id }+g.map { it.id }
            require(ids.all { it>0 && it<nextId } && ids.distinct().size==ids.size && nextId>0)
            val windowIds=w.map { it.id }.toSet()
            g.forEach { group ->
                require(group.window in windowIds);boundedText(group.name,80);require(group.name.isNotBlank())
                require(t.any { it.group==group.id }) { "Empty group" }
            }
            t.forEach { tab ->
                require(tab.window in windowIds);require(NavigationTarget.parse(tab.url)==tab.url);boundedText(tab.title,512)
                require(tab.group==null || (!tab.pinned && g.any { it.id==tab.group && it.window==tab.window }))
            }
            w.forEach { window ->
                val own=t.filter { it.window==window.id }
                require((own.isEmpty() && window.active==null) || own.any { it.id==window.active })
                require(own.takeWhile { it.pinned }.size==own.count { it.pinned }) { "Pinned section must be contiguous" }
                val visited=mutableSetOf<Long>();var prior:Long?=null
                own.forEach { tab -> if(tab.group!=prior) {
                    tab.group?.let { require(visited.add(it)) { "Group must be contiguous" } };prior=tab.group
                } }
                window.split?.let { split ->
                    require(split.dividerPermille in 200..800 && split.secondary!=window.active && own.any { it.id==split.secondary })
                }
                require(own.none { it.sleepRequested && (it.pinned || it.id==window.active || it.id==window.split?.secondary) })
            }
            return BrowserSessions(Collections.unmodifiableList(w),Collections.unmodifiableList(t),Collections.unmodifiableList(g),nextId)
        }
    }
    fun orderedTabs(window: Long): List<BrowserTab> = Collections.unmodifiableList(tabs.filter { it.window==window })
    private fun window(id:Long)=windows.single { it.id==id }
    private fun tab(id:Long)=tabs.single { it.id==id }
    private fun allocate(): Long { require(nextId<Long.MAX_VALUE) { "Session IDs exhausted" };return nextId }
    private fun rebuild(w:List<BrowserWindow> = windows,t:List<BrowserTab> = tabs,g:List<BrowserGroup> = groups,next:Long=nextId): BrowserSessions {
        val keptGroups=g.filter { group -> t.any { it.group==group.id } }
        val normalized=ArrayList<BrowserTab>()
        w.forEach { win ->
            val own=t.filter { it.window==win.id }
            normalized.addAll(own.filter { it.pinned })
            val visited=mutableSetOf<Long>()
            own.filterNot { it.pinned }.forEach { item ->
                if(item.group==null) normalized.add(item)
                else if(visited.add(item.group)) normalized.addAll(own.filter { it.group==item.group })
            }
        }
        val adjusted=w.map { win ->
            val own=normalized.filter { it.window==win.id }
            val active=win.active?.takeIf { id -> own.any { it.id==id } } ?: own.firstOrNull()?.id
            val split=win.split?.takeIf { it.secondary!=active && own.any { tab -> tab.id==it.secondary } }
            win.copy(active=active,split=split)
        }
        val awakened=normalized.map { item ->
            val win=adjusted.single { it.id==item.window }
            if(item.pinned || item.id==win.active || item.id==win.split?.secondary) item.copy(sleepRequested=false) else item
        }
        return admit(adjusted,awakened,keptGroups,next)
    }
    fun open(window:Long,url:String): BrowserSessions {
        window(window);require(tabs.size<MAX_TABS);val id=allocate()
        val item=BrowserTab(id,window,NavigationTarget.parse(url))
        return rebuild(w=windows.map { if(it.id==window) it.copy(active=id) else it },t=tabs+item,next=id+1)
    }
    fun select(id:Long): BrowserSessions {
        val tab=tab(id)
        return rebuild(w=windows.map { win -> if(win.id!=tab.window) win else win.copy(active=id,
            split=win.split?.let { if(it.secondary==id) win.active?.let { old -> it.copy(secondary=old) } else it }) })
    }
    fun navigate(id:Long,url:String,title:String=""): BrowserSessions {
        tab(id);boundedText(title,512)
        val value=NavigationTarget.parse(url)
        return rebuild(t=tabs.map { if(it.id==id) it.copy(url=value,title=title,sleepRequested=false) else it })
    }
    fun close(ids: Set<Long>): BrowserSessions {
        val frozen=ids.toSet();require(frozen.all { id -> tabs.any { it.id==id } })
        return rebuild(t=tabs.filterNot { it.id in frozen })
    }
    fun closeOthers(id: Long): BrowserSessions {
        val item=tab(id);return close(tabs.filter { it.window==item.window && it.id!=id && !it.pinned }.map { it.id }.toSet())
    }
    fun closeRight(id: Long): BrowserSessions {
        val item=tab(id);val own=orderedTabs(item.window)
        return close(own.drop(own.indexOfFirst { it.id==id }+1).filterNot { it.pinned }.map { it.id }.toSet())
    }
    /** Exact normalized URLs only; fragments differ. Pinned tabs are never batch-closed. */
    fun closeDuplicates(window: Long): BrowserSessions {
        window(window);val seen=mutableSetOf<String>();val remove=mutableSetOf<Long>()
        orderedTabs(window).forEach { if(!seen.add(it.url) && !it.pinned) remove.add(it.id) }
        return close(remove)
    }
    fun pin(id:Long,pinned:Boolean): BrowserSessions {
        tab(id);return rebuild(t=tabs.map { if(it.id==id) it.copy(pinned=pinned,group=if(pinned) null else it.group) else it })
    }
    /** Reorder detaches this tab from its group. Group blocks are normalized, so no split headers occur. */
    fun reorder(id:Long,position:Int): BrowserSessions {
        val item=tab(id);val own=orderedTabs(item.window).toMutableList();require(position in own.indices)
        own.removeAll { it.id==id };own.add(position,item.copy(group=null))
        return rebuild(t=tabs.filterNot { it.window==item.window }+own)
    }
    fun group(window:Long,ids:Set<Long>,name:String,color:GroupColor): BrowserSessions {
        window(window);require(groups.size<MAX_GROUPS && ids.isNotEmpty());val frozen=ids.toSet()
        require(frozen.all { id -> tabs.any { it.id==id && it.window==window && !it.pinned } })
        boundedText(name,80);require(name.isNotBlank());val id=allocate()
        return rebuild(t=tabs.map { if(it.id in frozen) it.copy(group=id) else it },g=groups+BrowserGroup(id,window,name,color),next=id+1)
    }
    fun editGroup(id:Long,name:String,color:GroupColor,collapsed:Boolean): BrowserSessions {
        require(groups.any { it.id==id });boundedText(name,80);require(name.isNotBlank())
        return rebuild(g=groups.map { if(it.id==id) it.copy(name=name,color=color,collapsed=collapsed) else it })
    }
    fun split(window:Long,secondary:Long,axis:SplitAxis,dividerPermille:Int=500): BrowserSessions {
        val win=window(window);require(secondary!=win.active && tab(secondary).window==window && dividerPermille in 200..800)
        return rebuild(w=windows.map { if(it.id==window) it.copy(split=BrowserSplit(secondary,axis,dividerPermille)) else it })
    }
    fun unsplit(window:Long): BrowserSessions {
        window(window);return rebuild(w=windows.map { if(it.id==window) it.copy(split=null) else it })
    }
    fun requestSleep(id:Long): BrowserSessions {
        val item=tab(id);val win=window(item.window)
        require(!item.pinned && id!=win.active && id!=win.split?.secondary)
        return rebuild(t=tabs.map { if(it.id==id) it.copy(sleepRequested=true) else it })
    }
    /** Domain transfer only, not an Android/compositor drag or renderer transfer. */
    fun detach(id:Long): BrowserSessions {
        val item=tab(id);require(windows.size<MAX_WINDOWS);val new=allocate()
        return rebuild(w=windows+BrowserWindow(new,id),t=tabs.map { if(it.id==id) it.copy(window=new,group=null) else it },next=new+1)
    }
    fun merge(from:Long,to:Long): BrowserSessions {
        require(from!=to);window(from);window(to)
        return rebuild(w=windows.filterNot { it.id==from },t=tabs.map { if(it.window==from) it.copy(window=to) else it },
            g=groups.map { if(it.window==from) it.copy(window=to) else it })
    }
    fun search(query:String): List<BrowserTab> {
        boundedText(query,256);val lower=query.lowercase(Locale.ROOT)
        return Collections.unmodifiableList(tabs.filter { it.title.lowercase(Locale.ROOT).contains(lower) || it.url.lowercase(Locale.ROOT).contains(lower) })
    }
}

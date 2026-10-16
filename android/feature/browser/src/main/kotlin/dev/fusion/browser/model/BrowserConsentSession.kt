package dev.fusion.browser.model

/** Owns document lifetimes and pending decisions. No rendering, permissions API, or authentic user-consent UI yet.
 * Caller must serialize REAL engine navigation callbacks through navigate/close before granting anything.
 */
class BrowserConsentSession(private val profile:String) {
    class Prompt internal constructor()
    class Grant internal constructor(internal val ticket:PermissionLeases.Ticket,internal val document:Any,internal val tab:Long,internal val kind:PermissionLeases.Kind)
    private data class Pending(val tab:Long,val document:Any,val scope:PermissionLeases.Scope,val kind:PermissionLeases.Kind)
    private var session=BrowserSessions.empty()
    private val documents=HashMap<Long,Any>()
    private val pending=LinkedHashMap<Prompt,Pending>()
    private val leases=PermissionLeases()
    private var closed=false
    init {require(profile.matches(Regex("[A-Za-z0-9_-]{1,32}")))}
    @Synchronized fun snapshot():BrowserSessions=session
    @Synchronized fun open(url:String):Long {
        check(!closed);val next=session.open(1,url);val id=requireNotNull(next.windows.single().active)
        session=next;documents[id]=Any();return id
    }
    @Synchronized fun navigate(tab:Long,url:String) {
        check(!closed);require(session.tabs.any {it.id==tab})
        invalidate(tab) // Fail closed even if an unsupported/malformed destination cannot enter the model.
        val next=session.navigate(tab,url)
        session=next;documents[tab]=Any() // Even same URL/back-forward return gets a fresh identity.
    }
    private fun invalidate(tab:Long) {pending.entries.removeAll {it.value.tab==tab};leases.revokeTab(profile,tab);documents.remove(tab)}
    @Synchronized fun close(tab:Long) {check(!closed);val next=session.close(setOf(tab));invalidate(tab);session=next}
    @Synchronized fun shutdown() {closed=true;pending.clear();documents.clear();leases.clear()}
    @Synchronized fun request(tab:Long,kind:PermissionLeases.Kind):Prompt {
        check(!closed);require(pending.size<64)
        val item=session.tabs.single {it.id==tab};val doc=requireNotNull(documents[tab])
        val scope=PermissionLeases.Scope(profile,tab,item.url)
        return Prompt().also {pending[it]=Pending(tab,doc,scope,kind)}
    }
    /** Decision burns the prompt, including deny or invalid parameters. True is trusted explicit consent, not inferred. */
    @Synchronized fun decide(prompt:Prompt,allow:Boolean,now:Long,ttlMs:Long):Grant? {
        val p=pending.remove(prompt) ?: return null
        if(closed || !allow || documents[p.tab]!==p.document) return null
        return Grant(leases.issue(p.scope,p.kind,now,ttlMs),p.document,p.tab,p.kind)
    }
    @Synchronized fun consume(grant:Grant,tab:Long,kind:PermissionLeases.Kind,now:Long):Boolean {
        // Always attempt/burn the ticket, even when the requested tab is absent/opaque or wrong.
        val actual=session.tabs.firstOrNull {it.id==tab}
        val valid=!closed && actual!=null && documents[tab]===grant.document && tab==grant.tab && kind==grant.kind
        if(!valid) {leases.clear();return false} // Conservative fail-closed; wrong context revokes all outstanding grants.
        return leases.consume(grant.ticket,PermissionLeases.Scope(profile,tab,actual!!.url),kind,now)
    }
}

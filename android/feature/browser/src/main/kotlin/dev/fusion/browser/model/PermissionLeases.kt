package dev.fusion.browser.model

/** Session-only one-shot permission decisions. Trusted caller supplies consent and a monotonic clock.
 * Not wired to a browser/Android permission request yet; not permission to grant without user consent.
 * Tickets are in-process object capabilities, not serialized Binder/authentication tokens.
 */
class PermissionLeases {
    enum class Kind { CAMERA, MICROPHONE, GEOLOCATION }
    class Scope(val profile:String,val tab:Long,url:String) {
        val origin:String=requireNotNull(NavigationTarget.origin(url)) {"Opaque origins cannot receive leases"}
        init {require(profile.matches(Regex("[A-Za-z0-9_-]{1,32}")) && tab>0)}
        internal fun same(other:Scope)=profile==other.profile && tab==other.tab && origin==other.origin
    }
    class Ticket internal constructor()
    private data class Entry(val scope:Scope,val kind:Kind,val expires:Long)
    private val entries=LinkedHashMap<Ticket,Entry>()
    private var lastNow=0L
    private fun clock(now:Long):Boolean {
        if(now<lastNow) {entries.clear();return false}
        lastNow=now;entries.entries.removeAll {it.value.expires<=now};return true
    }
    @Synchronized fun issue(scope:Scope,kind:Kind,now:Long,lifetimeMs:Long):Ticket {
        require(clock(now)) {"Clock rollback revoked all leases"}
        require(lifetimeMs in 1..60_000 && now<=Long.MAX_VALUE-lifetimeMs && entries.size<64)
        return Ticket().also {entries[it]=Entry(scope,kind,now+lifetimeMs)}
    }
    /** Wrong-context use burns the identified ticket; unknown tickets never consume someone else's entry. */
    @Synchronized fun consume(ticket:Ticket,scope:Scope,kind:Kind,now:Long):Boolean {
        if(!clock(now)) return false
        val e=entries.remove(ticket) ?: return false
        return e.scope.same(scope) && e.kind==kind
    }
    /** Invoke on EVERY navigation (even same-origin), tab close, and profile close respectively. */
    @Synchronized fun revokeTab(profile:String,tab:Long) {entries.entries.removeAll {it.value.scope.profile==profile && it.value.scope.tab==tab}}
    @Synchronized fun revokeProfile(profile:String) {entries.entries.removeAll {it.value.scope.profile==profile}}
    @Synchronized fun clear() {entries.clear()}
}

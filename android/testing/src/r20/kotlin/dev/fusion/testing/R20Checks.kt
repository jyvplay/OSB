package dev.fusion.testing
import dev.fusion.automation.*
import dev.fusion.browser.model.PermissionLeases
import dev.fusion.security.LinkDisclosure
import java.util.UUID
import java.time.LocalDate
object R20Checks {
    private var checks=0
    private fun yes(v:Boolean){checks++;check(v)}
    private fun no(block:()->Unit){var refused=false;try {block()} catch(_:IllegalArgumentException){refused=true};yes(refused)}
    @JvmStatic fun main(args:Array<String>) {
        val a=PermissionLeases.Scope("p",1,"HTTPS://Example.com:443/a?q=x")
        val b=PermissionLeases.Scope("p",1,"https://example.com/b#other")
        val kind=PermissionLeases.Kind.CAMERA
        for(ttl in listOf(1L,2L,60000L)) {
            val ledger=PermissionLeases();val t=ledger.issue(a,kind,100,ttl)
            yes(ledger.consume(t,b,kind,100+ttl-1));yes(!ledger.consume(t,b,kind,100+ttl-1))
            val u=ledger.issue(a,kind,100+ttl,ttl);yes(!ledger.consume(u,b,kind,100+ttl+ttl))
        }
        for(wrong in listOf(PermissionLeases.Scope("q",1,"https://example.com"),PermissionLeases.Scope("p",2,"https://example.com"),PermissionLeases.Scope("p",1,"http://example.com"),PermissionLeases.Scope("p",1,"https://example.com:444"))) {
            val ledger=PermissionLeases();val t=ledger.issue(a,kind,0,100)
            yes(!ledger.consume(t,wrong,kind,0));yes(!ledger.consume(t,a,kind,0))
        }
        run {val l=PermissionLeases();val t=l.issue(a,kind,0,10);yes(!l.consume(t,a,PermissionLeases.Kind.MICROPHONE,0));yes(!l.consume(t,a,kind,0))}
        for(action in 0..2) {val l=PermissionLeases();val t=l.issue(a,kind,0,100);when(action){0->l.revokeTab("p",1);1->l.revokeProfile("p");else->l.clear()};yes(!l.consume(t,a,kind,1))}
        run {val l=PermissionLeases();val t=l.issue(a,kind,50,100);yes(!l.consume(t,a,kind,49));yes(!l.consume(t,a,kind,50))}
        run {val l=PermissionLeases();val t=l.issue(a,kind,0,10);l.revokeTab("q",1);yes(l.consume(t,a,kind,1))}
        run {val l=PermissionLeases();val tickets=(1..64).map {l.issue(a,kind,0,100)};no {l.issue(a,kind,0,100)};tickets.forEach {yes(l.consume(it,a,kind,1))};yes(l.consume(l.issue(a,kind,2,1),a,kind,2))}
        for(ttl in listOf(-1L,0L,60001L,Long.MAX_VALUE)) no {PermissionLeases().issue(a,kind,0,ttl)}
        no {PermissionLeases().issue(a,kind,Long.MAX_VALUE,1)};no {PermissionLeases().issue(a,kind,-1,1)}
        for(url in listOf("about:blank","data:text/plain,x","https://user@example.com/","javascript:x")) no {PermissionLeases.Scope("p",1,url)}
        no {PermissionLeases.Scope("p",0,"https://example.com")}
        yes(LinkDisclosure.project("HTTPS://EXAMPLE.com:443/a%2Fb?name=Alice#private").text=="https://example.com/a%2Fb")
        yes(LinkDisclosure.project("https://example.com/Alice?secret=1").text.contains("Alice")) // counterexample to anonymization
        for(raw in listOf("", " about:blank", "file:///secret", "https://a@b/", "https://a:0/", "https://a:65536/", "https://a/\\b", "https://a/\n", "https://a/\ud800", "https://a/\u202E", "https://a/%", "https://a/"+"x".repeat(4096),"https://[fe80::1%25eth0]/")) no {LinkDisclosure.project(raw)}
        no {LinkDisclosure.project("https://a/?removed=\ud800")};no {LinkDisclosure.project("https://a/"+"界".repeat(1400))}
        yes(LinkDisclosure.project("http://[::1]:80").text=="http://[::1]/")
        yes(LinkDisclosure.project("https://a/?#").let {it.removedQuery && it.removedFragment})
        no {TaskCounts(128,1,0,0)};no {TaskCounts(-1,0,0,0)}
        for(n in 0..128) {
            val t=(0 until n).map {i->LocalTask(UUID(0,i+1L).toString(),"Sensitive $i",LocalDate.of(2028,2,29),i%2==0,i%3==0)}
            val x=TaskBook(500,t)
            val y=TaskBook(600,t.reversed().map {it.copy(id=UUID.randomUUID().toString(),title="_x0041_",due=null)})
            val cx=TaskCounts.from(x);val cy=TaskCounts.from(y)
            yes(cx.total==n);yes(cx==cy);yes(cx.export().bytes.contentEquals(cy.export().bytes))
            yes(TaskFrame.decode(TaskFrame.encode(x)).tasks==x.tasks)
        }
        println("R20: $checks actual public-consumer assertions PASS")
    }
}

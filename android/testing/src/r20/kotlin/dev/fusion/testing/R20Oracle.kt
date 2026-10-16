package dev.fusion.testing
import dev.fusion.automation.*
import dev.fusion.security.LinkDisclosure
import dev.fusion.browser.model.PermissionLeases
import java.util.Base64
object R20Oracle {
    @JvmStatic fun main(args:Array<String>) {
        val d=Base64.getDecoder();val e=Base64.getEncoder()
        generateSequence(::readLine).forEach {line ->try {
            val p=line.split('\t')
            when(p[0]) {
                "T"->println(e.encodeToString(TaskCounts.from(TaskFrame.decode(d.decode(p[1]))).export().bytes))
                "L"->{val r=LinkDisclosure.project(String(d.decode(p[1]),Charsets.UTF_8));println(e.encodeToString(r.text.toByteArray())+"\t${r.removedQuery}\t${r.removedFragment}")}
                "P"->{val l=PermissionLeases();val a=PermissionLeases.Scope(p[1],p[2].toLong(),p[3]);val k=PermissionLeases.Kind.valueOf(p[4]);val t=l.issue(a,k,p[5].toLong(),p[6].toLong());val b=PermissionLeases.Scope(p[7],p[8].toLong(),p[9]);val q=PermissionLeases.Kind.valueOf(p[10]);if(p[12]=="tab") l.revokeTab(a.profile,a.tab);if(p[12]=="profile")l.revokeProfile(a.profile);println("${l.consume(t,b,q,p[11].toLong())},${l.consume(t,b,q,p[11].toLong())}")}
                else->error("unknown request")
            }
        } catch(_:IllegalArgumentException) {println("REFUSED")} }
    }
}

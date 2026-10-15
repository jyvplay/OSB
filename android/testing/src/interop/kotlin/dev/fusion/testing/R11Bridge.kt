package dev.fusion.testing

import dev.fusion.browser.model.*
import dev.fusion.notes.*
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64

/** Real production kernels against a separately implemented Python oracle; no Android substitute. */
object R11Bridge {
    @JvmStatic fun main(args:Array<String>) {
        if(args[0]=="browser") {
            var state=BrowserSessions.empty().open(1,"https://EXAMPLE.com:443/a%2Fb?q=%2F").open(1,"http://other.example/")
            state=state.navigate(2,state.tabs.first().url,"Title\u0000🙂")
                .group(1,setOf(2,3),"Group",GroupColor.GREEN).split(1,2,SplitAxis.VERTICAL,350)
            Files.newOutputStream(Path.of(args[1])).use { BrowserSessionCodec.write(NamedBrowserSession("Session🙂",state),it) }
        } else {
            generateSequence(::readLine).forEach { line ->
                val (digest,encoded)=line.split('\t')
                val text=String(Base64.getDecoder().decode(encoded),Charsets.UTF_8)
                val graph=NoteLinks.build(sequenceOf(NoteSnapshot("source",text,NoteRevision("source",digest))))
                println(graph.outgoing.getValue("source").sorted().joinToString(","))
            }
        }
    }
}

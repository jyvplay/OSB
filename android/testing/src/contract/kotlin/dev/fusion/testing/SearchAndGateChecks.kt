package dev.fusion.testing

import dev.fusion.commands.*
import dev.fusion.index.*
import dev.fusion.files.*
import dev.fusion.model.*
import java.util.Locale

object SearchAndGateChecks {
    private var count=0
    private fun expect(value:Boolean) { count++; check(value) { "Search/gate check $count failed" } }
    private inline fun refused(action:()->Unit) { count++; try { action() } catch(_:Exception) { return }; error("Expected refusal at $count") }
    @JvmStatic fun main(args:Array<String>) {
        val keywords=mutableListOf("safe")
        val commands=mutableListOf(Command("a","INDEX","Local module",keywords),Command("b","Second","INDEX in hint"))
        val registry=CommandRegistry(commands)
        keywords.add("intruder"); commands.clear()
        expect(registry.search("intruder").isEmpty())
        expect(registry.all().size==2)
        refused { (registry.all() as MutableList<Command>).clear() }
        refused { (registry.all()[0].keywords as MutableList<String>).add("intruder") }
        expect(registry.search(" index ").map { it.id }==listOf("a","b"))
        expect(registry.search("",0).isEmpty())
        refused { registry.search("",-1) }; refused { registry.search("",101) }
        refused { registry.search("x".repeat(1025)) }
        refused { CommandRegistry(listOf(Command("a","A",""),Command("a","B",""))) }
        refused { CommandRegistry(listOf(Command("bad","Title","",List(33) { "word" }))) }
        val locale=Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            expect(registry.search("index").first().id=="a")
            val index=LocalIndex()
            index.upsert(Doc("a","INDEX","alpha","local")); index.upsert(Doc("b","Other","index in body","local"))
            expect(index.search("index").map { it.doc.id }==listOf("a","b"))
            expect(index.search(" ").map { it.doc.id }==listOf("a","b"))
            refused { index.search("",-1) }; refused { index.search("x".repeat(1025)) }
            val old=index.search(""); val units=index.payloadUnits()
            refused { index.upsert(Doc("a","Too large","x".repeat(LocalIndex.MAX_BODY+1),"local")) }
            expect(index.search("")==old && index.payloadUnits()==units)
            index.remove("missing"); expect(index.payloadUnits()==units)
            index.remove("a"); expect(index.size()==1 && index.payloadUnits()<units)
            index.clear(); expect(index.size()==0 && index.payloadUnits()==0)
            repeat(LocalIndex.MAX_DOCS) { index.upsert(Doc("$it","Title","","local")) }
            refused { index.upsert(Doc("excess","Title","","local")) }
            index.upsert(Doc("0","Replacement","","local")); expect(index.size()==LocalIndex.MAX_DOCS)
            val budget=LocalIndex(); var inserted=0
            while(true) {
                try { budget.upsert(Doc("$inserted","Big","x".repeat(LocalIndex.MAX_BODY),"local")); inserted++ }
                catch(_:IllegalArgumentException) { break }
            }
            expect(inserted>0 && budget.payloadUnits()<=LocalIndex.MAX_TOTAL_UNITS)
            val before=budget.payloadUnits()
            refused { budget.upsert(Doc("too_much","Big","x".repeat(LocalIndex.MAX_BODY),"local")) }
            expect(budget.payloadUnits()==before && budget.size()==inserted)
        } finally { Locale.setDefault(locale) }
        // Second-order attack derived from the published-list bug: admission data
        // must not be editable through a cast even by ordinary in-process callers.
        val beamIndex=PilotPolicy.modules.indexOfFirst { it.id=="beam" }
        refused { (PilotPolicy.modules as MutableList<PilotModule>)[beamIndex]=PilotPolicy.modules[beamIndex].copy(access=PilotAccess.CANDIDATE) }
        refused { (PilotPolicy.modules as MutableList<PilotModule>).clear() }
        expect(!PilotPolicy.mayRun("beam",setOf("beam")))
        for(operation in FileMutation.entries) {
            val result=FileMutationPolicy.denied<Unit>(operation)
            expect(result.isFailure && result.exceptionOrNull() is FileMutationUnavailable)
            expect(result.exceptionOrNull()!!.message!!.contains("S6"))
        }
        println("PASS: $count search/gate assertions; aliasing, immutable command snapshots, locale, bounds, atomic capacity refusal, and all backend mutation denials. No native palette/provider acceptance.")
    }
}

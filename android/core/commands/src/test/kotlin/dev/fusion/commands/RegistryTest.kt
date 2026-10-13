package dev.fusion.commands

import org.junit.Assert.assertEquals
import org.junit.Test

class RegistryTest {
    @Test fun prefixMatchesRankFirst() {
        val r = CommandRegistry(listOf(
            Command("a", "Open Files", "files", listOf("browse")),
            Command("b", "Files copy", "copy", listOf("open"))
        ))
        val hits = r.search("open")
        assertEquals("a", hits.first().id)
    }
    @Test fun blankReturnsAllCapped() {
        val r = CommandRegistry((1..20).map { Command("$it", "Cmd $it", "") })
        assertEquals(12, r.search("").size)
    }
}

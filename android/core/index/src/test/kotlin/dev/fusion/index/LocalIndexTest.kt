package dev.fusion.index

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalIndexTest {
    @Test fun searchRanksTitleOverBody() {
        val idx = LocalIndex()
        idx.upsert(Doc("1", "Beam discovery", "nsd mdns", "beam"))
        idx.upsert(Doc("2", "Notes", "beam meeting notes", "notes"))
        val hits = idx.search("beam")
        assertEquals(2, hits.size)
        assertEquals("1", hits[0].doc.id)
    }
    @Test fun emptyQueryReturnsInsertionOrder() {
        val idx = LocalIndex()
        idx.upsert(Doc("a", "A", "", "s"))
        idx.upsert(Doc("b", "B", "", "s"))
        assertEquals(listOf("a", "b"), idx.search("").map { it.doc.id })
    }
    @Test fun noMatchReturnsEmpty() {
        val idx = LocalIndex()
        idx.upsert(Doc("1", "Files", "saf", "files"))
        assertTrue(idx.search("zzz-nope").isEmpty())
    }
}

package dev.fusion.native

import org.junit.Assert.assertEquals
import org.junit.Test

class TextFormatsTest {
    @Test fun wordCountIgnoresBlank() {
        assertEquals(3, TextFormats.wordCount("  hello   brave\nworld "))
    }
    @Test fun csvHandlesQuotes() {
        val rows = TextFormats.csvRows("a,\"b,c\",d\n1,2,3")
        assertEquals(listOf("a", "b,c", "d"), rows[0])
        assertEquals(listOf("1", "2", "3"), rows[1])
    }
}

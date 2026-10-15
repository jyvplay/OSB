package dev.fusion.native

import org.junit.Assert.assertEquals
import org.junit.Test

class TextFormatsTest {
    @Test fun malformedUtf8MustNotBeReplacementDecoded() {
        org.junit.Assert.assertFalse(TextFormats.isProbablyUtf8(byteArrayOf(0xff.toByte())))
        org.junit.Assert.assertFalse(TextFormats.isProbablyUtf8(byteArrayOf(0xed.toByte(), 0xa0.toByte(), 0x80.toByte())))
        org.junit.Assert.assertTrue(TextFormats.isProbablyUtf8("α🙂".toByteArray()))
    }

    @Test fun wordCountIgnoresBlank() {
        assertEquals(3, TextFormats.wordCount("  hello   brave\nworld "))
    }
    @Test fun csvHandlesQuotes() {
        val rows = TextFormats.csvRows("a,\"b,c\",d\n1,2,3")
        assertEquals(listOf("a", "b,c", "d"), rows[0])
        assertEquals(listOf("1", "2", "3"), rows[1])
    }
}

package dev.fusion.beam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URL

class LocalMediaServerTest {

    private fun freePort(): Int {
        ServerSocket(0).use { return it.localPort }
    }

    private fun serverFor(data: ByteArray, mime: String = "video/mp4"): Pair<LocalMediaServer, Int> {
        val port = freePort()
        val server = LocalMediaServer(port = port) {
            MediaStream(ByteArrayInputStream(data), data.size.toLong(), mime)
        }
        assertTrue(server.start())
        return server to port
    }

    @Test fun servesFullContentWith200AndMime() {
        val data = "Hello Fusion Shell Desktop Media Streaming!".toByteArray()
        val (server, port) = serverFor(data, "video/mp4")
        try {
            val conn = URL("http://127.0.0.1:$port/media.mp4").openConnection() as HttpURLConnection
            conn.connectTimeout = 3000; conn.readTimeout = 3000; conn.connect()
            assertEquals(200, conn.responseCode)
            assertEquals("bytes", conn.getHeaderField("Accept-Ranges"))
            assertEquals("video/mp4", conn.getHeaderField("Content-Type"))
            assertEquals(data.size.toLong(), conn.contentLengthLong)
            assertEquals(String(data), String(conn.inputStream.readBytes()))
        } finally { server.stop() }
    }

    @Test fun servesByteRangeWith206() {
        val data = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ".toByteArray()
        val (server, port) = serverFor(data)
        try {
            val conn = URL("http://127.0.0.1:$port/sample.mp4").openConnection() as HttpURLConnection
            conn.setRequestProperty("Range", "bytes=5-14")
            conn.connectTimeout = 3000; conn.readTimeout = 3000; conn.connect()
            assertEquals(206, conn.responseCode)
            assertEquals("bytes 5-14/${data.size}", conn.getHeaderField("Content-Range"))
            assertEquals("56789ABCDE", String(conn.inputStream.readBytes()))
        } finally { server.stop() }
    }

    @Test fun servesSuffixRange() {
        val data = "0123456789".toByteArray()
        val (server, port) = serverFor(data)
        try {
            val conn = URL("http://127.0.0.1:$port/a.mp4").openConnection() as HttpURLConnection
            conn.setRequestProperty("Range", "bytes=-4")
            conn.connectTimeout = 3000; conn.readTimeout = 3000; conn.connect()
            assertEquals(206, conn.responseCode)
            assertEquals("bytes 6-9/10", conn.getHeaderField("Content-Range"))
            assertEquals("6789", String(conn.inputStream.readBytes()))
        } finally { server.stop() }
    }

    @Test fun rejectsUnsatisfiableRangeWith416() {
        val data = "0123456789".toByteArray()
        val (server, port) = serverFor(data)
        try {
            val conn = URL("http://127.0.0.1:$port/a.mp4").openConnection() as HttpURLConnection
            conn.setRequestProperty("Range", "bytes=99-199")
            conn.connectTimeout = 3000; conn.readTimeout = 3000; conn.connect()
            assertEquals(416, conn.responseCode)
        } finally { server.stop() }
    }

    @Test fun returns404ForUnknownPath() {
        val port = freePort()
        val server = LocalMediaServer(port = port) { null }
        assertTrue(server.start())
        try {
            val conn = URL("http://127.0.0.1:$port/nonexistent.mp4").openConnection() as HttpURLConnection
            conn.connectTimeout = 3000; conn.readTimeout = 3000; conn.connect()
            assertEquals(404, conn.responseCode)
        } finally { server.stop() }
    }

    @Test fun rejectsTraversalBeforeProvider() {
        val server = LocalMediaServer(port = 1) { throw AssertionError("provider must not run") }
        assertNull(server.cleanPath("/../secret.txt"))
        assertNull(server.cleanPath("/a/../../b"))
        assertNull(server.cleanPath("/\\windows\\path"))
        assertEquals("a/b.mp4", server.cleanPath("/a//b.mp4?token=1"))
    }
}

package dev.fusion.airplay

import com.sun.net.httpserver.HttpServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentLinkedQueue

class AirPlayUrlSenderTest {

    private lateinit var server: HttpServer
    private var port: Int = 0
    private val requests = ConcurrentLinkedQueue<String>()
    private val bodies = ConcurrentLinkedQueue<String>()
    @Volatile private var forcedStatus = 200

    @Before
    fun setUp() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val body = exchange.requestBody.use { it.readBytes() }.toString(Charsets.UTF_8)
            requests.add("${exchange.requestMethod} ${exchange.requestURI}")
            bodies.add(body)
            val payload = if (exchange.requestURI.path == "/playback-info") {
                textPlist(position = 12.5, duration = 90.0, rate = 1.0).toByteArray()
            } else {
                ByteArray(0)
            }
            exchange.sendResponseHeaders(forcedStatus, payload.size.toLong())
            exchange.responseBody.use { it.write(payload) }
        }
        server.start()
        port = server.address.port
    }

    @After
    fun tearDown() {
        server.stop(0)
    }

    @Test
    fun playPostsContentLocationToPlay() {
        val sender = AirPlayUrlSender()
        val code = sender.play("127.0.0.1", port, "http://192.168.1.10:8080/movie.mp4", startPosition = 0.25)
        assertEquals(200, code)
        assertTrue(requests.any { it == "POST /play" })
        val body = bodies.first { it.contains("Content-Location") }
        assertTrue(body.contains("Content-Location: http://192.168.1.10:8080/movie.mp4"))
        assertTrue(body.contains("Start-Position: 0.25"))
    }

    @Test
    fun scrubRateStopHitCorrectPaths() {
        val sender = AirPlayUrlSender()
        sender.scrub("127.0.0.1", port, 42.0)
        sender.rate("127.0.0.1", port, 1f)
        sender.stop("127.0.0.1", port)
        assertTrue(requests.any { it == "POST /scrub?position=42.0" })
        assertTrue(requests.any { it == "POST /rate?value=1.0" })
        assertTrue(requests.any { it == "POST /stop" })
    }

    @Test
    fun pairingRequiredSurfacesAsHttpCode() {
        forcedStatus = 470 // tvOS "pairing required"
        val sender = AirPlayUrlSender()
        assertEquals(470, sender.play("127.0.0.1", port, "http://x/movie.mp4"))
    }

    @Test
    fun playbackInfoParsesTextPlist() {
        val sender = AirPlayUrlSender()
        val info = sender.playbackInfo("127.0.0.1", port)
        assertEquals(12.5, info.position)
        assertEquals(90.0, info.duration)
        assertEquals(1.0, info.rate)
        assertTrue(info.raw.isNotEmpty())
    }

    private fun textPlist(position: Double, duration: Double, rate: Double): String =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <plist version="1.0">
        <dict>
          <key>duration</key><real>$duration</real>
          <key>position</key><real>$position</real>
          <key>rate</key><real>$rate</real>
        </dict>
        </plist>
        """.trimIndent()
}

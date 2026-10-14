package dev.fusion.castv2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Loopback transport unit test of [CastV2Client]. NOT a real Chromecast, TLS or hardware test.
 *
 * The fake device is a loopback ServerSocket that speaks real castv2 framing
 * (4-byte big-endian length + protobuf CastMessage) using the same [Proto]
 * codec. The codec itself is validated independently against hand-computed
 * bytes in [ProtoTest], so this test validates the client's connection,
 * request/response correlation, and media-session flow — not the codec.
 */
class CastV2ClientTest {

    /** A scripted fake Chromecast speaking castv2 framing over a plain socket. */
    private class FakeChromecast : AutoCloseable {
        val server = ServerSocket(0, 50, java.net.InetAddress.getByName("127.0.0.1"))
        val port: Int get() = server.localPort
        val received = ConcurrentLinkedQueue<CastMessage>()

        @Volatile
        var clientSocket: Socket? = null
        private var thread: Thread? = null

        fun start() {
            thread = Thread {
                try {
                    val s = server.accept()
                    clientSocket = s
                    val input = DataInputStream(BufferedInputStream(s.getInputStream()))
                    val out = DataOutputStream(BufferedOutputStream(s.getOutputStream()))
                    while (true) {
                        val len = input.readInt()
                        if (len <= 0 || len > 1_000_000) break
                        val payload = input.readNBytes(len)
                        if (payload.size < len) break
                        val msg = Proto.decode(payload)
                        received.add(msg)
                        respond(out, msg)
                    }
                } catch (_: Exception) {
                    // Socket closed by the client at end of test.
                }
            }.apply { isDaemon = true; start() }
        }

        private fun reply(source: String, namespace: String, body: String) = CastMessage(
            protocolVersion = 0,
            sourceId = source,
            destinationId = "sender-0",
            namespace = namespace,
            payloadType = CastMessage.PAYLOAD_STRING,
            payloadUtf8 = body
        )

        private fun respond(out: DataOutputStream, msg: CastMessage) {
            when (msg.namespace) {
                CastV2Client.NS_HEARTBEAT ->
                    if (msg.payloadUtf8?.contains("PING") == true) {
                        write(out, reply("receiver-0", CastV2Client.NS_HEARTBEAT, """{"type":"PONG"}"""))
                    }

                CastV2Client.NS_RECEIVER ->
                    if (msg.payloadUtf8?.contains("LAUNCH") == true) {
                        val body = """{"requestId":1,"status":{"applications":[{"appId":"CC1AD845",""" +
                            """"displayName":"Default Media Receiver","sessionId":"sess-1",""" +
                            """"transportId":"web-1","namespaces":[{"name":"urn:x-cast:com.google.cast.media"}]}]},""" +
                            """"type":"RECEIVER_STATUS"}"""
                        write(out, reply("receiver-0", CastV2Client.NS_RECEIVER, body))
                    }

                CastV2Client.NS_MEDIA -> {
                    val requestId = requireNotNull(Json.parseObject(requireNotNull(msg.payloadUtf8)).num("requestId")).toInt()
                    val body = """{"type":"MEDIA_STATUS","status":[{"mediaSessionId":7,""" +
                        """"playerState":"PLAYING"}],"requestId":$requestId}"""
                    write(out, reply("web-1", CastV2Client.NS_MEDIA, body))
                }
            }
        }

        private fun write(out: DataOutputStream, msg: CastMessage) {
            val body = Proto.encode(msg)
            out.writeInt(body.size)
            out.write(body)
            out.flush()
        }

        override fun close() {
            runCatching { clientSocket?.close() }
            runCatching { server.close() }
        }
    }

    @Test
    fun connectLaunchLoadAndControlMedia() {
        val fake = FakeChromecast().apply { start() }
        try {
            // Inject a plain-socket provider so no TLS is needed on loopback.
            val client = CastV2Client("127.0.0.1", fake.port) { h, p -> Socket(h, p) }
            client.connect()

            val apps = client.launch()
            assertEquals(1, apps.size)
            assertEquals("CC1AD845", apps[0].appId)
            assertEquals("web-1", apps[0].transportId)
            assertEquals("sess-1", apps[0].sessionId)
            assertEquals("web-1", client.transportId)
            assertEquals("sess-1", client.sessionId)

            val mediaSessionId = client.load("http://192.168.1.10:8080/video.mp4", "video/mp4", "Demo")
            assertEquals(7L, mediaSessionId)
            assertEquals(7L, client.mediaSessionId)

            // Control commands complete without error against the fake device.
            client.play()
            client.pause()
            client.seek(12.5)
            client.stop()

            assertTrue(client.connected)
            client.close()

            // The fake device actually received CONNECT + LAUNCH + LOAD + controls.
            val namespaces = fake.received.map { it.namespace }
            assertTrue(namespaces.contains(CastV2Client.NS_CONNECTION))
            assertTrue(namespaces.contains(CastV2Client.NS_RECEIVER))
            assertTrue(namespaces.contains(CastV2Client.NS_MEDIA))

            val requestIds = fake.received.mapNotNull { message ->
                message.payloadUtf8?.let { Json.parseObject(it).num("requestId")?.toInt() }
            }
            assertEquals((1..6).toList(), requestIds) // The peer must correlate every command, not repeat ID3.
            val loadPayload = fake.received.first { it.payloadUtf8?.contains("LOAD") == true }.payloadUtf8!!
            assertTrue(loadPayload.contains("http://192.168.1.10:8080/video.mp4"))
            assertTrue(loadPayload.contains("sess-1"))
        } finally {
            fake.close()
        }
    }

    @Test
    fun connectFailsWhenSocketProviderFails() {
        val client = CastV2Client("127.0.0.1", 1) { _, _ -> throw IOException("no network") }
        assertTrue(runCatching { client.connect() }.isFailure)
    }
}

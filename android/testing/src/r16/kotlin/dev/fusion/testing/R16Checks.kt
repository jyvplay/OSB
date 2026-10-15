package dev.fusion.testing

import dev.fusion.together.*
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object R16Checks {
    private var checks = 0
    private fun eq(a: Any?, b: Any?) { checks++; check(a == b) { "#$checks $a != $b" } }
    private fun no(block: () -> Unit) { checks++; try { block() } catch (_: IllegalArgumentException) { return }; error("Accepted invalid input") }
    @JvmStatic fun main(args: Array<String>) {
        for (offset in -100L..100L) for (delay in 0L..20L) {
            val c = PeerClock.from(1000, 1000 + offset + delay, 1007 + offset + delay, 1007 + 2 * delay)
            eq(offset - delay, c.offsetLowerNanos); eq(offset + delay, c.offsetUpperNanos)
            eq(offset, c.midpointNanos); eq(2 * delay, c.roundTripNanos)
        }
        eq(Long.MAX_VALUE, PeerClock.from(0, Long.MAX_VALUE, Long.MAX_VALUE, 0).midpointNanos)
        eq(-Long.MAX_VALUE, PeerClock.from(Long.MAX_VALUE, 0, 0, Long.MAX_VALUE).midpointNanos)
        eq(Long.MAX_VALUE, PeerClock.from(0, 0, 0, Long.MAX_VALUE).roundTripNanos)
        no { PeerClock.from(-1, 0, 0, 1) }; no { PeerClock.from(2, 0, 0, 1) }
        no { PeerClock.from(0, 2, 1, 3) }; no { PeerClock.from(0, 0, 4, 3) }
        no { PlaybackAdvertisement("a".repeat(63), 0, false) }
        no { PlaybackAdvertisement("A".repeat(64), 0, false) }
        no { PlaybackAdvertisement("a".repeat(64), -1, false) }
        no { PeerClock(10, -10, -1) }; no { PeerClock(0, 1, 2) }
        no { PeerClock(-Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE) }
        no { PeerClock.from(0, 0, 0, 1).copy(roundTripNanos = 2) }
        eq(0L, PeerClock(0, 1, 1).midpointNanos)
        for (forward in listOf(0L,1L,7L,1000L)) for (back in listOf(0L,2L,8L,1001L)) for (processing in listOf(0L,7L,200L)) {
            val offset=-100L
            val c=PeerClock.from(1000,1000+offset+forward,1000+offset+forward+processing,1000+forward+processing+back)
            eq(offset-back,c.offsetLowerNanos);eq(offset+forward,c.offsetUpperNanos)
            eq(forward+back,c.roundTripNanos);eq(offset-back+(forward+back)/2,c.midpointNanos)
        }
        repeat(2) {
            val pool=Executors.newFixedThreadPool(9);val clients=mutableListOf<Socket>()
            try {
                ServerSocket(0,9,InetAddress.getLoopbackAddress()).use { server ->
                    server.soTimeout=5000
                    fun attempt()=pool.submit<Exception?> {
                        try { TogetherChannel.respond(server.accept(),ByteArray(32),PlaybackAdvertisement("a".repeat(64),0,false),5000);null }
                        catch(e:Exception) { e }
                    }
                    val futures=(0 until 8).map {
                        val future=attempt();val socket=Socket(InetAddress.getLoopbackAddress(),server.localPort)
                        clients+=socket;socket.soTimeout=3000
                        socket.getOutputStream().write(byteArrayOf(0x46,0x54,0x47,0x31)+ByteArray(16))
                        val hello=ByteArray(20);java.io.DataInputStream(socket.getInputStream()).readFully(hello)
                        eq(true,hello.copyOfRange(0,4).contentEquals(byteArrayOf(0x46,0x54,0x47,0x31)));future
                    }
                    val denied=attempt()
                    Socket(InetAddress.getLoopbackAddress(),server.localPort).use { ninth -> ninth.soTimeout=3000;eq(-1,ninth.getInputStream().read()) }
                    eq(true,denied.get(5,TimeUnit.SECONDS) is IllegalStateException)
                    clients.forEach { it.close() }
                    futures.forEach { eq(true,it.get(5,TimeUnit.SECONDS) is java.io.IOException) }
                }
            } finally { clients.forEach { it.close() };pool.shutdownNow() }
        }
        val executor = Executors.newSingleThreadExecutor()
        try {
            ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
                server.soTimeout = 5000
                val key = ByteArray(32) { it.toByte() }; val digest = "a".repeat(64)
                val left = PlaybackAdvertisement(digest, 100, false)
                val right = PlaybackAdvertisement(digest, Long.MAX_VALUE, true)
                val reply = executor.submit<PlaybackAdvertisement> { TogetherChannel.respond(server.accept(), key, right) }
                val socket = Socket(InetAddress.getLoopbackAddress(), server.localPort)
                val observation = TogetherChannel.initiate(socket, key, left)
                eq(right, observation.advertisement); eq(left, reply.get(5, TimeUnit.SECONDS))
                eq(true, socket.isClosed); eq(true, observation.clock.roundTripNanos >= 0)
            }
        } finally { executor.shutdownNow() }
        println("R16: $checks assertions PASS; actual loopback exchange plus clock algebra. Not multi-device, player, pairing, Syncplay or physical acceptance.")
    }
}

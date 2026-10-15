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

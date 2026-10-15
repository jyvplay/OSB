package dev.fusion.shell

import dev.fusion.together.*
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Real Android TCP/JCA, not multi-device/media synchronization acceptance. */
class R16TogetherDeviceTest {
    @get:Rule val disposable = DisposableEmulatorRule()
    private val key = ByteArray(32) { it.toByte() } // Public fixture, not production pairing.
    private val local = PlaybackAdvertisement("a".repeat(64), 123, false)

    @Test fun actualAndroidSocketsExchangeAuthenticatedStateAndClockSample() {
        val executor = Executors.newSingleThreadExecutor()
        try {
            ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { listener ->
                listener.soTimeout = 5000
                val peer = local.copy(positionMillis = 456, playing = true)
                val future = executor.submit<PlaybackAdvertisement> { TogetherChannel.respond(listener.accept(), key, peer) }
                val socket = Socket(InetAddress.getLoopbackAddress(), listener.localPort)
                val result = TogetherChannel.initiate(socket, key, local)
                assertEquals(peer, result.advertisement)
                assertEquals(local, future.get(5, TimeUnit.SECONDS))
                assertTrue(result.clock.offsetLowerNanos <= result.clock.offsetUpperNanos)
                assertTrue(result.clock.roundTripNanos >= 0)
                assertTrue(socket.isClosed)
            }
        } finally { executor.shutdownNow() }
    }
    @Test fun wrongKeyRefusesWithoutReturningPeerStateAndClosesSockets() {
        val executor = Executors.newSingleThreadExecutor()
        try {
            ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { listener ->
                listener.soTimeout = 5000
                val future = executor.submit<Boolean> {
                    runCatching { TogetherChannel.respond(listener.accept(), key, local) }.exceptionOrNull() is java.security.GeneralSecurityException
                }
                val socket = Socket(InetAddress.getLoopbackAddress(), listener.localPort)
                assertTrue(runCatching { TogetherChannel.initiate(socket, ByteArray(32), local) }.exceptionOrNull() is java.io.IOException)
                assertTrue(future.get(5, TimeUnit.SECONDS)); assertTrue(socket.isClosed)
            }
        } finally { executor.shutdownNow() }
    }
    @Test fun wholeCallDeadlineClosesAnUnauthenticatedStalledSocket() {
        val executor = Executors.newSingleThreadExecutor()
        try {
            ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { listener ->
                listener.soTimeout = 5000
                val future = executor.submit<Boolean> {
                    val accepted = listener.accept()
                    val failure = runCatching { TogetherChannel.respond(accepted, key, local, 150) }.exceptionOrNull()
                    failure is java.io.IOException && accepted.isClosed
                }
                Socket(InetAddress.getLoopbackAddress(), listener.localPort).use { client ->
                    client.soTimeout = 3000
                    client.getOutputStream().write('F'.code)
                    assertEquals(-1, client.getInputStream().read())
                    assertTrue(future.get(5, TimeUnit.SECONDS))
                }
            }
        } finally { executor.shutdownNow() }
    }
}

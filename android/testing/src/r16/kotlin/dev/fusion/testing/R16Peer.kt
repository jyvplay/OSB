package dev.fusion.testing

import dev.fusion.together.*
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.system.exitProcess

/** Test-only public-API process, real sockets and crypto. No production default key. */
object R16Peer {
    @JvmStatic fun main(args: Array<String>) {
        try {
            val key = ByteArray(32) { it.toByte() }
            val local = PlaybackAdvertisement("a".repeat(64), 2345, true)
            if (args[0] == "server") {
                ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { listener ->
                    listener.soTimeout = 5000
                    println("PORT ${listener.localPort}"); System.out.flush()
                    val remote = TogetherChannel.respond(listener.accept(), key, local, 1000)
                    println("STATE ${remote.positionMillis} ${remote.playing}")
                }
            } else {
                val observation = TogetherChannel.initiate(Socket(InetAddress.getLoopbackAddress(), args[1].toInt()), key, local, 1000)
                println("STATE ${observation.advertisement.positionMillis} ${observation.advertisement.playing} RTT ${observation.clock.roundTripNanos}")
            }
        } catch (e: Exception) { println("REFUSED ${e.javaClass.simpleName}"); exitProcess(2) }
    }
}

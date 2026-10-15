package dev.fusion.together

import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Socket
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Caller assertion, not evidence of a real player or content ownership. No URI/path is transmitted. */
data class PlaybackAdvertisement(val sha256: String, val positionMillis: Long, val playing: Boolean) {
    init { require(sha256.matches(Regex("[0-9a-f]{64}"))); require(positionMillis >= 0) }
}
data class PeerObservation(val advertisement: PlaybackAdvertisement, val clock: PeerClock)

/** Experimental single-exchange control plane, NOT Syncplay or a co-watching service.
 * Takes ownership of a fresh caller-connected TCP socket and always closes it. Call OFF the
 * Android main thread. No listener, discovery, key pairing, automatic connection or player action.
 * Caller must protect a high-entropy pair-specific32-byte key and supply actual content identity.
 * AEAD authenticates key possession, not an individual or truthful playback/timestamps.
 * Cancellation: close the supplied socket. Up to8 concurrent calls and a best-effort whole-call
 * deadline; the JVM/OS scheduler is not a hard real-time or post-compromise watchdog.
 * [U] verify on API 34/36/37 and real multi-device LANs before any S22 exposure.
 */
object TogetherChannel {
    private val random = SecureRandom()
    private val origin = System.nanoTime()
    private val permits = Semaphore(8)
    private val deadlines = ScheduledThreadPoolExecutor(1) { task ->
        Thread(task, "fusion-together-deadline").apply { isDaemon = true }
    }.apply { removeOnCancelPolicy = true }
    private val magic = byteArrayOf(0x46, 0x54, 0x47, 0x31) // FTG1, not Syncplay.
    private fun now() = (System.nanoTime() - origin).also { check(it >= 0) }
    private fun bytes(n: Int) = ByteArray(n).also(random::nextBytes)
    private fun read(input: DataInputStream, n: Int) = ByteArray(n).also(input::readFully)
    private fun hello(input: DataInputStream): ByteArray {
        require(read(input, 4).contentEquals(magic)) { "Unknown protocol" }
        return read(input, 16)
    }
    private fun hello(output: DataOutputStream, nonce: ByteArray) { output.write(magic); output.write(nonce); output.flush() }
    private fun aad(client: ByteArray, server: ByteArray, role: Int) = magic + client + server + byteArrayOf(role.toByte())
    private fun encrypt(key: ByteArray, context: ByteArray, plain: ByteArray): ByteArray {
        val nonce = bytes(12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        cipher.updateAAD(context)
        return nonce + cipher.doFinal(plain)
    }
    private fun decrypt(key: ByteArray, context: ByteArray, frame: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, frame.copyOfRange(0, 12)))
        cipher.updateAAD(context)
        return cipher.doFinal(frame, 12, frame.size - 12)
    }
    private fun encode(state: PlaybackAdvertisement): ByteArray = ByteBuffer.allocate(41).apply {
        for (i in 0 until 32) put(state.sha256.substring(i * 2, i * 2 + 2).toInt(16).toByte())
        putLong(state.positionMillis); put(if (state.playing) 1.toByte() else 0.toByte())
    }.array()
    private fun decode(raw: ByteArray, expected: PlaybackAdvertisement): PlaybackAdvertisement {
        require(raw.size == 41)
        val buffer = ByteBuffer.wrap(raw)
        val digest = ByteArray(32).also(buffer::get)
        require(MessageDigest.isEqual(digest, encode(expected).copyOfRange(0, 32))) { "Different media identity" }
        val position = buffer.long; val playing = buffer.get().toInt()
        require(playing in 0..1)
        return PlaybackAdvertisement(expected.sha256, position, playing == 1)
    }
    private fun <T> owned(socket: Socket, pairKey: ByteArray, timeoutMillis: Int, body: (DataInputStream, DataOutputStream, ByteArray) -> T): T = socket.use {
        require(pairKey.size == 32); require(timeoutMillis in 100..5000)
        require(socket.isConnected && !socket.isClosed)
        check(permits.tryAcquire()) { "Peer exchange concurrency limit" }
        try {
            val key = pairKey.copyOf()
            val started = System.nanoTime()
            val timer = deadlines.schedule({ runCatching { socket.close() } }, timeoutMillis.toLong(), TimeUnit.MILLISECONDS)
            try {
                socket.soTimeout = timeoutMillis; socket.tcpNoDelay = true; socket.setSoLinger(false, 0)
                val result = body(DataInputStream(socket.getInputStream()), DataOutputStream(socket.getOutputStream()), key)
                check(System.nanoTime() - started <= TimeUnit.MILLISECONDS.toNanos(timeoutMillis.toLong())) { "Exchange deadline exceeded" }
                result
            } finally { timer.cancel(false); key.fill(0) }
        } finally { permits.release() }
    }
    fun initiate(socket: Socket, pairKey: ByteArray, local: PlaybackAdvertisement, timeoutMillis: Int = 3000): PeerObservation =
        owned(socket, pairKey, timeoutMillis) { input, output, key ->
            val client = bytes(16); hello(output, client); val server = hello(input)
            val send = now()
            output.write(encrypt(key, aad(client, server, 1), encode(local))); output.flush()
            val response = decrypt(key, aad(client, server, 2), read(input, 85))
            val received = now()
            val remote = decode(response.copyOfRange(0, 41), local)
            val times = ByteBuffer.wrap(response, 41, 16)
            PeerObservation(remote, PeerClock.from(send, times.long, times.long, received))
        }
    fun respond(socket: Socket, pairKey: ByteArray, local: PlaybackAdvertisement, timeoutMillis: Int = 3000): PlaybackAdvertisement =
        owned(socket, pairKey, timeoutMillis) { input, output, key ->
            val client = hello(input); val server = bytes(16); hello(output, server)
            val remote = decode(decrypt(key, aad(client, server, 1), read(input, 69)), local)
            val received = now()
            val response = encode(local) + ByteBuffer.allocate(16).putLong(received).putLong(now()).array()
            output.write(encrypt(key, aad(client, server, 2), response)); output.flush()
            remote
        }
}

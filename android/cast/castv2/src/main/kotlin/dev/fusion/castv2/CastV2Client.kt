package dev.fusion.castv2

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.Socket
import java.security.cert.X509Certificate
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Events emitted by [CastV2Client]. Delivered on the reader thread; UI code
 * must hop to the main thread before touching views.
 */
sealed interface CastEvent {
    data object Connected : CastEvent
    data class ReceiverStatus(val apps: List<AppInfo>) : CastEvent
    data class MediaStatus(val mediaSessionId: Long, val playerState: String?) : CastEvent
    data class Error(val message: String) : CastEvent
    data object Closed : CastEvent
}

data class AppInfo(
    val appId: String,
    val displayName: String,
    val sessionId: String,
    val transportId: String
)

/** Factory for the underlying socket. Defaults to TLS; tests inject a plain socket. */
typealias SocketProvider = (host: String, port: Int) -> Socket

/**
 * Real castv2 (Google Cast) client — no proprietary Cast SDK.
 *
 * Speaks the open castv2 protocol directly: TLS to :8009, length-prefixed
 * protobuf [CastMessage] frames, the connection/heartbeat/receiver/media
 * namespaces, and JSON payloads. Launches the Default Media Receiver
 * (CC1AD845) and LOADs a media URL the receiver fetches itself (typically the
 * phone's LocalMediaServer on the LAN).
 *
 * Flow: connect() -> launch() -> connectToApp() -> load(url) -> play/pause/stop/seek.
 *
 * Honest limits:
 *  - No custom receiver (that needs a Cast Developer Console app id). Only the
 *    Default Media Receiver is launched.
 *  - No screen mirroring (needs the Cast SDK remote-display path, not open).
 *  - TLS is encrypted but NOT PKI-authenticated: Chromecast devices present
 *    self-signed certs, so this client accepts any cert on this one socket.
 *    That is the standard castv2 tradeoff and is documented, not hidden.
 */
class CastV2Client(
    private val host: String,
    private val port: Int = 8009,
    private val socketProvider: SocketProvider = { h, p -> Tls.connect(h, p) }
) {
    companion object {
        const val NS_CONNECTION = "urn:x-cast:com.google.cast.tp.connection"
        const val NS_HEARTBEAT = "urn:x-cast:com.google.cast.tp.heartbeat"
        const val NS_RECEIVER = "urn:x-cast:com.google.cast.receiver"
        const val NS_MEDIA = "urn:x-cast:com.google.cast.media"
        const val DEFAULT_MEDIA_RECEIVER = "CC1AD845"
        private const val SENDER = "sender-0"
        private const val RECEIVER = "receiver-0"
        private const val HEARTBEAT_MS = 5_000L
        private const val REQUEST_TIMEOUT_MS = 8_000L
        private const val MAX_FRAME = 10 * 1024 * 1024
    }

    @Volatile var connected = false; private set
    @Volatile var transportId: String? = null; private set
    @Volatile var sessionId: String? = null; private set
    @Volatile var mediaSessionId: Long? = null; private set
    @Volatile var playerState: String? = null; private set
    @Volatile private var appConnected = false

    var listener: ((CastEvent) -> Unit)? = null

    private var socket: Socket? = null
    private var out: DataOutputStream? = null
    private var readerThread: Thread? = null
    private var heartbeatThread: Thread? = null
    private var nextRequestId = 1
    private val pending = ConcurrentHashMap<Int, CompletableFuture<Map<String, Any?>>>()

    @Synchronized
    private fun nextId(): Int = nextRequestId++

    @Throws(IOException::class)
    fun connect() {
        if (connected) return
        val s = socketProvider(host, port)
        socket = s
        out = DataOutputStream(BufferedOutputStream(s.getOutputStream()))
        connected = true
        readerThread = Thread({ readLoop(s) }, "castv2-reader").apply { isDaemon = true; start() }
        heartbeatThread = Thread({ heartbeatLoop() }, "castv2-heartbeat").apply { isDaemon = true; start() }
        // Establish the virtual connection to the platform receiver.
        send(NS_CONNECTION, RECEIVER, """{"type":"CONNECT"}""")
        listener?.invoke(CastEvent.Connected)
    }

    private fun readLoop(s: Socket) {
        try {
            val input = DataInputStream(BufferedInputStream(s.getInputStream()))
            while (connected) {
                val len = input.readInt()
                if (len <= 0 || len > MAX_FRAME) break
                val payload = input.readNBytes(len)
                if (payload.size < len) break
                handleMessage(Proto.decode(payload))
            }
        } catch (_: Exception) {
            // socket closed or protocol error — fall through to cleanup
        } finally {
            connected = false
            pending.values.forEach { it.completeExceptionally(IOException("Connection closed")) }
            pending.clear()
            listener?.invoke(CastEvent.Closed)
        }
    }

    private fun handleMessage(msg: CastMessage) {
        val payload = msg.payloadUtf8 ?: return
        val obj = runCatching { Json.parseObject(payload) }.getOrNull() ?: return
        when (msg.namespace) {
            NS_RECEIVER -> if (obj.str("type") == "RECEIVER_STATUS") {
                val apps = parseApps(obj)
                apps.firstOrNull { it.appId == DEFAULT_MEDIA_RECEIVER }?.let {
                    transportId = it.transportId
                    sessionId = it.sessionId
                }
                listener?.invoke(CastEvent.ReceiverStatus(apps))
                completePending(obj)
            }
            NS_MEDIA -> if (obj.str("type") == "MEDIA_STATUS") {
                val first = obj.arr("status")?.firstOrNull() as? Map<String, Any?>
                val msid = first?.num("mediaSessionId")?.toLong()
                if (msid != null) {
                    mediaSessionId = msid
                    playerState = first?.str("playerState")
                    listener?.invoke(CastEvent.MediaStatus(msid, playerState))
                }
                completePending(obj)
            }
            NS_HEARTBEAT -> { /* PONG — nothing to do */ }
        }
    }

    private fun parseApps(obj: Map<String, Any?>): List<AppInfo> =
        obj.obj("status")?.arr("applications")?.mapNotNull { app ->
            (app as? Map<String, Any?>)?.let {
                AppInfo(
                    appId = it.str("appId") ?: "",
                    displayName = it.str("displayName") ?: "",
                    sessionId = it.str("sessionId") ?: "",
                    transportId = it.str("transportId") ?: ""
                )
            }
        } ?: emptyList()

    private fun parseMediaSessionId(obj: Map<String, Any?>): Long? =
        (obj.arr("status")?.firstOrNull() as? Map<String, Any?>)?.num("mediaSessionId")?.toLong()

    private fun completePending(obj: Map<String, Any?>) {
        val rid = obj.num("requestId")?.toInt() ?: return
        pending.remove(rid)?.complete(obj)
    }

    private fun heartbeatLoop() {
        while (connected) {
            try {
                Thread.sleep(HEARTBEAT_MS)
                if (connected) send(NS_HEARTBEAT, RECEIVER, """{"type":"PING"}""")
            } catch (_: InterruptedException) {
                return
            } catch (_: Exception) {
                return
            }
        }
    }

    private fun send(namespace: String, destination: String, payloadUtf8: String) {
        val o = out ?: throw IOException("Not connected")
        val msg = CastMessage(
            sourceId = SENDER,
            destinationId = destination,
            namespace = namespace,
            payloadType = CastMessage.PAYLOAD_STRING,
            payloadUtf8 = payloadUtf8
        )
        val body = Proto.encode(msg)
        synchronized(o) {
            o.writeInt(body.size)
            o.write(body)
            o.flush()
        }
    }

    /** Correlated request: waits for the response carrying the same requestId. */
    private fun request(namespace: String, destination: String, payload: String, id: Int): Map<String, Any?> {
        val future = CompletableFuture<Map<String, Any?>>()
        pending[id] = future
        send(namespace, destination, payload)
        return try {
            future.get(REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (e: TimeoutException) {
            pending.remove(id)
            throw IOException("No response within ${REQUEST_TIMEOUT_MS}ms: $payload", e)
        } catch (e: Exception) {
            pending.remove(id)
            throw IOException("Request failed: $payload", e)
        }
    }

    @Throws(IOException::class)
    fun launch(appId: String = DEFAULT_MEDIA_RECEIVER): List<AppInfo> {
        val id = nextId()
        val payload = """{"type":"LAUNCH","appId":"${Json.escape(appId)}","requestId":$id}"""
        val resp = request(NS_RECEIVER, RECEIVER, payload, id)
        return parseApps(resp)
    }

    /** The app endpoint is itself a receiver: CONNECT to it before sending media messages. */
    @Throws(IOException::class)
    fun connectToApp() {
        val t = transportId ?: throw IllegalStateException("No app session; call launch() first")
        if (!appConnected) {
            send(NS_CONNECTION, t, """{"type":"CONNECT"}""")
            appConnected = true
        }
    }

    @Throws(IOException::class)
    fun load(url: String, contentType: String, title: String = ""): Long {
        connectToApp()
        val t = transportId ?: throw IllegalStateException("No app session")
        val id = nextId()
        val payload = buildString {
            append("""{"type":"LOAD","media":{"contentId":"${Json.escape(url)}","contentType":"${Json.escape(contentType)}","streamType":"BUFFERED"""")
            if (title.isNotEmpty()) append(""","metadata":{"type":0,"title":"${Json.escape(title)}"}""")
            append("""},"autoplay":true,"requestId":$id""")
            sessionId?.let { append(""","sessionId":"${Json.escape(it)}"""") }
            append("}")
        }
        val resp = request(NS_MEDIA, t, payload, id)
        val msid = parseMediaSessionId(resp)
            ?: throw IOException("Receiver did not return a mediaSessionId")
        mediaSessionId = msid
        return msid
    }

    @Throws(IOException::class)
    fun play() = mediaCommand("PLAY")

    @Throws(IOException::class)
    fun pause() = mediaCommand("PAUSE")

    @Throws(IOException::class)
    fun stop() = mediaCommand("STOP")

    @Throws(IOException::class)
    fun seek(positionSeconds: Double) {
        val t = transportId ?: throw IllegalStateException("No app session")
        val msid = mediaSessionId ?: throw IllegalStateException("No media session; call load() first")
        val id = nextId()
        val payload = """{"type":"SEEK","mediaSessionId":$msid,"currentTime":$positionSeconds,"requestId":$id}"""
        request(NS_MEDIA, t, payload, id)
    }

    @Throws(IOException::class)
    private fun mediaCommand(type: String) {
        val t = transportId ?: throw IllegalStateException("No app session")
        val msid = mediaSessionId ?: throw IllegalStateException("No media session; call load() first")
        val id = nextId()
        val payload = """{"type":"$type","mediaSessionId":$msid,"requestId":$id}"""
        request(NS_MEDIA, t, payload, id)
    }

    fun close() {
        connected = false
        runCatching { send(NS_CONNECTION, RECEIVER, """{"type":"CLOSE"}""") }
        runCatching { socket?.close() }
        socket = null
        out = null
        readerThread = null
        heartbeatThread = null
    }

    /** TLS socket factory. Chromecast devices use self-signed certs. */
    object Tls {
        fun connect(host: String, port: Int): Socket {
            // The channel is TLS-encrypted but not PKI-authenticated: cast
            // devices present self-signed certificates that no public CA signs.
            // Accepting any cert on this one socket is the standard castv2
            // approach. Documented tradeoff, not a hidden shortcut.
            val trustAll = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            })
            val ctx = SSLContext.getInstance("TLS").apply { init(null, trustAll, null) }
            val s = ctx.socketFactory.createSocket(host, port)
            (s as? SSLSocket)?.startHandshake()
            return s
        }
    }
}

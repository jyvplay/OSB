package dev.fusion.beam

import java.io.InputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.URLDecoder
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Minimal embedded HTTP server with HTTP/1.1 byte-range support.
 * Pure JVM sockets, no external dependencies.
 *
 * Serves SAF documents to DLNA renderers that fetch media by HTTP URL.
 * Path handling is strict: query strings are stripped, percent-decoding is
 * applied once, and any traversal ("..", absolute paths, backslashes) is
 * rejected before the provider is consulted.
 */
data class MediaStream(
    val stream: InputStream,
    val length: Long,
    val mime: String
)

class LocalMediaServer(
    val port: Int = 8080,
    private val dataProvider: (cleanPath: String) -> MediaStream?
) {
    private var serverSocket: ServerSocket? = null
    private val isRunning = AtomicBoolean(false)
    private var workerThread: Thread? = null
    var bindError: String? = null
        private set

    val isAlive: Boolean
        get() = isRunning.get() && serverSocket?.isClosed == false

    fun start(): Boolean {
        if (isRunning.get()) return serverSocket?.isClosed == false
        return try {
            val ss = ServerSocket(port)
            serverSocket = ss
            bindError = null
            isRunning.set(true)
            workerThread = Thread({
                while (isRunning.get() && !ss.isClosed) {
                    try {
                        val client = ss.accept()
                        Thread({ handleClient(client) }, "FusionMediaHttpWorker").start()
                    } catch (_: SocketException) {
                        break
                    } catch (_: Exception) {
                        // Accept loop must survive malformed handshakes.
                    }
                }
            }, "FusionMediaHttpServer").apply { isDaemon = true; start() }
            true
        } catch (e: Exception) {
            bindError = e.message
            isRunning.set(false)
            serverSocket = null
            false
        }
    }

    fun stop() {
        if (!isRunning.getAndSet(false)) return
        try { serverSocket?.close() } catch (_: Exception) { }
        serverSocket = null
        workerThread?.interrupt()
        workerThread = null
    }

    internal fun cleanPath(rawPath: String): String? {
        val withoutQuery = rawPath.substringBefore('?').substringBefore('#')
        val stripped = withoutQuery.removePrefix("/")
        val decoded = try {
            URLDecoder.decode(stripped, "UTF-8")
        } catch (_: Exception) {
            return null
        }
        if (decoded.isBlank()) return null
        if (decoded.contains("..") || decoded.startsWith("/") || decoded.contains('\\')) return null
        // Collapse duplicate slashes but keep single separators.
        return decoded.split('/').filter { it.isNotEmpty() }.joinToString("/").takeIf { it.isNotEmpty() }
    }

    private fun handleClient(socket: Socket) {
        socket.use { s ->
            s.soTimeout = 15_000
            val input = s.getInputStream()
            val output = s.getOutputStream()

            val requestLine = readLine(input) ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return
            val method = parts[0].uppercase()
            val clean = cleanPath(parts[1])
            if (clean == null) {
                output.write("HTTP/1.1 400 Bad Request\r\nConnection: close\r\n\r\n".toByteArray())
                output.flush()
                return
            }

            var rangeHeader: String? = null
            while (true) {
                val header = readLine(input) ?: break
                if (header.isEmpty()) break
                if (header.startsWith("Range:", ignoreCase = true)) {
                    rangeHeader = header.substringAfter(":").trim()
                }
            }

            if (method != "GET" && method != "HEAD") {
                output.write("HTTP/1.1 405 Method Not Allowed\r\nConnection: close\r\n\r\n".toByteArray())
                output.flush()
                return
            }

            val media = try { dataProvider(clean) } catch (_: Exception) { null }
            if (media == null) {
                output.write("HTTP/1.1 404 Not Found\r\nConnection: close\r\n\r\n".toByteArray())
                output.flush()
                return
            }

            media.stream.use { dataStream ->
                val total = media.length
                var start = 0L
                var end = if (total > 0) total - 1 else Long.MAX_VALUE
                var isPartial = false

                if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                    val spec = rangeHeader.removePrefix("bytes=").trim()
                    // Suffix range: "bytes=-500" means last 500 bytes.
                    if (spec.startsWith("-") && total > 0) {
                        val suffix = spec.removePrefix("-").toLongOrNull()
                        if (suffix != null && suffix > 0) {
                            start = (total - suffix).coerceAtLeast(0)
                            isPartial = true
                        }
                    } else {
                        val dash = spec.indexOf('-')
                        if (dash != -1) {
                            val startStr = spec.substring(0, dash)
                            val endStr = spec.substring(dash + 1)
                            val parsedStart = startStr.toLongOrNull()
                            val parsedEnd = endStr.toLongOrNull()
                            if (parsedStart != null && parsedStart >= 0) {
                                start = parsedStart
                                isPartial = true
                            }
                            if (parsedEnd != null && total > 0) {
                                end = parsedEnd.coerceAtMost(total - 1)
                            }
                        }
                    }
                }

                if (total > 0 && start >= total) {
                    val head = "HTTP/1.1 416 Range Not Satisfiable\r\n" +
                        "Content-Range: bytes */$total\r\nConnection: close\r\n\r\n"
                    output.write(head.toByteArray())
                    output.flush()
                    return
                }

                val contentLength = if (total > 0) (end - start + 1).coerceAtLeast(0) else -1L
                val header = StringBuilder()
                if (isPartial && total > 0) {
                    header.append("HTTP/1.1 206 Partial Content\r\n")
                    header.append("Content-Range: bytes $start-$end/$total\r\n")
                } else {
                    header.append("HTTP/1.1 200 OK\r\n")
                }
                header.append("Accept-Ranges: bytes\r\n")
                header.append("Content-Type: ${media.mime}\r\n")
                if (contentLength >= 0) header.append("Content-Length: $contentLength\r\n")
                header.append("Connection: close\r\n\r\n")
                output.write(header.toString().toByteArray())
                output.flush()

                if (method == "HEAD" || contentLength == 0L) return

                if (start > 0) {
                    var skipped = 0L
                    while (skipped < start) {
                        val s = dataStream.skip(start - skipped)
                        if (s <= 0) break
                        skipped += s
                    }
                }

                val buffer = ByteArray(64 * 1024)
                var remaining = if (contentLength >= 0) contentLength else Long.MAX_VALUE
                while (remaining > 0) {
                    val toRead = if (remaining > buffer.size) buffer.size else remaining.toInt()
                    val read = dataStream.read(buffer, 0, toRead)
                    if (read == -1) break
                    output.write(buffer, 0, read)
                    remaining -= read
                }
                output.flush()
            }
        }
    }

    private fun readLine(input: InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val b = input.read()
            if (b == -1) return if (sb.isEmpty()) null else sb.toString()
            if (b == '\n'.code) break
            if (b != '\r'.code) sb.append(b.toChar())
        }
        return sb.toString()
    }
}

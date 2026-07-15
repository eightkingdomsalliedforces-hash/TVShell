package dev.tvshell.torrent

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.Closeable
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

interface TorrentReadableFile {
    val length: Long
    val fileName: String
    val contentType: String
    fun prioritize(start: Long, endExclusive: Long)
    fun awaitAvailable(start: Long, endExclusive: Long, timeoutMillis: Long): Boolean
    fun read(start: Long, target: ByteArray, offset: Int, length: Int): Int
}

data class TorrentRangeEndpoint(
    val url: String,
    val port: Int,
)

class TorrentRangeServer(
    private val readTimeoutMillis: Long = 30_000,
    private val chunkBytes: Int = 1024 * 1024,
) : Closeable {
    private val running = AtomicBoolean(false)
    private val workers: ExecutorService = Executors.newCachedThreadPool { task ->
        Thread(task, "TVShell-torrent-http-worker").apply { isDaemon = true }
    }
    private val activeSockets = ConcurrentHashMap.newKeySet<Socket>()
    private var serverSocket: ServerSocket? = null
    private var acceptThread: Thread? = null
    private var readable: TorrentReadableFile? = null
    private var route = ""

    fun start(file: TorrentReadableFile): TorrentRangeEndpoint {
        require(file.length > 0) { "torrent file must not be empty" }
        check(running.compareAndSet(false, true)) { "range server is already running" }
        readable = file
        route = "/${UUID.randomUUID()}/stream"
        val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1")).also {
            it.reuseAddress = true
            serverSocket = it
        }
        acceptThread = Thread({ acceptLoop(server) }, "TVShell-torrent-http").apply {
            isDaemon = true
            start()
        }
        return TorrentRangeEndpoint("http://127.0.0.1:${server.localPort}$route", server.localPort)
    }

    private fun acceptLoop(server: ServerSocket) {
        while (running.get()) {
            try {
                val socket = server.accept()
                if (!running.get()) {
                    socket.close()
                    continue
                }
                activeSockets += socket
                if (!running.get()) {
                    activeSockets.remove(socket)
                    socket.close()
                    continue
                }
                try {
                    workers.execute { handle(socket) }
                } catch (throwable: Throwable) {
                    activeSockets.remove(socket)
                    socket.close()
                    throw throwable
                }
            } catch (_: SocketException) {
                if (running.get()) continue
            } catch (_: Throwable) {
                if (!running.get()) return
            }
        }
    }

    private fun handle(socket: Socket) {
        try {
            socket.use { client ->
                try {
                client.tcpNoDelay = true
                client.soTimeout = readTimeoutMillis.coerceAtMost(Int.MAX_VALUE.toLong()).toInt().coerceAtLeast(1_000)
                val input = BufferedInputStream(client.getInputStream())
                val output = BufferedOutputStream(client.getOutputStream())
                val requestLine = readLine(input) ?: return
                val parts = requestLine.split(' ')
                if (parts.size < 2) return respondText(output, 400, "Bad Request", "bad request")
                val method = parts[0].uppercase()
                val path = parts[1].substringBefore('?')
                val headers = linkedMapOf<String, String>()
                while (true) {
                    val line = readLine(input) ?: break
                    if (line.isEmpty()) break
                    val separator = line.indexOf(':')
                    if (separator > 0) headers[line.substring(0, separator).lowercase()] = line.substring(separator + 1).trim()
                }
                if (path != route) return respondText(output, 404, "Not Found", "not found")
                if (method != "GET" && method != "HEAD") return respondText(output, 405, "Method Not Allowed", "method not allowed")
                val source = readable ?: return respondText(output, 503, "Service Unavailable", "stream unavailable")
                val parsed = parseRange(headers["range"], source.length)
                if (headers.containsKey("range") && parsed == null) {
                    writeHeaders(
                        output,
                        416,
                        "Range Not Satisfiable",
                        mapOf(
                            "Content-Range" to "bytes */${source.length}",
                            "Content-Length" to "0",
                        ),
                    )
                    return output.flush()
                }
                val range = parsed ?: RequestedRange(0, source.length, partial = false)
                if (method == "HEAD") {
                    writeMediaHeaders(output, source, range)
                    return output.flush()
                }

                val firstEnd = minOf(range.endExclusive, range.start + chunkBytes)
                source.prioritize(range.start, firstEnd)
                if (!source.awaitAvailable(range.start, firstEnd, readTimeoutMillis)) {
                    return respondText(output, 503, "Service Unavailable", "buffering timeout")
                }

                writeMediaHeaders(output, source, range)
                output.flush()
                streamRange(output, source, range, firstEnd)
                } catch (_: Throwable) {
                    // A player is allowed to abandon an old range request after seeking.
                }
            }
        } finally {
            activeSockets.remove(socket)
        }
    }

    private fun streamRange(
        output: BufferedOutputStream,
        source: TorrentReadableFile,
        range: RequestedRange,
        firstReadyEnd: Long,
    ) {
        val buffer = ByteArray(chunkBytes)
        var cursor = range.start
        while (cursor < range.endExclusive && running.get()) {
            val end = minOf(range.endExclusive, cursor + buffer.size)
            if (cursor >= firstReadyEnd) {
                source.prioritize(cursor, end)
                if (!source.awaitAvailable(cursor, end, readTimeoutMillis)) return
            }
            val wanted = (end - cursor).toInt()
            val count = source.read(cursor, buffer, 0, wanted)
            if (count <= 0) return
            output.write(buffer, 0, count)
            output.flush()
            cursor += count
        }
    }

    private fun writeMediaHeaders(output: BufferedOutputStream, source: TorrentReadableFile, range: RequestedRange) {
        val contentLength = range.endExclusive - range.start
        val headers = linkedMapOf(
            "Accept-Ranges" to "bytes",
            "Content-Type" to source.contentType,
            "Content-Length" to contentLength.toString(),
            "Cache-Control" to "no-store",
            "Connection" to "close",
        )
        if (range.partial) headers["Content-Range"] = "bytes ${range.start}-${range.endExclusive - 1}/${source.length}"
        writeHeaders(output, if (range.partial) 206 else 200, if (range.partial) "Partial Content" else "OK", headers)
    }

    private fun respondText(output: BufferedOutputStream, status: Int, reason: String, body: String) {
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        writeHeaders(
            output,
            status,
            reason,
            mapOf(
                "Content-Type" to "text/plain; charset=utf-8",
                "Content-Length" to bytes.size.toString(),
                "Connection" to "close",
            ),
        )
        output.write(bytes)
        output.flush()
    }

    private fun writeHeaders(output: BufferedOutputStream, status: Int, reason: String, headers: Map<String, String>) {
        val value = buildString {
            append("HTTP/1.1 ").append(status).append(' ').append(reason).append("\r\n")
            headers.forEach { (key, headerValue) -> append(key).append(": ").append(headerValue).append("\r\n") }
            append("\r\n")
        }
        output.write(value.toByteArray(StandardCharsets.US_ASCII))
    }

    private fun parseRange(value: String?, length: Long): RequestedRange? {
        if (value == null) return RequestedRange(0, length, partial = false)
        if (!value.startsWith("bytes=", ignoreCase = true) || ',' in value) return null
        val token = value.substringAfter('=').trim()
        val separator = token.indexOf('-')
        if (separator < 0) return null
        val startToken = token.substring(0, separator).trim()
        val endToken = token.substring(separator + 1).trim()
        if (startToken.isEmpty()) {
            val suffix = endToken.toLongOrNull()?.coerceAtMost(length) ?: return null
            if (suffix <= 0) return null
            return RequestedRange(length - suffix, length, partial = true)
        }
        val start = startToken.toLongOrNull() ?: return null
        if (start < 0 || start >= length) return null
        val inclusiveEnd = if (endToken.isEmpty()) length - 1 else endToken.toLongOrNull() ?: return null
        if (inclusiveEnd < start) return null
        val endExclusive = if (inclusiveEnd == Long.MAX_VALUE) length else minOf(length, inclusiveEnd + 1)
        return RequestedRange(start, endExclusive, partial = true)
    }

    private fun readLine(input: BufferedInputStream): String? {
        val bytes = ArrayList<Byte>(128)
        while (bytes.size < 16_384) {
            val value = input.read()
            if (value < 0) return if (bytes.isEmpty()) null else bytes.toByteArray().toString(StandardCharsets.US_ASCII)
            if (value == '\n'.code) break
            if (value != '\r'.code) bytes += value.toByte()
        }
        return bytes.toByteArray().toString(StandardCharsets.US_ASCII)
    }

    override fun close() {
        if (!running.compareAndSet(true, false)) return
        runCatching { serverSocket?.close() }
        activeSockets.toList().forEach { socket -> runCatching { socket.close() } }
        activeSockets.clear()
        workers.shutdownNow()
        runCatching { acceptThread?.join(1_000) }
        readable = null
    }
}

private data class RequestedRange(
    val start: Long,
    val endExclusive: Long,
    val partial: Boolean,
)

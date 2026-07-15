package dev.tvshell.torrent

import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TorrentRangeServerTest {
    private var server: TorrentRangeServer? = null

    @AfterTest
    fun tearDown() {
        server?.close()
    }

    @Test
    fun servesHeadFullAndPartialContentOnlyOnLoopback() {
        val readable = FakeReadable("0123456789".encodeToByteArray())
        val started = TorrentRangeServer(readTimeoutMillis = 1_000).also { server = it }.start(readable)

        assertTrue(started.url.startsWith("http://127.0.0.1:"))

        val head = request(started.url, "HEAD", emptyMap())
        assertEquals(200, head.status)
        assertEquals("10", head.headers["content-length"])
        assertTrue(head.body.isEmpty())

        val partial = request(started.url, "GET", mapOf("Range" to "bytes=2-5"))
        assertEquals(206, partial.status)
        assertEquals("bytes 2-5/10", partial.headers["content-range"])
        assertContentEquals("2345".encodeToByteArray(), partial.body)
        assertTrue(readable.priorities.any { it == 2L until 6L })

        val full = request(started.url, "GET", emptyMap())
        assertEquals(200, full.status)
        assertContentEquals("0123456789".encodeToByteArray(), full.body)

        val oversizedEnd = request(started.url, "GET", mapOf("Range" to "bytes=7-${Long.MAX_VALUE}"))
        assertEquals(206, oversizedEnd.status)
        assertEquals("bytes 7-9/10", oversizedEnd.headers["content-range"])
        assertContentEquals("789".encodeToByteArray(), oversizedEnd.body)
    }

    @Test
    fun rejectsInvalidRangesAndReportsUnavailablePiecesWithoutSparseZeroes() {
        val readable = FakeReadable("abcdef".encodeToByteArray(), available = false)
        val started = TorrentRangeServer(readTimeoutMillis = 10).also { server = it }.start(readable)

        val invalid = request(started.url, "GET", mapOf("Range" to "bytes=20-30"))
        assertEquals(416, invalid.status)
        assertEquals("bytes */6", invalid.headers["content-range"])

        val unavailable = request(started.url, "GET", mapOf("Range" to "bytes=1-3"))
        assertEquals(503, unavailable.status)
        assertTrue(unavailable.body.decodeToString().contains("buffering"))
        assertTrue(readable.awaited.any { it == 1L until 4L })
    }

    @Test
    fun closingTheServerClosesActivePlayerSocketsImmediately() {
        val readable = BlockingReadable()
        val started = TorrentRangeServer(readTimeoutMillis = 30_000).also { server = it }.start(readable)
        val socket = Socket("127.0.0.1", started.port).apply { soTimeout = 300 }

        try {
            val path = "/" + started.url.substringAfter("127.0.0.1:${started.port}/")
            socket.getOutputStream().write(
                "GET $path HTTP/1.1\r\nHost: 127.0.0.1\r\nRange: bytes=0-3\r\n\r\n"
                    .toByteArray(StandardCharsets.US_ASCII),
            )
            socket.getOutputStream().flush()
            assertTrue(readable.awaitEntered.await(1, TimeUnit.SECONDS))

            server?.close()
            val closed = try {
                socket.getInputStream().read() == -1
            } catch (_: SocketTimeoutException) {
                false
            } catch (_: Throwable) {
                true
            }
            assertTrue(closed, "active player socket stayed open after TorrentRangeServer.close()")
        } finally {
            readable.release.countDown()
            socket.close()
        }
    }

    private fun request(url: String, method: String, headers: Map<String, String>): HttpResponse {
        val port = url.substringAfter("127.0.0.1:").substringBefore('/').toInt()
        val path = "/" + url.substringAfter("127.0.0.1:$port/")
        Socket("127.0.0.1", port).use { socket ->
            socket.soTimeout = 2_000
            val request = buildString {
                append(method).append(' ').append(path).append(" HTTP/1.1\r\n")
                append("Host: 127.0.0.1\r\n")
                headers.forEach { (key, value) -> append(key).append(": ").append(value).append("\r\n") }
                append("Connection: close\r\n\r\n")
            }
            socket.getOutputStream().write(request.toByteArray(StandardCharsets.US_ASCII))
            socket.getOutputStream().flush()
            val bytes = socket.getInputStream().readBytes()
            val separator = "\r\n\r\n".toByteArray(StandardCharsets.US_ASCII)
            val headerEnd = bytes.indexOfSequence(separator)
            val headerText = bytes.copyOfRange(0, headerEnd).toString(StandardCharsets.US_ASCII)
            val lines = headerText.lines()
            val status = lines.first().split(' ')[1].toInt()
            val parsedHeaders = lines.drop(1).mapNotNull { line ->
                val split = line.indexOf(':')
                if (split <= 0) null else line.substring(0, split).lowercase() to line.substring(split + 1).trim()
            }.toMap()
            return HttpResponse(status, parsedHeaders, bytes.copyOfRange(headerEnd + separator.size, bytes.size))
        }
    }

    private fun ByteArray.indexOfSequence(sequence: ByteArray): Int {
        for (index in 0..size - sequence.size) {
            if (sequence.indices.all { this[index + it] == sequence[it] }) return index
        }
        error("HTTP response did not contain a header terminator")
    }
}

private data class HttpResponse(val status: Int, val headers: Map<String, String>, val body: ByteArray)

private class FakeReadable(
    private val bytes: ByteArray,
    private val available: Boolean = true,
) : TorrentReadableFile {
    override val length: Long get() = bytes.size.toLong()
    override val fileName: String = "episode.mkv"
    override val contentType: String = "video/x-matroska"
    val priorities = mutableListOf<LongRange>()
    val awaited = mutableListOf<LongRange>()

    override fun prioritize(start: Long, endExclusive: Long) {
        priorities += start until endExclusive
    }

    override fun awaitAvailable(start: Long, endExclusive: Long, timeoutMillis: Long): Boolean {
        awaited += start until endExclusive
        return available
    }

    override fun read(start: Long, target: ByteArray, offset: Int, length: Int): Int {
        if (!available) error("unavailable bytes must not be read")
        val count = minOf(length, bytes.size - start.toInt())
        bytes.copyInto(target, offset, start.toInt(), start.toInt() + count)
        return count
    }
}

private class BlockingReadable : TorrentReadableFile {
    override val length: Long = 8
    override val fileName: String = "blocking.mkv"
    override val contentType: String = "video/x-matroska"
    val awaitEntered = CountDownLatch(1)
    val release = CountDownLatch(1)

    override fun prioritize(start: Long, endExclusive: Long) = Unit

    override fun awaitAvailable(start: Long, endExclusive: Long, timeoutMillis: Long): Boolean {
        awaitEntered.countDown()
        while (release.count > 0) {
            try {
                release.await(100, TimeUnit.MILLISECONDS)
            } catch (_: InterruptedException) {
                // Model a native/disk wait that cannot be cancelled by interrupt alone.
            }
        }
        return false
    }

    override fun read(start: Long, target: ByteArray, offset: Int, length: Int): Int = -1
}

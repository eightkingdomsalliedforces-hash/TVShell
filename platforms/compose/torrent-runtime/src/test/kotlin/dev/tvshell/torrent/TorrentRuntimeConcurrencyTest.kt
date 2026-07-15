package dev.tvshell.torrent

import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TorrentRuntimeConcurrencyTest {
    @Test
    fun cancellableWorkPublishesItsFutureBeforeAnInlineExecutorCanRunIt() {
        var published: Future<*>? = null
        var observedPublishedFuture = false

        scheduleCancellableWork(
            executor = Executor { it.run() },
            assign = { published = it },
        ) {
            observedPublishedFuture = published != null
        }

        assertTrue(observedPublishedFuture)
        assertTrue(published?.isDone == true)
    }

    @Test
    fun sameInfoHashWorkIsSerializedInsideOneProcess() {
        val gate = TorrentTaskGate()
        val firstEntered = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val secondEntered = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)

        try {
            executor.submit {
                gate.withTask("0123456789abcdef") {
                    firstEntered.countDown()
                    releaseFirst.await(2, TimeUnit.SECONDS)
                }
            }
            assertTrue(firstEntered.await(1, TimeUnit.SECONDS))
            executor.submit {
                gate.withTask("0123456789abcdef") { secondEntered.countDown() }
            }

            assertFalse(secondEntered.await(100, TimeUnit.MILLISECONDS))
            releaseFirst.countDown()
            assertTrue(secondEntered.await(1, TimeUnit.SECONDS))
        } finally {
            releaseFirst.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun cacheLeasePreventsAnotherProcessFromWritingTheSameInfoHash() {
        val root = File(System.getProperty("java.io.tmpdir"), "tvshell-torrent-lock-${System.nanoTime()}")
        val id = "0123456789abcdef0123456789abcdef01234567"

        try {
            val first = assertNotNull(TorrentTaskLease.tryAcquire(root, id))
            assertNull(TorrentTaskLease.tryAcquire(root, id))
            first.close()
            assertNotNull(TorrentTaskLease.tryAcquire(root, id)).close()
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun cacheManifestCannotRedirectPruningToAnotherInfoHashLock() {
        val root = File(System.getProperty("java.io.tmpdir"), "tvshell-torrent-manifest-${System.nanoTime()}")
        val directoryID = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        val forgedID = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
        val directory = File(root, directoryID).apply { mkdirs() }
        File(directory, ".tvshell-download.json").writeText(
            """{"id":"$forgedID","title":"forged","subtitle":"","lastAccessEpochSeconds":1}""",
        )

        val runtime = JvmTorrentPlaybackRuntime(root, listener = {})
        try {
            assertTrue(runtime.cachedDownloads().isEmpty())
            assertTrue(directory.exists())
        } finally {
            runtime.close()
            root.deleteRecursively()
        }
    }
}

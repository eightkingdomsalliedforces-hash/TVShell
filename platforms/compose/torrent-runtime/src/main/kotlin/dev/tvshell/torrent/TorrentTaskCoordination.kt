package dev.tvshell.torrent

import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.util.concurrent.Executor
import java.util.concurrent.Future
import java.util.concurrent.FutureTask
import java.util.concurrent.locks.ReentrantLock

internal fun scheduleCancellableWork(
    executor: Executor,
    assign: (Future<*>) -> Unit,
    block: () -> Unit,
) {
    val future = FutureTask<Unit> {
        block()
        Unit
    }
    assign(future)
    executor.execute(future)
}

internal class TorrentTaskGate {
    private data class Entry(
        val lock: ReentrantLock = ReentrantLock(),
        var users: Int = 0,
    )

    private val monitor = Any()
    private val entries = mutableMapOf<String, Entry>()

    fun <T> withTask(taskID: String, block: () -> T): T {
        val entry = synchronized(monitor) {
            entries.getOrPut(taskID, ::Entry).also { it.users += 1 }
        }
        var acquired = false
        try {
            entry.lock.lockInterruptibly()
            acquired = true
            return block()
        } finally {
            if (acquired) entry.lock.unlock()
            synchronized(monitor) {
                entry.users -= 1
                if (entry.users == 0) entries.remove(taskID, entry)
            }
        }
    }
}

internal class TorrentTaskLease private constructor(
    private val channel: FileChannel,
    private val lock: FileLock,
) : Closeable {
    override fun close() {
        runCatching { lock.release() }
        runCatching { channel.close() }
    }

    companion object {
        private val validID = Regex("[0-9a-fA-F]{8,64}")

        fun tryAcquire(cacheRoot: File, taskID: String): TorrentTaskLease? {
            require(taskID.matches(validID)) { "BT 快取識別格式錯誤" }
            val lockDirectory = File(cacheRoot, ".locks").apply { mkdirs() }
            val channel = RandomAccessFile(File(lockDirectory, "$taskID.lock"), "rw").channel
            return try {
                val lock = channel.tryLock()
                if (lock == null) {
                    channel.close()
                    null
                } else {
                    TorrentTaskLease(channel, lock)
                }
            } catch (_: OverlappingFileLockException) {
                channel.close()
                null
            } catch (throwable: Throwable) {
                channel.close()
                throw throwable
            }
        }
    }
}

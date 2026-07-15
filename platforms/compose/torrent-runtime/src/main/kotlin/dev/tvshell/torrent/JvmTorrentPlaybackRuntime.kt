package dev.tvshell.torrent

import com.frostwire.jlibtorrent.LibTorrent
import com.frostwire.jlibtorrent.Priority
import com.frostwire.jlibtorrent.SessionManager
import com.frostwire.jlibtorrent.TorrentFlags
import com.frostwire.jlibtorrent.TorrentHandle
import com.frostwire.jlibtorrent.TorrentInfo
import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

data class RuntimeTorrentFile(
    val index: Int,
    val path: String,
    val size: Long,
)

data class RuntimeCachedDownload(
    val id: String,
    val title: String,
    val subtitle: String,
    val bytes: Long,
    val lastAccessEpochSeconds: Long,
)

sealed interface RuntimeTorrentEvent {
    val generation: Long

    data class Metadata(
        override val generation: Long,
        val files: List<RuntimeTorrentFile>,
    ) : RuntimeTorrentEvent

    data class Progress(
        override val generation: Long,
        val selectedBytes: Long,
        val selectedSize: Long,
        val totalBytes: Long,
        val downloadRateBytesPerSecond: Long,
        val peers: Int,
        val seeds: Int,
        val completedPieces: Int,
        val totalPieces: Int,
        val etaSeconds: Long?,
        val tracker: String?,
    ) : RuntimeTorrentEvent

    data class Ready(
        override val generation: Long,
        val url: String,
        val selectedPath: String,
    ) : RuntimeTorrentEvent

    data class Failed(
        override val generation: Long,
        val reason: String,
    ) : RuntimeTorrentEvent
}

class JvmTorrentPlaybackRuntime(
    private val cacheRoot: File,
    private val listener: (RuntimeTorrentEvent) -> Unit,
    private val metadataTimeoutSeconds: Int = 120,
    private val selectionTimeoutSeconds: Long = 60,
    private val readyHeadBytes: Long = 48L * 1024L * 1024L,
    private val priorityHeadBytes: Long = 32L * 1024L * 1024L,
    private val priorityTailBytes: Long = 8L * 1024L * 1024L,
    private val inactivityTimeoutSeconds: Long = 120,
    private val onHandleReady: (TorrentHandle) -> Unit = {},
) : Closeable {
    private val closed = AtomicBoolean(false)
    private val sessionLock = Any()
    private val executor: ExecutorService = Executors.newCachedThreadPool { task ->
        Thread(task, "TVShell-torrent-runtime").apply { isDaemon = true }
    }
    private val taskGate = TorrentTaskGate()
    private val tasks = ConcurrentHashMap<Long, RuntimeTask>()
    private val handlesByID = ConcurrentHashMap<String, TorrentHandle>()
    @Volatile private var session: SessionManager? = null

    init {
        cacheRoot.mkdirs()
        pruneCache()
    }

    fun start(
        generation: Long,
        taskID: String,
        magnet: String,
        title: String,
        subtitle: String,
    ) {
        require(generation > 0) { "generation must be positive" }
        require(taskID.isNotBlank()) { "task id must not be blank" }
        require(magnet.startsWith("magnet:?", ignoreCase = true)) { "不是有效的 magnet 連結" }
        check(!closed.get()) { "BT 引擎已關閉" }

        tasks.values.filter { it.taskID == taskID }.forEach { previous ->
            previous.detached.set(true)
            closePlaybackResources(previous)
            previous.future?.cancel(true)
            tasks.remove(previous.generation, previous)
        }
        val directory = safeTaskDirectory(taskID).apply { mkdirs() }
        val task = RuntimeTask(generation, taskID, magnet, title, subtitle, directory)
        tasks[generation] = task
        scheduleCancellableWork(executor, { task.future = it }) { runTask(task) }
    }

    fun select(generation: Long, fileIndex: Int) {
        tasks[generation]?.selection?.complete(fileIndex)
    }

    fun rejectSelection(generation: Long, reason: String) {
        tasks[generation]?.selection?.completeExceptionally(IllegalStateException(reason.ifBlank { "種子裡沒有可播放影片" }))
    }

    fun keepInBackground(generation: Long) {
        tasks[generation]?.let { task ->
            task.background.set(true)
            synchronized(task.lifecycleLock) {
                task.rangeServer?.close()
                task.rangeServer = null
            }
        }
    }

    fun cachedDownloads(): List<RuntimeCachedDownload> = cacheRoot.listFiles()
        ?.asSequence()
        ?.filter(File::isDirectory)
        ?.mapNotNull(::readManifest)
        ?.sortedByDescending(RuntimeCachedDownload::lastAccessEpochSeconds)
        ?.toList()
        .orEmpty()

    fun deleteCachedDownload(id: String) {
        require(id.matches(Regex("[0-9a-fA-F]{8,64}"))) { "BT 快取識別格式錯誤" }
        tasks.values.filter { it.taskID.equals(id, ignoreCase = true) }.forEach { task ->
            task.detached.set(true)
            closePlaybackResources(task)
            task.future?.cancel(true)
            tasks.remove(task.generation, task)
        }
        taskGate.withTask(id) {
            val lease = TorrentTaskLease.tryAcquire(cacheRoot, id)
                ?: error("此 BT 下載正由另一個 TVShell 程序使用，請先停止播放再刪除")
            lease.use {
                handlesByID.remove(id)?.let(::removeHandle)
                val directory = safeTaskDirectory(id)
                check(deleteDirectoryWithRetries(directory)) { "無法刪除 BT 快取：$id" }
            }
        }
    }

    fun nativeVersion(): String = LibTorrent.version()

    private fun runTask(task: RuntimeTask) {
        try {
            taskGate.withTask(task.taskID) { runTaskExclusively(task) }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            tasks.remove(task.generation, task)
        }
    }

    private fun runTaskExclusively(task: RuntimeTask) {
        var failed = false
        var lease: TorrentTaskLease? = null
        try {
            if (task.detached.get() || closed.get()) return
            lease = TorrentTaskLease.tryAcquire(cacheRoot, task.taskID)
                ?: error("此 magnet 正由另一個 TVShell 程序下載或播放")
            task.lease = lease
            writeManifest(task, selectedPath = null)
            val manager = ensureSession()
            if (task.detached.get() || closed.get()) return
            val info = loadTorrentInfo(manager, task)
            if (task.detached.get() || closed.get()) return
            task.info = info
            val storage = info.files()
            val files = (0 until storage.numFiles()).map { index ->
                RuntimeTorrentFile(index, storage.filePath(index), storage.fileSize(index))
            }
            listener(RuntimeTorrentEvent.Metadata(task.generation, files))
            val selectedIndex = task.selection.get(selectionTimeoutSeconds, TimeUnit.SECONDS)
            if (task.detached.get() || closed.get()) return
            require(selectedIndex in 0 until info.numFiles()) { "選取的 BT 影片索引無效" }
            task.selectedIndex = selectedIndex
            task.selectedPath = storage.filePath(selectedIndex)
            task.selectedSize = storage.fileSize(selectedIndex)
            writeManifest(task, task.selectedPath)

            val priorities = Priority.array(Priority.IGNORE, info.numFiles())
            priorities[selectedIndex] = Priority.SEVEN
            manager.download(info, task.directory, null, priorities, null, TorrentFlags.SEQUENTIAL_DOWNLOAD)
            val handle = waitForHandle(manager, info, task)
            task.handle = handle
            handlesByID[task.taskID] = handle
            // SessionManager can add a new torrent before its initial file priorities
            // have reached the native handle. Apply them again here so unwanted files
            // never compete with the selected episode for bandwidth or disk space.
            handle.prioritizeFiles(priorities)
            handle.resume()
            onHandleReady(handle)

            val readable = JlibTorrentReadableFile(
                info = info,
                handle = handle,
                fileIndex = selectedIndex,
                saveDirectory = File(handle.savePath()).takeIf { it.isDirectory } ?: task.directory,
                cancelled = { task.detached.get() || closed.get() },
            )
            task.readable = readable
            readable.prioritize(0, minOf(task.selectedSize, priorityHeadBytes))
            if (task.selectedSize > priorityTailBytes) {
                readable.prioritize(task.selectedSize - priorityTailBytes, task.selectedSize)
            }

            var readyPublished = false
            var lastProgressBytes = 0L
            var lastProgressNanos = System.nanoTime()
            while (!task.detached.get() && !closed.get()) {
                val status = handle.status()
                val statusError = status.errorCode()
                if (statusError.isError) error("BT 引擎錯誤：${statusError.message()}")
                val fileProgress = handle.fileProgress(TorrentHandle.PIECE_GRANULARITY)
                val selectedBytes = fileProgress.getOrElse(selectedIndex) { 0L }.coerceAtMost(task.selectedSize)
                if (selectedBytes > lastProgressBytes) {
                    lastProgressBytes = selectedBytes
                    lastProgressNanos = System.nanoTime()
                }
                val rate = status.downloadPayloadRate().toLong().coerceAtLeast(0)
                val remaining = (task.selectedSize - selectedBytes).coerceAtLeast(0)
                listener(
                    RuntimeTorrentEvent.Progress(
                        generation = task.generation,
                        selectedBytes = selectedBytes,
                        selectedSize = task.selectedSize,
                        totalBytes = status.totalDone().coerceAtLeast(0),
                        downloadRateBytesPerSecond = rate,
                        peers = status.numPeers().coerceAtLeast(0),
                        seeds = status.numSeeds().coerceAtLeast(0),
                        completedPieces = status.numPieces().coerceAtLeast(0),
                        totalPieces = info.numPieces().coerceAtLeast(0),
                        etaSeconds = if (rate > 0) remaining / rate else null,
                        tracker = status.currentTracker().takeIf(String::isNotBlank),
                    ),
                )

                if (!readyPublished && inactivityTimeoutSeconds > 0 &&
                    System.nanoTime() - lastProgressNanos >= TimeUnit.SECONDS.toNanos(inactivityTimeoutSeconds)) {
                    error("BT 下載已停滯 ${inactivityTimeoutSeconds} 秒（Peer ${status.numPeers().coerceAtLeast(0)}），請更換種子或稍後再試")
                }

                if (!readyPublished && isReady(readable, task.selectedSize)) {
                    readyPublished = true
                    touchManifest(task)
                    synchronized(task.lifecycleLock) {
                        if (!task.background.get() && !task.detached.get() && !closed.get()) {
                            val rangeServer = TorrentRangeServer()
                            val endpoint = rangeServer.start(readable)
                            task.rangeServer = rangeServer
                            listener(RuntimeTorrentEvent.Ready(task.generation, endpoint.url, task.selectedPath.orEmpty()))
                        }
                    }
                }

                if (selectedBytes >= task.selectedSize && task.background.get()) {
                    stopTask(task, removeHandle = true)
                    return
                }
                Thread.sleep(250)
            }
        } catch (throwable: Throwable) {
            failed = true
            if (!task.detached.get() && !closed.get()) {
                listener(RuntimeTorrentEvent.Failed(task.generation, humanReason(throwable)))
            }
        } finally {
            val removeHandle = !closed.get() && (failed || task.background.get() || task.detached.get())
            stopTask(task, removeHandle)
            tasks.remove(task.generation, task)
            task.lease = null
            lease?.close()
            pruneCache()
        }
    }

    private fun loadTorrentInfo(manager: SessionManager, task: RuntimeTask): TorrentInfo {
        val torrentFile = File(task.directory, "metadata.torrent")
        if (torrentFile.isFile && torrentFile.length() > 0) {
            runCatching { TorrentInfo(torrentFile) }.getOrNull()?.takeIf(TorrentInfo::isValid)?.let { return it }
            torrentFile.delete()
        }
        val bytes = manager.fetchMagnet(task.magnet, metadataTimeoutSeconds, task.directory)
            ?: error("${metadataTimeoutSeconds} 秒內沒有取得種子資訊，可能目前沒有 Peer 或 Tracker 無回應")
        val info = TorrentInfo(bytes)
        check(info.isValid) { "磁力連結的種子資訊無效" }
        val temporary = File(task.directory, "metadata.torrent.tmp")
        temporary.writeBytes(bytes)
        if (torrentFile.exists()) torrentFile.delete()
        if (!temporary.renameTo(torrentFile)) {
            torrentFile.writeBytes(bytes)
            temporary.delete()
        }
        return info
    }

    private fun closePlaybackResources(task: RuntimeTask) {
        synchronized(task.lifecycleLock) {
            task.rangeServer?.close()
            task.rangeServer = null
            task.readable?.close()
            task.readable = null
        }
    }

    private fun stopTask(task: RuntimeTask, removeHandle: Boolean) {
        closePlaybackResources(task)
        if (!removeHandle) return
        val handle = synchronized(task.lifecycleLock) {
            task.handle.also { task.handle = null }
        } ?: task.info?.let { info -> session?.find(info)?.takeIf(TorrentHandle::isValid) } ?: return
        handlesByID.remove(task.taskID, handle)
        removeHandle(handle)
    }

    private fun removeHandle(handle: TorrentHandle) {
        runCatching { handle.pause() }
        session?.let { manager -> runCatching { manager.remove(handle) } }
    }

    private fun deleteDirectoryWithRetries(directory: File): Boolean {
        if (!directory.exists()) return true
        repeat(20) {
            if (directory.deleteRecursively() || !directory.exists()) return true
            Thread.sleep(50)
        }
        return !directory.exists()
    }

    private fun ensureSession(): SessionManager {
        session?.let { return it }
        synchronized(sessionLock) {
            session?.let { return it }
            check(!closed.get()) { "BT 引擎已關閉" }
            return try {
                SessionManager(false).also {
                    it.start()
                    session = it
                }
            } catch (throwable: Throwable) {
                throw IllegalStateException("BT 原生引擎載入失敗：${throwable.message ?: throwable::class.simpleName}", throwable)
            }
        }
    }

    private fun waitForHandle(manager: SessionManager, info: TorrentInfo, task: RuntimeTask): TorrentHandle {
        repeat(120) {
            if (task.detached.get() || closed.get()) error("BT 任務已取消")
            manager.find(info)?.takeIf(TorrentHandle::isValid)?.let { return it }
            Thread.sleep(250)
        }
        error("BT 引擎沒有建立下載任務")
    }

    private fun isReady(readable: JlibTorrentReadableFile, fileSize: Long): Boolean {
        if (fileSize <= 0) return false
        val headEnd = minOf(fileSize, readyHeadBytes)
        val tailStart = (fileSize - priorityTailBytes).coerceAtLeast(0)
        return readable.isAvailable(0, headEnd) && readable.isAvailable(tailStart, fileSize)
    }

    private fun safeTaskDirectory(id: String): File {
        val root = cacheRoot.canonicalFile
        val directory = File(root, id).canonicalFile
        require(directory.parentFile == root) { "BT 快取路徑越界" }
        return directory
    }

    private fun writeManifest(task: RuntimeTask, selectedPath: String?) {
        val now = System.currentTimeMillis() / 1_000
        val payload = buildString {
            append("{\n")
            append("  \"id\": \"").append(jsonEscape(task.taskID)).append("\",\n")
            append("  \"title\": \"").append(jsonEscape(task.title)).append("\",\n")
            append("  \"subtitle\": \"").append(jsonEscape(task.subtitle)).append("\",\n")
            append("  \"magnet\": \"").append(jsonEscape(task.magnet)).append("\",\n")
            append("  \"selectedPath\": \"").append(jsonEscape(selectedPath.orEmpty())).append("\",\n")
            append("  \"lastAccessEpochSeconds\": ").append(now).append('\n')
            append("}\n")
        }
        File(task.directory, MANIFEST_NAME).writeText(payload)
    }

    private fun touchManifest(task: RuntimeTask) = writeManifest(task, task.selectedPath)

    private fun readManifest(directory: File): RuntimeCachedDownload? {
        val file = File(directory, MANIFEST_NAME).takeIf(File::isFile) ?: return null
        val text = runCatching(file::readText).getOrNull() ?: return null
        val id = jsonString(text, "id")?.takeIf(String::isNotBlank) ?: return null
        if (!id.equals(directory.name, ignoreCase = true) || !id.matches(CACHE_ID_PATTERN)) return null
        return RuntimeCachedDownload(
            id = id,
            title = jsonString(text, "title").orEmpty().ifBlank { id },
            subtitle = jsonString(text, "subtitle").orEmpty(),
            bytes = directory.walkTopDown().filter(File::isFile).sumOf(File::length),
            lastAccessEpochSeconds = Regex("\"lastAccessEpochSeconds\"\\s*:\\s*(\\d+)")
                .find(text)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: (file.lastModified() / 1_000),
        )
    }

    private fun pruneCache() {
        if (!cacheRoot.isDirectory) return
        val active = tasks.values.map(RuntimeTask::taskID).toSet()
        val now = System.currentTimeMillis() / 1_000
        val entries = cacheRoot.listFiles()?.filter(File::isDirectory)?.mapNotNull { directory ->
            readManifest(directory)?.let { directory to it }
        }.orEmpty().sortedBy { it.second.lastAccessEpochSeconds }
        val remaining = entries.toMutableList()
        entries.filter { (_, item) -> item.id !in active && now - item.lastAccessEpochSeconds > CACHE_EXPIRATION_SECONDS }
            .forEach { entry ->
                if (deleteCacheDirectoryIfUnlocked(entry.first, entry.second.id)) remaining.remove(entry)
            }
        var total = remaining.sumOf { it.second.bytes }
        remaining.forEach { (directory, item) ->
            if (total <= MAX_CACHE_BYTES) return@forEach
            if (item.id !in active) {
                if (deleteCacheDirectoryIfUnlocked(directory, item.id)) total -= item.bytes
            }
        }
    }

    private fun deleteCacheDirectoryIfUnlocked(directory: File, id: String): Boolean {
        val lease = runCatching { TorrentTaskLease.tryAcquire(cacheRoot, id) }.getOrNull() ?: return false
        return lease.use {
            handlesByID.remove(id)?.let(::removeHandle)
            deleteDirectoryWithRetries(directory)
        }
    }

    private fun humanReason(throwable: Throwable): String {
        val root = generateSequence(throwable) { it.cause }.last()
        val message = root.message.orEmpty().trim()
        return when {
            throwable is java.util.concurrent.TimeoutException -> "等待選集超時，BT 任務已停止"
            message.contains("No space", ignoreCase = true) -> "儲存空間不足"
            message.contains("magnet", ignoreCase = true) && message.contains("invalid", ignoreCase = true) -> "磁力連結格式錯誤"
            message.isNotBlank() -> message
            else -> root::class.simpleName ?: "未知 BT 錯誤"
        }
    }

    private fun jsonEscape(value: String): String = value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")

    private fun jsonString(text: String, key: String): String? =
        Regex("\"${Regex.escape(key)}\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"")
            .find(text)?.groupValues?.getOrNull(1)
            ?.replace("\\n", "\n")
            ?.replace("\\\"", "\"")
            ?.replace("\\\\", "\\")

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        tasks.values.forEach { task ->
            task.detached.set(true)
            task.rangeServer?.close()
            task.readable?.close()
            task.future?.cancel(true)
        }
        tasks.clear()
        executor.shutdownNow()
        val manager = session
        session = null
        if (manager != null) runCatching(manager::stop)
    }

    private data class RuntimeTask(
        val generation: Long,
        val taskID: String,
        val magnet: String,
        val title: String,
        val subtitle: String,
        val directory: File,
        val selection: CompletableFuture<Int> = CompletableFuture(),
        val detached: AtomicBoolean = AtomicBoolean(false),
        val background: AtomicBoolean = AtomicBoolean(false),
        val lifecycleLock: Any = Any(),
        @Volatile var future: Future<*>? = null,
        @Volatile var lease: TorrentTaskLease? = null,
        @Volatile var info: TorrentInfo? = null,
        @Volatile var handle: TorrentHandle? = null,
        @Volatile var selectedIndex: Int = -1,
        @Volatile var selectedPath: String? = null,
        @Volatile var selectedSize: Long = 0,
        @Volatile var rangeServer: TorrentRangeServer? = null,
        @Volatile var readable: JlibTorrentReadableFile? = null,
    )

    companion object {
        private const val MANIFEST_NAME = ".tvshell-download.json"
        private val CACHE_ID_PATTERN = Regex("[0-9a-fA-F]{8,64}")
        private const val MAX_CACHE_BYTES = 20L * 1024L * 1024L * 1024L
        private const val CACHE_EXPIRATION_SECONDS = 7L * 24L * 60L * 60L
    }
}

private class JlibTorrentReadableFile(
    private val info: TorrentInfo,
    private val handle: TorrentHandle,
    private val fileIndex: Int,
    saveDirectory: File,
    private val cancelled: () -> Boolean,
) : TorrentReadableFile, Closeable {
    private val storage = info.files()
    private val file = safePayloadFile(saveDirectory, storage.filePath(fileIndex))
    private val randomAccess = lazy { RandomAccessFile(file, "r") }
    override val length: Long = storage.fileSize(fileIndex)
    override val fileName: String = file.name
    override val contentType: String = contentTypeFor(fileName)

    override fun prioritize(start: Long, endExclusive: Long) {
        if (endExclusive <= start || length <= 0) return
        val pieces = pieceRange(start, endExclusive)
        pieces.forEachIndexed { index, piece ->
            runCatching { handle.setPieceDeadline(piece, (index * 25).coerceAtMost(5_000)) }
        }
    }

    fun isAvailable(start: Long, endExclusive: Long): Boolean =
        endExclusive <= start || pieceRange(start, endExclusive).all(handle::havePiece)

    override fun awaitAvailable(start: Long, endExclusive: Long, timeoutMillis: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMillis.coerceAtLeast(1)
        while (!cancelled() && System.currentTimeMillis() < deadline) {
            if (isAvailable(start, endExclusive)) return true
            Thread.sleep(50)
        }
        return isAvailable(start, endExclusive)
    }

    override fun read(start: Long, target: ByteArray, offset: Int, length: Int): Int = synchronized(this) {
        if (start >= this.length) return@synchronized -1
        val wanted = minOf(length.toLong(), this.length - start).toInt()
        val reader = randomAccess.value
        reader.seek(start)
        reader.read(target, offset, wanted)
    }

    private fun pieceRange(start: Long, endExclusive: Long): IntRange {
        val boundedStart = start.coerceIn(0, (length - 1).coerceAtLeast(0))
        val boundedEnd = (endExclusive - 1).coerceIn(boundedStart, (length - 1).coerceAtLeast(0))
        val first = info.mapFile(fileIndex, boundedStart, 1).piece()
        val last = info.mapFile(fileIndex, boundedEnd, 1).piece()
        return first..last
    }

    override fun close() {
        if (randomAccess.isInitialized()) runCatching { randomAccess.value.close() }
    }

    companion object {
        private fun safePayloadFile(root: File, relativePath: String): File {
            val canonicalRoot = root.canonicalFile
            val file = File(canonicalRoot, relativePath).canonicalFile
            require(file.path == canonicalRoot.path || file.path.startsWith(canonicalRoot.path + File.separator)) {
                "種子檔案路徑越界"
            }
            return file
        }

        private fun contentTypeFor(path: String): String = when (path.substringAfterLast('.', "").lowercase()) {
            "mp4", "m4v" -> "video/mp4"
            "mov" -> "video/quicktime"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "avi" -> "video/x-msvideo"
            "ts", "m2ts" -> "video/mp2t"
            else -> "application/octet-stream"
        }
    }
}

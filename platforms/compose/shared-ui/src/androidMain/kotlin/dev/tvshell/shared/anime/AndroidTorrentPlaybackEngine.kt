package dev.tvshell.shared.anime

import dev.tvshell.torrent.JvmTorrentPlaybackRuntime
import dev.tvshell.torrent.RuntimeTorrentEvent
import java.io.File

class AndroidTorrentPlaybackEngine(
    cacheRoot: File,
) : TorrentPlaybackEngine {
    private val lock = Any()
    private val controller = TorrentEngineController()
    private val runtime = JvmTorrentPlaybackRuntime(cacheRoot, ::acceptRuntimeEvent)

    override fun start(request: TorrentStartRequest): Result<Long> = runCatching {
        val (generation, snapshot) = synchronized(lock) {
            val next = controller.start(request)
            next to controller.snapshot()
        }
        try {
            runtime.start(generation, snapshot.taskID, snapshot.magnet, request.title, request.subtitle)
        } catch (throwable: Throwable) {
            synchronized(lock) {
                controller.accept(TorrentEngineEvent.Failed(generation, throwable.message ?: "BT 引擎啟動失敗"))
            }
            throw throwable
        }
        generation
    }

    override fun snapshot(): TorrentTransferSnapshot = synchronized(lock) { controller.snapshot() }

    override fun consumeReadyStream(generation: Long): TorrentPlayableStream? =
        synchronized(lock) { controller.consumeReadyStream(generation) }

    override fun cancelAutoplay(generation: Long) {
        synchronized(lock) { controller.cancelAutoplay(generation) }
        runtime.keepInBackground(generation)
    }

    override fun cachedDownloads(): List<TorrentCachedDownload> = runtime.cachedDownloads().map {
        TorrentCachedDownload(it.id, it.title, it.subtitle, it.bytes, it.lastAccessEpochSeconds)
    }

    override fun deleteCachedDownload(id: String): Result<Unit> = runCatching { runtime.deleteCachedDownload(id) }

    override fun close() = runtime.close()

    private fun acceptRuntimeEvent(event: RuntimeTorrentEvent) {
        var rejection: String? = null
        val selection = synchronized(lock) {
            val result = when (event) {
                is RuntimeTorrentEvent.Metadata -> controller.accept(
                    TorrentEngineEvent.Metadata(
                        event.generation,
                        event.files.map { TorrentFileCandidate(it.index, it.path, it.size) },
                    ),
                )
                is RuntimeTorrentEvent.Progress -> controller.accept(
                    TorrentEngineEvent.Progress(
                        event.generation,
                        event.selectedBytes,
                        event.selectedSize,
                        event.totalBytes,
                        event.downloadRateBytesPerSecond,
                        event.peers,
                        event.seeds,
                        event.completedPieces,
                        event.totalPieces,
                        event.etaSeconds,
                        event.tracker,
                    ),
                )
                is RuntimeTorrentEvent.Ready -> controller.accept(
                    TorrentEngineEvent.Ready(event.generation, event.url, event.selectedPath),
                )
                is RuntimeTorrentEvent.Failed -> controller.accept(
                    TorrentEngineEvent.Failed(event.generation, event.reason),
                )
            }
            if (event is RuntimeTorrentEvent.Metadata && result == null && controller.snapshot().phase == TorrentTransferPhase.Failed) {
                rejection = controller.snapshot().error
            }
            result
        }
        selection?.let { runtime.select(it.generation, it.selectedFile.index) }
        rejection?.let { runtime.rejectSelection(event.generation, it) }
    }
}

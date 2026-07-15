package dev.tvshell.shared.anime

data class TorrentStartRequest(
    val magnet: String,
    val episodeNumber: Int?,
    val title: String,
    val subtitle: String,
    val quality: String = "BT / RSS",
)

sealed interface TorrentEngineEvent {
    val generation: Long

    data class Metadata(
        override val generation: Long,
        val files: List<TorrentFileCandidate>,
    ) : TorrentEngineEvent

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
        val tracker: String? = null,
    ) : TorrentEngineEvent

    data class Ready(
        override val generation: Long,
        val streamURL: String,
        val selectedPath: String,
    ) : TorrentEngineEvent

    data class Failed(
        override val generation: Long,
        val reason: String,
    ) : TorrentEngineEvent
}

data class TorrentBackendSelection(
    val generation: Long,
    val taskID: String,
    val selectedFile: TorrentFileCandidate,
)

class TorrentEngineController {
    private var nextGeneration = 0L
    private var request: TorrentStartRequest? = null
    private var currentSnapshot = TorrentTransferSnapshot()
    private var autoplayAllowed = false
    private var readyStream: TorrentPlayableStream? = null

    fun start(value: TorrentStartRequest): Long {
        val normalizedMagnet = MagnetLink.normalize(value.magnet)
        val normalizedRequest = value.copy(magnet = normalizedMagnet)
        val generation = ++nextGeneration
        request = normalizedRequest
        autoplayAllowed = true
        readyStream = null
        currentSnapshot = TorrentTransferSnapshot(
            generation = generation,
            taskID = TorrentIdentity.stableID(normalizedMagnet),
            magnet = normalizedMagnet,
            phase = TorrentTransferPhase.Metadata,
        )
        return generation
    }

    fun snapshot(): TorrentTransferSnapshot = currentSnapshot

    fun accept(event: TorrentEngineEvent): TorrentBackendSelection? {
        if (event.generation != currentSnapshot.generation || event.generation == 0L) return null
        return when (event) {
            is TorrentEngineEvent.Metadata -> acceptMetadata(event)
            is TorrentEngineEvent.Progress -> {
                currentSnapshot = currentSnapshot.copy(
                    phase = when (currentSnapshot.phase) {
                        TorrentTransferPhase.Ready -> TorrentTransferPhase.Ready
                        TorrentTransferPhase.Background -> TorrentTransferPhase.Background
                        else -> if (event.selectedBytes > 0) TorrentTransferPhase.Buffering else TorrentTransferPhase.Downloading
                    },
                    selectedBytes = event.selectedBytes.coerceAtLeast(0),
                    selectedSize = event.selectedSize.coerceAtLeast(0),
                    totalBytes = event.totalBytes.coerceAtLeast(0),
                    downloadRateBytesPerSecond = event.downloadRateBytesPerSecond.coerceAtLeast(0),
                    peers = event.peers.coerceAtLeast(0),
                    seeds = event.seeds.coerceAtLeast(0),
                    completedPieces = event.completedPieces.coerceAtLeast(0),
                    totalPieces = event.totalPieces.coerceAtLeast(0),
                    etaSeconds = event.etaSeconds,
                    tracker = event.tracker,
                    error = null,
                )
                null
            }
            is TorrentEngineEvent.Ready -> {
                val activeRequest = request ?: return null
                if (autoplayAllowed) {
                    currentSnapshot = currentSnapshot.copy(
                        phase = TorrentTransferPhase.Ready,
                        selectedPath = event.selectedPath,
                        error = null,
                    )
                    readyStream = TorrentPlayableStream(
                        generation = event.generation,
                        taskID = currentSnapshot.taskID,
                        url = event.streamURL,
                        selectedPath = event.selectedPath,
                        quality = activeRequest.quality,
                    )
                } else {
                    currentSnapshot = currentSnapshot.copy(
                        phase = TorrentTransferPhase.Background,
                        selectedPath = event.selectedPath,
                        error = null,
                    )
                    readyStream = null
                }
                null
            }
            is TorrentEngineEvent.Failed -> {
                currentSnapshot = currentSnapshot.copy(
                    phase = TorrentTransferPhase.Failed,
                    error = event.reason.ifBlank { "未知原因" },
                )
                readyStream = null
                null
            }
        }
    }

    fun cancelAutoplay(generation: Long) {
        if (generation != currentSnapshot.generation) return
        autoplayAllowed = false
        readyStream = null
        if (currentSnapshot.phase !in setOf(TorrentTransferPhase.Idle, TorrentTransferPhase.Failed)) {
            currentSnapshot = currentSnapshot.copy(phase = TorrentTransferPhase.Background)
        }
    }

    fun consumeReadyStream(generation: Long): TorrentPlayableStream? {
        if (generation != currentSnapshot.generation) return null
        val result = readyStream
        readyStream = null
        return result
    }

    private fun acceptMetadata(event: TorrentEngineEvent.Metadata): TorrentBackendSelection? {
        val activeRequest = request ?: return null
        val staysInBackground = currentSnapshot.phase == TorrentTransferPhase.Background || !autoplayAllowed
        currentSnapshot = currentSnapshot.copy(
            phase = if (staysInBackground) TorrentTransferPhase.Background else TorrentTransferPhase.Selecting,
            error = null,
        )
        val selected = TorrentFileSelector.select(event.files, activeRequest.episodeNumber)
        if (selected == null) {
            currentSnapshot = currentSnapshot.copy(
                phase = TorrentTransferPhase.Failed,
                error = "種子裡沒有支援的可播放影片",
            )
            return null
        }
        currentSnapshot = currentSnapshot.copy(
            phase = if (staysInBackground) TorrentTransferPhase.Background else TorrentTransferPhase.Downloading,
            selectedPath = selected.path,
            selectedSize = selected.size,
            error = null,
        )
        return TorrentBackendSelection(
            generation = event.generation,
            taskID = currentSnapshot.taskID,
            selectedFile = selected,
        )
    }
}

interface TorrentPlaybackEngine {
    fun start(request: TorrentStartRequest): Result<Long>
    fun snapshot(): TorrentTransferSnapshot
    fun consumeReadyStream(generation: Long): TorrentPlayableStream?
    fun cancelAutoplay(generation: Long)
    fun cachedDownloads(): List<TorrentCachedDownload>
    fun deleteCachedDownload(id: String): Result<Unit>
    fun close()
}

class UnavailableTorrentPlaybackEngine(
    private val reason: String,
) : TorrentPlaybackEngine {
    private var lastSnapshot = TorrentTransferSnapshot()

    override fun start(request: TorrentStartRequest): Result<Long> = runCatching {
        val generation = 1L
        lastSnapshot = TorrentTransferSnapshot(
            generation = generation,
            taskID = TorrentIdentity.stableID(request.magnet),
            magnet = request.magnet,
            phase = TorrentTransferPhase.Failed,
            error = reason,
        )
        error(reason)
    }

    override fun snapshot(): TorrentTransferSnapshot = lastSnapshot
    override fun consumeReadyStream(generation: Long): TorrentPlayableStream? = null
    override fun cancelAutoplay(generation: Long) = Unit
    override fun cachedDownloads(): List<TorrentCachedDownload> = emptyList()
    override fun deleteCachedDownload(id: String): Result<Unit> = Result.failure(IllegalStateException(reason))
    override fun close() = Unit
}

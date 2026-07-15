package dev.tvshell.shared.anime

import dev.tvshell.shared.AnimeSourceKind
import dev.tvshell.shared.NativeMediaCard

data class AnimePlatformCapabilities(
    val css1: Boolean,
    val danmaku: Boolean,
    val internalPlayer: Boolean,
)

data class AnimePlaybackSnapshot(
    val positionSeconds: Double = 0.0,
    val durationSeconds: Double = 0.0,
    val isPlaying: Boolean = false,
    val error: String? = null,
)

interface PlatformAnimeService {
    val capabilities: AnimePlatformCapabilities
    val css1SubscriptionURL: String
    fun feed(source: AnimeSourceKind): Result<List<NativeMediaCard>>
    fun episodes(source: AnimeSourceKind, card: NativeMediaCard): Result<List<AnimeEpisode>>
    fun streams(source: AnimeSourceKind, episode: AnimeEpisode): Result<List<AnimeStreamCandidate>>
    fun load(candidate: AnimeStreamCandidate): Result<Unit>
    fun startTorrent(request: TorrentStartRequest): Result<Long> =
        Result.failure(UnsupportedOperationException("此平台尚未連接 BT 邊下邊播引擎"))
    fun torrentSnapshot(): TorrentTransferSnapshot = TorrentTransferSnapshot()
    fun consumeTorrentPlayableStream(generation: Long): TorrentPlayableStream? = null
    fun cancelTorrentAutoplay(generation: Long) = Unit
    fun torrentDownloads(): Result<List<TorrentCachedDownload>> = Result.success(emptyList())
    fun deleteTorrentDownload(id: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("此平台尚未連接 BT 快取管理"))
    fun play(): Result<Unit>
    fun pause(): Result<Unit>
    fun seekBy(seconds: Int): Result<Unit>
    fun volume(direction: Int): Result<Unit> = Result.success(Unit)
    fun mute(): Result<Unit> = Result.success(Unit)
    fun stop(): Result<Unit>
    fun playbackSnapshot(): AnimePlaybackSnapshot = AnimePlaybackSnapshot()
    fun danmaku(source: AnimeSourceKind, card: NativeMediaCard, episode: AnimeEpisode): Result<List<DanmakuComment>>
    fun close() = Unit
}

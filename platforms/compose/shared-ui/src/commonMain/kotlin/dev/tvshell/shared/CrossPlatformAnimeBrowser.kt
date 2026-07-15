package dev.tvshell.shared

import dev.tvshell.shared.anime.AnimeStreamCandidate
import dev.tvshell.shared.anime.TorrentCachedDownload
import dev.tvshell.shared.anime.TorrentDownloadManagerState

enum class CrossPlatformAnimePhase {
    Sources,
    Loading,
    Titles,
    Details,
    EpisodeLoading,
    Episodes,
    Resolving,
    Buffering,
    Playing,
}

enum class AnimeTopTab(val title: String) {
    Recommended("推薦"),
    OfficialSources("正版來源"),
    Subscriptions("我的訂閱"),
    History("觀看記錄"),
    Search("搜尋"),
}

enum class AnimeSourceKind {
    Bilibili,
    BangumiYouTube,
    AniGamer,
    YouTube,
    CSS1,
    AniSubsBT,
    Mikan,
    DMHY,
}

data class AnimeSourceDefinition(
    val kind: AnimeSourceKind,
    val title: String,
    val subtitle: String,
    val tab: AnimeTopTab,
)

private val defaultAnimeSources = listOf(
    AnimeSourceDefinition(AnimeSourceKind.Bilibili, "Bilibili 番劇", "推薦、排行與搜尋", AnimeTopTab.Recommended),
    AnimeSourceDefinition(AnimeSourceKind.BangumiYouTube, "Bangumi + YouTube", "Bangumi 資料與別名 · YouTube 播放", AnimeTopTab.Recommended),
    AnimeSourceDefinition(AnimeSourceKind.YouTube, "官方 YouTube 動畫", "正版授權頻道", AnimeTopTab.Recommended),
    AnimeSourceDefinition(AnimeSourceKind.AniGamer, "動畫瘋", "官方網站 · 保留廣告與限制", AnimeTopTab.OfficialSources),
    AnimeSourceDefinition(AnimeSourceKind.YouTube, "官方 YouTube", "正版授權頻道", AnimeTopTab.OfficialSources),
    AnimeSourceDefinition(AnimeSourceKind.CSS1, "ani-subs CSS1", "Web Selector 訂閱", AnimeTopTab.Subscriptions),
    AnimeSourceDefinition(AnimeSourceKind.AniSubsBT, "ani-subs BT", "RSS／BT 訂閱", AnimeTopTab.Subscriptions),
    AnimeSourceDefinition(AnimeSourceKind.Mikan, "Mikan Project", "RSS／BT", AnimeTopTab.Subscriptions),
    AnimeSourceDefinition(AnimeSourceKind.DMHY, "動漫花園", "RSS／BT", AnimeTopTab.Subscriptions),
)

fun animeSourcesFor(tab: AnimeTopTab): List<AnimeSourceDefinition> =
    defaultAnimeSources.filter { it.tab == tab }

data class CrossPlatformAnimeBrowserState(
    val sourceCount: Int = animeSourcesFor(AnimeTopTab.Recommended).size,
    val gridColumns: Int = 8,
    val episodeGridColumns: Int = 7,
    val historyGridColumns: Int = 4,
    val phase: CrossPlatformAnimePhase = CrossPlatformAnimePhase.Sources,
    val focusedTopTab: AnimeTopTab = AnimeTopTab.Recommended,
    val focusedSource: Int = 0,
    val focusedCard: Int = 0,
    val cardCount: Int = 0,
    val selectedCardIndex: Int = 0,
    val episodeCount: Int = 0,
    val focusedEpisode: Int = 0,
    val streamCandidates: List<AnimeStreamCandidate> = emptyList(),
    val focusedStreamIndex: Int = 0,
    val selectedStreamIndex: Int = 0,
    val resolvedPlaybackCandidate: AnimeStreamCandidate? = null,
    val activeTorrentGeneration: Long? = null,
    val downloadManager: TorrentDownloadManagerState = TorrentDownloadManagerState(),
    val isStreamPickerVisible: Boolean = false,
    val isPlaying: Boolean = true,
    val isPlayerHUDVisible: Boolean = true,
    val pendingSeekSeconds: Int = 0,
    val isTopNavigationFocused: Boolean = true,
    val pendingAction: String? = null,
) {
    val activePlayerCandidate: AnimeStreamCandidate?
        get() = resolvedPlaybackCandidate ?: streamCandidates.getOrNull(selectedStreamIndex)
    val activeTitleGridColumns: Int
        get() = if (focusedTopTab == AnimeTopTab.History) historyGridColumns else gridColumns

    fun reduce(command: RemoteCommand): CrossPlatformAnimeBrowserState {
        if (downloadManager.isVisible) {
            val manager = downloadManager.reduce(command)
            return copy(downloadManager = manager, pendingAction = manager.pendingAction)
        }
        if (isStreamPickerVisible) {
            return when (command) {
                RemoteCommand.Left, RemoteCommand.Up -> copy(
                    focusedStreamIndex = (focusedStreamIndex - 1).coerceAtLeast(0),
                    pendingAction = null,
                )
                RemoteCommand.Right, RemoteCommand.Down -> copy(
                    focusedStreamIndex = (focusedStreamIndex + 1).coerceAtMost((streamCandidates.size - 1).coerceAtLeast(0)),
                    pendingAction = null,
                )
                RemoteCommand.Select -> selectStream(focusedStreamIndex)
                RemoteCommand.Back, RemoteCommand.Menu -> copy(
                    phase = if (phase == CrossPlatformAnimePhase.Playing || phase == CrossPlatformAnimePhase.Buffering) {
                        phase
                    } else {
                        CrossPlatformAnimePhase.Episodes
                    },
                    isStreamPickerVisible = false,
                    pendingAction = null,
                )
                RemoteCommand.Home -> copy(
                    phase = CrossPlatformAnimePhase.Episodes,
                    isStreamPickerVisible = false,
                    pendingAction = "home",
                )
                else -> this
            }
        }
        return when (phase) {
        CrossPlatformAnimePhase.Sources -> when {
            command == RemoteCommand.Back || command == RemoteCommand.Home -> copy(pendingAction = "exit")
            isTopNavigationFocused && command == RemoteCommand.Left -> selectingTopTab(focusedTopTab.ordinal - 1)
            isTopNavigationFocused && command == RemoteCommand.Right -> selectingTopTab(focusedTopTab.ordinal + 1)
            isTopNavigationFocused && command == RemoteCommand.Down -> copy(isTopNavigationFocused = false)
            !isTopNavigationFocused && command == RemoteCommand.Up -> copy(isTopNavigationFocused = true)
            !isTopNavigationFocused && command == RemoteCommand.Left -> copy(focusedSource = (focusedSource - 1).coerceAtLeast(0))
            !isTopNavigationFocused && command == RemoteCommand.Right -> copy(focusedSource = (focusedSource + 1).coerceAtMost((sourceCount - 1).coerceAtLeast(0)))
            !isTopNavigationFocused && command == RemoteCommand.Select && sourceCount > 0 -> copy(phase = CrossPlatformAnimePhase.Loading, pendingAction = "load:$focusedSource")
            else -> this
        }
        CrossPlatformAnimePhase.Loading -> when (command) {
            RemoteCommand.Back, RemoteCommand.Home -> backToSources()
            else -> this
        }
        CrossPlatformAnimePhase.Titles -> when {
            isTopNavigationFocused && command == RemoteCommand.Left -> selectingTopTab(focusedTopTab.ordinal - 1)
            isTopNavigationFocused && command == RemoteCommand.Right -> selectingTopTab(focusedTopTab.ordinal + 1)
            isTopNavigationFocused && command == RemoteCommand.Down && cardCount > 0 -> copy(isTopNavigationFocused = false)
            !isTopNavigationFocused && command == RemoteCommand.Up && focusedCard < activeTitleGridColumns -> copy(isTopNavigationFocused = true)
            !isTopNavigationFocused && command == RemoteCommand.Up -> copy(focusedCard = (focusedCard - activeTitleGridColumns).coerceAtLeast(0))
            !isTopNavigationFocused && command == RemoteCommand.Down -> copy(focusedCard = (focusedCard + activeTitleGridColumns).coerceAtMost((cardCount - 1).coerceAtLeast(0)))
            !isTopNavigationFocused && command == RemoteCommand.Left -> copy(focusedCard = (focusedCard - 1).coerceAtLeast(0))
            !isTopNavigationFocused && command == RemoteCommand.Right -> copy(focusedCard = (focusedCard + 1).coerceAtMost((cardCount - 1).coerceAtLeast(0)))
            !isTopNavigationFocused && command == RemoteCommand.Select && cardCount > 0 -> copy(
                phase = CrossPlatformAnimePhase.Details,
                selectedCardIndex = focusedCard,
                pendingAction = null,
            )
            command == RemoteCommand.Back -> backToSources()
            else -> this
        }
        CrossPlatformAnimePhase.Details -> when (command) {
            RemoteCommand.Select -> copy(
                phase = CrossPlatformAnimePhase.EpisodeLoading,
                pendingAction = "episodes:$selectedCardIndex",
            )
            RemoteCommand.Back -> copy(
                phase = CrossPlatformAnimePhase.Titles,
                focusedCard = selectedCardIndex,
                isTopNavigationFocused = false,
                pendingAction = null,
            )
            else -> this
        }
        CrossPlatformAnimePhase.EpisodeLoading -> when (command) {
            RemoteCommand.Back, RemoteCommand.Home -> copy(phase = CrossPlatformAnimePhase.Details, pendingAction = null)
            else -> this
        }
        CrossPlatformAnimePhase.Episodes -> when (command) {
            RemoteCommand.Left -> copy(focusedEpisode = (focusedEpisode - 1).coerceAtLeast(0))
            RemoteCommand.Right -> copy(focusedEpisode = (focusedEpisode + 1).coerceAtMost((episodeCount - 1).coerceAtLeast(0)))
            RemoteCommand.Up -> copy(focusedEpisode = (focusedEpisode - episodeGridColumns).coerceAtLeast(0))
            RemoteCommand.Down -> copy(focusedEpisode = (focusedEpisode + episodeGridColumns).coerceAtMost((episodeCount - 1).coerceAtLeast(0)))
            RemoteCommand.Select -> if (episodeCount > 0) copy(
                phase = CrossPlatformAnimePhase.Resolving,
                pendingAction = "streams:$focusedEpisode",
            ) else this
            RemoteCommand.Back -> copy(phase = CrossPlatformAnimePhase.Details, pendingAction = null)
            RemoteCommand.Menu -> copy(
                downloadManager = downloadManager.opened(emptyList()),
                pendingAction = "downloads:refresh",
            )
            RemoteCommand.Home -> copy(pendingAction = "home")
            else -> this
        }
        CrossPlatformAnimePhase.Resolving -> when (command) {
            RemoteCommand.Back, RemoteCommand.Home -> copy(
                phase = CrossPlatformAnimePhase.Episodes,
                isStreamPickerVisible = false,
                pendingAction = null,
            )
            else -> this
        }
        CrossPlatformAnimePhase.Buffering -> when (command) {
            RemoteCommand.Menu -> if (streamCandidates.size > 1) copy(
                isStreamPickerVisible = true,
                focusedStreamIndex = selectedStreamIndex,
                isPlayerHUDVisible = true,
                pendingAction = null,
            ) else copy(isPlayerHUDVisible = true, pendingAction = null)
            RemoteCommand.Back -> copy(
                phase = CrossPlatformAnimePhase.Episodes,
                isPlaying = false,
                isPlayerHUDVisible = false,
                pendingAction = activeTorrentGeneration?.let { "torrent:background:$it" } ?: "torrent:cancel",
            )
            RemoteCommand.Home -> copy(pendingAction = "home")
            else -> copy(isPlayerHUDVisible = true)
        }
        CrossPlatformAnimePhase.Playing -> when (command) {
            RemoteCommand.Select, RemoteCommand.PlayPause -> copy(
                isPlaying = !isPlaying,
                isPlayerHUDVisible = true,
                pendingAction = if (isPlaying) "pause" else "play",
            )
            RemoteCommand.Left, RemoteCommand.Rewind -> copy(
                pendingSeekSeconds = -10,
                isPlayerHUDVisible = true,
                pendingAction = "seek:-10",
            )
            RemoteCommand.Right, RemoteCommand.FastForward -> copy(
                pendingSeekSeconds = 10,
                isPlayerHUDVisible = true,
                pendingAction = "seek:10",
            )
            RemoteCommand.Up, RemoteCommand.VolumeUp -> copy(isPlayerHUDVisible = true, pendingAction = "volume:up")
            RemoteCommand.Down, RemoteCommand.VolumeDown -> copy(isPlayerHUDVisible = true, pendingAction = "volume:down")
            RemoteCommand.Mute -> copy(isPlayerHUDVisible = true, pendingAction = "volume:mute")
            RemoteCommand.Menu -> if (streamCandidates.size > 1) copy(
                isStreamPickerVisible = true,
                focusedStreamIndex = selectedStreamIndex,
                isPlayerHUDVisible = true,
                pendingAction = null,
            ) else copy(isPlayerHUDVisible = true, pendingAction = null)
            RemoteCommand.Back -> copy(
                phase = CrossPlatformAnimePhase.Episodes,
                isPlaying = false,
                isPlayerHUDVisible = false,
                pendingAction = "stop",
            )
            RemoteCommand.Home -> copy(pendingAction = "home")
        }
    }
    }

    fun loaded(cardCount: Int) = copy(
        phase = CrossPlatformAnimePhase.Titles,
        focusedCard = 0,
        cardCount = cardCount,
        pendingAction = null,
        isTopNavigationFocused = true,
    )

    fun loadingFirstSource() = copy(
        focusedTopTab = AnimeTopTab.Recommended,
        sourceCount = animeSourcesFor(AnimeTopTab.Recommended).size,
        focusedSource = 0,
        phase = CrossPlatformAnimePhase.Loading,
        pendingAction = "load:0",
        isTopNavigationFocused = true,
    )

    fun failed() = backToSources()
    fun clearAction() = copy(
        pendingAction = null,
        pendingSeekSeconds = 0,
        downloadManager = downloadManager.clearAction(),
    )

    fun episodesLoaded(episodeCount: Int) = copy(
        phase = if (episodeCount > 0) CrossPlatformAnimePhase.Episodes else CrossPlatformAnimePhase.Details,
        episodeCount = episodeCount,
        focusedEpisode = 0,
        pendingAction = null,
    )

    fun streamsLoaded(candidates: List<AnimeStreamCandidate>): CrossPlatformAnimeBrowserState {
        val choices = candidates.distinctBy { it.url }
        return when (choices.size) {
            0 -> copy(phase = CrossPlatformAnimePhase.Episodes, streamCandidates = emptyList(), pendingAction = null)
            1 -> copy(streamCandidates = choices).selectStream(0)
            else -> copy(
                phase = CrossPlatformAnimePhase.Resolving,
                streamCandidates = choices,
                selectedStreamIndex = 0,
                focusedStreamIndex = 0,
                isStreamPickerVisible = true,
                pendingAction = null,
            )
        }
    }

    fun torrentStarted(generation: Long): CrossPlatformAnimeBrowserState = if (phase == CrossPlatformAnimePhase.Buffering) {
        copy(activeTorrentGeneration = generation, pendingAction = null)
    } else this

    fun openingMagnet(candidate: AnimeStreamCandidate): CrossPlatformAnimeBrowserState {
        require(candidate.url.startsWith("magnet:?", ignoreCase = true)) { "外部播放來源不是 magnet" }
        val subscriptionSources = animeSourcesFor(AnimeTopTab.Subscriptions)
        val sourceIndex = subscriptionSources.indexOfFirst { it.kind == AnimeSourceKind.AniSubsBT }.coerceAtLeast(0)
        return copy(
            sourceCount = subscriptionSources.size,
            phase = CrossPlatformAnimePhase.Buffering,
            focusedTopTab = AnimeTopTab.Subscriptions,
            focusedSource = sourceIndex,
            focusedCard = 0,
            cardCount = 1,
            selectedCardIndex = 0,
            episodeCount = 1,
            focusedEpisode = 0,
            streamCandidates = listOf(candidate),
            focusedStreamIndex = 0,
            selectedStreamIndex = 0,
            resolvedPlaybackCandidate = null,
            activeTorrentGeneration = null,
            downloadManager = TorrentDownloadManagerState(),
            isStreamPickerVisible = false,
            isPlaying = false,
            isPlayerHUDVisible = true,
            pendingSeekSeconds = 0,
            isTopNavigationFocused = false,
            pendingAction = "torrent:start:0",
        )
    }

    fun torrentReady(generation: Long, candidate: AnimeStreamCandidate): CrossPlatformAnimeBrowserState =
        if (phase == CrossPlatformAnimePhase.Buffering && activeTorrentGeneration == generation) {
            copy(
                phase = CrossPlatformAnimePhase.Playing,
                resolvedPlaybackCandidate = candidate,
                isPlaying = true,
                isPlayerHUDVisible = true,
                pendingAction = "load:${candidate.url}",
            )
        } else this

    fun torrentFailed(generation: Long): CrossPlatformAnimeBrowserState =
        if (activeTorrentGeneration == generation) copy(
            phase = CrossPlatformAnimePhase.Episodes,
            resolvedPlaybackCandidate = null,
            activeTorrentGeneration = null,
            isPlaying = false,
            pendingAction = null,
        ) else this

    fun downloadsLoaded(items: List<TorrentCachedDownload>): CrossPlatformAnimeBrowserState = copy(
        downloadManager = downloadManager.opened(items),
        pendingAction = null,
    )

    fun torrentDownloadDeleted(id: String): CrossPlatformAnimeBrowserState = copy(
        downloadManager = downloadManager.deleted(id),
        pendingAction = null,
    )

    fun hidePlayerHUD() = if (phase == CrossPlatformAnimePhase.Playing) copy(isPlayerHUDVisible = false) else this

    fun backToSources() = copy(
        phase = CrossPlatformAnimePhase.Sources,
        cardCount = 0,
        focusedCard = 0,
        selectedCardIndex = 0,
        episodeCount = 0,
        focusedEpisode = 0,
        streamCandidates = emptyList(),
        resolvedPlaybackCandidate = null,
        activeTorrentGeneration = null,
        downloadManager = TorrentDownloadManagerState(),
        isStreamPickerVisible = false,
        pendingAction = null,
    )

    private fun selectingTopTab(index: Int): CrossPlatformAnimeBrowserState {
        val tab = AnimeTopTab.entries[index.coerceIn(AnimeTopTab.entries.indices)]
        return copy(
            focusedTopTab = tab,
            sourceCount = animeSourcesFor(tab).size,
            phase = CrossPlatformAnimePhase.Sources,
            focusedSource = 0,
            focusedCard = 0,
            cardCount = 0,
            pendingAction = null,
        )
    }

    private fun selectStream(index: Int): CrossPlatformAnimeBrowserState {
        val candidate = streamCandidates.getOrNull(index) ?: return this
        val torrent = candidate.url.startsWith("magnet:", ignoreCase = true) || candidate.headers["resolver"] == "torrent"
        return if (torrent) {
            copy(
                phase = CrossPlatformAnimePhase.Buffering,
                selectedStreamIndex = index,
                focusedStreamIndex = index,
                resolvedPlaybackCandidate = null,
                activeTorrentGeneration = null,
                isStreamPickerVisible = false,
                isPlaying = false,
                isPlayerHUDVisible = true,
                pendingAction = "torrent:start:$index",
            )
        } else {
            copy(
                phase = CrossPlatformAnimePhase.Playing,
                selectedStreamIndex = index,
                focusedStreamIndex = index,
                resolvedPlaybackCandidate = null,
                activeTorrentGeneration = null,
                isStreamPickerVisible = false,
                isPlaying = true,
                isPlayerHUDVisible = true,
                pendingAction = "load:${candidate.url}",
            )
        }
    }
}

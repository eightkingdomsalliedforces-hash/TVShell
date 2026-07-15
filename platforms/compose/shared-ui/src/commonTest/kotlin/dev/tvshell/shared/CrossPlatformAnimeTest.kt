package dev.tvshell.shared

import dev.tvshell.shared.anime.AnimePlayerCommand
import dev.tvshell.shared.anime.AnimePlayerState
import dev.tvshell.shared.anime.AnimeStreamCandidate
import dev.tvshell.shared.anime.AniSubsBTSearch
import dev.tvshell.shared.anime.AniSubsBTSubscriptionParser
import dev.tvshell.shared.anime.BTRssParser
import dev.tvshell.shared.anime.BilibiliAnimeParser
import dev.tvshell.shared.anime.BangumiMetadataParser
import dev.tvshell.shared.anime.CSS1HtmlParser
import dev.tvshell.shared.anime.CSS1SubscriptionParser
import dev.tvshell.shared.anime.CSS1Anchor
import dev.tvshell.shared.anime.CSS1ContentClient
import dev.tvshell.shared.anime.CSS1Resolver
import dev.tvshell.shared.anime.DanmakuMotion
import dev.tvshell.shared.anime.DanmakuTimeline
import dev.tvshell.shared.anime.DandanplayParser
import dev.tvshell.shared.anime.DandanplayCredentials
import dev.tvshell.shared.anime.DandanplayService
import dev.tvshell.shared.anime.ServiceCredentialsParser
import dev.tvshell.shared.anime.SourceHealthState
import dev.tvshell.shared.anime.TorrentCacheEntry
import dev.tvshell.shared.anime.TorrentCachePolicy
import dev.tvshell.shared.anime.TorrentByteRange
import dev.tvshell.shared.anime.TorrentCachedDownload
import dev.tvshell.shared.anime.TorrentDownloadManagerState
import dev.tvshell.shared.anime.TorrentFileCandidate
import dev.tvshell.shared.anime.TorrentFileSelector
import dev.tvshell.shared.anime.TorrentIdentity
import dev.tvshell.shared.anime.MagnetLink
import dev.tvshell.shared.anime.MagnetHistoryReplay
import dev.tvshell.shared.anime.TorrentReadinessPolicy
import dev.tvshell.shared.anime.TorrentTransferPhase
import dev.tvshell.shared.anime.TorrentTransferSnapshot
import dev.tvshell.shared.anime.TorrentPlaybackUiState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class CrossPlatformAnimeTest {
    @Test
    fun aniSubsBtSubscriptionKeepsOnlyRssSearchTemplates() {
        val payload = """
            {"exportedMediaSourceDataList":{"mediaSources":[
              {"factoryId":"rss","arguments":{"name":"Nyaa","searchConfig":{"searchUrl":"https://nyaa.example/rss?q={keyword}"}}},
              {"factoryId":"web","arguments":{"name":"Skip","searchConfig":{"searchUrl":"https://skip/{keyword}"}}},
              {"factoryId":"rss","arguments":{"name":"Broken","searchConfig":{"searchUrl":"https://broken/no-placeholder"}}}
            ]}}
        """.trimIndent()

        val sources = AniSubsBTSubscriptionParser.sources(payload)
        assertEquals(listOf("Nyaa"), sources.map { it.name })
        assertTrue(sources.single().searchURLTemplate.contains("{keyword}"))

        val releaseTitle = "[字幕組] 葬送的芙莉蓮 - 01 [1080p][HEVC]"
        val candidate = dev.tvshell.shared.anime.AniSubsBTSearch.candidates(
            sourceName = "AnimeGarden",
            rssPayload = """<rss><channel><item><title>$releaseTitle</title><link>magnet:?xt=urn:btih:ABC</link></item></channel></rss>""",
            episodeNumber = 1,
        ).single()
        assertEquals(releaseTitle, candidate.headers["title"])
        assertEquals("AnimeGarden", candidate.headers["channel"])

        val gardenURL = AniSubsBTSearch.queryURL(
            "https://garden.example/feed.xml?filter=[{\"type\":\"动画\",\"search\":[\"{keyword}\"]}]",
            "葬送的芙莉蓮 S2",
        )
        assertFalse(gardenURL.any { it in " []{}\"" })
        assertTrue(gardenURL.contains("%E5%8A%A8%E7%94%BB"))
        assertTrue(gardenURL.contains("%E8%91%AC%E9%80%81%E7%9A%84"))
    }

    @Test
    fun torrentIdentityAndEpisodeSelectionAreStableAcrossSeasonPacks() {
        val hexMagnet = "magnet:?xt=urn:btih:0000000000000000000000000000000000000000&dn=Example&tr=udp://one"
        val reorderedMagnet = "magnet:?tr=udp://two&dn=Renamed&xt=urn%3Abtih%3A0000000000000000000000000000000000000000"
        val base32Magnet = "magnet:?xt=urn:btih:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
        assertEquals(TorrentIdentity.stableID(hexMagnet), TorrentIdentity.stableID(reorderedMagnet))
        assertEquals(TorrentIdentity.stableID(hexMagnet), TorrentIdentity.stableID(base32Magnet))
        assertEquals(40, TorrentIdentity.stableID(hexMagnet).length)

        val hybridV1 = "1111111111111111111111111111111111111111"
        val hybridV2 = "1220" + "22".repeat(32)
        val hybridFirstV1 = "magnet:?xt=urn:btih:$hybridV1&xt=urn:btmh:$hybridV2&dn=Hybrid"
        val hybridFirstV2 = "magnet:?xt=urn:btmh:$hybridV2&xt=urn:btih:$hybridV1&dn=Hybrid"
        assertEquals(TorrentIdentity.stableID(hybridFirstV1), TorrentIdentity.stableID(hybridFirstV2))
        assertEquals("22".repeat(32), TorrentIdentity.stableID(hybridFirstV1))

        val files = listOf(
            TorrentFileCandidate(0, "Show/[Group] Show - 01 [1080p].mkv", 900_000_000),
            TorrentFileCandidate(1, "Show/[Group] Show - 10 [1080p].mkv", 910_000_000),
            TorrentFileCandidate(2, "Show/Show EP12.mp4", 920_000_000),
            TorrentFileCandidate(3, "Show/Sample 01.mkv", 50_000_000),
            TorrentFileCandidate(4, "Show/poster.jpg", 4_000_000),
        )

        assertEquals(0, TorrentFileSelector.select(files, 1)?.index)
        assertEquals(1, TorrentFileSelector.select(files, 10)?.index)
        assertEquals(2, TorrentFileSelector.select(files, 12)?.index)
        assertTrue(TorrentFileSelector.isPlayable("episode.M2TS"))
        assertFalse(TorrentFileSelector.isPlayable("poster.jpg"))

        val incompleteSeason = files.filter { it.index in 0..1 }
        assertEquals(null, TorrentFileSelector.select(incompleteSeason, 3))
        val sampleFirst = listOf(
            TorrentFileCandidate(0, "Show/[Sample].mkv", 2_000_000_000),
            TorrentFileCandidate(1, "Show/Movie.mkv", 1_000_000_000),
        )
        assertEquals(1, TorrentFileSelector.select(sampleFirst, null)?.index)
    }

    @Test
    fun torrentReadinessRequiresContiguousHeadAndTailVerifiedRanges() {
        val mib = 1024L * 1024L
        val size = 800L * mib
        val onlyHead = listOf(TorrentByteRange(0, 48 * mib))
        val headAndTail = onlyHead + TorrentByteRange(size - 8 * mib, size)

        assertFalse(TorrentReadinessPolicy.isReady(size, onlyHead))
        assertTrue(TorrentReadinessPolicy.isReady(size, headAndTail))
        assertTrue(TorrentReadinessPolicy.isReady(10 * mib, listOf(TorrentByteRange(0, 10 * mib))))
    }

    @Test
    fun torrentSnapshotFormatsRealProgressAndRejectsAnOldGeneration() {
        val current = TorrentTransferSnapshot(
            generation = 7,
            taskID = "current",
            magnet = "magnet:?xt=urn:btih:ABC",
            phase = TorrentTransferPhase.Downloading,
            selectedPath = "Show EP01.mkv",
            selectedBytes = 64L * 1024 * 1024,
            selectedSize = 800L * 1024 * 1024,
            totalBytes = 70L * 1024 * 1024,
            downloadRateBytesPerSecond = 3L * 1024 * 1024,
            peers = 18,
            completedPieces = 256,
            totalPieces = 3_200,
            etaSeconds = 240,
        )
        val stale = current.copy(generation = 6, selectedBytes = 1)
        val ui = TorrentPlaybackUiState(activeGeneration = 7).accept(current).accept(stale)

        assertEquals(64L * 1024 * 1024, ui.snapshot.selectedBytes)
        assertTrue(current.statusText.contains("64 MB"))
        assertTrue(current.detailText.contains("3.0 MB/s"))
        assertTrue(current.detailText.contains("Peer 18"))
        assertEquals(0.08f, current.progressFraction)
    }

    @Test
    fun torrentCacheProtectsActiveIDsAndDownloadManagerFollowsRemoteFocus() {
        val entries = listOf(
            TorrentCacheEntry("active", bytes = 800, lastAccessEpochSeconds = 1),
            TorrentCacheEntry("old", bytes = 700, lastAccessEpochSeconds = 2),
            TorrentCacheEntry("new", bytes = 600, lastAccessEpochSeconds = 100),
        )
        assertEquals(
            listOf("old"),
            TorrentCachePolicy.idsToDelete(
                entries,
                maxBytes = 1_500,
                nowEpochSeconds = 120,
                expirationSeconds = 1_000,
                protectedIDs = setOf("active"),
            ),
        )

        val downloads = entries.map { TorrentCachedDownload(it.id, it.id, "1080p", it.bytes, it.lastAccessEpochSeconds) }
        var manager = TorrentDownloadManagerState().opened(downloads)
        manager = manager.reduce(RemoteCommand.Down).reduce(RemoteCommand.Down).reduce(RemoteCommand.Down)
        assertEquals(2, manager.focusedIndex)
        manager = manager.reduce(RemoteCommand.Select)
        assertEquals("delete:new", manager.pendingAction)
        manager = manager.deleted("new")
        assertEquals(1, manager.focusedIndex)
        assertEquals(listOf("active", "old"), manager.items.map { it.id })
        assertFalse(manager.reduce(RemoteCommand.Back).isVisible)
    }

    @Test
    fun bangumiMetadataKeepsChineseJapaneseAliasesAndEpisodeCount() {
        val subjects = BangumiMetadataParser.subjects(
            """{"data":[{"id":325285,"name":"Sousou no Frieren","name_cn":"葬送的芙莉蓮","eps":28,"summary":"勇者一行人的後日談","images":{"large":"https://lain.bgm.tv/pic/cover/l/test.jpg"}}]}""",
        )

        assertEquals(listOf("葬送的芙莉蓮", "Sousou no Frieren"), subjects.single().aliases)
        assertEquals(28, subjects.single().episodeCount)
        assertEquals("https://lain.bgm.tv/pic/cover/l/test.jpg", subjects.single().coverURL)

        val calendar = BangumiMetadataParser.calendar(
            """[{"weekday":{"id":1},"items":[{"id":456080,"name":"Test Anime","name_cn":"測試動畫","images":{"large":"http://lain.bgm.tv/test.jpg"}}]}]""",
        )
        assertEquals("測試動畫", calendar.single().title)
        assertEquals("https://lain.bgm.tv/test.jpg", calendar.single().coverURL)
    }

    @Test
    fun css1SubscriptionDecodesWebSelectorsInsteadOfTreatingJsonAsAnimeHtml() {
        val payload = """
            {"exportedMediaSourceDataList":{"mediaSources":[
              {"factoryId":"web-selector","arguments":{"name":"測試源","userAgent":"TVShell Test","searchConfig":{
                "searchUrl":"https://anime.example/search?wd={keyword}",
                "selectorSubjectFormatA":{"selectLists":".result a"},
                "selectorChannelFormatFlattened":{"selectEpisodeLists":".playlist","selectEpisodesFromList":"a","selectEpisodeLinksFromList":"","matchEpisodeSortFromName":"第\\\\s*(\\\\d+)\\\\s*集"},
                "matchVideo":{"enableNestedUrl":false,"matchVideoUrl":"(https?://[^\\\"]+\\\\.m3u8)","addHeadersToVideo":{"referer":"https://anime.example/"}}
              }}}
            ]}}
        """.trimIndent()

        val sources = CSS1SubscriptionParser.decode(payload)

        assertEquals(1, sources.size)
        assertEquals("測試源", sources.single().name)
        assertEquals(".result a", sources.single().searchSelector)
        assertEquals("https://anime.example/", sources.single().videoHeaders["Referer"])
    }

    @Test
    fun dandanplayKeepsAllRelatedCommentsAndMovesThemRightToLeft() {
        val comments = DandanplayParser.comments(
            """{"comments":[
              {"p":"1.200,1,25,16777215,0","m":"第一條"},
              {"p":"2.000,5,25,16711680,source-id","m":"第二條"}
            ]}""",
        )

        assertEquals(listOf("第一條", "第二條"), comments.map { it.text })
        assertEquals("#FF0000", comments[1].colorHex)
        assertTrue(DanmakuMotion.horizontalOffset(ageSeconds = 2.0, viewportWidth = 1920f, textWidth = 240f, speedScale = 1f) <
            DanmakuMotion.horizontalOffset(ageSeconds = 1.0, viewportWidth = 1920f, textWidth = 240f, speedScale = 1f))
        assertTrue(DanmakuMotion.lifetime(1920f, 240f, 1f) > 3.5)
    }

    @Test
    fun css1SearchIsDeferredUntilASelectedTitleNeedsEpisodes() = runBlocking {
        val subscription = """{"exportedMediaSourceDataList":{"mediaSources":[{"factoryId":"web-selector","arguments":{"name":"快源","searchConfig":{"searchUrl":"https://source.example/search?wd={keyword}","selectorSubjectFormatA":{"selectLists":".result a"},"selectorChannelFormatFlattened":{"selectEpisodeLists":".playlist","selectEpisodesFromList":"a"},"matchVideo":{"matchVideoUrl":"(https?://[^\\\"]+\\\\.m3u8)"}}}}]}}"""
        val client = object : CSS1ContentClient {
            val requested = mutableListOf<String>()
            override suspend fun get(url: String, headers: Map<String, String>): String {
                requested += url
                return when {
                    url.endsWith("css1.json") -> subscription
                    "/search?" in url -> "SEARCH"
                    url.endsWith("/show/frieren") -> "DETAIL"
                    url.endsWith("/play/1") -> "https://cdn.example/frieren-1080.m3u8"
                    else -> error("unexpected $url")
                }
            }
            override fun anchors(html: String, selector: String, baseURL: String): List<CSS1Anchor> = when (html) {
                "SEARCH" -> listOf(CSS1Anchor("葬送的芙莉蓮", "https://source.example/show/frieren"))
                "DETAIL" -> listOf(CSS1Anchor("第 1 集", "https://source.example/play/1"))
                else -> emptyList()
            }
            override fun blocks(html: String, selector: String): List<String> = if (html == "DETAIL") listOf(html) else emptyList()
            override fun encodeQuery(value: String): String = value
            override fun decodeURL(value: String): String = value
            override fun resolveURL(baseURL: String, value: String): String = value
        }
        val resolver = CSS1Resolver(client, "https://sub.example/css1.json")
        assertEquals(emptyList(), client.requested)

        val episodes = resolver.episodes("葬送的芙莉蓮")
        val streams = resolver.streams(episodes.single())

        assertEquals(listOf(1), episodes.map { it.number })
        assertEquals("https://cdn.example/frieren-1080.m3u8", streams.single().url)
        assertTrue(client.requested.first().endsWith("css1.json"))
    }

    @Test
    fun dandanplaySearchesEpisodeThenLoadsTheFullRelatedCommentSet() = runBlocking {
        val requests = mutableListOf<Pair<String, Map<String, String>>>()
        val client = object : CSS1ContentClient {
            override suspend fun get(url: String, headers: Map<String, String>): String {
                requests += url to headers
                return if ("search/episodes" in url) {
                    """{"animes":[{"episodes":[{"episodeId":123450001,"episodeNumber":"1"}]}]}"""
                } else {
                    """{"comments":[{"p":"1.0,1,25,16777215,0","m":"完整彈幕"}]}"""
                }
            }
            override fun anchors(html: String, selector: String, baseURL: String) = emptyList<CSS1Anchor>()
            override fun blocks(html: String, selector: String) = emptyList<String>()
            override fun encodeQuery(value: String) = value.replace(" ", "%20")
            override fun decodeURL(value: String) = value
            override fun resolveURL(baseURL: String, value: String) = value
        }
        val service = DandanplayService(client) { "signed:$it" }

        val comments = service.comments(
            title = "葬送的芙莉蓮",
            episode = 1,
            credentials = DandanplayCredentials("app-id", "app-secret"),
            timestamp = 123456,
        )

        assertEquals(listOf("完整彈幕"), comments.map { it.text })
        assertTrue(requests.first().first.contains("anime=葬送的芙莉蓮"))
        assertEquals("app-id", requests.first().second["X-AppId"])
        assertTrue(requests.last().first.endsWith("/123450001?withRelated=true"))
    }

    @Test
    fun sharedCredentialsFileKeepsDandanplayAndBilibiliLoginValues() {
        val credentials = ServiceCredentialsParser.decode(
            """{"dandanplay":{"appID":"dd-app","appSecret":"dd-secret"},"bilibili":{"cookie":"SESSDATA=session; bili_jct=csrf; DedeUserID=1"}}""",
        )

        assertEquals("dd-app", credentials.dandanplay.appID)
        assertEquals("dd-secret", credentials.dandanplay.appSecret)
        assertTrue(credentials.bilibiliCookie.contains("SESSDATA=session"))

        val netscape = ServiceCredentialsParser.decode(
            """# Netscape HTTP Cookie File
            #HttpOnly_.bilibili.com	TRUE	/	TRUE	2147483647	SESSDATA	session
            .bilibili.com	TRUE	/	FALSE	2147483647	bili_jct	csrf
            .bilibili.com	TRUE	/	FALSE	2147483647	DedeUserID	123""",
        )
        assertTrue(netscape.bilibiliCookie.contains("SESSDATA=session"))
        assertTrue(netscape.bilibiliCookie.contains("bili_jct=csrf"))

        val browserExport = ServiceCredentialsParser.decode(
            """{"bilibili":{"cookie":[{"domain":".bilibili.com","name":"SESSDATA","value":"session"},{"domain":"accounts.example.com","name":"private","value":"do-not-import"},{"domain":".bilibili.com","name":"bili_jct","value":"csrf"},{"domain":".bilibili.com","name":"DedeUserID","value":"123"}]}}""",
        )
        assertTrue(browserExport.bilibiliCookie.contains("DedeUserID=123"))
        assertFalse(browserExport.bilibiliCookie.contains("private="))
    }

    @Test
    fun danmakuTimelineRetainsACommentUntilItsWholeTextLeavesTheLeftEdge() {
        val comments = listOf(dev.tvshell.shared.anime.DanmakuComment(1.0, "測試彈幕"))

        assertEquals(1, DanmakuTimeline.active(comments, 2.0, 1920f, 240f, 1f).size)
        assertEquals(0, DanmakuTimeline.active(comments, 6.0, 1920f, 240f, 1f).size)
    }

    @Test
    fun animeSourceCatalogMatchesTheNativeMacTabs() {
        assertEquals(
            listOf(AnimeSourceKind.Bilibili, AnimeSourceKind.BangumiYouTube, AnimeSourceKind.YouTube),
            animeSourcesFor(AnimeTopTab.Recommended).map(AnimeSourceDefinition::kind),
        )
        assertEquals(
            listOf(AnimeSourceKind.AniGamer, AnimeSourceKind.YouTube),
            animeSourcesFor(AnimeTopTab.OfficialSources).map(AnimeSourceDefinition::kind),
        )
        assertEquals(
            listOf(AnimeSourceKind.CSS1, AnimeSourceKind.AniSubsBT, AnimeSourceKind.Mikan, AnimeSourceKind.DMHY),
            animeSourcesFor(AnimeTopTab.Subscriptions).map(AnimeSourceDefinition::kind),
        )
    }

    @Test
    fun changingAnimeTabsResetsContentAndUsesThatTabsSourceCount() {
        var state = CrossPlatformAnimeBrowserState().loaded(cardCount = 8)
        state = state.copy(isTopNavigationFocused = true).reduce(RemoteCommand.Right)
        assertEquals(AnimeTopTab.OfficialSources, state.focusedTopTab)
        assertEquals(CrossPlatformAnimePhase.Sources, state.phase)
        assertEquals(2, state.sourceCount)
        assertEquals(0, state.focusedSource)

        state = state.reduce(RemoteCommand.Right)
        assertEquals(AnimeTopTab.Subscriptions, state.focusedTopTab)
        assertEquals(4, state.sourceCount)
    }

    @Test
    fun standaloneAnimeHomeStartsWithTheRecommendedFeed() {
        val state = CrossPlatformAnimeBrowserState().loadingFirstSource()
        assertEquals(AnimeTopTab.Recommended, state.focusedTopTab)
        assertEquals(CrossPlatformAnimePhase.Loading, state.phase)
        assertEquals("load:0", state.pendingAction)
    }

    @Test
    fun animeTopNavigationMatchesTheFiveMacTabs() {
        var state = CrossPlatformAnimeBrowserState(sourceCount = 2)
        state = state.reduce(RemoteCommand.Right).reduce(RemoteCommand.Right).reduce(RemoteCommand.Right).reduce(RemoteCommand.Right)
        assertEquals(AnimeTopTab.Search, state.focusedTopTab)
        state = state.reduce(RemoteCommand.Down)
        assertFalse(state.isTopNavigationFocused)
        state = state.reduce(RemoteCommand.Up)
        assertTrue(state.isTopNavigationFocused)
    }

    @Test
    fun animeBrowserOpensARealFeedAndSelectsPlayback() {
        var state = CrossPlatformAnimeBrowserState(sourceCount = 2)
        state = state.reduce(RemoteCommand.Down).reduce(RemoteCommand.Select)
        assertEquals(CrossPlatformAnimePhase.Loading, state.phase)
        state = state.loaded(cardCount = 3)
        assertEquals(CrossPlatformAnimePhase.Titles, state.phase)
        state = state.reduce(RemoteCommand.Down).reduce(RemoteCommand.Right).reduce(RemoteCommand.Select)
        assertEquals(CrossPlatformAnimePhase.Details, state.phase)
        assertEquals(1, state.selectedCardIndex)
    }

    @Test
    fun animeLoadingCanBeCancelledWithBack() {
        var state = CrossPlatformAnimeBrowserState(sourceCount = 2, isTopNavigationFocused = false)
        state = state.reduce(RemoteCommand.Select)
        assertEquals(CrossPlatformAnimePhase.Loading, state.phase)
        state = state.reduce(RemoteCommand.Back)
        assertEquals(CrossPlatformAnimePhase.Sources, state.phase)
        val root = CrossPlatformAnimeBrowserState(sourceCount = 2).reduce(RemoteCommand.Back)
        assertEquals("exit", root.pendingAction)
    }

    @Test
    fun animeTitleGridMovesLikeTheMacBrowser() {
        var state = CrossPlatformAnimeBrowserState(sourceCount = 2, gridColumns = 4).loaded(cardCount = 9)
            .copy(isTopNavigationFocused = false)
        state = state.reduce(RemoteCommand.Down)
        assertEquals(4, state.focusedCard)
        state = state.reduce(RemoteCommand.Right).reduce(RemoteCommand.Up)
        assertEquals(1, state.focusedCard)
        state = state.reduce(RemoteCommand.Up)
        assertTrue(state.isTopNavigationFocused)
    }

    @Test
    fun titleEpisodeAndHistoryGridsUseTheSameColumnCountsAsMac() {
        var titles = CrossPlatformAnimeBrowserState(
            gridColumns = 8,
            episodeGridColumns = 7,
            historyGridColumns = 4,
        ).loaded(cardCount = 20).copy(isTopNavigationFocused = false)
        titles = titles.reduce(RemoteCommand.Down)
        assertEquals(8, titles.focusedCard)

        var episodes = titles.copy(phase = CrossPlatformAnimePhase.Details)
            .reduce(RemoteCommand.Select)
            .episodesLoaded(20)
            .reduce(RemoteCommand.Down)
        assertEquals(7, episodes.focusedEpisode)

        var history = CrossPlatformAnimeBrowserState(
            gridColumns = 8,
            episodeGridColumns = 7,
            historyGridColumns = 4,
            focusedTopTab = AnimeTopTab.History,
        ).loaded(cardCount = 12).copy(isTopNavigationFocused = false)
        history = history.reduce(RemoteCommand.Down)
        assertEquals(4, history.focusedCard)
    }
    @Test
    fun css1FiltersMetadataAndRanksPlayableQuality() {
        val episodes = CSS1HtmlParser.episodes(
            """
            <a href='/watch/1'>第 1 集</a>
            <a href='/rating'>豆瓣評分 8.1</a>
            <a href='/year'>2021</a>
            <a href='/watch/2'>第 2 話</a>
            """.trimIndent(),
            "https://source.example/show/86",
        )
        assertEquals(listOf(1, 2), episodes.map { it.number })

        val streams = CSS1HtmlParser.streams(
            """
            <source src='https://cdn.example/video-720p.mp4' label='720p'>
            <source src='https://cdn.example/video-1080p.mp4' label='1080p'>
            """.trimIndent(),
        )
        assertEquals("1080p", streams.first().quality)
        assertEquals(2, streams.size)
    }

    @Test
    fun btRssNormalizesMagnetAndSourceHealthSkipsFailedHost() {
        val items = BTRssParser.items(
            """
            <rss><channel><item><title>[Lilith-Raws] 葬送的芙莉蓮 - 01 [1080p]</title>
            <enclosure url='magnet:?xt=urn:btih:ABC123&amp;dn=Frieren' type='application/x-bittorrent'/>
            </item></channel></rss>
            """.trimIndent(),
        )
        assertEquals(1, items.first().episode)
        assertTrue(items.first().magnet.startsWith("magnet:?xt=urn:btih:ABC123"))

        val elementMagnet = BTRssParser.items(
            """<rss><channel><item><title>[Group] Show [12] [1080p]</title><magneturi>magnet:?xt=urn:btih:DEF456&amp;dn=Show</magneturi></item></channel></rss>""",
        ).single()
        assertEquals(12, elementMagnet.episode)
        assertTrue(elementMagnet.magnet.contains("DEF456"))

        var health = SourceHealthState()
        health = health.recordFailure("broken.example", "timeout")
        assertFalse(health.shouldLoad("broken.example"))
        health = health.reset("broken.example")
        assertTrue(health.shouldLoad("broken.example"))
    }

    @Test
    fun cacheCleanupAndPlayerCommandsArePortable() {
        val entries = listOf(
            TorrentCacheEntry("old", bytes = 700, lastAccessEpochSeconds = 10),
            TorrentCacheEntry("new", bytes = 600, lastAccessEpochSeconds = 100),
        )
        assertEquals(listOf("old"), TorrentCachePolicy.idsToDelete(entries, maxBytes = 900, nowEpochSeconds = 120, expirationSeconds = 1_000))

        var player = AnimePlayerState()
        player = player.reduce(AnimePlayerCommand.PlayPause)
        player = player.reduce(AnimePlayerCommand.FastForward)
        assertTrue(player.isPlaying)
        assertEquals(15, player.pendingSeekSeconds)
    }

    @Test
    fun animeEpisodeKeepsMasterStreamAndQualityAlternatives() {
        val master = AnimeStreamCandidate("https://cdn.example/master.m3u8", "自動")
        val variants = listOf(
            AnimeStreamCandidate("https://cdn.example/1080.m3u8", "1080p"),
            AnimeStreamCandidate("https://cdn.example/720.m3u8", "720p"),
        )
        var player = AnimePlayerState().loaded(master, variants)
        assertEquals(master.url, player.selectedCandidate?.url)
        assertEquals(3, player.candidates.size)
        player = player.reduce(AnimePlayerCommand.OpenSourcePicker)
            .reduce(AnimePlayerCommand.NextSource)
            .reduce(AnimePlayerCommand.ConfirmSource)
        assertEquals("1080p", player.selectedCandidate?.quality)
        assertEquals("load:https://cdn.example/1080.m3u8", player.pendingAction)
    }

    @Test
    fun animeJourneyMatchesMacDetailsEpisodesAndPlaybackOrder() {
        var state = CrossPlatformAnimeBrowserState(gridColumns = 4).loaded(cardCount = 8)
            .copy(isTopNavigationFocused = false, focusedCard = 2)

        state = state.reduce(RemoteCommand.Select)
        assertEquals(CrossPlatformAnimePhase.Details, state.phase)
        assertEquals(2, state.selectedCardIndex)

        state = state.reduce(RemoteCommand.Select)
        assertEquals(CrossPlatformAnimePhase.EpisodeLoading, state.phase)
        assertEquals("episodes:2", state.pendingAction)

        state = state.episodesLoaded(episodeCount = 12)
        assertEquals(CrossPlatformAnimePhase.Episodes, state.phase)
        assertEquals(0, state.focusedEpisode)

        state = state.reduce(RemoteCommand.Right).reduce(RemoteCommand.Select)
        assertEquals(CrossPlatformAnimePhase.Resolving, state.phase)
        assertEquals("streams:1", state.pendingAction)
    }

    @Test
    fun multipleAnimePlaybackLinesRequireTheSameExplicitPickerAsMac() {
        val candidates = listOf(
            AnimeStreamCandidate("https://cdn.example/master.m3u8", "自動"),
            AnimeStreamCandidate("https://cdn.example/1080.m3u8", "1080p"),
        )
        var state = CrossPlatformAnimeBrowserState().loaded(1)
            .copy(isTopNavigationFocused = false)
            .reduce(RemoteCommand.Select)
            .reduce(RemoteCommand.Select)
            .episodesLoaded(1)
            .reduce(RemoteCommand.Select)
            .streamsLoaded(candidates)

        assertEquals(CrossPlatformAnimePhase.Resolving, state.phase)
        assertTrue(state.isStreamPickerVisible)
        state = state.reduce(RemoteCommand.Right).reduce(RemoteCommand.Select)
        assertEquals(CrossPlatformAnimePhase.Playing, state.phase)
        assertEquals(1, state.selectedStreamIndex)
        assertEquals("load:https://cdn.example/1080.m3u8", state.pendingAction)
    }

    @Test
    fun magnetWaitsForTheEmbeddedEngineBeforeTheInternalPlayerLoads() {
        val magnet = AnimeStreamCandidate(
            "magnet:?xt=urn:btih:SEASON",
            "BT / RSS",
            mapOf("resolver" to "torrent"),
        )
        var state = CrossPlatformAnimeBrowserState().loaded(1)
            .copy(isTopNavigationFocused = false)
            .reduce(RemoteCommand.Select)
            .reduce(RemoteCommand.Select)
            .episodesLoaded(12)
            .copy(focusedEpisode = 9)
            .reduce(RemoteCommand.Select)
            .streamsLoaded(listOf(magnet))

        assertEquals(CrossPlatformAnimePhase.Buffering, state.phase)
        assertEquals("torrent:start:0", state.pendingAction)
        assertEquals(magnet, state.activePlayerCandidate)

        state = state.torrentStarted(42).clearAction()
        state = state.torrentReady(
            generation = 42,
            candidate = AnimeStreamCandidate("http://127.0.0.1:43123/token/stream", "BT · 1080p"),
        )
        assertEquals(CrossPlatformAnimePhase.Playing, state.phase)
        assertEquals("load:http://127.0.0.1:43123/token/stream", state.pendingAction)
        assertTrue(state.activePlayerCandidate?.url.orEmpty().startsWith("http://127.0.0.1:"))
    }

    @Test
    fun externalMagnetIsValidatedNamedAndRoutedIntoTheEmbeddedEngine() {
        val magnet = MagnetLink.normalize(
            "  MAGNET:?xt=urn%3Abtih%3A0000000000000000000000000000000000000000&dn=%E8%91%AC%E9%80%81%E7%9A%84%E8%8A%99%E8%8E%89%E8%93%AE%20S01  ",
        )
        assertTrue(magnet.startsWith("magnet:?"))
        assertEquals("葬送的芙莉蓮 S01", MagnetLink.displayName(magnet))

        val candidate = AnimeStreamCandidate(
            magnet,
            "Magnet · BT",
            mapOf("resolver" to "torrent", "source" to "外部 Magnet"),
        )
        val state = CrossPlatformAnimeBrowserState().openingMagnet(candidate)

        assertEquals(AnimeTopTab.Subscriptions, state.focusedTopTab)
        assertEquals(CrossPlatformAnimePhase.Buffering, state.phase)
        assertEquals("torrent:start:0", state.pendingAction)
        assertEquals(candidate, state.activePlayerCandidate)
        assertEquals(1, state.cardCount)
        assertEquals(1, state.episodeCount)

        val invalid = runCatching { MagnetLink.normalize("https://example.com/video") }
        assertTrue(invalid.isFailure)
        val malformedHash = runCatching { MagnetLink.normalize("magnet:?xt=urn:btih:ABC") }
        assertTrue(malformedHash.isFailure)
        assertTrue(malformedHash.exceptionOrNull()?.message.orEmpty().contains("格式"))
    }

    @Test
    fun magnetWatchHistoryRebuildsOneEpisodeAndRoutesBackThroughTheTorrentEngine() {
        val magnet = "magnet:?xt=urn:btih:6666666666666666666666666666666666666666&dn=Replay"
        val card = NativeMediaCard(
            id = "magnet-${TorrentIdentity.stableID(magnet)}",
            title = "Replay",
            subtitle = "外部 Magnet · BT 邊下邊播",
            thumbnailURL = "",
            playbackURL = magnet,
            animeSource = AnimeSourceKind.AniSubsBT,
            animeEpisodeNumber = 0,
        )

        val episode = assertNotNull(MagnetHistoryReplay.episode(card))
        val stream = assertNotNull(MagnetHistoryReplay.stream(episode))

        assertEquals(0, episode.number)
        assertEquals(magnet, episode.pageURL)
        assertEquals("torrent", stream.headers["resolver"])
        assertEquals(magnet, stream.url)

        val historyCard = MagnetHistoryReplay.card(
            original = card.copy(id = "bangumi-42", playbackURL = "https://bgm.tv/subject/42"),
            episode = episode.copy(number = 7, title = "Replay 第 7 集"),
            candidate = stream,
        )
        assertEquals(magnet, historyCard.playbackURL)
        assertEquals(7, historyCard.animeEpisodeNumber)
        assertEquals(AnimeSourceKind.AniSubsBT, historyCard.animeSource)
        assertTrue(historyCard.id.startsWith("magnet-"))
    }

    @Test
    fun episodeMenuOpensDownloadsAndPickerMenuCannotLeaveAResolvingDeadlock() {
        var episodes = CrossPlatformAnimeBrowserState().loaded(1)
            .copy(isTopNavigationFocused = false)
            .reduce(RemoteCommand.Select)
            .reduce(RemoteCommand.Select)
            .episodesLoaded(2)
            .reduce(RemoteCommand.Menu)
        assertTrue(episodes.downloadManager.isVisible)
        assertEquals("downloads:refresh", episodes.pendingAction)
        episodes = episodes.reduce(RemoteCommand.Back)
        assertFalse(episodes.downloadManager.isVisible)

        val choices = listOf(
            AnimeStreamCandidate("https://cdn.example/a.m3u8", "自動"),
            AnimeStreamCandidate("https://cdn.example/b.m3u8", "1080p"),
        )
        val picker = CrossPlatformAnimeBrowserState().loaded(1)
            .copy(isTopNavigationFocused = false)
            .reduce(RemoteCommand.Select)
            .reduce(RemoteCommand.Select)
            .episodesLoaded(1)
            .reduce(RemoteCommand.Select)
            .streamsLoaded(choices)
            .reduce(RemoteCommand.Menu)
        assertEquals(CrossPlatformAnimePhase.Episodes, picker.phase)
        assertFalse(picker.isStreamPickerVisible)
    }

    @Test
    fun closingTheSourcePickerDuringPlaybackKeepsTheCurrentPlayerAlive() {
        val choices = listOf(
            AnimeStreamCandidate("https://cdn.example/a.m3u8", "自動"),
            AnimeStreamCandidate("https://cdn.example/b.m3u8", "1080p"),
        )
        var state = CrossPlatformAnimeBrowserState().loaded(1)
            .copy(isTopNavigationFocused = false)
            .reduce(RemoteCommand.Select)
            .reduce(RemoteCommand.Select)
            .episodesLoaded(1)
            .reduce(RemoteCommand.Select)
            .streamsLoaded(choices)
            .reduce(RemoteCommand.Select)
            .clearAction()

        assertEquals(CrossPlatformAnimePhase.Playing, state.phase)
        state = state.reduce(RemoteCommand.Menu)
        assertTrue(state.isStreamPickerVisible)
        state = state.reduce(RemoteCommand.Back)
        assertEquals(CrossPlatformAnimePhase.Playing, state.phase)
        assertFalse(state.isStreamPickerVisible)
        assertEquals(null, state.pendingAction)
    }

    @Test
    fun bufferingMagnetCanOpenCloseAndSwitchTheSourcePicker() {
        val choices = listOf(
            AnimeStreamCandidate("magnet:?xt=urn:btih:AAA", "1080p · A", mapOf("resolver" to "torrent")),
            AnimeStreamCandidate("magnet:?xt=urn:btih:BBB", "1080p · B", mapOf("resolver" to "torrent")),
        )
        var state = CrossPlatformAnimeBrowserState().streamsLoaded(choices)
            .reduce(RemoteCommand.Select)
            .torrentStarted(42)
            .clearAction()

        assertEquals(CrossPlatformAnimePhase.Buffering, state.phase)
        state = state.reduce(RemoteCommand.Menu)
        assertTrue(state.isStreamPickerVisible)
        state = state.reduce(RemoteCommand.Back)
        assertEquals(CrossPlatformAnimePhase.Buffering, state.phase)
        assertEquals(42, state.activeTorrentGeneration)

        state = state.reduce(RemoteCommand.Menu).reduce(RemoteCommand.Right).reduce(RemoteCommand.Select)
        assertEquals(CrossPlatformAnimePhase.Buffering, state.phase)
        assertEquals(1, state.selectedStreamIndex)
        assertEquals(null, state.activeTorrentGeneration)
        assertEquals("torrent:start:1", state.pendingAction)
    }

    @Test
    fun changingAwayFromAnActiveTorrentMovesItsDownloadToTheBackground() {
        assertEquals(42L, torrentGenerationToBackground(42L, null, "load:https://cdn.example/video.m3u8"))
        assertEquals(42L, torrentGenerationToBackground(42L, null, "torrent:start:1"))
        assertEquals(null, torrentGenerationToBackground(42L, 42L, "load:http://127.0.0.1:1234/stream"))
        assertEquals(null, torrentGenerationToBackground(42L, null, "pause"))
    }

    @Test
    fun animePlayerRemoteCommandsMatchMacHudSeekVolumeAndBack() {
        val candidate = AnimeStreamCandidate("https://cdn.example/1080.m3u8", "1080p")
        var state = CrossPlatformAnimeBrowserState().loaded(1)
            .copy(isTopNavigationFocused = false)
            .reduce(RemoteCommand.Select)
            .reduce(RemoteCommand.Select)
            .episodesLoaded(1)
            .reduce(RemoteCommand.Select)
            .streamsLoaded(listOf(candidate))
            .clearAction()

        state = state.reduce(RemoteCommand.Right)
        assertEquals(10, state.pendingSeekSeconds)
        assertEquals("seek:10", state.pendingAction)
        state = state.clearAction().reduce(RemoteCommand.Up)
        assertEquals("volume:up", state.pendingAction)
        state = state.clearAction().reduce(RemoteCommand.PlayPause)
        assertFalse(state.isPlaying)
        assertEquals("pause", state.pendingAction)
        state = state.clearAction().reduce(RemoteCommand.Back)
        assertEquals(CrossPlatformAnimePhase.Episodes, state.phase)
        assertEquals("stop", state.pendingAction)
    }

    @Test
    fun animeHudUsesPlaybackTimeInsteadOfTorrentDownloadProgress() {
        assertEquals("1:05", animePlaybackTimeLabel(65.4))
        assertEquals("0:00", animePlaybackTimeLabel(Double.NaN))
        assertEquals(0f, animePlayerProgress(currentSeconds = 120.0, durationSeconds = 0.0))
        assertEquals(.5f, animePlayerProgress(currentSeconds = 120.0, durationSeconds = 240.0))
    }

    @Test
    fun bilibiliSeasonAndPlaybackResponsesBecomeEpisodesAndCombinedStreams() {
        val episodes = BilibiliAnimeParser.episodes(
            """{"result":{"main_section":{"episodes":[{"id":123,"title":"1","long_title":"相遇","cid":456,"bvid":"BV1ABC"},{"id":124,"title":"2","long_title":"出發","cid":457,"bvid":"BV2ABC"}]}}}""",
        )
        assertEquals(listOf("第 1 集 · 相遇", "第 2 集 · 出發"), episodes.map { it.title })
        assertEquals("bilibili:123:456", episodes.first().id)

        val streams = BilibiliAnimeParser.streams(
            """{"result":{"quality":80,"accept_quality":[80,64],"durl":[{"url":"https://cdn.example/video.flv"}]}}""",
        )
        assertEquals(1, streams.size)
        assertEquals("1080p", streams.single().quality)
        assertEquals("https://www.bilibili.com/", streams.single().headers["Referer"])

        val danmaku = BilibiliAnimeParser.danmaku(
            """<i><d p="1.5,1,25,16777215,0,0,0,0">第一條&amp;彈幕</d><d p="3.0,5,25,16711680,0,0,0,0">置頂</d></i>""",
        )
        assertEquals(listOf("第一條&彈幕", "置頂"), danmaku.map { it.text })
        assertEquals("#FF0000", danmaku[1].colorHex)
    }

    @Test
    fun bilibiliEpisodesRemainUsableWhenTheRealApiOmitsBvid() {
        val episodes = BilibiliAnimeParser.episodes(
            """{"result":{"main_section":{"episodes":[{"aid":1806595774,"cid":39787692307,"id":4353829,"long_title":"先行片 PILOT（中文配音）","share_url":"https://www.bilibili.com/bangumi/play/ep4353829","title":"1","vid":""}]}}}""",
        )
        assertEquals(1, episodes.size)
        assertEquals("bilibili:4353829:39787692307", episodes.single().id)
    }

    @Test
    fun bilibiliPlaybackFailureKeepsTheRealRegionReason() {
        assertEquals(
            "抱歉您所在地区不可观看！",
            BilibiliAnimeParser.failureReason("""{"code":-10403,"message":"抱歉您所在地区不可观看！"}"""),
        )
    }
}

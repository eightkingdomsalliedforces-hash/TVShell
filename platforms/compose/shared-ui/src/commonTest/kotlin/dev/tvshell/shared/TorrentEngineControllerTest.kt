package dev.tvshell.shared

import dev.tvshell.shared.anime.TorrentEngineController
import dev.tvshell.shared.anime.TorrentEngineEvent
import dev.tvshell.shared.anime.TorrentFileCandidate
import dev.tvshell.shared.anime.TorrentStartRequest
import dev.tvshell.shared.anime.TorrentTransferPhase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TorrentEngineControllerTest {
    @Test
    fun controllerRejectsMalformedInfoHashBeforeStartingNativeWork() {
        val controller = TorrentEngineController()

        val failure = runCatching {
            controller.start(TorrentStartRequest("magnet:?xt=urn:btih:BAD", 1, "Bad", "EP1"))
        }.exceptionOrNull()

        assertNotNull(failure)
        assertTrue(failure.message.orEmpty().contains("資訊雜湊格式錯誤"))
    }

    @Test
    fun controllerSelectsTheRequestedEpisodeAndPublishesAReadyStream() {
        val controller = TorrentEngineController()
        val generation = controller.start(
            TorrentStartRequest(
                magnet = "magnet:?xt=urn:btih:0000000000000000000000000000000000000001",
                episodeNumber = 10,
                title = "測試動畫",
                subtitle = "第 10 集",
                quality = "1080p",
            ),
        )

        assertEquals(TorrentTransferPhase.Metadata, controller.snapshot().phase)
        val selection = controller.accept(
            TorrentEngineEvent.Metadata(
                generation,
                listOf(
                    TorrentFileCandidate(0, "Show - 01.mkv", 800_000_000),
                    TorrentFileCandidate(1, "Show - 10.mkv", 900_000_000),
                ),
            ),
        )
        assertEquals(1, assertNotNull(selection).selectedFile.index)
        assertEquals(TorrentTransferPhase.Downloading, controller.snapshot().phase)

        controller.accept(
            TorrentEngineEvent.Progress(
                generation = generation,
                selectedBytes = 64L * 1024 * 1024,
                selectedSize = 900_000_000,
                totalBytes = 70L * 1024 * 1024,
                downloadRateBytesPerSecond = 4L * 1024 * 1024,
                peers = 12,
                seeds = 3,
                completedPieces = 220,
                totalPieces = 2_000,
                etaSeconds = 180,
            ),
        )
        assertEquals(TorrentTransferPhase.Buffering, controller.snapshot().phase)

        controller.accept(TorrentEngineEvent.Ready(generation, "http://127.0.0.1:43123/stream", "Show - 10.mkv"))
        val playable = assertNotNull(controller.consumeReadyStream(generation))
        assertEquals("http://127.0.0.1:43123/stream", playable.url)
        assertEquals("1080p", playable.quality)
        assertNull(controller.consumeReadyStream(generation))
    }

    @Test
    fun staleEventsAndLateReadyAfterBackCannotStartPlayback() {
        val controller = TorrentEngineController()
        val first = controller.start(TorrentStartRequest("magnet:?xt=urn:btih:1111111111111111111111111111111111111111", 1, "First", "EP1"))
        val second = controller.start(TorrentStartRequest("magnet:?xt=urn:btih:2222222222222222222222222222222222222222", 2, "Second", "EP2"))

        controller.accept(TorrentEngineEvent.Failed(first, "old failure"))
        assertEquals(second, controller.snapshot().generation)
        assertEquals(TorrentTransferPhase.Metadata, controller.snapshot().phase)

        controller.accept(
            TorrentEngineEvent.Metadata(
                second,
                listOf(TorrentFileCandidate(2, "Second E02.mkv", 700_000_000)),
            ),
        )
        controller.cancelAutoplay(second)
        controller.accept(TorrentEngineEvent.Ready(second, "http://127.0.0.1:43123/stream", "Second E02.mkv"))

        assertNull(controller.consumeReadyStream(second))
        assertEquals(TorrentTransferPhase.Background, controller.snapshot().phase)
    }

    @Test
    fun cancellingBeforeMetadataKeepsTheLateSelectionInBackground() {
        val controller = TorrentEngineController()
        val generation = controller.start(TorrentStartRequest("magnet:?xt=urn:btih:3333333333333333333333333333333333333333", 2, "Late", "EP2"))
        controller.cancelAutoplay(generation)

        val selection = controller.accept(
            TorrentEngineEvent.Metadata(
                generation,
                listOf(TorrentFileCandidate(0, "Late E02.mkv", 700_000_000)),
            ),
        )

        assertNotNull(selection)
        assertEquals(TorrentTransferPhase.Background, controller.snapshot().phase)
    }

    @Test
    fun missingPlayableFilesAndBackendFailuresKeepTheirRealReason() {
        val controller = TorrentEngineController()
        val generation = controller.start(TorrentStartRequest("magnet:?xt=urn:btih:4444444444444444444444444444444444444444", 1, "Bad", "EP1"))

        controller.accept(
            TorrentEngineEvent.Metadata(
                generation,
                listOf(TorrentFileCandidate(0, "poster.jpg", 1_000_000)),
            ),
        )
        assertEquals(TorrentTransferPhase.Failed, controller.snapshot().phase)
        assertTrue(controller.snapshot().error.orEmpty().contains("可播放影片"))

        val retry = controller.start(TorrentStartRequest("magnet:?xt=urn:btih:5555555555555555555555555555555555555555", 1, "Bad", "EP1"))
        controller.accept(TorrentEngineEvent.Failed(retry, "磁力連結格式錯誤"))
        assertEquals("磁力連結格式錯誤", controller.snapshot().error)
    }
}

package dev.tvshell.shared

import com.sun.jna.Pointer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopLibVLCLifecycleTest {
    @Test
    fun snapshotNeverTouchesNativePointersAfterClose() {
        val api = FakeLibVLCNative()
        val runtime = DesktopLibVLCRuntime(api, Pointer(1), Pointer(2))

        runtime.close()
        val readsBefore = api.nativeReads.get()
        val snapshot = runtime.snapshot()

        assertEquals(readsBefore, api.nativeReads.get())
        assertTrue(snapshot.error.orEmpty().contains("關閉"))
        assertEquals(1, api.playerReleaseCount.get())
    }

    @Test
    fun closeWaitsForAnInFlightSnapshotBeforeReleasingNativePointers() {
        val api = FakeLibVLCNative(blockSnapshot = true)
        val runtime = DesktopLibVLCRuntime(api, Pointer(1), Pointer(2))
        val executor = Executors.newFixedThreadPool(2)

        try {
            val snapshot = executor.submit { runtime.snapshot() }
            assertTrue(api.snapshotEntered.await(1, TimeUnit.SECONDS))
            val close = executor.submit { runtime.close() }

            assertFalse(api.playerReleased.await(100, TimeUnit.MILLISECONDS))
            api.allowSnapshot.countDown()
            snapshot.get(1, TimeUnit.SECONDS)
            close.get(1, TimeUnit.SECONDS)
            assertTrue(api.playerReleased.await(1, TimeUnit.SECONDS))
        } finally {
            api.allowSnapshot.countDown()
            executor.shutdownNow()
        }
    }
}

private class FakeLibVLCNative(
    private val blockSnapshot: Boolean = false,
) : LibVLCNative {
    val nativeReads = AtomicInteger(0)
    val playerReleaseCount = AtomicInteger(0)
    val snapshotEntered = CountDownLatch(1)
    val allowSnapshot = CountDownLatch(1)
    val playerReleased = CountDownLatch(1)

    override fun libvlc_get_version(): String = "3.0.23"
    override fun libvlc_new(argc: Int, argv: Array<String>): Pointer = Pointer(1)
    override fun libvlc_release(instance: Pointer) = Unit
    override fun libvlc_media_new_location(instance: Pointer, mrl: String): Pointer = Pointer(3)
    override fun libvlc_media_add_option(media: Pointer, option: String) = Unit
    override fun libvlc_media_release(media: Pointer) = Unit
    override fun libvlc_media_player_new(instance: Pointer): Pointer = Pointer(2)
    override fun libvlc_media_player_release(mediaPlayer: Pointer) {
        playerReleaseCount.incrementAndGet()
        playerReleased.countDown()
    }
    override fun libvlc_media_player_set_hwnd(mediaPlayer: Pointer, drawable: Pointer) = Unit
    override fun libvlc_media_player_set_media(mediaPlayer: Pointer, media: Pointer) = Unit
    override fun libvlc_media_player_play(mediaPlayer: Pointer): Int = 0
    override fun libvlc_media_player_set_pause(mediaPlayer: Pointer, pause: Int) = Unit
    override fun libvlc_media_player_is_playing(mediaPlayer: Pointer): Int {
        nativeReads.incrementAndGet()
        return 1
    }
    override fun libvlc_media_player_get_time(mediaPlayer: Pointer): Long {
        nativeReads.incrementAndGet()
        return 1_000
    }
    override fun libvlc_media_player_get_length(mediaPlayer: Pointer): Long {
        nativeReads.incrementAndGet()
        return 10_000
    }
    override fun libvlc_media_player_get_state(mediaPlayer: Pointer): Int {
        nativeReads.incrementAndGet()
        snapshotEntered.countDown()
        if (blockSnapshot) allowSnapshot.await(2, TimeUnit.SECONDS)
        return 3
    }
    override fun libvlc_media_player_set_time(mediaPlayer: Pointer, time: Long): Int = 0
    override fun libvlc_audio_get_volume(mediaPlayer: Pointer): Int = 100
    override fun libvlc_audio_set_volume(mediaPlayer: Pointer, volume: Int): Int = 0
    override fun libvlc_audio_get_mute(mediaPlayer: Pointer): Int = 0
    override fun libvlc_audio_set_mute(mediaPlayer: Pointer, muted: Int) = Unit
    override fun libvlc_media_player_stop(mediaPlayer: Pointer) = Unit
}

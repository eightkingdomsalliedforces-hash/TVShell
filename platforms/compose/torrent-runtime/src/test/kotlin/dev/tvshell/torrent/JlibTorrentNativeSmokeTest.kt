package dev.tvshell.torrent

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class JlibTorrentNativeSmokeTest {
    @Test
    fun pinnedNativeLibraryLoadsOnTheHostArchitecture() {
        val root = File(System.getProperty("java.io.tmpdir"), "tvshell-torrent-smoke-${System.nanoTime()}")
        try {
            val runtime = JvmTorrentPlaybackRuntime(root, listener = {})
            assertTrue(runtime.nativeVersion().isNotBlank())
            runtime.close()
        } finally {
            root.deleteRecursively()
        }
    }
}

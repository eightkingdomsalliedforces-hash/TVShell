package dev.tvshell.shared

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class DesktopLibVLCRuntimeSmokeTest {
    @Test
    fun bundledWindowsRuntimeLoadsThroughJnaWhenCiProvidesIt() {
        val directory = System.getenv("TVSHELL_VLC_DIR")?.takeIf(String::isNotBlank)?.let(::File) ?: return
        val version = DesktopLibVLCRuntime.probeVersion(directory)
        assertTrue(version.startsWith("3.0.23"), "unexpected bundled LibVLC version: $version")
        DesktopLibVLCRuntime.open(directory).use { runtime ->
            assertTrue(runtime.toString().isNotBlank())
        }
    }
}

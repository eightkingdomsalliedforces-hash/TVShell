package dev.tvshell.shared

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DesktopLibVLCLocatorTest {
    @Test
    fun findsBundledWindowsLibVLCBeforeAComputerWideInstallation() {
        val root = createTempDirectory("tvshell-vlc").toFile()
        val resources = File(root, "resources").apply { mkdirs() }
        val bundled = File(resources, "vlc").apply(::makeWindowsRuntime)
        val installed = File(root, "Program Files/VideoLAN/VLC").apply(::makeWindowsRuntime)

        val result = DesktopLibVLCLocator.locate(
            osName = "Windows 11",
            explicitDirectory = null,
            resourcesDirectory = resources,
            executableDirectory = File(root, "app"),
            programFilesDirectory = File(root, "Program Files"),
        )

        assertEquals(bundled.canonicalFile, result?.canonicalFile)
        root.deleteRecursively()
    }

    @Test
    fun rejectsAnIncompleteRuntimeAndNonWindowsHosts() {
        val root = createTempDirectory("tvshell-vlc-incomplete").toFile()
        val explicit = File(root, "vlc").apply { mkdirs() }
        File(explicit, "libvlc.dll").writeText("stub")

        assertNull(DesktopLibVLCLocator.locate("Windows 11", explicit, null, null, null))
        makeWindowsRuntime(explicit)
        assertNull(DesktopLibVLCLocator.locate("macOS", explicit, null, null, null))
        root.deleteRecursively()
    }

    private fun makeWindowsRuntime(directory: File) {
        directory.mkdirs()
        File(directory, "libvlc.dll").writeText("stub")
        File(directory, "libvlccore.dll").writeText("stub")
        File(directory, "plugins").mkdirs()
    }
}

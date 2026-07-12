package dev.tvshell.shared

import kotlin.test.Test
import kotlin.test.assertEquals

class LauncherStateTest {
    private val apps = listOf(ShellApp("a", "A"), ShellApp("b", "B"), ShellApp("c", "C"))

    @Test
    fun focusMovesAndClampsInVisualOrder() {
        var state = LauncherState(apps)
        state = state.reduce(RemoteCommand.Left)
        assertEquals(0, state.focusedIndex)
        state = state.reduce(RemoteCommand.Right).reduce(RemoteCommand.Right).reduce(RemoteCommand.Right)
        assertEquals(2, state.focusedIndex)
        state = state.reduce(RemoteCommand.Left)
        assertEquals("B", state.focusedApp?.name)
    }

    @Test
    fun designTokensMatchCanonicalMacLayout() {
        assertEquals(222f, TVShellDesign.AppTileWidth)
        assertEquals(1.55f, TVShellDesign.AppTileAspectRatio)
        assertEquals(86f, TVShellDesign.HorizontalPadding)
    }
}

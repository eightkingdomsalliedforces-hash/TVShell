package dev.tvshell.shared

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

enum class WebRuntimeCommand {
    None,
    ScrollUp,
    ScrollDown,
    ScrollLeft,
    ScrollRight,
    Select,
    Back,
    PlayPause,
    Rewind,
    FastForward,
    VolumeUp,
    VolumeDown,
    Mute,
}

data class WebRuntimeSignal(
    val command: WebRuntimeCommand = WebRuntimeCommand.None,
    val sequence: Long = 0,
)

data class WebRuntimeState(
    val url: String,
    val signal: WebRuntimeSignal = WebRuntimeSignal(),
    val pendingAction: String? = null,
) {
    fun reduce(command: RemoteCommand): WebRuntimeState = when (command) {
        RemoteCommand.Home -> copy(pendingAction = "exit")
        RemoteCommand.Up -> signaled(WebRuntimeCommand.ScrollUp)
        RemoteCommand.Down -> signaled(WebRuntimeCommand.ScrollDown)
        RemoteCommand.Left -> signaled(WebRuntimeCommand.ScrollLeft)
        RemoteCommand.Right -> signaled(WebRuntimeCommand.ScrollRight)
        RemoteCommand.Select -> signaled(WebRuntimeCommand.Select)
        RemoteCommand.Back -> signaled(WebRuntimeCommand.Back)
        RemoteCommand.PlayPause -> signaled(WebRuntimeCommand.PlayPause)
        RemoteCommand.Rewind -> signaled(WebRuntimeCommand.Rewind)
        RemoteCommand.FastForward -> signaled(WebRuntimeCommand.FastForward)
        RemoteCommand.VolumeUp -> signaled(WebRuntimeCommand.VolumeUp)
        RemoteCommand.VolumeDown -> signaled(WebRuntimeCommand.VolumeDown)
        RemoteCommand.Mute -> signaled(WebRuntimeCommand.Mute)
        else -> this
    }

    fun clearAction(): WebRuntimeState = copy(pendingAction = null)

    private fun signaled(command: WebRuntimeCommand): WebRuntimeState =
        copy(signal = WebRuntimeSignal(command, signal.sequence + 1), pendingAction = null)
}

@Composable
expect fun PlatformWebSurface(
    url: String,
    signal: WebRuntimeSignal,
    onExitRequested: () -> Unit,
    modifier: Modifier = Modifier,
)

package dev.tvshell.shared

class RemoteCommandDispatcher {
    private var nextID = 0
    private val listeners = linkedMapOf<Int, (RemoteCommand) -> Unit>()

    fun subscribe(listener: (RemoteCommand) -> Unit): () -> Unit {
        val id = nextID++
        listeners[id] = listener
        return { listeners.remove(id) }
    }

    fun dispatch(command: RemoteCommand) {
        listeners.values.toList().forEach { it(command) }
    }
}

object AndroidTVKeyMapper {
    fun command(keyCode: Int, isLongPress: Boolean): RemoteCommand? {
        if (keyCode == 4 && isLongPress) return RemoteCommand.Home
        return when (keyCode) {
            19 -> RemoteCommand.Up
            20 -> RemoteCommand.Down
            21 -> RemoteCommand.Left
            22 -> RemoteCommand.Right
            23, 66, 96, 160 -> RemoteCommand.Select
            4, 111 -> RemoteCommand.Back
            3 -> RemoteCommand.Home
            82 -> RemoteCommand.Menu
            85, 62, 126, 127 -> RemoteCommand.PlayPause
            89 -> RemoteCommand.Rewind
            90 -> RemoteCommand.FastForward
            24 -> RemoteCommand.VolumeUp
            25 -> RemoteCommand.VolumeDown
            164 -> RemoteCommand.Mute
            else -> null
        }
    }
}

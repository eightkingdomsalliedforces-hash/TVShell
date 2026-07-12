package dev.tvshell.shared

object TVShellDesign {
    const val ReferenceWidth = 1920f
    const val ReferenceHeight = 1080f
    const val HorizontalPadding = 86f
    const val TopPadding = 48f
    const val CardSpacing = 42f
    const val AppTileWidth = 222f
    const val AppTileAspectRatio = 1.55f
    const val AppTitleSize = 34f
    const val FocusScale = 1.06f
    const val FocusLift = 10f
    const val FocusAnimationMilliseconds = 180
}

enum class RemoteCommand {
    Up, Down, Left, Right, Select, Back, Home, Menu,
    PlayPause, Rewind, FastForward, VolumeUp, VolumeDown, Mute
}

data class ShellApp(
    val id: String,
    val name: String,
    val subtitle: String = "App",
    val packageName: String? = null,
    val executable: String? = null,
    val isSystemSettings: Boolean = false,
)

interface PlatformAdapter {
    fun installedApps(): List<ShellApp>
    fun launch(app: ShellApp): Result<Unit>
    fun openSystemSettings(): Result<Unit>
}

data class LauncherState(
    val apps: List<ShellApp>,
    val focusedIndex: Int = 0,
    val status: String = "方向鍵選擇 App，OK 開啟，Menu 進入控制中心。",
) {
    val focusedApp: ShellApp? get() = apps.getOrNull(focusedIndex)

    fun reduce(command: RemoteCommand): LauncherState = when (command) {
        RemoteCommand.Left -> copy(focusedIndex = (focusedIndex - 1).coerceAtLeast(0))
        RemoteCommand.Right -> copy(focusedIndex = (focusedIndex + 1).coerceAtMost((apps.size - 1).coerceAtLeast(0)))
        else -> this
    }
}

data class AnimeState(
    val tabs: List<String> = listOf("推薦", "正版來源", "我的訂閱", "觀看記錄", "搜尋"),
    val focusedTab: Int = 0,
    val focusedCard: Int = 0,
    val isTopNavigationFocused: Boolean = true,
) {
    fun reduce(command: RemoteCommand): AnimeState = when {
        isTopNavigationFocused && command == RemoteCommand.Left -> copy(focusedTab = (focusedTab - 1).coerceAtLeast(0))
        isTopNavigationFocused && command == RemoteCommand.Right -> copy(focusedTab = (focusedTab + 1).coerceAtMost(tabs.lastIndex))
        isTopNavigationFocused && command == RemoteCommand.Down -> copy(isTopNavigationFocused = false)
        isTopNavigationFocused.not() && command == RemoteCommand.Up -> copy(isTopNavigationFocused = true)
        isTopNavigationFocused.not() && command == RemoteCommand.Left -> copy(focusedCard = (focusedCard - 1).coerceAtLeast(0))
        isTopNavigationFocused.not() && command == RemoteCommand.Right -> copy(focusedCard = (focusedCard + 1).coerceAtMost(7))
        else -> this
    }
}

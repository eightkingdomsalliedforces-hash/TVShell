package dev.tvshell.anime.desktop

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import dev.tvshell.shared.PlatformAdapter
import dev.tvshell.shared.ShellApp
import dev.tvshell.shared.TVShellApp

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "TVShell 動畫", undecorated = true) {
        TVShellApp(AnimeDesktopAdapter, animeOnly = true)
    }
}

private object AnimeDesktopAdapter : PlatformAdapter {
    override fun installedApps(): List<ShellApp> = emptyList()
    override fun launch(app: ShellApp): Result<Unit> = Result.failure(IllegalStateException("請先在動畫 App 內設定來源"))
    override fun openSystemSettings(): Result<Unit> = Result.success(Unit)
}

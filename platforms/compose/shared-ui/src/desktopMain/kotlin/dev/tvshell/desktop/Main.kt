package dev.tvshell.desktop

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import dev.tvshell.shared.PlatformAdapter
import dev.tvshell.shared.ShellApp
import dev.tvshell.shared.TVShellApp
import java.io.File

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "TVShell",
        undecorated = true,
    ) {
        TVShellApp(WindowsPlatformAdapter())
    }
}

private class WindowsPlatformAdapter : PlatformAdapter {
    override fun installedApps(): List<ShellApp> {
        val roots = listOfNotNull(
            System.getenv("ProgramData")?.let { File(it, "Microsoft/Windows/Start Menu/Programs") },
            System.getenv("APPDATA")?.let { File(it, "Microsoft/Windows/Start Menu/Programs") },
        )
        return roots.asSequence()
            .filter(File::isDirectory)
            .flatMap { it.walkTopDown().asSequence() }
            .filter { it.isFile && (it.extension.equals("lnk", true) || it.extension.equals("exe", true)) }
            .distinctBy { it.absolutePath.lowercase() }
            .take(80)
            .map { ShellApp("windows:${it.absolutePath}", it.nameWithoutExtension, "Windows App", executable = it.absolutePath) }
            .toList()
    }

    override fun launch(app: ShellApp): Result<Unit> = runCatching {
        val executable = requireNotNull(app.executable) { "這是 TVShell 內建 App，尚未接上此平台服務。" }
        ProcessBuilder("cmd", "/c", "start", "", executable).start()
    }

    override fun openSystemSettings(): Result<Unit> = runCatching {
        ProcessBuilder("cmd", "/c", "start", "", "ms-settings:").start()
    }
}

package dev.tvshell.desktop

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.window.application
import dev.tvshell.shared.PlatformAdapter
import dev.tvshell.shared.NativeMediaCard
import dev.tvshell.shared.NativeMediaParser
import dev.tvshell.shared.NativeMediaService
import dev.tvshell.shared.ShellApp
import dev.tvshell.shared.TVShellApp
import java.io.File
import java.net.HttpURLConnection
import java.net.URI

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "TVShell",
        undecorated = true,
        state = rememberWindowState(placement = WindowPlacement.Fullscreen),
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

    override fun fetchMediaFeed(service: NativeMediaService): Result<List<NativeMediaCard>> = runCatching {
        val url = when (service) {
            NativeMediaService.YouTube -> "https://www.youtube.com/results?search_query=%E5%8B%95%E7%95%AB&hl=zh-TW&gl=TW"
            NativeMediaService.Bilibili -> "https://api.bilibili.com/x/web-interface/popular?ps=30&pn=1"
        }
        val body = fetchText(url)
        when (service) {
            NativeMediaService.YouTube -> NativeMediaParser.youtube(body)
            NativeMediaService.Bilibili -> NativeMediaParser.bilibili(body)
        }.ifEmpty { error("服務沒有回傳可顯示的影片") }
    }

    override fun playMedia(card: NativeMediaCard): Result<Unit> = runCatching {
        ProcessBuilder("cmd", "/c", "start", "", card.playbackURL).start()
    }

    private fun fetchText(url: String): String {
        val connection = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            requestMethod = "GET"
            connectTimeout = 8_000
            readTimeout = 8_000
            setRequestProperty("User-Agent", "Mozilla/5.0 TVShell/1.0")
            setRequestProperty("Accept-Language", "zh-TW,zh;q=0.9,en;q=0.7")
        }
        return try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            require(status in 200..299 && stream != null) { "HTTP $status" }
            stream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}

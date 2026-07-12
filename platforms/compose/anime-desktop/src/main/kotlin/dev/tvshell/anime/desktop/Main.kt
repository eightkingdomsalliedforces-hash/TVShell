package dev.tvshell.anime.desktop

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
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.system.exitProcess

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "TVShell 動畫",
        undecorated = true,
        state = rememberWindowState(placement = WindowPlacement.Fullscreen),
    ) {
        TVShellApp(AnimeDesktopAdapter, animeOnly = true)
    }
}

private object AnimeDesktopAdapter : PlatformAdapter {
    private val client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()
    override fun installedApps(): List<ShellApp> = emptyList()
    override fun launch(app: ShellApp): Result<Unit> = Result.failure(IllegalStateException("請先在動畫 App 內設定來源"))
    override fun openSystemSettings(): Result<Unit> = Result.success(Unit)
    override fun fetchMediaFeed(service: NativeMediaService): Result<List<NativeMediaCard>> = runCatching {
        val endpoint = when (service) {
            NativeMediaService.YouTube -> "https://www.youtube.com/results?search_query=%E5%AE%98%E6%96%B9%E5%8B%95%E7%95%AB&hl=zh-TW&gl=TW"
            NativeMediaService.Bilibili -> "https://api.bilibili.com/x/web-interface/search/type?search_type=video&keyword=%E5%8B%95%E7%95%AB&page=1"
        }
        val request = HttpRequest.newBuilder(URI(endpoint)).header("User-Agent", "Mozilla/5.0 TVShell/1.0").GET().build()
        val body = client.send(request, HttpResponse.BodyHandlers.ofString()).body()
        when (service) {
            NativeMediaService.YouTube -> NativeMediaParser.youtube(body)
            NativeMediaService.Bilibili -> NativeMediaParser.bilibili(body)
        }.ifEmpty { error("來源沒有回傳可播放內容") }
    }
    override fun playMedia(card: NativeMediaCard): Result<Unit> = runCatching {
        ProcessBuilder("cmd", "/c", "start", "", card.playbackURL).start()
    }
    override fun exitApp(): Result<Unit> = runCatching { exitProcess(0) }
}

package dev.tvshell.anime.android

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.net.Uri
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dev.tvshell.shared.PlatformAdapter
import dev.tvshell.shared.AndroidTVKeyMapper
import dev.tvshell.shared.RemoteCommandDispatcher
import dev.tvshell.shared.ShellApp
import dev.tvshell.shared.NativeMediaCard
import dev.tvshell.shared.NativeMediaParser
import dev.tvshell.shared.NativeMediaService
import dev.tvshell.shared.TVShellApp
import java.net.HttpURLConnection
import java.net.URL

class AnimeActivity : ComponentActivity() {
    private val remoteDispatcher = RemoteCommandDispatcher()
    private var longBackDispatched = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TVShellApp(AnimePlatformAdapter(this), animeOnly = true, dispatcher = remoteDispatcher) }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount > 0 && event.isLongPress) {
                longBackDispatched = true
                remoteDispatcher.dispatch(dev.tvshell.shared.RemoteCommand.Home)
                return true
            }
            if (event.action == KeyEvent.ACTION_DOWN) return true
            if (event.action == KeyEvent.ACTION_UP) {
                if (longBackDispatched) longBackDispatched = false
                else remoteDispatcher.dispatch(dev.tvshell.shared.RemoteCommand.Back)
                return true
            }
        }
        if (event.action == KeyEvent.ACTION_DOWN) {
            AndroidTVKeyMapper.command(event.keyCode, event.isLongPress)?.let {
                remoteDispatcher.dispatch(it)
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }
}

private class AnimePlatformAdapter(private val activity: ComponentActivity) : PlatformAdapter {
    override fun installedApps(): List<ShellApp> = emptyList()
    override fun launch(app: ShellApp): Result<Unit> = Result.failure(IllegalStateException("請先在動畫 App 內設定來源"))
    override fun openSystemSettings(): Result<Unit> = runCatching {
        activity.startActivity(Intent(Settings.ACTION_SETTINGS))
    }
    override fun fetchMediaFeed(service: NativeMediaService): Result<List<NativeMediaCard>> = runCatching {
        val endpoint = when (service) {
            NativeMediaService.YouTube -> "https://www.youtube.com/results?search_query=%E5%AE%98%E6%96%B9%E5%8B%95%E7%95%AB&hl=zh-TW&gl=TW"
            NativeMediaService.Bilibili -> "https://api.bilibili.com/x/web-interface/search/type?search_type=video&keyword=%E5%8B%95%E7%95%AB&page=1"
        }
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        connection.connectTimeout = 8_000
        connection.readTimeout = 8_000
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 TVShell/1.0")
        val body = connection.inputStream.bufferedReader().use { it.readText() }
        when (service) {
            NativeMediaService.YouTube -> NativeMediaParser.youtube(body)
            NativeMediaService.Bilibili -> NativeMediaParser.bilibili(body)
        }.ifEmpty { error("來源沒有回傳可播放內容") }
    }
    override fun playMedia(card: NativeMediaCard): Result<Unit> = runCatching {
        activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(card.playbackURL)))
    }
    override fun exitApp(): Result<Unit> = runCatching { activity.finish() }
}

package dev.tvshell.anime.android

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.net.Uri
import android.view.KeyEvent
import android.content.Context
import android.media.AudioManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import dev.tvshell.shared.PlatformAdapter
import dev.tvshell.shared.AndroidTVKeyMapper
import dev.tvshell.shared.RemoteCommandDispatcher
import dev.tvshell.shared.ShellApp
import dev.tvshell.shared.NativeMediaCard
import dev.tvshell.shared.NativeMediaParser
import dev.tvshell.shared.NativeMediaService
import dev.tvshell.shared.TVShellApp
import dev.tvshell.shared.BingWallpaperMetadata
import dev.tvshell.shared.AnimeSourceKind
import dev.tvshell.shared.anime.AndroidMediaPlayerAdapter
import dev.tvshell.shared.anime.AnimeEpisode
import dev.tvshell.shared.anime.AnimeStreamCandidate
import dev.tvshell.shared.anime.BilibiliAnimeParser
import dev.tvshell.shared.anime.CSS1HtmlParser
import dev.tvshell.shared.anime.CSS1Resolver
import dev.tvshell.shared.anime.PlatformCSS1ContentClient
import dev.tvshell.shared.anime.DandanplayService
import dev.tvshell.shared.anime.DanmakuComment
import dev.tvshell.shared.anime.ServiceCredentials
import dev.tvshell.shared.anime.ServiceCredentialsParser
import dev.tvshell.shared.anime.platformSHA256Base64
import java.net.HttpURLConnection
import java.net.URL
import java.io.File
import kotlinx.coroutines.runBlocking

class AnimeActivity : ComponentActivity() {
    private val remoteDispatcher = RemoteCommandDispatcher()
    private var longBackDispatched = false
    private lateinit var platformAdapter: AnimePlatformAdapter
    private val credentialsPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && ::platformAdapter.isInitialized) platformAdapter.importCredentials(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        platformAdapter = AnimePlatformAdapter(this) {
            credentialsPicker.launch(arrayOf("application/json", "text/plain", "text/*"))
        }
        setContent { TVShellApp(platformAdapter, animeOnly = true, dispatcher = remoteDispatcher) }
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

private class AnimePlatformAdapter(
    private val activity: ComponentActivity,
    private val chooseCredentials: () -> Unit,
) : PlatformAdapter {
    private val animePlayer = AndroidMediaPlayerAdapter(activity)
    private val css1Resolver = CSS1Resolver(PlatformCSS1ContentClient())
    private val dandanplay = DandanplayService(PlatformCSS1ContentClient(), ::platformSHA256Base64)
    override fun installedApps(): List<ShellApp> = emptyList()
    override fun launch(app: ShellApp): Result<Unit> = Result.failure(IllegalStateException("請先在動畫 App 內設定來源"))
    override fun openSystemSettings(): Result<Unit> = runCatching {
        activity.startActivity(Intent(Settings.ACTION_SETTINGS))
    }
    override fun openCredentialsImporter(): Result<Unit> = runCatching { chooseCredentials() }
    override fun fetchMediaFeed(service: NativeMediaService): Result<List<NativeMediaCard>> = runCatching {
        val endpoint = when (service) {
            NativeMediaService.YouTube -> "https://www.youtube.com/results?search_query=%E5%AE%98%E6%96%B9%E5%8B%95%E7%95%AB&hl=zh-TW&gl=TW"
            NativeMediaService.Bilibili -> "https://api.bilibili.com/pgc/web/rank/list?season_type=1&day=3"
        }
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        connection.connectTimeout = 8_000
        connection.readTimeout = 8_000
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android TV) AppleWebKit/537.36 Chrome/125 Safari/537.36")
        connection.setRequestProperty("Accept", "application/json,text/plain,*/*")
        connection.setRequestProperty("Accept-Language", "zh-TW,zh;q=0.9,en;q=0.7")
        if (endpoint.contains("bilibili.com")) {
            connection.setRequestProperty("Referer", "https://search.bilibili.com/")
        }
        val body = connection.inputStream.bufferedReader().use { it.readText() }
        when (service) {
            NativeMediaService.YouTube -> NativeMediaParser.youtube(body)
            NativeMediaService.Bilibili -> NativeMediaParser.bilibiliBangumi(body)
        }.ifEmpty { error("來源沒有回傳可播放內容") }
    }
    override fun fetchAnimeFeed(source: AnimeSourceKind): Result<List<NativeMediaCard>> =
        (if (source == AnimeSourceKind.CSS1) fetchMediaFeed(NativeMediaService.Bilibili)
        else super<PlatformAdapter>.fetchAnimeFeed(source))
            .map { cards -> cards.map { it.copy(animeSource = source) } }
    override fun fetchAnimeEpisodes(source: AnimeSourceKind, card: NativeMediaCard): Result<List<AnimeEpisode>> = when (source) {
        AnimeSourceKind.Bilibili -> runCatching {
            val seasonID = card.id.substringAfter("bilibili-season-", "").takeIf(String::isNotBlank)
                ?: card.playbackURL.substringAfter("/ss", "").substringBefore('?').takeIf(String::isNotBlank)
                ?: error("缺少 Bilibili season_id")
            BilibiliAnimeParser.episodes(fetchText("https://api.bilibili.com/pgc/web/season/section?season_id=$seasonID"))
                .ifEmpty { error("Bilibili 沒有回傳選集") }
        }
        AnimeSourceKind.CSS1 -> runCatching { runBlocking {
            css1Resolver.episodes(card.title).ifEmpty { error("CSS1 搜不到：${card.title}") }
        } }
        else -> super<PlatformAdapter>.fetchAnimeEpisodes(source, card)
    }
    override fun resolveAnimeStreams(source: AnimeSourceKind, episode: AnimeEpisode): Result<List<AnimeStreamCandidate>> = when (source) {
        AnimeSourceKind.Bilibili -> runCatching {
            val fields = episode.id.split(':')
            require(fields.size >= 3) { "Bilibili 選集識別格式錯誤" }
            val payload = fetchText("https://api.bilibili.com/pgc/player/web/playurl?ep_id=${fields[1]}&cid=${fields[2]}&qn=80&fnver=0&fnval=0&fourk=0")
            BilibiliAnimeParser.failureReason(payload)?.let(::error)
            BilibiliAnimeParser.streams(payload).ifEmpty { error("Bilibili 沒有可用播放網址，可能需要登入或會員權限") }
        }
        AnimeSourceKind.CSS1 -> runCatching { runBlocking {
            css1Resolver.streams(episode).ifEmpty { error("CSS1 選集解析失敗：沒有可用播放源") }
        } }
        else -> super<PlatformAdapter>.resolveAnimeStreams(source, episode)
    }
    override fun loadAnimeStream(candidate: AnimeStreamCandidate): Result<Unit> = runCatching {
        if (candidate.headers["resolver"] == "official") {
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(candidate.url)))
        } else {
            require(candidate.url.startsWith("magnet:").not()) { "Android TV BT 邊下邊播仍需完成 torrent 引擎連接" }
            animePlayer.load(candidate)
            animePlayer.play()
        }
    }
    override fun playAnime(): Result<Unit> = runCatching { animePlayer.play() }
    override fun pauseAnime(): Result<Unit> = runCatching { animePlayer.pause() }
    override fun seekAnimeBy(seconds: Int): Result<Unit> = runCatching { animePlayer.seekBy(seconds) }
    override fun adjustAnimeVolume(direction: Int): Result<Unit> = runCatching {
        val manager = activity.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        manager.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            if (direction >= 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
            AudioManager.FLAG_SHOW_UI,
        )
    }
    override fun stopAnime(): Result<Unit> = runCatching { animePlayer.release() }
    override fun fetchAnimeDanmaku(
        source: AnimeSourceKind,
        card: NativeMediaCard,
        episode: AnimeEpisode,
    ): Result<List<DanmakuComment>> = runCatching { runBlocking {
        if (source == AnimeSourceKind.Bilibili) {
            val cid = episode.id.split(':').getOrNull(2) ?: error("Bilibili 選集缺少 cid，無法讀取彈幕")
            BilibiliAnimeParser.danmaku(fetchText("https://api.bilibili.com/x/v1/dm/list.so?oid=$cid"))
        } else {
            dandanplay.comments(card.title, episode.number, loadCredentials().dandanplay, (System.currentTimeMillis() / 1_000).toInt())
        }
    } }
    override fun playMedia(card: NativeMediaCard): Result<Unit> = runCatching {
        activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(card.playbackURL)))
    }
    override fun exitApp(): Result<Unit> = runCatching { activity.finish() }
    override fun fetchWallpaperURL(): Result<String> = runCatching {
        val connection = URL("https://www.bing.com/HPImageArchive.aspx?format=js&idx=0&n=1&mkt=zh-TW").openConnection() as HttpURLConnection
        connection.connectTimeout = 8_000
        connection.readTimeout = 8_000
        val body = try { connection.inputStream.bufferedReader().use { it.readText() } } finally { connection.disconnect() }
        BingWallpaperMetadata.imageURL(body) ?: error("Bing 沒有回傳圖片")
    }

    private fun fetchText(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android TV) AppleWebKit/537.36 Chrome/125 Safari/537.36")
            connection.setRequestProperty("Accept", "application/json,text/html,*/*")
            connection.setRequestProperty("Accept-Language", "zh-TW,zh;q=0.9,en;q=0.7")
            if (url.contains("bilibili.com")) connection.setRequestProperty("Referer", "https://www.bilibili.com/")
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            require(status in 200..299 && stream != null) { "HTTP $status" }
            stream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun loadCredentials(): ServiceCredentials {
        val candidates = listOfNotNull(
            File(activity.filesDir, "credentials.json"),
            activity.getExternalFilesDir(null)?.let { File(it, "credentials.json") },
        )
        return candidates.firstOrNull(File::isFile)?.let {
            runCatching { ServiceCredentialsParser.decode(it.readText()) }.getOrNull()
        } ?: ServiceCredentials()
    }

    fun importCredentials(uri: Uri) {
        runCatching {
            val destination = File(activity.filesDir, "credentials.json")
            activity.contentResolver.openInputStream(uri)?.use { input ->
                destination.outputStream().use(input::copyTo)
            } ?: error("無法讀取選擇的憑證檔案")
            val parsed = ServiceCredentialsParser.decode(destination.readText())
            require(parsed.bilibiliCookie.isNotBlank() || parsed.dandanplay.isConfigured) {
                "檔案中找不到 Bilibili Cookie 或 Dandanplay 憑證"
            }
        }.onFailure { it.printStackTrace() }
    }
}

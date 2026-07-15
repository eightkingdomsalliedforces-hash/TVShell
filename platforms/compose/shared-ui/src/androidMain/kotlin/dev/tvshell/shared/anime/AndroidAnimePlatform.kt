package dev.tvshell.shared.anime

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.view.Surface
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class AndroidAnimeHTTPTransport : AnimeHTTPTransport {
    override suspend fun get(url: String, headers: Map<String, String>): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.instanceFollowRedirects = true
            headers.forEach(connection::setRequestProperty)
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}

class AndroidMediaPlayerAdapter(private val context: Context) : AnimePlayerAdapter {
    private val lock = Any()
    private var player: MediaPlayer? = null
    private var isPrepared = false
    private var playWhenPrepared = false
    private var surface: Surface? = null
    private var playbackError: String? = null
    private var generation = 0L

    init {
        AndroidAnimePlaybackRegistry.player = this
    }

    override fun load(candidate: AnimeStreamCandidate) {
        val next = MediaPlayer()
        val old: MediaPlayer?
        val token: Long
        val currentSurface: Surface?
        synchronized(lock) {
            generation += 1
            token = generation
            old = player
            player = next
            isPrepared = false
            playWhenPrepared = false
            playbackError = null
            currentSurface = surface
        }
        runCatching { old?.release() }
        try {
            next.setSurface(currentSurface)
            next.setDataSource(context, Uri.parse(candidate.url), candidate.headers)
            next.setOnErrorListener { _, what, extra ->
                synchronized(lock) {
                    if (generation == token && player === next) {
                        playbackError = "Android MediaPlayer 錯誤 $what/$extra"
                        isPrepared = false
                        playWhenPrepared = false
                    }
                }
                true
            }
            next.setOnCompletionListener {
                synchronized(lock) {
                    if (generation == token && player === next) playWhenPrepared = false
                }
            }
            next.setOnPreparedListener {
                val shouldPlay = synchronized(lock) {
                    if (generation != token || player !== next) return@setOnPreparedListener
                    isPrepared = true
                    playWhenPrepared
                }
                if (shouldPlay) {
                    runCatching { next.start() }.onFailure { throwable ->
                        synchronized(lock) {
                            if (generation == token && player === next) {
                                playbackError = "內建播放器無法開始播放：${throwable.message ?: throwable::class.simpleName}"
                                isPrepared = false
                                playWhenPrepared = false
                            }
                        }
                    }
                }
            }
            next.prepareAsync()
        } catch (throwable: Throwable) {
            synchronized(lock) {
                if (generation == token && player === next) {
                    player = null
                    isPrepared = false
                    playWhenPrepared = false
                    playbackError = "內建播放器無法載入此影片：${throwable.message ?: throwable::class.simpleName}"
                }
            }
            runCatching { next.release() }
            throw IllegalStateException("內建播放器無法載入此影片：${throwable.message ?: throwable::class.simpleName}", throwable)
        }
    }

    override fun play() {
        synchronized(lock) {
            playWhenPrepared = true
            if (!isPrepared) return
            try {
                player?.start()
            } catch (throwable: Throwable) {
                throw IllegalStateException("內建播放器無法開始播放：${throwable.message ?: throwable::class.simpleName}", throwable)
            }
        }
    }

    override fun pause() {
        synchronized(lock) {
            playWhenPrepared = false
            if (isPrepared) player?.pause()
        }
    }

    override fun seekBy(seconds: Int) {
        synchronized(lock) {
            if (isPrepared) player?.seekTo(((player?.currentPosition ?: 0) + seconds * 1_000).coerceAtLeast(0))
        }
    }

    override fun release() {
        val old = synchronized(lock) {
            generation += 1
            player.also {
                player = null
                isPrepared = false
                playWhenPrepared = false
                playbackError = null
            }
        }
        runCatching { old?.release() }
    }

    fun attachSurface(value: Surface?) {
        synchronized(lock) {
            surface = value
            runCatching { player?.setSurface(value) }.onFailure {
                playbackError = "內建播放器無法連接畫面：${it.message ?: it::class.simpleName}"
            }
        }
    }

    fun snapshot(): AnimePlaybackSnapshot {
        return synchronized(lock) {
            val current = player
            val error = playbackError
            if (current == null || !isPrepared) return@synchronized AnimePlaybackSnapshot(error = error)
            runCatching {
                AnimePlaybackSnapshot(
                    positionSeconds = current.currentPosition.coerceAtLeast(0) / 1_000.0,
                    durationSeconds = current.duration.coerceAtLeast(0) / 1_000.0,
                    isPlaying = current.isPlaying,
                    error = error,
                )
            }.getOrElse { throwable ->
                AnimePlaybackSnapshot(error = throwable.message ?: "Android 內建播放器狀態讀取失敗")
            }
        }
    }
}

object AndroidAnimePlaybackRegistry {
    var player: AndroidMediaPlayerAdapter? = null
}

class AndroidTorrentCacheCleaner(private val root: File) {
    fun clean(maxBytes: Long, nowEpochSeconds: Long, expirationSeconds: Long): List<String> {
        val entries = root.listFiles()?.filter { it.isDirectory }?.map {
            TorrentCacheEntry(it.name, it.walkTopDown().filter(File::isFile).sumOf(File::length), it.lastModified() / 1_000)
        }.orEmpty()
        val ids = TorrentCachePolicy.idsToDelete(entries, maxBytes, nowEpochSeconds, expirationSeconds)
        ids.forEach { File(root, it).deleteRecursively() }
        return ids
    }
}

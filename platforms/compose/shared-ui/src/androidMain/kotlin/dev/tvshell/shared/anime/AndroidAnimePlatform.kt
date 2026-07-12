package dev.tvshell.shared.anime

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
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
    private var player: MediaPlayer? = null
    private var isPrepared = false
    private var playWhenPrepared = false

    override fun load(candidate: AnimeStreamCandidate) {
        release()
        isPrepared = false
        player = MediaPlayer().apply {
            setDataSource(context, Uri.parse(candidate.url), candidate.headers)
            setOnPreparedListener {
                isPrepared = true
                if (playWhenPrepared) it.start()
            }
            prepareAsync()
        }
    }

    override fun play() {
        playWhenPrepared = true
        if (isPrepared) player?.start()
    }

    override fun pause() {
        playWhenPrepared = false
        if (isPrepared) player?.pause()
    }

    override fun seekBy(seconds: Int) {
        if (isPrepared) player?.seekTo(((player?.currentPosition ?: 0) + seconds * 1_000).coerceAtLeast(0))
    }

    override fun release() {
        player?.release()
        player = null
        isPrepared = false
        playWhenPrepared = false
    }
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

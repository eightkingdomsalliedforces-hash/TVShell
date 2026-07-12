package dev.tvshell.shared.anime

import java.io.BufferedWriter
import java.io.File
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URI

class DesktopAnimeHTTPTransport : AnimeHTTPTransport {
    override suspend fun get(url: String, headers: Map<String, String>): String {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
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

class DesktopVLCPlayerAdapter(
    private val executable: String = System.getenv("TVSHELL_VLC_PATH")?.takeIf(String::isNotBlank) ?: "vlc",
) : AnimePlayerAdapter {
    private var process: Process? = null
    private var commandWriter: BufferedWriter? = null

    override fun load(candidate: AnimeStreamCandidate) {
        release()
        val command = mutableListOf(executable, "--intf", "rc", "--rc-fake-tty", "--fullscreen")
        candidate.headers["Referer"]?.let { command += listOf("--http-referrer", it) }
        candidate.headers["User-Agent"]?.let { command += listOf("--http-user-agent", it) }
        command += candidate.url
        process = ProcessBuilder(command).redirectErrorStream(true).start()
        commandWriter = BufferedWriter(OutputStreamWriter(process!!.outputStream))
    }

    override fun play() = send("play")
    override fun pause() = send("pause")
    override fun seekBy(seconds: Int) = send("seek ${if (seconds >= 0) "+" else ""}$seconds")

    override fun release() {
        send("quit")
        commandWriter?.close()
        commandWriter = null
        process?.destroy()
        process = null
    }

    private fun send(command: String) {
        commandWriter?.apply {
            write(command)
            newLine()
            flush()
        }
    }
}

class DesktopTorrentCacheCleaner(private val root: File) {
    fun clean(maxBytes: Long, nowEpochSeconds: Long, expirationSeconds: Long): List<String> {
        val entries = root.listFiles()?.filter(File::isDirectory)?.map {
            TorrentCacheEntry(it.name, it.walkTopDown().filter(File::isFile).sumOf(File::length), it.lastModified() / 1_000)
        }.orEmpty()
        val ids = TorrentCachePolicy.idsToDelete(entries, maxBytes, nowEpochSeconds, expirationSeconds)
        ids.forEach { File(root, it).deleteRecursively() }
        return ids
    }
}

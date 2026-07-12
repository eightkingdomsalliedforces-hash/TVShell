package dev.tvshell.shared.anime

data class AnimeTitle(val id: String, val title: String, val detailURL: String)
data class AnimeEpisode(val id: String, val title: String, val number: Int, val pageURL: String)
data class AnimeStreamCandidate(val url: String, val quality: String, val headers: Map<String, String> = emptyMap())
data class BTRssItem(val title: String, val episode: Int?, val quality: String?, val magnet: String)

interface AnimeHTTPTransport {
    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): String
}

interface AnimePlayerAdapter {
    fun load(candidate: AnimeStreamCandidate)
    fun play()
    fun pause()
    fun seekBy(seconds: Int)
    fun release()
}

object CSS1HtmlParser {
    private val anchor = Regex("""<a\b[^>]*href\s*=\s*(['\"])(.*?)\1[^>]*>(.*?)</a>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val episodeNumber = Regex("""第\s*(\d{1,4})\s*[話话集]""", RegexOption.IGNORE_CASE)
    private val source = Regex("""<source\b([^>]*)>""", RegexOption.IGNORE_CASE)
    private val attribute = Regex("""([A-Za-z0-9_-]+)\s*=\s*(['\"])(.*?)\2""", RegexOption.IGNORE_CASE)

    fun titles(html: String, baseURL: String): List<AnimeTitle> = anchor.findAll(html).mapNotNull { match ->
        val href = decodeHTML(match.groupValues[2]).trim()
        val title = cleanText(match.groupValues[3])
        if (href.isEmpty() || title.isEmpty()) null
        else AnimeTitle(id = absoluteURL(baseURL, href), title = title, detailURL = absoluteURL(baseURL, href))
    }.distinctBy { it.id }.toList()

    fun episodes(html: String, baseURL: String): List<AnimeEpisode> = anchor.findAll(html).mapNotNull { match ->
        val href = decodeHTML(match.groupValues[2]).trim()
        val title = cleanText(match.groupValues[3])
        val number = episodeNumber.find(title)?.groupValues?.getOrNull(1)?.toIntOrNull()
        if (href.isEmpty() || number == null) null
        else AnimeEpisode(
            id = "${absoluteURL(baseURL, href)}#$number",
            title = title,
            number = number,
            pageURL = absoluteURL(baseURL, href),
        )
    }.distinctBy { it.number }.sortedBy { it.number }.toList()

    fun streams(html: String): List<AnimeStreamCandidate> = source.findAll(html).mapNotNull { match ->
        val attributes = attribute.findAll(match.groupValues[1]).associate {
            it.groupValues[1].lowercase() to decodeHTML(it.groupValues[3])
        }
        val url = attributes["src"]?.takeIf { it.startsWith("https://") || it.startsWith("http://") } ?: return@mapNotNull null
        val quality = attributes["label"] ?: inferQuality(url)
        AnimeStreamCandidate(url, quality)
    }.distinctBy { it.url }.sortedByDescending { qualityScore(it.quality) }.toList()

    private fun inferQuality(value: String): String = Regex("""(\d{3,4})p""", RegexOption.IGNORE_CASE)
        .find(value)?.groupValues?.get(1)?.let { "${it}p" } ?: "自動"

    private fun qualityScore(value: String): Int = Regex("""\d{3,4}""").find(value)?.value?.toIntOrNull() ?: 0

    private fun cleanText(value: String): String = decodeHTML(value.replace(Regex("<[^>]+>"), ""))
        .replace(Regex("\\s+"), " ").trim()

    private fun absoluteURL(base: String, value: String): String = when {
        value.startsWith("http://") || value.startsWith("https://") -> value
        value.startsWith("/") -> {
            val schemeEnd = base.indexOf("://")
            val pathStart = if (schemeEnd >= 0) base.indexOf('/', schemeEnd + 3) else -1
            (if (pathStart >= 0) base.substring(0, pathStart) else base) + value
        }
        else -> base.substringBeforeLast('/', base) + "/" + value
    }

    internal fun decodeHTML(value: String): String = value
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
}

object BTRssParser {
    private val item = Regex("""<item\b[^>]*>(.*?)</item>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val title = Regex("""<title\b[^>]*>(?:<!\[CDATA\[)?(.*?)(?:]]>)?</title>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val enclosure = Regex("""<enclosure\b[^>]*url\s*=\s*(['\"])(.*?)\1""", RegexOption.IGNORE_CASE)

    fun items(xml: String): List<BTRssItem> = item.findAll(xml).mapNotNull { match ->
        val body = match.groupValues[1]
        val titleValue = title.find(body)?.groupValues?.getOrNull(1)?.trim() ?: return@mapNotNull null
        val magnet = enclosure.find(body)?.groupValues?.getOrNull(2)?.let(CSS1HtmlParser::decodeHTML)
            ?.takeIf { it.startsWith("magnet:?") } ?: return@mapNotNull null
        val episode = Regex("""(?:\s-\s*|E)(\d{1,4})(?:\s|\D)""", RegexOption.IGNORE_CASE)
            .find(titleValue)?.groupValues?.getOrNull(1)?.toIntOrNull()
        val quality = Regex("""(\d{3,4}p)""", RegexOption.IGNORE_CASE).find(titleValue)?.groupValues?.getOrNull(1)
        BTRssItem(titleValue, episode, quality, magnet)
    }.distinctBy { it.magnet }.toList()
}

data class SourceFailure(val reason: String, val failedAtEpochSeconds: Long)

data class SourceHealthState(val failures: Map<String, SourceFailure> = emptyMap()) {
    fun shouldLoad(host: String): Boolean = host.lowercase() !in failures
    fun recordFailure(host: String, reason: String, nowEpochSeconds: Long = 0): SourceHealthState =
        copy(failures = failures + (host.lowercase() to SourceFailure(reason, nowEpochSeconds)))
    fun reset(host: String): SourceHealthState = copy(failures = failures - host.lowercase())
}

data class TorrentCacheEntry(val id: String, val bytes: Long, val lastAccessEpochSeconds: Long)

object TorrentCachePolicy {
    fun idsToDelete(
        entries: List<TorrentCacheEntry>,
        maxBytes: Long,
        nowEpochSeconds: Long,
        expirationSeconds: Long,
    ): List<String> {
        val sorted = entries.sortedBy { it.lastAccessEpochSeconds }
        val removed = linkedSetOf<String>()
        sorted.filter { nowEpochSeconds - it.lastAccessEpochSeconds > expirationSeconds }.forEach { removed += it.id }
        var remainingBytes = sorted.filterNot { it.id in removed }.sumOf { it.bytes }
        for (entry in sorted) {
            if (remainingBytes <= maxBytes) break
            if (entry.id !in removed) {
                removed += entry.id
                remainingBytes -= entry.bytes
            }
        }
        return sorted.map { it.id }.filter { it in removed }
    }
}

enum class AnimePlayerCommand { PlayPause, Rewind, FastForward }

data class AnimePlayerState(val isPlaying: Boolean = false, val pendingSeekSeconds: Int = 0) {
    fun reduce(command: AnimePlayerCommand): AnimePlayerState = when (command) {
        AnimePlayerCommand.PlayPause -> copy(isPlaying = !isPlaying, pendingSeekSeconds = 0)
        AnimePlayerCommand.Rewind -> copy(pendingSeekSeconds = -15)
        AnimePlayerCommand.FastForward -> copy(pendingSeekSeconds = 15)
    }
}

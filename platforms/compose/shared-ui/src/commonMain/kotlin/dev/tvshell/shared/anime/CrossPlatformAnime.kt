package dev.tvshell.shared.anime

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

data class AnimeTitle(val id: String, val title: String, val detailURL: String)
data class AnimeEpisode(val id: String, val title: String, val number: Int, val pageURL: String)
data class AnimeStreamCandidate(val url: String, val quality: String, val headers: Map<String, String> = emptyMap())
data class BTRssItem(val title: String, val episode: Int?, val quality: String?, val magnet: String)
data class AniSubsRSSSource(val name: String, val searchURLTemplate: String)

object AniSubsBTSubscriptionParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun sources(payload: String): List<AniSubsRSSSource> {
        val root = runCatching { json.parseToJsonElement(payload) as? JsonObject }.getOrNull() ?: return emptyList()
        val exported = root["exportedMediaSourceDataList"] as? JsonObject ?: return emptyList()
        val sources = exported["mediaSources"] as? JsonArray ?: return emptyList()
        return sources.mapNotNull { element ->
            val source = element as? JsonObject ?: return@mapNotNull null
            if (source.string("factoryId") != "rss") return@mapNotNull null
            val arguments = source["arguments"] as? JsonObject ?: return@mapNotNull null
            val name = arguments.string("name")?.trim()?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            val search = arguments["searchConfig"] as? JsonObject ?: return@mapNotNull null
            val template = search.string("searchUrl")?.trim()?.takeIf { "{keyword}" in it } ?: return@mapNotNull null
            AniSubsRSSSource(name, template)
        }.distinctBy { it.searchURLTemplate }
    }

    private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
}

object AniSubsBTSearch {
    fun queryURL(template: String, keyword: String): String = encodeUnsafeURLCharacters(
        template.replace("{keyword}", keyword.trim()),
    )

    fun candidates(sourceName: String, rssPayload: String, episodeNumber: Int): List<AnimeStreamCandidate> {
        val items = BTRssParser.items(rssPayload)
        val exact = items.filter { it.episode == episodeNumber }
        val seasonPacks = items.filter { it.episode == null }
        return (exact + seasonPacks).distinctBy(BTRssItem::magnet).take(12).map { item ->
            AnimeStreamCandidate(
                url = item.magnet,
                quality = listOfNotNull(item.quality, sourceName, "BT").joinToString(" · "),
                headers = mapOf(
                    "resolver" to "torrent",
                    "source" to sourceName,
                    "channel" to sourceName,
                    "title" to item.title,
                ),
            )
        }
    }

    private fun encodeUnsafeURLCharacters(value: String): String = buildString {
        val bytes = value.encodeToByteArray()
        var index = 0
        while (index < bytes.size) {
            val unsigned = bytes[index].toInt() and 0xff
            val character = unsigned.toChar()
            val validEscape = character == '%' && index + 2 < bytes.size &&
                bytes[index + 1].toInt().toChar().isHexDigit() && bytes[index + 2].toInt().toChar().isHexDigit()
            when {
                validEscape -> {
                    append('%')
                    append(bytes[index + 1].toInt().toChar().uppercaseChar())
                    append(bytes[index + 2].toInt().toChar().uppercaseChar())
                    index += 3
                    continue
                }
                character.isLetterOrDigit() && unsigned < 128 || character in "-._~:/?&=,+;@!$'()*" -> append(character)
                else -> {
                    append('%')
                    append("0123456789ABCDEF"[unsigned ushr 4])
                    append("0123456789ABCDEF"[unsigned and 0xf])
                }
            }
            index += 1
        }
    }

    private fun Char.isHexDigit(): Boolean = this in '0'..'9' || lowercaseChar() in 'a'..'f'
}

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
    private val magnetElement = Regex("""<(?:magnet|magneturi|link)\b[^>]*>\s*(?:<!\[CDATA\[)?(magnet:\?[^<\s]+)(?:]]>)?\s*</(?:magnet|magneturi|link)>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

    fun items(xml: String): List<BTRssItem> = item.findAll(xml).mapNotNull { match ->
        val body = match.groupValues[1]
        val titleValue = title.find(body)?.groupValues?.getOrNull(1)?.trim() ?: return@mapNotNull null
        val magnet = (enclosure.find(body)?.groupValues?.getOrNull(2)
            ?: magnetElement.find(body)?.groupValues?.getOrNull(1))?.let(CSS1HtmlParser::decodeHTML)
            ?.takeIf { it.startsWith("magnet:?") } ?: return@mapNotNull null
        val episode = listOf(
            Regex("""(?:\s-\s*|(?:EP|E)\s*)(\d{1,4})(?!\d)""", RegexOption.IGNORE_CASE),
            Regex("""第\s*(\d{1,4})\s*[話话集]""", RegexOption.IGNORE_CASE),
            Regex("""[\[(【]\s*(\d{1,3})\s*[\])】]""", RegexOption.IGNORE_CASE),
        ).firstNotNullOfOrNull { pattern ->
            pattern.find(titleValue)?.groupValues?.getOrNull(1)?.toIntOrNull()
        }
        val quality = Regex("""(\d{3,4}p)""", RegexOption.IGNORE_CASE).find(titleValue)?.groupValues?.getOrNull(1)
        BTRssItem(titleValue, episode, quality, magnet)
    }.distinctBy { it.magnet }.toList()
}

object BilibiliAnimeParser {
    private val episodeMarker = Regex("\\\"id\\\"\\s*:\\s*(\\d+)")

    fun episodes(payload: String): List<AnimeEpisode> {
        val matches = episodeMarker.findAll(payload).toList()
        return matches.mapIndexedNotNull { index, match ->
            val end = matches.getOrNull(index + 1)?.range?.first ?: payload.length
            val cidStart = payload.lastIndexOf("\"cid\"", match.range.first).takeIf { it >= 0 } ?: match.range.first
            val block = payload.substring(cidStart, end.coerceAtMost(cidStart + 8_000))
            val episodeID = match.groupValues[1]
            val cid = numberField(block, "cid") ?: return@mapIndexedNotNull null
            val rawNumber = stringField(block, "title").orEmpty()
            val number = rawNumber.toIntOrNull() ?: (index + 1)
            val longTitle = stringField(block, "long_title").orEmpty().trim()
            val label = buildString {
                append("第 ").append(rawNumber.ifBlank { number.toString() }).append(" 集")
                if (longTitle.isNotBlank()) append(" · ").append(longTitle)
            }
            AnimeEpisode(
                id = "bilibili:$episodeID:$cid",
                title = decodeJSON(label),
                number = number,
                pageURL = "https://www.bilibili.com/bangumi/play/ep$episodeID",
            )
        }.distinctBy { it.id }
    }

    fun streams(payload: String): List<AnimeStreamCandidate> {
        val quality = numberField(payload, "quality")?.toIntOrNull() ?: 0
        val durl = payload.substringAfter("\"durl\":", "")
        if (durl.isBlank()) return emptyList()
        return Regex("\\\"url\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"").findAll(durl.substringBefore("],", durl))
            .map { match ->
                AnimeStreamCandidate(
                    url = decodeJSON(match.groupValues[1]),
                    quality = qualityLabel(quality),
                    headers = mapOf(
                        "Referer" to "https://www.bilibili.com/",
                        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/125 Safari/537.36",
                    ),
                )
            }
            .filter { it.url.startsWith("http://") || it.url.startsWith("https://") }
            .distinctBy { it.url }
            .toList()
    }

    fun failureReason(payload: String): String? {
        val code = Regex("\\\"code\\\"\\s*:\\s*(-?\\d+)").find(payload)?.groupValues?.get(1)?.toIntOrNull()
        if (code == null || code == 0) return null
        return stringField(payload, "message")?.let(::decodeJSON)?.takeIf(String::isNotBlank)
            ?: "Bilibili API 錯誤 $code"
    }

    fun danmaku(xml: String): List<DanmakuComment> =
        Regex("""<d\s+p=[\"']([^\"']+)[\"'][^>]*>(.*?)</d>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .findAll(xml).mapNotNull { match ->
                val fields = match.groupValues[1].split(',')
                val time = fields.getOrNull(0)?.toDoubleOrNull() ?: return@mapNotNull null
                val mode = when (fields.getOrNull(1)?.toIntOrNull()) {
                    4 -> DanmakuMode.Bottom
                    5 -> DanmakuMode.Top
                    else -> DanmakuMode.Scroll
                }
                val color = fields.getOrNull(3)?.toLongOrNull() ?: 0xFFFFFF
                val text = CSS1HtmlParser.decodeHTML(match.groupValues[2]).replace("&#x27;", "'").trim()
                if (text.isBlank()) null else DanmakuComment(
                    time,
                    text,
                    "#${color.coerceIn(0, 0xFFFFFF).toString(16).uppercase().padStart(6, '0')}",
                    mode,
                )
            }.sortedBy(DanmakuComment::time).toList()

    private fun qualityLabel(value: Int): String = when (value) {
        127 -> "8K"
        126 -> "杜比視界"
        120 -> "4K"
        116 -> "1080p60"
        112 -> "1080p+"
        80 -> "1080p"
        64 -> "720p"
        32 -> "480p"
        16 -> "360p"
        else -> if (value > 0) "清晰度 $value" else "自動"
    }

    private fun stringField(value: String, name: String): String? =
        Regex("\\\"${Regex.escape(name)}\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"").find(value)?.groupValues?.get(1)

    private fun numberField(value: String, name: String): String? =
        Regex("\\\"${Regex.escape(name)}\\\"\\s*:\\s*(\\d+)").find(value)?.groupValues?.get(1)

    private fun decodeJSON(value: String): String = value
        .replace("\\u0026", "&")
        .replace("\\/", "/")
        .replace("\\\"", "\"")
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
        protectedIDs: Set<String> = emptySet(),
    ): List<String> {
        val sorted = entries.sortedBy { it.lastAccessEpochSeconds }
        val removed = linkedSetOf<String>()
        sorted.filter { it.id !in protectedIDs && nowEpochSeconds - it.lastAccessEpochSeconds > expirationSeconds }
            .forEach { removed += it.id }
        var remainingBytes = sorted.filterNot { it.id in removed }.sumOf { it.bytes }
        for (entry in sorted) {
            if (remainingBytes <= maxBytes) break
            if (entry.id !in removed && entry.id !in protectedIDs) {
                removed += entry.id
                remainingBytes -= entry.bytes
            }
        }
        return sorted.map { it.id }.filter { it in removed }
    }
}

enum class AnimePlayerCommand {
    PlayPause,
    Rewind,
    FastForward,
    OpenSourcePicker,
    PreviousSource,
    NextSource,
    ConfirmSource,
    Back,
}

data class AnimePlayerState(
    val isPlaying: Boolean = false,
    val pendingSeekSeconds: Int = 0,
    val candidates: List<AnimeStreamCandidate> = emptyList(),
    val selectedCandidateIndex: Int = 0,
    val focusedCandidateIndex: Int = 0,
    val isSourcePickerVisible: Boolean = false,
    val pendingAction: String? = null,
) {
    val selectedCandidate: AnimeStreamCandidate? get() = candidates.getOrNull(selectedCandidateIndex)

    fun loaded(master: AnimeStreamCandidate, alternatives: List<AnimeStreamCandidate>): AnimePlayerState = copy(
        candidates = (listOf(master) + alternatives).distinctBy { it.url },
        selectedCandidateIndex = 0,
        focusedCandidateIndex = 0,
        pendingAction = null,
    )

    fun reduce(command: AnimePlayerCommand): AnimePlayerState = when (command) {
        AnimePlayerCommand.PlayPause -> copy(isPlaying = !isPlaying, pendingSeekSeconds = 0)
        AnimePlayerCommand.Rewind -> copy(pendingSeekSeconds = -15)
        AnimePlayerCommand.FastForward -> copy(pendingSeekSeconds = 15)
        AnimePlayerCommand.OpenSourcePicker -> copy(
            isSourcePickerVisible = candidates.isNotEmpty(),
            focusedCandidateIndex = selectedCandidateIndex,
            pendingAction = null,
        )
        AnimePlayerCommand.PreviousSource -> if (isSourcePickerVisible) copy(
            focusedCandidateIndex = (focusedCandidateIndex - 1).coerceAtLeast(0),
        ) else this
        AnimePlayerCommand.NextSource -> if (isSourcePickerVisible) copy(
            focusedCandidateIndex = (focusedCandidateIndex + 1).coerceAtMost((candidates.size - 1).coerceAtLeast(0)),
        ) else this
        AnimePlayerCommand.ConfirmSource -> if (isSourcePickerVisible && candidates.isNotEmpty()) copy(
            selectedCandidateIndex = focusedCandidateIndex,
            isSourcePickerVisible = false,
            pendingAction = "load:${candidates[focusedCandidateIndex].url}",
        ) else this
        AnimePlayerCommand.Back -> if (isSourcePickerVisible) copy(isSourcePickerVisible = false, pendingAction = null)
        else copy(pendingAction = "exit")
    }

    fun clearAction(): AnimePlayerState = copy(pendingAction = null)
}

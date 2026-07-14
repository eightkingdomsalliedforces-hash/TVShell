package dev.tvshell.shared.anime

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.math.max

const val DefaultCSS1SubscriptionURL = "https://sub.creamycake.org/v1/css1.json"

data class CSS1SourceConfig(
    val name: String,
    val searchURLTemplate: String,
    val searchSelector: String,
    val episodeListSelector: String,
    val episodeSelector: String,
    val episodeLinkSelector: String?,
    val episodeSortPattern: String?,
    val enableNestedURL: Boolean,
    val nestedURLPattern: String?,
    val videoPattern: String,
    val userAgent: String,
    val videoHeaders: Map<String, String>,
)

object CSS1SubscriptionParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun decode(payload: String): List<CSS1SourceConfig> {
        val root = json.parseToJsonElement(payload).jsonObject
        val mediaSources = root.objectValue("exportedMediaSourceDataList")
            ?.arrayValue("mediaSources") ?: return emptyList()
        return mediaSources.mapNotNull { element ->
            val source = element as? JsonObject ?: return@mapNotNull null
            if (source.stringValue("factoryId") != "web-selector") return@mapNotNull null
            val arguments = source.objectValue("arguments") ?: return@mapNotNull null
            val config = arguments.objectValue("searchConfig") ?: return@mapNotNull null
            val channel = config.objectValue("selectorChannelFormatFlattened") ?: return@mapNotNull null
            val matcher = config.objectValue("matchVideo") ?: return@mapNotNull null
            val name = arguments.stringValue("name")?.trim().orEmpty()
            val searchURL = config.stringValue("searchUrl").orEmpty()
            val searchSelector = config.objectValue("selectorSubjectFormatA")?.stringValue("selectLists")
                ?: config.objectValue("selectorSubjectFormatIndexed")?.stringValue("selectLists")
            val episodeList = channel.stringValue("selectEpisodeLists")
            val episodeSelector = channel.stringValue("selectEpisodesFromList")
            val videoPattern = matcher.stringValue("matchVideoUrl")
            if (name.isBlank() || !searchURL.contains("{keyword}") || searchSelector.isNullOrBlank() ||
                episodeList.isNullOrBlank() || episodeSelector.isNullOrBlank() || videoPattern.isNullOrBlank()) {
                return@mapNotNull null
            }
            val headerObject = matcher.objectValue("addHeadersToVideo")
            val headers = buildMap {
                headerObject?.stringValue("referer")?.takeIf(String::isNotBlank)?.let { put("Referer", it) }
                headerObject?.stringValue("userAgent")?.takeIf(String::isNotBlank)?.let { put("User-Agent", it) }
                matcher.stringValue("cookies")?.takeIf(String::isNotBlank)?.let { put("Cookie", it) }
            }
            CSS1SourceConfig(
                name = name,
                searchURLTemplate = searchURL,
                searchSelector = searchSelector,
                episodeListSelector = episodeList,
                episodeSelector = episodeSelector,
                episodeLinkSelector = channel.stringValue("selectEpisodeLinksFromList")?.takeIf(String::isNotBlank),
                episodeSortPattern = channel.stringValue("matchEpisodeSortFromName")?.takeIf(String::isNotBlank),
                enableNestedURL = matcher.booleanValue("enableNestedUrl"),
                nestedURLPattern = matcher.stringValue("matchNestedUrl")?.takeIf(String::isNotBlank),
                videoPattern = videoPattern,
                userAgent = arguments.stringValue("userAgent")
                    ?: headerObject?.stringValue("userAgent")
                    ?: "TVShell/0.1 ani-subs-css1",
                videoHeaders = headers,
            )
        }
    }

    private fun JsonObject.objectValue(key: String): JsonObject? = get(key) as? JsonObject
    private fun JsonObject.arrayValue(key: String): JsonArray? = get(key) as? JsonArray
    private fun JsonObject.stringValue(key: String): String? = get(key)?.jsonPrimitive?.contentOrNull
    private fun JsonObject.booleanValue(key: String): Boolean = get(key)?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false
}

data class CSS1Anchor(val title: String, val url: String)

interface CSS1ContentClient {
    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): String
    fun anchors(html: String, selector: String, baseURL: String): List<CSS1Anchor>
    fun blocks(html: String, selector: String): List<String>
    fun encodeQuery(value: String): String
    fun decodeURL(value: String): String
    fun resolveURL(baseURL: String, value: String): String
}

class CSS1Resolver(
    private val client: CSS1ContentClient,
    val subscriptionURL: String = DefaultCSS1SubscriptionURL,
) {
    init {
        require(subscriptionURL.startsWith("https://") || subscriptionURL.startsWith("http://")) {
            "CSS1 訂閱網址必須使用 http 或 https"
        }
    }
    private data class EpisodeLine(
        val number: Int,
        val title: String,
        val url: String,
        val source: CSS1SourceConfig,
    )

    private var cachedSources: List<CSS1SourceConfig>? = null
    private var linesByEpisode: Map<Int, List<EpisodeLine>> = emptyMap()

    suspend fun episodes(title: String): List<AnimeEpisode> {
        val sources = sources()
        val results = coroutineScope {
            sources.take(16).map { source ->
                async { runCatching { search(source, title) }.getOrDefault(emptyList()) }
            }.awaitAll().flatten()
        }
        linesByEpisode = results.groupBy(EpisodeLine::number)
        return linesByEpisode.entries.sortedBy { it.key }.map { (number, lines) ->
            AnimeEpisode(
                id = "css1:$number",
                title = lines.firstOrNull()?.title?.takeIf(String::isNotBlank) ?: "第 $number 集",
                number = number,
                pageURL = lines.first().url,
            )
        }
    }

    suspend fun streams(episode: AnimeEpisode): List<AnimeStreamCandidate> {
        val lines = linesByEpisode[episode.number].orEmpty().ifEmpty {
            val source = sources().firstOrNull() ?: return emptyList()
            listOf(EpisodeLine(episode.number, episode.title, episode.pageURL, source))
        }
        return coroutineScope {
            lines.mapIndexed { index, line ->
                async { runCatching { resolve(line, index) }.getOrNull() }
            }.awaitAll().filterNotNull()
        }.distinctBy(AnimeStreamCandidate::url)
    }

    private suspend fun sources(): List<CSS1SourceConfig> = cachedSources ?: CSS1SubscriptionParser
        .decode(client.get(subscriptionURL, mapOf("Accept" to "application/json", "User-Agent" to "TVShell/0.1 ani-subs-css1")))
        .also { cachedSources = it }

    private suspend fun search(source: CSS1SourceConfig, title: String): List<EpisodeLine> {
        val searchURL = source.searchURLTemplate.replace("{keyword}", client.encodeQuery(title))
        val searchHTML = client.get(searchURL, requestHeaders(source))
        val wanted = normalized(title)
        val subjects = client.anchors(searchHTML, source.searchSelector, searchURL)
            .filter { wanted.isBlank() || normalized(it.title).contains(wanted) || wanted.contains(normalized(it.title)) }
            .take(3)
        return coroutineScope {
            subjects.map { subject -> async { detailLines(source, subject) } }.awaitAll().flatten()
        }
    }

    private suspend fun detailLines(source: CSS1SourceConfig, subject: CSS1Anchor): List<EpisodeLine> {
        val html = client.get(subject.url, requestHeaders(source))
        val blocks = client.blocks(html, source.episodeListSelector).ifEmpty { listOf(html) }
        return blocks.flatMap { block ->
            val titleAnchors = client.anchors(block, source.episodeSelector, subject.url)
            val linkAnchors = source.episodeLinkSelector?.let { client.anchors(block, it, subject.url) }.orEmpty()
            titleAnchors.mapIndexedNotNull { index, anchor ->
                val resolved = linkAnchors.getOrNull(index)?.copy(title = anchor.title) ?: anchor
                val number = episodeNumber(resolved.title, source.episodeSortPattern) ?: return@mapIndexedNotNull null
                EpisodeLine(number, resolved.title, resolved.url, source)
            }
        }.distinctBy { "${it.number}:${it.url}" }
    }

    private suspend fun resolve(line: EpisodeLine, index: Int): AnimeStreamCandidate? {
        val headers = requestHeaders(line.source)
        val watchHTML = client.get(line.url, headers)
        val (playbackHTML, baseURL) = if (line.source.enableNestedURL) {
            firstNestedURL(watchHTML, line.source.nestedURLPattern, line.url)?.let { nested ->
                client.get(nested, headers) to nested
            } ?: (watchHTML to line.url)
        } else {
            watchHTML to line.url
        }
        val streamURL = firstVideoURL(playbackHTML, line.source.videoPattern, baseURL) ?: return null
        val quality = qualityLabel(streamURL)
        return AnimeStreamCandidate(
            streamURL,
            quality,
            line.source.videoHeaders + mapOf(
                "resolver" to "web-selector",
                "source" to line.source.name,
                "User-Agent" to line.source.userAgent,
                "Referer" to (line.source.videoHeaders["Referer"] ?: line.url),
                "line" to "${index + 1}",
            ),
        )
    }

    private fun requestHeaders(source: CSS1SourceConfig): Map<String, String> =
        mapOf("User-Agent" to source.userAgent, "Accept-Language" to "zh-TW,zh;q=0.9,en;q=0.7")

    private fun episodeNumber(title: String, pattern: String?): Int? {
        if (!pattern.isNullOrBlank()) {
            runCatching { Regex(pattern, RegexOption.IGNORE_CASE).find(title) }.getOrNull()?.groupValues
                ?.drop(1)?.firstNotNullOfOrNull { it.filter(Char::isDigit).toIntOrNull() }?.let { return it }
        }
        return Regex("(?:第\\s*)?(\\d{1,4})(?:\\s*[話话集]|\\s*$)", RegexOption.IGNORE_CASE)
            .find(title)?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: Regex("ep\\.?\\s*(\\d{1,4})", RegexOption.IGNORE_CASE).find(title)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    private fun firstNestedURL(html: String, pattern: String?, baseURL: String): String? {
        if (pattern.isNullOrBlank() || pattern == "$^") return null
        return regexURL(html, pattern, baseURL)
    }

    private fun firstVideoURL(html: String, pattern: String, baseURL: String): String? {
        val normalized = html.replace("\\/", "/").replace("\\u002F", "/")
        Regex("(?:[?&]|\\\")url=([^&\\\"'\\s<>]+)", RegexOption.IGNORE_CASE).find(normalized)
            ?.groupValues?.getOrNull(1)?.let(client::decodeURL)?.let { candidate ->
                client.resolveURL(baseURL, cleanURL(candidate)).takeIf(::isPlayableURL)?.let { return it }
            }
        regexURL(normalized, pattern, baseURL)?.takeIf(::isPlayableURL)?.let { return it }
        return Regex("https?://[^\\\"'\\s<>\\\\]+?(?:\\.mp4|\\.m3u8|\\.flv|\\.mkv)(?:\\?[^\\\"'\\s<>\\\\]+)?", RegexOption.IGNORE_CASE)
            .find(normalized)?.value?.let(::cleanURL)
    }

    private fun regexURL(html: String, pattern: String, baseURL: String): String? {
        val match = runCatching { Regex(pattern.replace("\\/", "/"), setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(html) }
            .getOrNull() ?: return null
        return match.groupValues.asSequence().drop(1).plus(match.value).map(::cleanURL)
            .firstNotNullOfOrNull { candidate ->
                client.resolveURL(baseURL, candidate).takeIf { it.startsWith("http://") || it.startsWith("https://") }
            }
    }

    private fun cleanURL(value: String): String = client.decodeURL(value)
        .substringAfter("url=", value).trim('"', '\'', ' ', '\\').replace("&amp;", "&")

    private fun isPlayableURL(value: String): Boolean {
        val key = value.lowercase()
        return listOf(".m3u8", ".mp4", ".flv", ".mkv", "bilivideo.com", "akamaized.net").any(key::contains)
    }

    private fun qualityLabel(url: String): String {
        val key = url.lowercase()
        return when {
            "2160" in key || "4k" in key || "uhd" in key -> "2160p"
            "1080" in key || "fhd" in key -> "1080p"
            "720" in key -> "720p"
            "480" in key -> "480p"
            "360" in key -> "360p"
            else -> "CSS1"
        }
    }

    private fun normalized(value: String): String = value.lowercase().filter { it.isLetterOrDigit() }
}

enum class DanmakuMode { Scroll, Top, Bottom }

data class DanmakuComment(
    val time: Double,
    val text: String,
    val colorHex: String = "#FFFFFF",
    val mode: DanmakuMode = DanmakuMode.Scroll,
)

data class DandanplayCredentials(val appID: String = "", val appSecret: String = "") {
    val isConfigured: Boolean get() = appID.isNotBlank() && appSecret.isNotBlank()
}

data class ServiceCredentials(
    val dandanplay: DandanplayCredentials = DandanplayCredentials(),
    val bilibiliCookie: String = "",
)

object ServiceCredentialsParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun decode(payload: String): ServiceCredentials {
        val root = runCatching { json.parseToJsonElement(payload).jsonObject }.getOrNull()
        val dandanplay = root?.get("dandanplay") as? JsonObject
        val bilibili = root?.get("bilibili") as? JsonObject
        val cookieElement = bilibili?.get("cookie")
        val jsonCookie = runCatching { cookieElement?.jsonPrimitive?.contentOrNull }.getOrNull().orEmpty()
        val browserCookie = (cookieElement as? JsonArray).orEmpty().mapNotNull { element ->
            val entry = element as? JsonObject ?: return@mapNotNull null
            val domain = entry["domain"]?.jsonPrimitive?.contentOrNull?.lowercase()
            if (domain != null && domain != "bilibili.com" && !domain.endsWith(".bilibili.com")) return@mapNotNull null
            val name = entry["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val value = entry["value"]?.jsonPrimitive?.contentOrNull.orEmpty()
            if (name.isBlank() || value.isBlank()) null else "$name=$value"
        }.joinToString("; ")
        val netscapeCookie = payload.lineSequence().mapNotNull { line ->
            val fields = line.trim().split('\t')
            if (fields.size < 7) return@mapNotNull null
            val domain = fields[0].removePrefix("#HttpOnly_").lowercase()
            if (domain != "bilibili.com" && !domain.endsWith(".bilibili.com")) return@mapNotNull null
            val name = fields[5].trim()
            val value = fields[6].trim()
            if (name.isBlank() || value.isBlank()) null else "$name=$value"
        }.joinToString("; ")
        return ServiceCredentials(
            dandanplay = DandanplayCredentials(
                dandanplay?.get("appID")?.jsonPrimitive?.contentOrNull.orEmpty(),
                dandanplay?.get("appSecret")?.jsonPrimitive?.contentOrNull.orEmpty(),
            ),
            bilibiliCookie = jsonCookie.ifBlank { browserCookie }.ifBlank { netscapeCookie },
        )
    }
}

class DandanplayService(
    private val client: CSS1ContentClient,
    private val sha256Base64: (String) -> String,
) {
    suspend fun comments(
        title: String,
        episode: Int,
        credentials: DandanplayCredentials,
        timestamp: Int,
    ): List<DanmakuComment> {
        require(credentials.isConfigured) { "尚未設定 Dandanplay App ID／App Secret" }
        val searchPath = "/api/v2/search/episodes"
        val searchURL = "https://api.dandanplay.net$searchPath?anime=${client.encodeQuery(title)}&episode=$episode"
        val searchPayload = client.get(searchURL, headers(searchPath, credentials, timestamp))
        val episodeID = DandanplayParser.episodeID(searchPayload, episode)
            ?: error("Dandanplay 搜不到：$title 第 $episode 集")
        val commentPath = "/api/v2/comment/$episodeID"
        val commentPayload = client.get(
            "https://api.dandanplay.net$commentPath?withRelated=true",
            headers(commentPath, credentials, timestamp),
        )
        return DandanplayParser.comments(commentPayload)
    }

    private fun headers(path: String, credentials: DandanplayCredentials, timestamp: Int): Map<String, String> = mapOf(
        "Accept" to "application/json",
        "X-AppId" to credentials.appID,
        "X-Timestamp" to timestamp.toString(),
        "X-Signature" to sha256Base64("${credentials.appID}$timestamp$path${credentials.appSecret}"),
    )
}

object DandanplayParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun comments(payload: String): List<DanmakuComment> {
        val root = runCatching { json.parseToJsonElement(payload).jsonObject }.getOrNull() ?: return emptyList()
        val values = root["comments"] as? JsonArray ?: return emptyList()
        return values.mapNotNull { element ->
            val raw = element as? JsonObject ?: return@mapNotNull null
            val fields = raw["p"]?.jsonPrimitive?.contentOrNull?.split(',') ?: return@mapNotNull null
            if (fields.size < 4) return@mapNotNull null
            val time = fields[0].toDoubleOrNull() ?: return@mapNotNull null
            val mode = when (fields[1].toIntOrNull()) {
                4 -> DanmakuMode.Bottom
                5 -> DanmakuMode.Top
                else -> DanmakuMode.Scroll
            }
            val colorIndex = if (fields.size == 4) 2 else 3
            val color = fields.getOrNull(colorIndex)?.toLongOrNull() ?: 0xFFFFFF
            val text = raw["m"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            DanmakuComment(time, text, "#${color.coerceIn(0, 0xFFFFFF).toString(16).uppercase().padStart(6, '0')}", mode)
        }.sortedBy(DanmakuComment::time)
    }

    fun episodeID(payload: String, preferredEpisode: Int): String? {
        val root = runCatching { json.parseToJsonElement(payload).jsonObject }.getOrNull() ?: return null
        val direct = root["episodes"] as? JsonArray
        val nested = (root["animes"] as? JsonArray).orEmpty().flatMap { anime ->
            ((anime as? JsonObject)?.get("episodes") as? JsonArray).orEmpty()
        }
        val episodes = direct?.toList().orEmpty().ifEmpty { nested }
        fun JsonObject.id(): String? = get("episodeId")?.jsonPrimitive?.contentOrNull
        fun JsonObject.matches(): Boolean = listOf("episodeNumber", "episodeTitle").any { key ->
            get(key)?.jsonPrimitive?.contentOrNull?.filter(Char::isDigit)?.toIntOrNull() == preferredEpisode
        }
        return episodes.mapNotNull { it as? JsonObject }.firstOrNull(JsonObject::matches)?.id()
            ?: episodes.mapNotNull { it as? JsonObject }.firstOrNull()?.id()
    }
}

object DanmakuMotion {
    private const val PointsPerSecond = 600.0

    fun lifetime(viewportWidth: Float, textWidth: Float, speedScale: Float): Double =
        (max(viewportWidth, 0f) + max(textWidth, 0f)) / (PointsPerSecond * max(speedScale, .1f))

    fun horizontalOffset(
        ageSeconds: Double,
        viewportWidth: Float,
        textWidth: Float,
        speedScale: Float,
    ): Float {
        val duration = lifetime(viewportWidth, textWidth, speedScale).coerceAtLeast(.001)
        val progress = (ageSeconds / duration).coerceIn(0.0, 1.0)
        return viewportWidth - (progress * (viewportWidth + textWidth)).toFloat()
    }

    fun laneIndex(identity: String, laneCount: Int): Int {
        var hash = 0xcbf29ce484222325UL
        identity.encodeToByteArray().forEach { byte ->
            hash = (hash xor byte.toUByte().toULong()) * 0x100000001b3UL
        }
        return (hash % max(laneCount, 1).toULong()).toInt()
    }
}

object DanmakuTimeline {
    fun active(
        comments: List<DanmakuComment>,
        currentTime: Double,
        viewportWidth: Float,
        estimatedTextWidth: Float,
        speedScale: Float,
    ): List<DanmakuComment> {
        val lifetime = DanmakuMotion.lifetime(viewportWidth, estimatedTextWidth, speedScale)
        return comments.filter { currentTime >= it.time && currentTime - it.time <= lifetime }
    }
}

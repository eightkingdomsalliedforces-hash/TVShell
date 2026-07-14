package dev.tvshell.shared.anime

import java.net.HttpURLConnection
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import android.util.Base64
import org.jsoup.Jsoup

class PlatformCSS1ContentClient : CSS1ContentClient {
    override suspend fun get(url: String, headers: Map<String, String>): String {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.instanceFollowRedirects = true
            headers.forEach(connection::setRequestProperty)
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            require(status in 200..299 && stream != null) { "HTTP $status · ${URI(url).host}" }
            stream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    override fun anchors(html: String, selector: String, baseURL: String): List<CSS1Anchor> = runCatching {
        Jsoup.parse(html, baseURL).select(selector).mapNotNull { element ->
            val anchor = if (element.tagName() == "a") element else element.closest("a") ?: element.selectFirst("a")
            val url = anchor?.absUrl("href").orEmpty()
            val title = element.text().trim().ifBlank { anchor?.text()?.trim().orEmpty() }
            if (url.isBlank() || title.isBlank()) null else CSS1Anchor(title, url)
        }.distinctBy(CSS1Anchor::url)
    }.getOrDefault(emptyList())

    override fun blocks(html: String, selector: String): List<String> = runCatching {
        Jsoup.parse(html).select(selector).map { it.outerHtml() }
    }.getOrDefault(emptyList())

    override fun encodeQuery(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())
    override fun decodeURL(value: String): String = runCatching { URLDecoder.decode(value, StandardCharsets.UTF_8.name()) }.getOrDefault(value)
    override fun resolveURL(baseURL: String, value: String): String = runCatching { URI(baseURL).resolve(value).toString() }.getOrDefault(value)
}

fun platformSHA256Base64(value: String): String = Base64.encodeToString(
    MessageDigest.getInstance("SHA-256").digest(value.toByteArray(StandardCharsets.UTF_8)),
    Base64.NO_WRAP,
)

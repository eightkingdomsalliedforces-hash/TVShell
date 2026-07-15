package dev.tvshell.shared.anime

import dev.tvshell.shared.NativeMediaCard
import dev.tvshell.shared.RemoteCommand

enum class TorrentTransferPhase {
    Idle,
    Metadata,
    Selecting,
    Downloading,
    Buffering,
    Ready,
    Background,
    Failed,
    Cancelled,
}

data class TorrentFileCandidate(
    val index: Int,
    val path: String,
    val size: Long,
)

object TorrentIdentity {
    private const val FNV_OFFSET_BASIS = -3750763034362895579L
    private const val FNV_PRIME = 1099511628211L
    private const val HEX_DIGITS = "0123456789abcdef"
    private const val BASE32_DIGITS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    fun stableID(magnet: String): String {
        canonicalInfoHash(magnet)?.let { return it }
        val identity = magnet.substringAfter('?', "")
            .split('&')
            .firstOrNull { it.substringBefore('=').equals("xt", ignoreCase = true) }
            ?.substringAfter('=', "")
            ?.let(::percentDecode)
            ?.lowercase()
            ?: magnet
        return fnvHex(identity) + fnvHex("TVShell:$identity")
    }

    private fun canonicalInfoHash(magnet: String): String? {
        val exactTopics = magnet.substringAfter('?', "")
            .split('&')
            .filter { it.substringBefore('=').equals("xt", ignoreCase = true) }
            .map { percentDecode(it.substringAfter('=', "")).trim() }
        val v2Hashes = mutableListOf<String>()
        val v1Hashes = mutableListOf<String>()
        exactTopics.forEach { topic ->
            val lower = topic.lowercase()
            if (lower.startsWith("urn:btih:")) {
                val hash = topic.substringAfterLast(':')
                if (hash.length == 40 && hash.all { it.isHexDigit() }) v1Hashes += hash.lowercase()
                if (hash.length == 32 && hash.all { it.uppercaseChar() in BASE32_DIGITS }) {
                    v1Hashes += base32ToHex(hash)
                }
            }
            if (lower.startsWith("urn:btmh:")) {
                val multihash = topic.substringAfterLast(':').lowercase()
                if (multihash.length == 68 && multihash.startsWith("1220") && multihash.all { it.isHexDigit() }) {
                    v2Hashes += multihash.removePrefix("1220")
                }
            }
        }
        return v2Hashes.minOrNull() ?: v1Hashes.minOrNull()
    }

    private fun base32ToHex(value: String): String {
        var buffer = 0
        var bits = 0
        val bytes = mutableListOf<Int>()
        value.uppercase().forEach { character ->
            val digit = BASE32_DIGITS.indexOf(character)
            require(digit >= 0) { "無效的 BTIH Base32" }
            buffer = (buffer shl 5) or digit
            bits += 5
            if (bits >= 8) {
                bits -= 8
                bytes += (buffer shr bits) and 0xff
                buffer = buffer and ((1 shl bits) - 1)
            }
        }
        return buildString(bytes.size * 2) {
            bytes.forEach { byte ->
                append(HEX_DIGITS[byte ushr 4])
                append(HEX_DIGITS[byte and 0xf])
            }
        }
    }

    private fun percentDecode(value: String): String = Regex("%([0-9a-fA-F]{2})").replace(value) {
        it.groupValues[1].toInt(16).toChar().toString()
    }

    private fun fnvHex(value: String): String {
        var hash = FNV_OFFSET_BASIS
        value.encodeToByteArray().forEach { byte ->
            hash = (hash xor (byte.toLong() and 0xffL)) * FNV_PRIME
        }
        return buildString(16) {
            for (shift in 60 downTo 0 step 4) append(HEX_DIGITS[((hash ushr shift) and 0xf).toInt()])
        }
    }

    private fun Char.isHexDigit(): Boolean = this in '0'..'9' || lowercaseChar() in 'a'..'f'
}

object MagnetLink {
    fun normalize(rawValue: String): String {
        val value = rawValue.trim()
        require(value.startsWith("magnet:?", ignoreCase = true)) { "不是有效的 magnet 連結" }
        val query = value.substringAfter('?', "")
        val exactTopics = query.split('&').mapNotNull { field ->
            val key = field.substringBefore('=').trim()
            if (!key.equals("xt", ignoreCase = true)) return@mapNotNull null
            percentDecode(field.substringAfter('=', "")).trim().lowercase()
        }
        val torrentTopics = exactTopics.filter { it.startsWith("urn:btih:") || it.startsWith("urn:btmh:") }
        require(torrentTopics.isNotEmpty()) { "magnet 缺少 BTIH／BTMH 資訊雜湊" }
        val validInfoHash = torrentTopics.any { topic ->
            when {
                topic.startsWith("urn:btih:") -> topic.substringAfterLast(':').let { hash ->
                    hash.length == 40 && hash.all { it.isHexDigit() } ||
                        hash.length == 32 && hash.all { it.uppercaseChar() in "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567" }
                }
                topic.startsWith("urn:btmh:") -> topic.substringAfterLast(':').let { hash ->
                    hash.length == 68 && hash.startsWith("1220") && hash.all { it.isHexDigit() }
                }
                else -> false
            }
        }
        require(validInfoHash) { "magnet 的 BTIH／BTMH 資訊雜湊格式錯誤" }
        return "magnet:${value.substringAfter(':')}"
    }

    fun displayName(magnet: String): String = magnet.substringAfter('?', "")
        .split('&')
        .firstOrNull { it.substringBefore('=').equals("dn", ignoreCase = true) }
        ?.substringAfter('=', "")
        ?.let(::percentDecode)
        ?.trim()
        ?.takeIf(String::isNotBlank)
        ?.take(160)
        ?: "Magnet 下載"

    private fun percentDecode(value: String): String = buildString {
        val bytes = mutableListOf<Byte>()
        fun flushBytes() {
            if (bytes.isEmpty()) return
            append(bytes.toByteArray().decodeToString())
            bytes.clear()
        }

        var index = 0
        while (index < value.length) {
            if (value[index] == '%' && index + 2 < value.length) {
                val byte = value.substring(index + 1, index + 3).toIntOrNull(16)
                if (byte != null) {
                    bytes += byte.toByte()
                    index += 3
                    continue
                }
            }
            flushBytes()
            append(if (value[index] == '+') ' ' else value[index])
            index += 1
        }
        flushBytes()
    }

    private fun Char.isHexDigit(): Boolean = this in '0'..'9' || lowercaseChar() in 'a'..'f'
}

object MagnetHistoryReplay {
    fun card(
        original: NativeMediaCard,
        episode: AnimeEpisode,
        candidate: AnimeStreamCandidate,
    ): NativeMediaCard {
        val magnet = MagnetLink.normalize(candidate.url)
        val source = original.animeSource?.takeIf {
            it == dev.tvshell.shared.AnimeSourceKind.Mikan || it == dev.tvshell.shared.AnimeSourceKind.DMHY
        } ?: dev.tvshell.shared.AnimeSourceKind.AniSubsBT
        return original.copy(
            id = "magnet-${TorrentIdentity.stableID(magnet)}",
            subtitle = listOf(episode.title, candidate.quality).filter(String::isNotBlank).joinToString(" · "),
            playbackURL = magnet,
            animeSource = source,
            animeEpisodeNumber = episode.number,
        )
    }

    fun episode(card: NativeMediaCard): AnimeEpisode? {
        if (!card.playbackURL.trim().startsWith("magnet:?", ignoreCase = true)) return null
        val magnet = MagnetLink.normalize(card.playbackURL)
        return AnimeEpisode(
            id = "magnet-history:${TorrentIdentity.stableID(magnet)}",
            title = card.title,
            number = card.animeEpisodeNumber ?: 0,
            pageURL = magnet,
        )
    }

    fun stream(episode: AnimeEpisode): AnimeStreamCandidate? {
        if (!episode.pageURL.trim().startsWith("magnet:?", ignoreCase = true)) return null
        val magnet = MagnetLink.normalize(episode.pageURL)
        return AnimeStreamCandidate(
            url = magnet,
            quality = "Magnet · BT",
            headers = mapOf("resolver" to "torrent", "source" to "觀看記錄"),
        )
    }
}

object TorrentFileSelector {
    private val playableExtensions = setOf("mp4", "m4v", "mov", "mkv", "webm", "avi", "ts", "m2ts")
    private val sampleMarker = Regex("(?:^|[\\s._/\\-\\[\\]()【】])(sample|preview|trailer|ncop|nced)(?:[\\s._/\\-\\[\\]()【】]|$)", RegexOption.IGNORE_CASE)

    fun isPlayable(path: String): Boolean = path.substringAfterLast('.', "").lowercase() in playableExtensions

    fun select(files: List<TorrentFileCandidate>, episodeNumber: Int?): TorrentFileCandidate? {
        val playable = files.filter { it.size > 0 && isPlayable(it.path) }
        if (playable.isEmpty()) return null
        if (playable.size == 1) return playable.single()
        if (episodeNumber == null || episodeNumber <= 0) return playable.maxWithOrNull(compareBy<TorrentFileCandidate> { scoreBase(it) }.thenBy { it.size })

        val exact = playable.filter { episodeScore(it.path, episodeNumber) > 0 }
        if (exact.isEmpty() && playable.any { hasEpisodeMarker(it.path) }) return null
        return (exact.ifEmpty { playable }).maxWithOrNull(
            compareBy<TorrentFileCandidate> { episodeScore(it.path, episodeNumber) + scoreBase(it) }
                .thenBy { it.size },
        )
    }

    private fun scoreBase(file: TorrentFileCandidate): Int = if (sampleMarker.containsMatchIn(file.path)) -10_000 else 0

    private fun hasEpisodeMarker(path: String): Boolean = listOf(
        Regex("第\\s*\\d{1,4}\\s*[話话集]", RegexOption.IGNORE_CASE),
        Regex("[\\[(【]\\s*\\d{1,4}(?:v\\d+)?\\s*[\\])】]", RegexOption.IGNORE_CASE),
        Regex("(?:EP|E|Episode)[\\s._-]*\\d{1,4}(?!\\d)", RegexOption.IGNORE_CASE),
        Regex("(?:^|[\\s._-])\\d{1,3}(?:v\\d+)?(?![-_.\\s]?bit\\b)(?=[\\s._-]|\\.(?:mp4|m4v|mov|mkv|webm|avi|ts|m2ts)$)", RegexOption.IGNORE_CASE),
    ).any { it.containsMatchIn(path) }

    private fun episodeScore(path: String, episode: Int): Int {
        val value = Regex.escape(episode.toString())
        val padded = episode.toString().padStart(2, '0')
        val paddedValue = Regex.escape(padded)
        val tokens = if (padded == episode.toString()) value else "(?:$paddedValue|0*$value)"
        val patterns = listOf(
            Regex("第\\s*$tokens\\s*[話话集]", RegexOption.IGNORE_CASE) to 900,
            Regex("[\\[(【]\\s*$tokens(?:v\\d+)?\\s*[\\])】]", RegexOption.IGNORE_CASE) to 850,
            Regex("(?:EP|E|Episode)[\\s._-]*$tokens(?!\\d)", RegexOption.IGNORE_CASE) to 800,
            Regex("(?:^|[\\s._-])$tokens(?:v\\d+)?(?=[\\s._-]|$)", RegexOption.IGNORE_CASE) to 700,
        )
        return patterns.firstNotNullOfOrNull { (pattern, score) -> score.takeIf { pattern.containsMatchIn(path) } } ?: 0
    }
}

data class TorrentByteRange(
    val start: Long,
    val endExclusive: Long,
) {
    init {
        require(start >= 0) { "range start must not be negative" }
        require(endExclusive >= start) { "range end must not precede start" }
    }
}

object TorrentReadinessPolicy {
    const val DefaultReadyBytes = 48L * 1024L * 1024L
    const val PriorityHeadBytes = 32L * 1024L * 1024L
    const val PriorityTailBytes = 8L * 1024L * 1024L

    fun requiredRanges(
        fileSize: Long,
        readyBytes: Long = DefaultReadyBytes,
        tailBytes: Long = PriorityTailBytes,
    ): List<TorrentByteRange> {
        if (fileSize <= 0) return emptyList()
        val headEnd = minOf(fileSize, readyBytes.coerceAtLeast(1))
        val tailStart = (fileSize - tailBytes.coerceAtLeast(0)).coerceAtLeast(0)
        return merge(listOf(TorrentByteRange(0, headEnd), TorrentByteRange(tailStart, fileSize)))
    }

    fun isReady(
        fileSize: Long,
        verifiedRanges: List<TorrentByteRange>,
        readyBytes: Long = DefaultReadyBytes,
        tailBytes: Long = PriorityTailBytes,
    ): Boolean {
        if (fileSize <= 0) return false
        val merged = merge(verifiedRanges)
        return requiredRanges(fileSize, readyBytes, tailBytes).all { required ->
            merged.any { available -> available.start <= required.start && available.endExclusive >= required.endExclusive }
        }
    }

    private fun merge(ranges: List<TorrentByteRange>): List<TorrentByteRange> {
        val sorted = ranges.filter { it.endExclusive > it.start }.sortedBy(TorrentByteRange::start)
        if (sorted.isEmpty()) return emptyList()
        val result = mutableListOf<TorrentByteRange>()
        sorted.forEach { range ->
            val last = result.lastOrNull()
            if (last != null && range.start <= last.endExclusive) {
                result[result.lastIndex] = last.copy(endExclusive = maxOf(last.endExclusive, range.endExclusive))
            } else {
                result += range
            }
        }
        return result
    }
}

data class TorrentTransferSnapshot(
    val generation: Long = 0,
    val taskID: String = "",
    val magnet: String = "",
    val phase: TorrentTransferPhase = TorrentTransferPhase.Idle,
    val selectedPath: String? = null,
    val selectedBytes: Long = 0,
    val selectedSize: Long = 0,
    val totalBytes: Long = 0,
    val downloadRateBytesPerSecond: Long = 0,
    val peers: Int = 0,
    val seeds: Int = 0,
    val completedPieces: Int = 0,
    val totalPieces: Int = 0,
    val etaSeconds: Long? = null,
    val tracker: String? = null,
    val error: String? = null,
) {
    val progressFraction: Float
        get() = if (selectedSize <= 0) 0f else (selectedBytes.toDouble() / selectedSize.toDouble()).toFloat().coerceIn(0f, 1f)

    val statusText: String
        get() = when (phase) {
            TorrentTransferPhase.Idle -> "BT 尚未啟動"
            TorrentTransferPhase.Metadata -> "BT：正在取得磁力連結資訊…"
            TorrentTransferPhase.Selecting -> "BT：正在選擇播放集數…"
            TorrentTransferPhase.Downloading, TorrentTransferPhase.Buffering -> "BT 下載中：已緩衝 ${formatBytes(selectedBytes)} / ${formatBytes(selectedSize)}"
            TorrentTransferPhase.Ready -> "BT：已可播放 ${selectedPath.orEmpty()}"
            TorrentTransferPhase.Background -> "BT：背景下載中 ${formatBytes(selectedBytes)}"
            TorrentTransferPhase.Failed -> "BT 失敗：${error ?: "未知原因"}"
            TorrentTransferPhase.Cancelled -> "BT：已取消自動播放"
        }

    val detailText: String
        get() = buildList {
            if (downloadRateBytesPerSecond > 0) add("${formatRate(downloadRateBytesPerSecond)}")
            add("Peer $peers")
            if (seeds > 0) add("Seed $seeds")
            if (totalPieces > 0) add("Piece $completedPieces/$totalPieces")
            etaSeconds?.takeIf { it >= 0 }?.let { add("剩餘 ${formatDuration(it)}") }
        }.joinToString(" · ")

    companion object {
        fun formatBytes(bytes: Long): String {
            val value = bytes.coerceAtLeast(0)
            val mib = 1024L * 1024L
            val gib = 1024L * mib
            return when {
                value >= gib -> "${oneDecimal(value, gib)} GB"
                value >= 10L * mib -> "${value / mib} MB"
                else -> "${oneDecimal(value, mib)} MB"
            }
        }

        fun formatRate(bytesPerSecond: Long): String = "${oneDecimal(bytesPerSecond.coerceAtLeast(0), 1024L * 1024L)} MB/s"

        private fun oneDecimal(value: Long, unit: Long): String {
            val tenths = ((value.toDouble() / unit.toDouble()) * 10.0).toLong()
            return "${tenths / 10}.${tenths % 10}"
        }

        private fun formatDuration(seconds: Long): String = when {
            seconds >= 3_600 -> "${seconds / 3_600}:${((seconds % 3_600) / 60).toString().padStart(2, '0')}:${(seconds % 60).toString().padStart(2, '0')}"
            else -> "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
        }
    }
}

data class TorrentPlaybackUiState(
    val activeGeneration: Long = 0,
    val snapshot: TorrentTransferSnapshot = TorrentTransferSnapshot(),
) {
    fun accept(incoming: TorrentTransferSnapshot): TorrentPlaybackUiState =
        if (incoming.generation == activeGeneration) copy(snapshot = incoming) else this
}

data class TorrentCachedDownload(
    val id: String,
    val title: String,
    val subtitle: String,
    val bytes: Long,
    val lastAccessEpochSeconds: Long,
)

data class TorrentPlayableStream(
    val generation: Long,
    val taskID: String,
    val url: String,
    val selectedPath: String,
    val quality: String,
)

data class TorrentDownloadManagerState(
    val isVisible: Boolean = false,
    val items: List<TorrentCachedDownload> = emptyList(),
    val focusedIndex: Int = 0,
    val pendingAction: String? = null,
) {
    fun opened(downloads: List<TorrentCachedDownload>) = copy(
        isVisible = true,
        items = downloads,
        focusedIndex = focusedIndex.coerceIn(0, (downloads.size - 1).coerceAtLeast(0)),
        pendingAction = null,
    )

    fun reduce(command: RemoteCommand): TorrentDownloadManagerState = when (command) {
        RemoteCommand.Up -> copy(focusedIndex = (focusedIndex - 1).coerceAtLeast(0), pendingAction = null)
        RemoteCommand.Down -> copy(focusedIndex = (focusedIndex + 1).coerceAtMost((items.size - 1).coerceAtLeast(0)), pendingAction = null)
        RemoteCommand.Select, RemoteCommand.Menu -> items.getOrNull(focusedIndex)?.let { copy(pendingAction = "delete:${it.id}") } ?: this
        RemoteCommand.Back, RemoteCommand.Home -> copy(isVisible = false, pendingAction = null)
        else -> this
    }

    fun deleted(id: String): TorrentDownloadManagerState {
        val remaining = items.filterNot { it.id == id }
        return copy(
            items = remaining,
            focusedIndex = focusedIndex.coerceIn(0, (remaining.size - 1).coerceAtLeast(0)),
            pendingAction = null,
        )
    }

    fun clearAction(): TorrentDownloadManagerState = copy(pendingAction = null)
}

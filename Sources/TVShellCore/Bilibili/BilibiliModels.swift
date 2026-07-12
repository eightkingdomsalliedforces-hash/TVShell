import Foundation

public enum BilibiliSearchNormalizer {
    /// Bilibili's search index is predominantly Simplified Chinese. Convert only
    /// for the request and keep the original Traditional Chinese text in the UI.
    public static func simplified(_ value: String) -> String {
        value.applyingTransform(StringTransform("Hant-Hans"), reverse: false) ?? value
    }
}

public struct BilibiliCredentials: Codable, Equatable, Sendable {
    public var cookie: String

    private enum CodingKeys: String, CodingKey { case cookie }
    private struct ImportedCookie: Decodable { var domain: String?; var name: String; var value: String }

    public init(cookie: String = "") {
        self.cookie = Self.normalizedCookie(from: cookie)
    }

    public init(importedText: String) {
        self.cookie = Self.normalizedCookie(from: importedText)
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        if let text = try? container.decode(String.self, forKey: .cookie) {
            self.init(importedText: text)
        } else if let entries = try? container.decode([ImportedCookie].self, forKey: .cookie) {
            self.init(importedText: entries.filter { entry in
                guard let domain = entry.domain?.lowercased() else { return true }
                return domain == "bilibili.com" || domain.hasSuffix(".bilibili.com")
            }.map { "\($0.name)=\($0.value)" }.joined(separator: "; "))
        } else {
            self.init(cookie: "")
        }
    }

    public func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(cookie, forKey: .cookie)
    }

    public static func environment(_ environment: [String: String] = ProcessInfo.processInfo.environment) -> BilibiliCredentials {
        BilibiliCredentials(cookie: environment["TVSHELL_BILIBILI_COOKIE"] ?? "")
    }

    public var isConfigured: Bool {
        cookie.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty == false
    }

    public var isAuthenticated: Bool {
        authenticationIssue == nil
    }

    public var authenticationIssue: String? {
        let names = Set(cookiePairs.keys.map { $0.lowercased() })
        let missing = ["SESSDATA", "bili_jct", "DedeUserID"].filter { names.contains($0.lowercased()) == false }
        return missing.isEmpty ? nil : "Cookie 缺少 \(missing.joined(separator: "、"))；請匯出包含 HttpOnly 的完整 bilibili.com Cookie"
    }

    public var requestHeaders: [String: String] {
        isConfigured ? ["Cookie": cookie] : [:]
    }

    public var csrfToken: String? {
        cookiePairs.first { $0.key.caseInsensitiveCompare("bili_jct") == .orderedSame }?.value
    }

    private var cookiePairs: [String: String] {
        cookie.split(separator: ";").reduce(into: [String: String]()) { result, component in
            let pair = component.split(separator: "=", maxSplits: 1).map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            guard pair.count == 2, pair[0].isEmpty == false, pair[1].isEmpty == false else { return }
            result[pair[0]] = pair[1]
        }
    }

    private static func normalizedCookie(from importedText: String) -> String {
        let trimmed = importedText.trimmingCharacters(in: .whitespacesAndNewlines)
        if let data = trimmed.data(using: .utf8),
           let objects = try? JSONSerialization.jsonObject(with: data) as? [[String: Any]] {
            let pairs = objects.compactMap { object -> String? in
                guard let name = object["name"] as? String, let value = object["value"] as? String else { return nil }
                if let domain = (object["domain"] as? String)?.lowercased(),
                   domain != "bilibili.com", domain.hasSuffix(".bilibili.com") == false { return nil }
                return "\(name)=\(value)"
            }
            if pairs.isEmpty == false { return canonical(pairs) }
        }
        let netscapePairs = trimmed.components(separatedBy: .newlines).compactMap { rawLine -> String? in
            let isHTTPOnly = rawLine.hasPrefix("#HttpOnly_")
            guard rawLine.hasPrefix("#") == false || isHTTPOnly else { return nil }
            let line = isHTTPOnly ? String(rawLine.dropFirst("#HttpOnly_".count)) : rawLine
            let fields = line.split(separator: "\t", omittingEmptySubsequences: false)
            guard fields.count >= 7 else { return nil }
            let domain = fields[0].lowercased()
            guard domain == "bilibili.com" || domain.hasSuffix(".bilibili.com") else { return nil }
            return "\(fields[5])=\(fields[6])"
        }
        if netscapePairs.isEmpty == false { return canonical(netscapePairs) }
        let withoutHeader = trimmed.replacingOccurrences(of: #"^\s*Cookie\s*:\s*"#, with: "", options: [.regularExpression, .caseInsensitive])
        let pairs = withoutHeader
            .replacingOccurrences(of: "\r", with: "")
            .replacingOccurrences(of: "\n", with: ";")
            .split(separator: ";")
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { $0.contains("=") }
        return canonical(pairs)
    }

    private static func canonical(_ pairs: [String]) -> String {
        guard pairs.isEmpty == false else { return "" }
        return pairs.joined(separator: "; ") + ";"
    }
}

public enum BilibiliDetailAction: String, CaseIterable, Equatable, Sendable {
    case play
    case like
    case coin
    case favorite

    public var title: String {
        switch self {
        case .play: "播放"
        case .like: "讚"
        case .coin: "投幣"
        case .favorite: "收藏"
        }
    }

    public var symbolName: String {
        switch self {
        case .play: "play.fill"
        case .like: "hand.thumbsup.fill"
        case .coin: "b.circle.fill"
        case .favorite: "star.fill"
        }
    }
}

public enum BilibiliItemKind: String, Codable, Equatable, Sendable {
    case bangumi
    case video
}

public enum BilibiliContentMode: String, Codable, Equatable, CaseIterable, Sendable {
    case all
    case bangumi
    case video

    public var title: String {
        switch self {
        case .all: "全部"
        case .bangumi: "番劇"
        case .video: "一般影片"
        }
    }

    public var next: BilibiliContentMode {
        switch self {
        case .all: .bangumi
        case .bangumi: .video
        case .video: .all
        }
    }
}

public enum BilibiliTopTab: String, Codable, Equatable, CaseIterable, Sendable {
    case recommended
    case popular
    case ranking
    case dynamic
    case profile
    case search

    public var title: String {
        switch self {
        case .recommended: "推薦"
        case .popular: "熱門"
        case .ranking: "排行榜"
        case .dynamic: "動態"
        case .profile: "我的"
        case .search: "搜尋"
        }
    }

    public var previous: BilibiliTopTab {
        guard let index = Self.allCases.firstIndex(of: self), index > 0 else { return self }
        return Self.allCases[index - 1]
    }

    public var next: BilibiliTopTab {
        guard let index = Self.allCases.firstIndex(of: self), index < Self.allCases.count - 1 else { return self }
        return Self.allCases[index + 1]
    }
}

public struct BilibiliSeason: Identifiable, Codable, Equatable, Sendable {
    public var id: Int
    public var itemKind: BilibiliItemKind
    public var aid: Int?
    public var bvid: String?
    public var title: String
    public var subtitle: String?
    public var coverURL: URL?
    public var badge: String?
    public var totalText: String?

    public init(
        id: Int,
        itemKind: BilibiliItemKind = .bangumi,
        aid: Int? = nil,
        bvid: String? = nil,
        title: String,
        subtitle: String? = nil,
        coverURL: URL? = nil,
        badge: String? = nil,
        totalText: String? = nil
    ) {
        self.id = id
        self.itemKind = itemKind
        self.aid = aid
        self.bvid = bvid
        self.title = title
        self.subtitle = subtitle
        self.coverURL = coverURL
        self.badge = badge
        self.totalText = totalText
    }
}

public struct BilibiliEpisode: Identifiable, Codable, Equatable, Sendable {
    public var id: Int
    public var aid: Int?
    public var cid: Int?
    public var bvid: String?
    public var title: String
    public var longTitle: String
    public var coverURL: URL?
    public var badge: String?
    public var number: Int

    public init(
        id: Int,
        aid: Int? = nil,
        cid: Int? = nil,
        bvid: String? = nil,
        title: String,
        longTitle: String = "",
        coverURL: URL? = nil,
        badge: String? = nil,
        number: Int
    ) {
        self.id = id
        self.aid = aid
        self.cid = cid
        self.bvid = bvid
        self.title = title
        self.longTitle = longTitle
        self.coverURL = coverURL
        self.badge = badge
        self.number = number
    }
}

public struct BilibiliSeasonDetail: Identifiable, Codable, Equatable, Sendable {
    public var id: Int
    public var title: String
    public var coverURL: URL?
    public var subtitle: String?
    public var evaluate: String?
    public var ratingScore: Double?
    public var views: Int?
    public var danmaku: Int?
    public var episodes: [BilibiliEpisode]

    public init(
        id: Int,
        title: String,
        coverURL: URL? = nil,
        subtitle: String? = nil,
        evaluate: String? = nil,
        ratingScore: Double? = nil,
        views: Int? = nil,
        danmaku: Int? = nil,
        episodes: [BilibiliEpisode]
    ) {
        self.id = id
        self.title = title
        self.coverURL = coverURL
        self.subtitle = subtitle
        self.evaluate = evaluate
        self.ratingScore = ratingScore
        self.views = views
        self.danmaku = danmaku
        self.episodes = episodes
    }
}

public struct BilibiliPlaybackStream: Equatable, Sendable {
    public var url: URL
    public var quality: String
    public var headers: [String: String]
    public var durationSeconds: Double?

    public init(url: URL, quality: String, headers: [String: String], durationSeconds: Double? = nil) {
        self.url = url
        self.quality = quality
        self.headers = headers
        self.durationSeconds = durationSeconds
    }
}

public struct BilibiliProfile: Codable, Equatable, Sendable {
    public var mid: Int
    public var name: String
    public var faceURL: URL?
    public var coins: Double
    public var dynamicCount: Int
    public var following: Int
    public var followers: Int

    public init(
        mid: Int,
        name: String,
        faceURL: URL? = nil,
        coins: Double = 0,
        dynamicCount: Int = 0,
        following: Int = 0,
        followers: Int = 0
    ) {
        self.mid = mid
        self.name = name
        self.faceURL = faceURL
        self.coins = coins
        self.dynamicCount = dynamicCount
        self.following = following
        self.followers = followers
    }
}

public struct BilibiliRelationStats: Codable, Equatable, Sendable {
    public var following: Int
    public var followers: Int

    public init(following: Int, followers: Int) {
        self.following = following
        self.followers = followers
    }
}

public enum BilibiliRuntimePhase: String, Codable, Equatable, Sendable {
    case browsing
    case detail
    case playing
}

public struct BilibiliRuntimeState: Equatable, Sendable {
    public private(set) var phase: BilibiliRuntimePhase
    public private(set) var focusedSeasonIndex: Int
    public private(set) var focusedEpisodeIndex: Int
    private var seasonCount: Int
    private var episodeCount: Int

    public init(
        phase: BilibiliRuntimePhase = .browsing,
        focusedSeasonIndex: Int = 0,
        focusedEpisodeIndex: Int = 0,
        seasonCount: Int = 0,
        episodeCount: Int = 0
    ) {
        self.phase = phase
        self.seasonCount = max(seasonCount, 0)
        self.episodeCount = max(episodeCount, 0)
        self.focusedSeasonIndex = min(max(focusedSeasonIndex, 0), max(seasonCount - 1, 0))
        self.focusedEpisodeIndex = min(max(focusedEpisodeIndex, 0), max(episodeCount - 1, 0))
    }

    public mutating func updateSeasonCount(_ count: Int) {
        seasonCount = max(count, 0)
        focusedSeasonIndex = min(focusedSeasonIndex, max(seasonCount - 1, 0))
    }

    public mutating func updateEpisodeCount(_ count: Int) {
        episodeCount = max(count, 0)
        focusedEpisodeIndex = min(focusedEpisodeIndex, max(episodeCount - 1, 0))
    }

    public mutating func resetToBrowsing() {
        phase = .browsing
    }

    public mutating func openDetail() {
        if seasonCount > 0 {
            phase = .detail
        }
    }

    public mutating func openPlayer() {
        if episodeCount > 0 {
            phase = .playing
        }
    }

    public mutating func closePlayer() {
        phase = .detail
    }

    public mutating func applyBrowsing(_ command: RemoteCommand, columns: Int) {
        let columnStep = max(columns, 1)
        switch command {
        case .left:
            focusedSeasonIndex = max(0, focusedSeasonIndex - 1)
        case .right:
            focusedSeasonIndex = min(max(seasonCount - 1, 0), focusedSeasonIndex + 1)
        case .up:
            focusedSeasonIndex = max(0, focusedSeasonIndex - columnStep)
        case .down:
            focusedSeasonIndex = min(max(seasonCount - 1, 0), focusedSeasonIndex + columnStep)
        default:
            break
        }
    }

    public mutating func applyDetail(_ command: RemoteCommand, columns: Int) {
        let columnStep = max(columns, 1)
        switch command {
        case .left:
            focusedEpisodeIndex = max(0, focusedEpisodeIndex - 1)
        case .right:
            focusedEpisodeIndex = min(max(episodeCount - 1, 0), focusedEpisodeIndex + 1)
        case .up:
            focusedEpisodeIndex = max(0, focusedEpisodeIndex - columnStep)
        case .down:
            focusedEpisodeIndex = min(max(episodeCount - 1, 0), focusedEpisodeIndex + columnStep)
        default:
            break
        }
    }
}

import Foundation

/// Presents Bilibili's licensed PGC catalogue through the common Anime app.
/// General Bilibili videos are deliberately excluded here and remain in the
/// Bilibili app (while its search can still return both kinds of content).
public struct BilibiliAnimeSourceProvider: AnimeMediaSourceAdapter {
    public let id = "bilibili-bangumi"
    public let displayName = "Bilibili 番劇"
    public let resolverKind: AnimeResolverKind = .http

    private let provider: any BilibiliBangumiProviding

    public init(provider: any BilibiliBangumiProviding) {
        self.provider = provider
    }

    public init(
        credentials: BilibiliCredentials = .environment(),
        transport: any AnimeHTTPTransport = URLSessionAnimeHTTPTransport()
    ) {
        provider = BilibiliBangumiProvider(credentials: credentials, transport: transport)
    }

    public func search(_ query: AnimeSearchQuery) async throws -> [AnimeSearchResult] {
        try await provider.search(keyword: query.keyword)
            .filter { $0.itemKind == .bangumi }
            .map { season in
                AnimeSearchResult(
                    id: "bilibili-season-\(season.id)",
                    title: season.title,
                    subtitle: season.subtitle,
                    coverURL: season.coverURL,
                    episodeCount: Self.episodeCount(from: season.totalText),
                    episodes: []
                )
            }
    }

    public func episodes(for result: AnimeSearchResult) async throws -> [AnimeEpisode] {
        guard let seasonID = Self.trailingInteger(in: result.id) else {
            throw BilibiliAPIError.missingData("season id")
        }
        let detail = try await provider.detail(seasonID: seasonID)
        return detail.episodes.map { episode in
            let label = episode.longTitle.trimmingCharacters(in: .whitespacesAndNewlines)
            return AnimeEpisode(
                id: "bilibili-episode-\(episode.id)",
                title: label.isEmpty ? "第 \(episode.number) 話" : "第 \(episode.number) 話 · \(label)",
                number: episode.number,
                identity: AnimeEpisodeIdentity(
                    providerID: id,
                    subjectID: "\(seasonID)",
                    episodeID: "\(episode.id)",
                    subjectAliases: [detail.title]
                )
            )
        }
        .sorted { $0.number < $1.number }
    }

    public func streams(for episode: AnimeEpisode) async throws -> [AnimeStreamCandidate] {
        guard let episodeID = Int(episode.identity.episodeID) else {
            throw BilibiliAPIError.missingData("episode id")
        }
        let bilibiliEpisode = BilibiliEpisode(
            id: episodeID,
            title: "\(episode.number)",
            longTitle: episode.title,
            number: episode.number
        )
        let stream = try await provider.playback(episode: bilibiliEpisode)
        return [AnimeStreamCandidate(
            url: stream.url,
            quality: stream.quality,
            priority: 120,
            headers: stream.headers
        )]
    }

    private static func trailingInteger(in value: String) -> Int? {
        Int(value.split(separator: "-").last ?? "")
    }

    private static func episodeCount(from text: String?) -> Int? {
        guard let text,
              let range = text.range(of: #"\d+"#, options: .regularExpression)
        else { return nil }
        return Int(text[range])
    }
}

import Foundation

public struct YouTubeCredentials: Codable, Equatable, Sendable {
    public var apiKey: String

    public init(apiKey: String) {
        self.apiKey = apiKey
    }

    public static func environment(_ environment: [String: String] = ProcessInfo.processInfo.environment) -> YouTubeCredentials {
        YouTubeCredentials(apiKey: environment["TVSHELL_YOUTUBE_API_KEY"] ?? "")
    }

    public var isConfigured: Bool {
        apiKey.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty == false
    }
}

public struct YouTubeVideo: Identifiable, Codable, Equatable, Sendable {
    public var id: String
    public var title: String
    public var channelTitle: String
    public var description: String
    public var thumbnailURL: URL?

    public init(
        id: String,
        title: String,
        channelTitle: String,
        description: String = "",
        thumbnailURL: URL? = nil
    ) {
        self.id = id
        self.title = title
        self.channelTitle = channelTitle
        self.description = description
        self.thumbnailURL = thumbnailURL
    }
}

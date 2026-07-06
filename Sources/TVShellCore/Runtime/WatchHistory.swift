import Foundation

public enum WatchHistoryKind: String, Codable, Equatable, Sendable {
    case anime
    case youtube
    case media
    case web
}

public struct WatchHistoryEntry: Identifiable, Codable, Equatable, Sendable {
    public var id: UUID
    public var title: String
    public var subtitle: String?
    public var kind: WatchHistoryKind
    public var updatedAt: Date

    public init(
        id: UUID = UUID(),
        title: String,
        subtitle: String? = nil,
        kind: WatchHistoryKind,
        updatedAt: Date = Date()
    ) {
        self.id = id
        self.title = title
        self.subtitle = subtitle
        self.kind = kind
        self.updatedAt = updatedAt
    }
}

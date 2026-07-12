public enum AnimeWatchHistory {
    public static func entries(from history: [WatchHistoryEntry]) -> [WatchHistoryEntry] {
        var seen = Set<String>()
        return history
            .filter { $0.kind == .anime && $0.mediaID?.isEmpty == false }
            .sorted { $0.updatedAt > $1.updatedAt }
            .filter { entry in
                seen.insert(entry.mediaID ?? entry.id.uuidString).inserted
            }
    }
}

public enum YouTubeRuntimePhase: String, Codable, Equatable, Sendable {
    case browsing
    case playing
}

public struct YouTubeRuntimeState: Equatable, Sendable {
    public private(set) var focusedIndex: Int
    public private(set) var phase: YouTubeRuntimePhase
    private var itemCount: Int

    public init(itemCount: Int, focusedIndex: Int = 0, phase: YouTubeRuntimePhase = .browsing) {
        self.itemCount = max(itemCount, 0)
        self.focusedIndex = min(max(focusedIndex, 0), max(itemCount - 1, 0))
        self.phase = phase
    }

    public mutating func updateItemCount(_ count: Int) {
        itemCount = max(count, 0)
        focusedIndex = min(focusedIndex, max(itemCount - 1, 0))
    }

    public mutating func apply(_ command: RemoteCommand) {
        switch phase {
        case .browsing:
            switch command {
            case .left, .up:
                focusedIndex = max(0, focusedIndex - 1)
            case .right, .down:
                focusedIndex = min(max(itemCount - 1, 0), focusedIndex + 1)
            case .select:
                if itemCount > 0 {
                    phase = .playing
                }
            default:
                break
            }
        case .playing:
            if command == .back {
                phase = .browsing
            }
        }
    }
}

public enum YouTubeTopTab: String, CaseIterable, Codable, Equatable, Sendable {
    case recommended
    case popular
    case subscriptions
    case history
    case search

    public var title: String {
        switch self {
        case .recommended: "推薦"
        case .popular: "熱門"
        case .subscriptions: "訂閱"
        case .history: "記錄"
        case .search: "搜尋"
        }
    }

    public var symbolName: String? {
        self == .search ? "magnifyingglass" : nil
    }
}

public struct YouTubeTopNavigationState: Equatable, Sendable {
    public private(set) var selectedIndex: Int
    public private(set) var isNavigationFocused: Bool

    public init(selectedIndex: Int = 0, isNavigationFocused: Bool = false) {
        self.selectedIndex = min(max(selectedIndex, 0), YouTubeTopTab.allCases.count - 1)
        self.isNavigationFocused = isNavigationFocused
    }

    public var selectedTab: YouTubeTopTab {
        YouTubeTopTab.allCases[selectedIndex]
    }

    public mutating func move(_ command: RemoteCommand) {
        switch command {
        case .left: selectedIndex = max(0, selectedIndex - 1)
        case .right: selectedIndex = min(YouTubeTopTab.allCases.count - 1, selectedIndex + 1)
        default: break
        }
    }

    public mutating func enterNavigation() {
        isNavigationFocused = true
    }

    public mutating func enterContent() {
        isNavigationFocused = false
    }
}

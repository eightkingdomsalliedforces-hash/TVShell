public enum WebRemoteMode: String, Codable, Equatable, Sendable {
    case keyboard
    case domFocus
    case scroll
    case mouse

    public var title: String {
        switch self {
        case .keyboard: "鍵盤"
        case .domFocus: "焦點"
        case .scroll: "捲動"
        case .mouse: "滑鼠"
        }
    }

    public var next: WebRemoteMode {
        switch self {
        case .keyboard: .domFocus
        case .domFocus: .scroll
        case .scroll: .mouse
        case .mouse: .keyboard
        }
    }
}

public enum SettingsFocus: String, Codable, Equatable, Sendable {
    case scale
    case wallpaper
    case webZoom
    case videoSource

    public var next: SettingsFocus {
        switch self {
        case .scale: .wallpaper
        case .wallpaper: .webZoom
        case .webZoom: .videoSource
        case .videoSource: .scale
        }
    }

    public var previous: SettingsFocus {
        switch self {
        case .scale: .videoSource
        case .wallpaper: .scale
        case .webZoom: .wallpaper
        case .videoSource: .webZoom
        }
    }
}

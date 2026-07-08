public indirect enum RemoteCommand: Equatable, Codable, Sendable {
    case up
    case down
    case left
    case right
    case select
    case back
    case home
    case menu
    case playPause
    case rewind
    case fastForward
    case volumeUp
    case volumeDown
    case mute
    case longPress(RemoteCommand)
}

extension RemoteCommand: CustomStringConvertible {
    public var description: String {
        switch self {
        case .up: "up"
        case .down: "down"
        case .left: "left"
        case .right: "right"
        case .select: "select"
        case .back: "back"
        case .home: "home"
        case .menu: "menu"
        case .playPause: "playPause"
        case .rewind: "rewind"
        case .fastForward: "fastForward"
        case .volumeUp: "volumeUp"
        case .volumeDown: "volumeDown"
        case .mute: "mute"
        case let .longPress(command): "longPress(\(command))"
        }
    }
}

public extension RemoteCommand {
    static func networkRemoteCommand(named rawName: String) -> RemoteCommand? {
        let name = rawName
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: "-", with: "")
            .replacingOccurrences(of: "_", with: "")
            .lowercased()

        return switch name {
        case "up": .up
        case "down": .down
        case "left": .left
        case "right": .right
        case "ok", "enter", "select": .select
        case "back", "return": .back
        case "home": .home
        case "menu", "settings": .menu
        case "play", "pause", "playpause": .playPause
        case "rewind", "backward": .rewind
        case "fastforward", "forward": .fastForward
        case "volumeup", "volup": .volumeUp
        case "volumedown", "voldown": .volumeDown
        case "mute": .mute
        default: nil
        }
    }
}

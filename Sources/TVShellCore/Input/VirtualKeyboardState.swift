import Foundation

public enum VirtualKeyboardAction: Equatable, Sendable {
    case none
    case textChanged
    case submitted(String)
    case cancelled
}

public struct VirtualKeyboardKey: Identifiable, Equatable, Sendable {
    public var id: String { label }
    public var label: String
    public var value: String?
    public var kind: Kind

    public enum Kind: Equatable, Sendable {
        case character
        case space
        case delete
        case submit
        case cancel
    }

    public init(_ label: String, value: String? = nil, kind: Kind = .character) {
        self.label = label
        self.value = value
        self.kind = kind
    }
}

public struct VirtualKeyboardState: Equatable, Sendable {
    public private(set) var text: String
    public private(set) var focusedRow: Int
    public private(set) var focusedColumn: Int
    public let rows: [[VirtualKeyboardKey]]

    public init(text: String = "") {
        self.text = text
        self.focusedRow = 0
        self.focusedColumn = 0
        self.rows = [
            "1234567890".map { VirtualKeyboardKey(String($0)) },
            "QWERTYUIOP".map { VirtualKeyboardKey(String($0)) },
            "ASDFGHJKL".map { VirtualKeyboardKey(String($0)) },
            "ZXCVBNM".map { VirtualKeyboardKey(String($0)) },
            [
                VirtualKeyboardKey("空格", value: " ", kind: .space),
                VirtualKeyboardKey("刪除", kind: .delete),
                VirtualKeyboardKey("搜尋", kind: .submit),
                VirtualKeyboardKey("取消", kind: .cancel)
            ]
        ]
    }

    public var focusedKey: VirtualKeyboardKey {
        rows[focusedRow][focusedColumn]
    }

    public mutating func apply(_ command: RemoteCommand) -> VirtualKeyboardAction {
        switch command {
        case .left:
            focusedColumn = max(0, focusedColumn - 1)
            return .none
        case .right:
            focusedColumn = min(rows[focusedRow].count - 1, focusedColumn + 1)
            return .none
        case .up:
            focusedRow = max(0, focusedRow - 1)
            focusedColumn = min(focusedColumn, rows[focusedRow].count - 1)
            return .none
        case .down:
            focusedRow = min(rows.count - 1, focusedRow + 1)
            focusedColumn = min(focusedColumn, rows[focusedRow].count - 1)
            return .none
        case .back:
            if text.isEmpty {
                return .cancelled
            }
            text.removeLast()
            return .textChanged
        case .select:
            return activateFocusedKey()
        default:
            return .none
        }
    }

    private mutating func activateFocusedKey() -> VirtualKeyboardAction {
        let key = focusedKey
        switch key.kind {
        case .character, .space:
            text += key.value ?? key.label
            return .textChanged
        case .delete:
            if text.isEmpty == false {
                text.removeLast()
            }
            return .textChanged
        case .submit:
            let query = text.trimmingCharacters(in: .whitespacesAndNewlines)
            return query.isEmpty ? .none : .submitted(query)
        case .cancel:
            return .cancelled
        }
    }
}

import Foundation

public struct PlaybackOverlayState: Equatable, Sendable {
    public let autoHideInterval: TimeInterval
    public private(set) var lastInputDate: Date?

    public init(autoHideInterval: TimeInterval = 3, lastInputDate: Date? = nil) {
        self.autoHideInterval = max(0, autoHideInterval)
        self.lastInputDate = lastInputDate
    }

    public mutating func registerInput(at date: Date = Date()) {
        lastInputDate = date
    }

    public func isVisible(at date: Date = Date()) -> Bool {
        guard let lastInputDate else { return false }
        return date.timeIntervalSince(lastInputDate) < autoHideInterval
    }
}

public struct VolumeLevel: Equatable, Sendable {
    public let value: Double

    public init(_ value: Double) {
        self.value = min(max(value, 0), 1)
    }

    public func adjusted(by amount: Double) -> VolumeLevel {
        VolumeLevel(value + amount)
    }
}

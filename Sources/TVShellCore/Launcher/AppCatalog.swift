import Foundation

public enum CatalogMoveDirection: Equatable, Sendable {
    case left
    case right
}

public struct AppCatalog: Equatable, Sendable {
    public private(set) var apps: [TVAppProfile]

    public init(apps: [TVAppProfile]) {
        self.apps = apps
    }

    public var visibleApps: [TVAppProfile] {
        apps.filter(\.isVisibleOnHome)
    }

    public mutating func toggleVisibility(for id: UUID) {
        guard let index = apps.firstIndex(where: { $0.id == id }) else {
            return
        }
        apps[index].isVisibleOnHome.toggle()
    }

    public mutating func moveApp(id: UUID, direction: CatalogMoveDirection) {
        guard let index = apps.firstIndex(where: { $0.id == id }) else {
            return
        }

        let targetIndex: Int
        switch direction {
        case .left:
            targetIndex = max(index - 1, 0)
        case .right:
            targetIndex = min(index + 1, apps.count - 1)
        }

        guard targetIndex != index else {
            return
        }

        apps.swapAt(index, targetIndex)
    }
}

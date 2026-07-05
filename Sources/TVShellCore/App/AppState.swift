import Foundation
import SwiftUI

@MainActor
public final class AppState: ObservableObject {
    @Published public var activeRuntime: ActiveRuntime = .launcher
    @Published public var focusedAppID: UUID?
    @Published public var lastCommand: RemoteCommand?
    @Published public var apps: [TVAppProfile]

    private let nativeRuntime = NativeAppRuntime()

    public init(apps: [TVAppProfile] = SeedApps.defaultApps) {
        self.apps = apps
        focusedAppID = apps.first?.id
    }

    public func handle(_ command: RemoteCommand) {
        lastCommand = command

        switch activeRuntime {
        case .launcher:
            handleLauncher(command)
        case .web, .native, .remoteLearning:
            handleRuntimeCommand(command)
        }
    }

    private func handleLauncher(_ command: RemoteCommand) {
        switch command {
        case .left:
            moveFocusedApp(by: -1)
        case .right:
            moveFocusedApp(by: 1)
        case .select:
            openFocusedApp()
        case .home:
            activeRuntime = .launcher
        default:
            break
        }
    }

    private func handleRuntimeCommand(_ command: RemoteCommand) {
        if command == .home {
            activeRuntime = .launcher
            NSApp.activate(ignoringOtherApps: true)
            return
        }

        if activeRuntime == .remoteLearning, command == .select {
            AccessibilityScanner.requestTrustPrompt()
            return
        }

        NotificationCenter.default.post(
            name: .tvShellRuntimeCommand,
            object: nil,
            userInfo: [RuntimeCommandNotification.commandKey: command]
        )
    }

    private func moveFocusedApp(by offset: Int) {
        guard let currentFocusedAppID = focusedAppID,
              let index = apps.firstIndex(where: { $0.id == currentFocusedAppID })
        else {
            focusedAppID = apps.first?.id
            return
        }

        let nextIndex = min(max(index + offset, 0), apps.count - 1)
        focusedAppID = apps[nextIndex].id
    }

    private func openFocusedApp() {
        guard let app = apps.first(where: { $0.id == focusedAppID }) else {
            return
        }

        switch app.target {
        case let .web(url) where url.scheme == "tv-shell" && url.host == "remote-learning":
            activeRuntime = .remoteLearning
        case .web:
            activeRuntime = .web(app)
        case .nativeApp:
            activeRuntime = .native(app)
            nativeRuntime.launch(app)
        }
    }
}

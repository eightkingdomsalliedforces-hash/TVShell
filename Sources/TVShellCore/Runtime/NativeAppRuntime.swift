import AppKit
import Foundation

public struct NativeLaunchRequest: Equatable, Sendable {
    public let bundleIdentifier: String

    public init?(profile: TVAppProfile) {
        guard case let .nativeApp(bundleIdentifier) = profile.target else {
            return nil
        }
        self.bundleIdentifier = bundleIdentifier
    }
}

@MainActor
public final class NativeAppRuntime {
    public init() {}

    public func launch(_ profile: TVAppProfile) {
        guard let request = NativeLaunchRequest(profile: profile),
              let appURL = NSWorkspace.shared.urlForApplication(withBundleIdentifier: request.bundleIdentifier)
        else {
            return
        }

        let configuration = NSWorkspace.OpenConfiguration()
        configuration.activates = true
        NSWorkspace.shared.openApplication(at: appURL, configuration: configuration) { _, _ in }
    }
}

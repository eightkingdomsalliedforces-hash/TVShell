import Foundation

public enum SeedApps {
    public static let defaultApps: [TVAppProfile] = [
        TVAppProfile(
            name: "YouTube",
            target: .web(URL(string: "https://www.youtube.com/tv")!),
            controlMode: .web
        ),
        TVAppProfile(
            name: "Apple",
            target: .web(URL(string: "https://www.apple.com")!),
            controlMode: .web
        ),
        TVAppProfile(
            name: "Safari",
            target: .nativeApp(bundleIdentifier: "com.apple.Safari"),
            controlMode: .hybridNative
        ),
        TVAppProfile(
            name: "Remote",
            target: .web(URL(string: "tv-shell://remote-learning")!),
            controlMode: .web
        )
    ]
}

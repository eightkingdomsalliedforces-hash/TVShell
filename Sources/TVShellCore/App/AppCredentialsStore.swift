import Foundation

public struct AppCredentialsSnapshot: Codable, Equatable, Sendable {
    public var youtube: YouTubeCredentials
    public var dandanplay: DandanplayCredentials
    public var bilibili: BilibiliCredentials

    public init(
        youtube: YouTubeCredentials = .environment(),
        dandanplay: DandanplayCredentials = .environment(),
        bilibili: BilibiliCredentials = .environment()
    ) {
        self.youtube = youtube
        self.dandanplay = dandanplay
        self.bilibili = bilibili
    }

    private enum CodingKeys: String, CodingKey {
        case youtube
        case dandanplay
        case bilibili
    }

    // A credentials file is intentionally allowed to contain only the services a
    // person uses. A blank template should never prevent the other API sections
    // from loading.
    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        youtube = try container.decodeIfPresent(YouTubeCredentials.self, forKey: .youtube)
            ?? YouTubeCredentials(apiKey: "")
        dandanplay = try container.decodeIfPresent(DandanplayCredentials.self, forKey: .dandanplay)
            ?? DandanplayCredentials(appID: "", appSecret: "")
        bilibili = try container.decodeIfPresent(BilibiliCredentials.self, forKey: .bilibili)
            ?? BilibiliCredentials(cookie: "")
    }

    public func importingBilibili(_ text: String) -> AppCredentialsSnapshot {
        var snapshot = self
        snapshot.bilibili = BilibiliCredentials(importedText: text)
        return snapshot
    }
}

public struct AppCredentialsStore: Sendable {
    public var fileURL: URL

    public init(fileURL: URL) {
        self.fileURL = fileURL
    }

    public static func applicationSupport() -> AppCredentialsStore {
        let directory = TVShellStorageMigration.resolvedApplicationSupportDirectory()
        return AppCredentialsStore(fileURL: directory.appending(path: "credentials.json"))
    }

    public static func userHome() -> AppCredentialsStore {
        AppCredentialsStore(fileURL: FileManager.default.homeDirectoryForCurrentUser.appendingPathComponent("credentials.json"))
    }

    public func load() throws -> AppCredentialsSnapshot? {
        guard FileManager.default.fileExists(atPath: fileURL.path) else {
            return nil
        }
        let data = try Data(contentsOf: fileURL)
        do {
            return try JSONDecoder().decode(AppCredentialsSnapshot.self, from: data)
        } catch {
            guard let text = String(data: data, encoding: .utf8),
                  let recovered = CredentialFileRecovery.snapshot(from: text)
            else { throw error }
            let backupURL = fileURL.appendingPathExtension("invalid.bak")
            if FileManager.default.fileExists(atPath: backupURL.path) == false {
                try data.write(to: backupURL, options: [.atomic])
            }
            try save(recovered)
            return recovered
        }
    }

    public func save(_ snapshot: AppCredentialsSnapshot) throws {
        try FileManager.default.createDirectory(
            at: fileURL.deletingLastPathComponent(),
            withIntermediateDirectories: true
        )
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        let data = try encoder.encode(snapshot)
        try data.write(to: fileURL, options: [.atomic])
    }

    public func ensureTemplate() throws {
        guard FileManager.default.fileExists(atPath: fileURL.path) == false else {
            return
        }
        try save(AppCredentialsSnapshot(
            youtube: YouTubeCredentials(apiKey: ""),
            dandanplay: DandanplayCredentials(appID: "", appSecret: ""),
            bilibili: BilibiliCredentials(cookie: "")
        ))
    }
}

private enum CredentialFileRecovery {
    static func snapshot(from text: String) -> AppCredentialsSnapshot? {
        let recognized = ["apiKey", "appID", "appSecret", "cookie"].contains { text.contains("\"\($0)\"") }
        guard recognized else { return nil }
        return AppCredentialsSnapshot(
            youtube: YouTubeCredentials(apiKey: quotedValue(named: "apiKey", in: text) ?? ""),
            dandanplay: DandanplayCredentials(
                appID: quotedValue(named: "appID", in: text) ?? "",
                appSecret: quotedValue(named: "appSecret", in: text) ?? ""
            ),
            bilibili: BilibiliCredentials(cookie: quotedValue(named: "cookie", in: text) ?? rawValue(named: "cookie", in: text) ?? "")
        )
    }

    private static func quotedValue(named field: String, in text: String) -> String? {
        let escaped = NSRegularExpression.escapedPattern(for: field)
        guard let regex = try? NSRegularExpression(
            pattern: "\\\"\(escaped)\\\"\\s*:\\s*(\\\"(?:\\\\.|[^\\\"\\\\])*\\\")"
        ) else { return nil }
        let range = NSRange(text.startIndex..<text.endIndex, in: text)
        guard let match = regex.firstMatch(in: text, range: range),
              let valueRange = Range(match.range(at: 1), in: text)
        else { return nil }
        return try? JSONDecoder().decode(String.self, from: Data(text[valueRange].utf8))
    }

    private static func rawValue(named field: String, in text: String) -> String? {
        guard let marker = text.range(of: "\"\(field)\"") else { return nil }
        let suffix = text[marker.upperBound...]
        guard let colon = suffix.firstIndex(of: ":") else { return nil }
        let valueStart = suffix.index(after: colon)
        let remainder = suffix[valueStart...]
        let valueEnd = remainder.firstIndex(where: { $0 == "\n" || $0 == "\r" || $0 == "}" }) ?? remainder.endIndex
        var value = String(remainder[..<valueEnd]).trimmingCharacters(in: .whitespacesAndNewlines)
        while value.last == "," { value.removeLast() }
        value = value.trimmingCharacters(in: .whitespacesAndNewlines)
        if value.hasPrefix("\"") && value.hasSuffix("\"") { value = String(value.dropFirst().dropLast()) }
        return value.isEmpty ? nil : value
    }
}

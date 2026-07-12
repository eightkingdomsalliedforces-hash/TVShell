import Foundation

public enum TVShellStorageMigrationResult: Equatable, Sendable {
    case migrated
    case destinationAlreadyExists
    case noLegacyData
}

public enum TVShellStorageMigration {
    public static func migrateApplicationSupport(
        baseURL: URL = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0],
        fileManager: FileManager = .default
    ) throws -> TVShellStorageMigrationResult {
        let destination = baseURL.appendingPathComponent("TVShell", isDirectory: true)
        if fileManager.fileExists(atPath: destination.path) {
            return .destinationAlreadyExists
        }

        let legacy = baseURL.appendingPathComponent("MacTV", isDirectory: true)
        guard fileManager.fileExists(atPath: legacy.path) else {
            return .noLegacyData
        }

        try fileManager.createDirectory(at: baseURL, withIntermediateDirectories: true)
        let temporary = baseURL.appendingPathComponent(".TVShell-migration-\(UUID().uuidString)", isDirectory: true)
        do {
            try fileManager.copyItem(at: legacy, to: temporary)
            guard fileManager.fileExists(atPath: temporary.path) else {
                throw CocoaError(.fileNoSuchFile)
            }
            try fileManager.moveItem(at: temporary, to: destination)
            return .migrated
        } catch {
            try? fileManager.removeItem(at: temporary)
            throw error
        }
    }

    public static func resolvedApplicationSupportDirectory(
        baseURL: URL = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0],
        fileManager: FileManager = .default
    ) -> URL {
        let destination = baseURL.appendingPathComponent("TVShell", isDirectory: true)
        if fileManager.fileExists(atPath: destination.path) {
            return destination
        }
        do {
            _ = try migrateApplicationSupport(baseURL: baseURL, fileManager: fileManager)
        } catch {
            let legacy = baseURL.appendingPathComponent("MacTV", isDirectory: true)
            if fileManager.fileExists(atPath: legacy.path) {
                return legacy
            }
        }
        return destination
    }
}

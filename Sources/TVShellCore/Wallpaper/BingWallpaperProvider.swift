import Foundation

public struct BingWallpaperMetadata: Codable, Equatable, Sendable {
    public let imageURL: URL
    public let copyright: String
    public let startDate: String
    public let cachedFileURL: URL

    public init(imageURL: URL, copyright: String, startDate: String, cachedFileURL: URL) {
        self.imageURL = imageURL
        self.copyright = copyright
        self.startDate = startDate
        self.cachedFileURL = cachedFileURL
    }
}

public struct BingWallpaperProvider: Sendable {
    public static let metadataURL = URL(string: "https://www.bing.com/HPImageArchive.aspx?format=js&idx=0&n=1")!

    private let transport: any AnimeHTTPTransport
    public let cacheDirectory: URL

    public init(
        transport: any AnimeHTTPTransport = URLSessionAnimeHTTPTransport(),
        cacheDirectory: URL? = nil
    ) {
        self.transport = transport
        self.cacheDirectory = cacheDirectory ?? Self.defaultCacheDirectory
    }

    public func fetch() async throws -> WallpaperSource {
        do {
            let responseData = try await transport.data(for: AnimeHTTPRequest(method: "GET", url: Self.metadataURL))
            let response = try JSONDecoder().decode(BingArchiveResponse.self, from: responseData)
            guard let image = response.images.first,
                  let remoteURL = URL(string: image.url, relativeTo: URL(string: "https://www.bing.com"))?.absoluteURL
            else {
                throw BingWallpaperError.missingImage
            }
            let imageData = try await transport.data(for: AnimeHTTPRequest(method: "GET", url: remoteURL))
            try FileManager.default.createDirectory(at: cacheDirectory, withIntermediateDirectories: true)
            let fileURL = cacheDirectory.appendingPathComponent("bing-daily.jpg")
            try imageData.write(to: fileURL, options: .atomic)
            let metadata = BingWallpaperMetadata(
                imageURL: remoteURL,
                copyright: image.copyright,
                startDate: image.startdate,
                cachedFileURL: fileURL
            )
            let encoded = try JSONEncoder().encode(metadata)
            try encoded.write(to: metadataFileURL, options: .atomic)
            return .bingDaily(fileURL)
        } catch {
            if let metadata = cachedMetadata(),
               FileManager.default.fileExists(atPath: metadata.cachedFileURL.path) {
                return .bingDaily(metadata.cachedFileURL)
            }
            throw error
        }
    }

    public func cachedMetadata() -> BingWallpaperMetadata? {
        guard let data = try? Data(contentsOf: metadataFileURL) else { return nil }
        return try? JSONDecoder().decode(BingWallpaperMetadata.self, from: data)
    }

    private var metadataFileURL: URL {
        cacheDirectory.appendingPathComponent("bing-daily.json")
    }

    private static var defaultCacheDirectory: URL {
        FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("TVShell/Wallpaper", isDirectory: true)
    }
}

public enum BingWallpaperError: Error, Equatable, Sendable {
    case missingImage
}

private struct BingArchiveResponse: Decodable {
    let images: [BingArchiveImage]
}

private struct BingArchiveImage: Decodable {
    let url: String
    let copyright: String
    let startdate: String
}

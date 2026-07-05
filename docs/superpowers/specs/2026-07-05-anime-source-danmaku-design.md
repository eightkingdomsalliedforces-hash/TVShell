# Anime Source And Danmaku Design

## Reference

The next media phase is inspired by open-ani/animeko:

- Bangumi account sync and watching progress.
- Multiple video source providers with automatic source selection.
- Danmaku aggregation through public danmaku services and Animeko's own server.
- Local cache/download and external/custom media sources.

Reference: https://github.com/open-ani/animeko

## Product Goal

TVShell should let a user open the Video app, search or choose an anime title, select an episode with a remote, and start playback with large-screen controls. The first usable version should support provider-based source parsing and Bangumi-style danmaku overlay without turning the macOS shell into a full desktop browser workflow.

## Module Layout

### Anime Library

`AnimeLibraryService` owns title search, season lists, episode metadata, artwork, and user-facing names. It should expose data already shaped for TV UI rows:

- continue watching
- recently updated
- search results
- season episodes
- source candidates

Bangumi integration should live behind `BangumiClient`, so login, subject lookup, episode mapping, and progress sync do not leak into the player.

### Source Providers

`AnimeSourceProvider` should be a protocol loaded by a registry:

```swift
public protocol AnimeSourceProvider {
    var id: String { get }
    var displayName: String { get }
    func search(_ query: AnimeSearchQuery) async throws -> [AnimeSearchResult]
    func episodes(for result: AnimeSearchResult) async throws -> [AnimeEpisode]
    func streams(for episode: AnimeEpisode) async throws -> [AnimeStreamCandidate]
}
```

The automatic selector should score candidates by resolution, subtitle language, latency, provider priority, and previous user choice. Native player playback should receive a resolved stream URL plus headers, not provider-specific objects.

### Danmaku

`DanmakuProvider` should aggregate timed comments from Bangumi-linked sources, Dandanplay-compatible sources, and optional custom endpoints:

```swift
public protocol DanmakuProvider {
    func comments(for episode: AnimeEpisodeIdentity) async throws -> [DanmakuComment]
}
```

The player should render danmaku in a SwiftUI overlay above `MediaPlayerSurface`. Remote commands should support danmaku on/off, opacity, density, speed, and font size.

### Player Runtime

The existing `MediaRuntimeView` remains the playback surface. The anime phase adds:

- episode picker before playback
- source picker when auto selection is uncertain
- danmaku overlay synchronized with `AVPlayer.currentTime()`
- resume position and progress sync
- retry/fallback when a stream candidate fails

## Remote-First Flow

1. Open `影片`.
2. Choose `本機影片`, `動畫源`, or `最近播放`.
3. Search with a TV keyboard or choose from recommendations.
4. Select season and episode.
5. Auto resolve best stream and danmaku.
6. During playback:
   - OK toggles controls.
   - Play/Pause toggles playback.
   - Left/Right seeks.
   - Up opens source/danmaku quick controls.
   - Back returns to episode list.
   - Home returns to launcher.

## Implementation Phases

1. Add pure models and provider protocols with checks.
2. Add a mock provider and TV episode picker UI.
3. Add danmaku overlay with local fixture comments.
4. Add Bangumi identity mapping and progress sync.
5. Add real provider adapters and source auto-selection.
6. Add cache/download manager.

## Constraints

- Provider adapters must be isolated from UI.
- Playback should remain usable when providers fail.
- Danmaku rendering must be optional and capped for 60fps.
- Legal and regional behavior must be surfaced to users through provider configuration.

# Compose Magnet Streaming and Player Parity Design

**Date:** 2026-07-15

## Goal

Finish real magnet streaming in the Windows and Android TV Compose products. A magnet is resolved, prioritised and downloaded inside TVShell, then the playable local file is handed to TVShell's built-in player. The experience must follow the macOS Swift anime runtime: the same route, remote controls, source picker, download HUD, cache manager, focus movement and 1920×1080 tvOS 18 visual scale.

The Swift macOS application remains Swift and keeps its existing aria2 engine. This design does not replace it with JVM code.

## Backend decision

Use FrostWire jlibtorrent 2.0.12.9 in the Compose products.

- The Java wrapper and platform JNI libraries are embedded in the Windows portable/installable distributions and Android APKs.
- Windows uses the x86-64 native artifact. Android packages arm, arm64, x86 and x86-64 artifacts. Local Compose macOS development selects the matching macOS artifact so tests and screenshots can run without an external daemon.
- Android minimum SDK becomes 24, which is the supported floor of the current jlibtorrent Android package.
- Users do not install aria2, VLC or another torrent application. No magnet is sent through `ACTION_VIEW`, Desktop browsing, or an OS file association.
- Swift macOS continues to use aria2 because replacing a working native implementation would create an unrelated migration and violate the product constraint.

An embedded aria2 Android cross-build was rejected because its NDK/static-dependency packaging is substantially more fragile. A Windows-only aria2 implementation was rejected because two independent Compose engines would make playback and cache behaviour diverge.

## Shared domain contract

Common Kotlin owns pure, tested models and policies:

- `TorrentTransferPhase`: idle, metadata, selecting, downloading, buffering, ready, background, failed and cancelled.
- `TorrentTransferSnapshot`: stable task id, magnet, selected path and size, verified selected-file bytes, total bytes, peers, seeds, speed, ETA, pieces, tracker and a user-facing failure reason.
- `TorrentCachedDownload`: manifest data and physical cache usage.
- `TorrentPlayableFile`: a loopback streaming URL plus the selected local path and original title/quality metadata that the internal player needs.
- `TorrentPlaybackEngine`: start, snapshot, consume-ready-file, keep-in-background, cancel-autoplay, cached-download list and delete.
- `TorrentFileSelector`: video-extension filtering and deterministic episode scoring.
- `TorrentReadinessPolicy`: requires a contiguous verified prefix, plus a verified suffix for formats that may keep their index at the end. It never uses the disappearance of an `.aria2` marker as a readiness signal.
- `TorrentCachePolicy`: stable FNV-1a id, 20 GiB and seven-day eviction, with active/current task protection.

Supported files are MP4, M4V, MOV, MKV, WebM, AVI, TS and M2TS. Episode matching recognises `第01話`, `第01集`, `[01]`, `EP01`, `E01`, `- 01` and `_01`, and uses the largest playable file only when no episode marker is available.

## Engine lifecycle

1. The Anime source resolver returns the magnet with `resolver=torrent`.
2. `loadAnimeStream` intercepts either marker; the player never receives the magnet string.
3. The engine starts or reattaches a task under the platform cache root and obtains metadata through DHT/trackers.
4. Once metadata exists, only the selected episode file is enabled. The first 32 MiB and final 8 MiB pieces receive deadlines/high priority; remaining selected-file pieces use normal priority.
5. The engine publishes snapshots at no more than four updates per second. The UI polls the platform adapter and remains responsive.
6. When the readiness policy observes enough contiguous verified data (default target 48 MiB, capped by file size) and required tail pieces, it publishes a private `127.0.0.1` HTTP streaming URL.
7. TVShell changes only the media payload, not the Anime route, and gives that URL to the built-in JavaFX or Android player. The loopback server implements byte ranges, maps every requested range to torrent pieces, waits until those pieces verify, and raises their deadlines. It therefore never exposes sparse-file zeroes and seeking naturally reprioritises the new playback window.
8. Back cancels autoplay and returns to episodes, but may keep the download in the background. Deleting an active item stops its torrent handle before deleting data. Home always returns to the launcher and cannot be undone by a late callback.

Every start owns a monotonically increasing generation. UI callbacks and ready files from an old generation are ignored, preventing a completed background task from opening after the user left the player.

## Storage and recovery

- Windows: `%LOCALAPPDATA%/TVShell/Cache/Torrents` (with a user-home fallback).
- Android: `context.cacheDir/TVShell/Torrents`.
- Each task directory contains data, torrent/resume metadata and `.tvshell-download.json` with id, title, subtitle, magnet, selected path and last access.
- Cache cleanup runs at engine startup and after recording a task. It removes expired entries first, then least-recently-used entries until usage is at most 20 GiB. Active and current ids are protected.
- Existing manifests are restored on startup. Partial data is verified by libtorrent before resuming.
- Metadata timeout, zero peers, invalid magnet, disk errors and native-load failures produce explicit Traditional Chinese reasons and never leave an endless loading state. A timed-out task may remain marked as a background download and can be retried.

## Platform bridge

`PlatformAnimeService` gains non-blocking torrent methods. Desktop and Android services own a platform engine and expose snapshots, ready-file consumption and cache management through `PlatformAdapter`. The full shell and standalone Anime entry points delegate the same methods, so both products have identical behaviour.

The engine's piece-aware loopback range server is shared by both Compose platforms. JavaFX and Android `MediaPlayer` receive only that private HTTP URL, so their visual surfaces remain unchanged while reads and seeks are controlled by the torrent engine. The direct-media paths for CSS1 and Bilibili remain unchanged.

## UI parity

The Compose Anime runtime adds these macOS-equivalent surfaces:

- During metadata/download/buffering, the black built-in player remains on the same route and shows `BT 下載中` with selected-file bytes, total bytes, speed, peer count, piece progress and ETA. Failures include the actual reason and a Back-to-episodes path.
- The normal player HUD and the top-right status pill hide together after three seconds. Any remote action reveals them again.
- Menu in playback opens the existing source picker. Menu in the episode grid opens the BT download manager instead of the global control centre.
- The manager is a 980×760 dark panel over a 45% black scrim, with no visible scrollbar. Up/Down moves focus, the list follows focus, OK/Menu deletes after a two-step confirmation, and Back/Home closes it.
- Playback is black, aspect-fit and has no native controls. OK/PlayPause toggles, Left/Right/Rewind/FastForward seek ten seconds, Up/Down changes volume by a consistent step, Back returns to episodes and Home returns to launcher.
- All layouts keep the current 1920×1080 reference canvas, tvOS 18 surfaces, focus scale/lift and 1280×720 scaling behaviour.

## Tests and acceptance

Pure tests cover file selection (single file and season episodes 1/10/12), stable ids, status text, readiness, old-generation suppression, manager navigation and cache protection. A fake torrent backend drives metadata/downloading/buffering/ready/failure transitions before the real JNI adapter is connected. Platform builds prove every native artifact resolves and packages.

Final verification includes common tests, desktop tests/build, Android unit tests/builds, portable packaging, the existing Swift checks, a legal small magnet smoke test where network permits, and reference-size UI screenshots. Acceptance requires:

- no external magnet/player launch;
- a ready local file reaches the built-in player;
- real progress/error text replaces fixed fake values;
- remote navigation and HUD behaviour match the Swift route;
- no scrollbar appears;
- Windows portable and both Android TV APK variants contain the engine;
- cancellation, background download, cache deletion and restart recovery do not crash or trigger late playback.

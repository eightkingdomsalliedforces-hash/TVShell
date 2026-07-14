# Compose Magnet Streaming and Player Parity Implementation Plan

> Execute on `main` as explicitly requested. Follow red/green TDD, verify each batch, commit intentionally, push to `origin`, and inspect the release workflow.

**Goal:** Embed a real piece-aware BT engine in Windows and Android TV, send its loopback stream to the built-in player, and match the Swift Anime download/player UI.

**Architecture:** Pure common Kotlin defines torrent selection, state, readiness, cache and UI reducers. A JVM/Android shared jlibtorrent runtime owns metadata, priorities, verified-piece reads, a loopback HTTP range server and recovery manifests. Platform services expose it through the existing adapters; Compose polls snapshots and consumes a ready stream without changing routes.

**Dependencies:** Kotlin Multiplatform, FrostWire jlibtorrent 2.0.12.9, JavaFX Media, Android MediaPlayer, kotlinx.coroutines, Compose Multiplatform.

---

## Task 1: Lock the common torrent contract with failing tests

**Files:**
- Modify: `platforms/compose/shared-ui/src/commonTest/kotlin/dev/tvshell/shared/CrossPlatformAnimeTest.kt`
- Create: `platforms/compose/shared-ui/src/commonMain/kotlin/dev/tvshell/shared/anime/TorrentRuntime.kt`
- Modify: `platforms/compose/shared-ui/src/commonMain/kotlin/dev/tvshell/shared/anime/CrossPlatformAnime.kt`

1. Add tests for stable magnet ids, playable extension filtering, deterministic episode 1/10/12 season-pack selection, readiness ranges, formatted progress/failure text, generation suppression, cache active-id protection and download-manager remote reduction.
2. Run `./gradlew :shared-ui:allTests` and confirm the new tests fail because the contract does not exist.
3. Implement the smallest pure types and reducers required by those tests.
4. Run the same command and confirm it passes.

## Task 2: Add the embedded native dependency and fake-backed controller

**Files:**
- Modify: `platforms/compose/settings.gradle.kts`
- Modify: `platforms/compose/shared-ui/build.gradle.kts`
- Modify: `platforms/compose/shared-ui/src/commonMain/kotlin/dev/tvshell/shared/anime/PlatformAnimeService.kt`
- Create: `platforms/compose/shared-ui/src/commonTest/kotlin/dev/tvshell/shared/TorrentEngineControllerTest.kt`
- Create: `platforms/compose/shared-ui/src/commonMain/kotlin/dev/tvshell/shared/anime/TorrentEngineController.kt`

1. Add controller tests using a fake backend for metadata, selection, buffering, ready, error, stale generation and background transitions.
2. Run the focused test and confirm red.
3. Implement the backend boundary/controller and non-blocking service methods.
4. Add the pinned FrostWire repository and wrapper/platform dependencies; raise all Android min SDK declarations to 24.
5. Run common tests and dependency resolution on the current host.

## Task 3: Implement the jlibtorrent engine and piece-aware HTTP range server

**Files:**
- Create: `platforms/compose/shared-ui/src/jvmAndAndroidMain/kotlin/dev/tvshell/shared/anime/JvmTorrentPlaybackEngine.kt`
- Create: `platforms/compose/shared-ui/src/jvmAndAndroidMain/kotlin/dev/tvshell/shared/anime/TorrentRangeServer.kt`
- Create: `platforms/compose/shared-ui/src/jvmAndAndroidTest/kotlin/dev/tvshell/shared/anime/TorrentRangeServerTest.kt`
- Modify: `platforms/compose/shared-ui/build.gradle.kts`

1. Add HTTP tests for HEAD, full GET, valid ranges, invalid ranges, disconnected clients, range-piece waiting and deadline reprioritisation using a fake readable torrent.
2. Confirm red, then implement the loopback-only server.
3. Implement session start, magnet metadata fetch, selected-file priorities, head/tail deadlines, true status snapshots, manifest/resume storage, ready publication, cache listing/deletion and shutdown.
4. Add a native class-load smoke test and run JVM tests. Do not put a network swarm test in the default suite.

## Task 4: Connect desktop and Android services and every app adapter

**Files:**
- Modify: `platforms/compose/shared-ui/src/desktopMain/kotlin/dev/tvshell/shared/anime/DesktopAnimeService.kt`
- Modify: `platforms/compose/shared-ui/src/androidMain/kotlin/dev/tvshell/shared/anime/AndroidAnimeService.kt`
- Modify: `platforms/compose/shared-ui/src/commonMain/kotlin/dev/tvshell/shared/TVShellContract.kt`
- Modify: full-shell and standalone Anime desktop/Android adapter entry points.

1. Add delegation tests/compile contracts so both services intercept `magnet:` and `resolver=torrent`, while ordinary HTTP candidates still use the current player path.
2. Instantiate the engine with `%LOCALAPPDATA%/TVShell/Cache/Torrents` or `context.cacheDir/TVShell/Torrents`.
3. Expose snapshot, consume-ready-stream, cache list/delete, autoplay cancellation and background continuation through every adapter.
4. Ensure a ready candidate uses the loopback URL and is the only value given to JavaFX/Android MediaPlayer.
5. Run desktop and Android compilation/tests.

## Task 5: Add the macOS-parity BT UI and correct remote routing

**Files:**
- Modify: `platforms/compose/shared-ui/src/commonMain/kotlin/dev/tvshell/shared/TVShellApp.kt`
- Modify: common Anime browser/reducer files and tests.

1. Add red reducer tests for Menu on episodes opening downloads, manager focus movement/deletion/closing, playback Menu preserving source picker, Back cancelling autoplay, and Home returning to launcher.
2. Implement torrent snapshot polling and consume ready streams only for the active generation.
3. Replace the fixed 18% placeholder with selected-file/total progress plus speed, peers, pieces and ETA; render explicit failures and background state.
4. Add the 980×760 download manager with 45% scrim, no scrollbar and focus-following list.
5. Make main HUD and top-right pill hide together after three seconds; preserve black aspect-fit playback, ten-second seek and volume controls.
6. Run common UI/reducer tests and desktop visual smoke tests at 1920×1080 and 1280×720.

## Task 6: Packaging, regression and release verification

**Files:**
- Modify: `.github/workflows/release.yml`
- Modify: third-party notices if present.

1. Add CI assertions for the Windows jlibtorrent DLL and all four Android ABI libraries, plus Android min SDK 24.
2. Run the full Compose test/build/package suite, existing Swift checks and repository release checks.
3. Build the portable Windows shell and Anime distributions and all Android TV APK variants.
4. Inspect archives/APKs to prove the native engine is present and verify there is no external magnet dispatch path.
5. Compare local Compose Anime screenshots with the Swift reference and correct material spacing, type, focus or HUD differences before proceeding.
6. Review the complete diff against the design acceptance list, then commit on `main`, push `origin/main`, and monitor GitHub Actions through completion. Report artifact links and any environment-limited smoke test honestly.

# TVShell Input, Runtime, and Media Recovery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make all remotes and platform shells operable, repair official/media focus and APIs, add real icons and non-web extensions, then continue CSS1/BT playback portability.

**Architecture:** Raw input is normalized before UI state reducers. Platform activities/windows own reserved key translation and feed a shared command dispatcher. Media sources and interactions expose small provider protocols so Swift and Kotlin UI reducers remain testable without live services.

**Tech Stack:** Swift 6, SwiftUI/AppKit/WebKit/AVFoundation, Kotlin 2.3.20, Compose Multiplatform 1.11.1, Android SDK 36, CryptoKit, Bilibili HTTP APIs, RSS/BT, GitHub Actions.

## Global Constraints

- Work directly on `main`, preserve user changes, commit coherent slices, and push `origin/main` automatically.
- AniGamer advertising, entitlement, authentication, and region checks remain official and untouched.
- Android physical Home is not intercepted; expose system HOME role and an in-app remappable alternative.
- Android and Windows use the same TVShell 1920×1080 design contract and Compose UI.
- Every functional change follows red-green verification.

---

### Task 1: Persisted remote remapping

**Files:**
- Create: `Sources/TVShellCore/Input/RemoteMappingCenter.swift`
- Modify: `Sources/TVShellCore/Input/InputRouter.swift`
- Modify: `Sources/TVShellCore/Settings/RemoteLearningView.swift`
- Test: `Sources/TVShellChecks/main.swift`

**Interfaces:**
- Produces: `RemoteMappingCenter.command(for:)`, `armCapture(for:)`, `reset()`, and published capture state.

- [ ] Add a failing check proving an unknown raw event can be armed, learned as `.select`, persisted, and reloaded.
- [ ] Run `swift run TVShellChecks`; expect the remote-mapping-center check to fail because the type is absent.
- [ ] Implement atomic JSON persistence and captured-event consumption.
- [ ] Route every AppKit raw event through the center, including events with no fallback mapping.
- [ ] Add focusable Remote Learning command rows, capture status, and reset action.
- [ ] Run checks and commit `fix: connect custom remote mappings`.

### Task 2: Compose focus and Android packaging

**Files:**
- Modify: `platforms/compose/shared-ui/src/commonMain/kotlin/dev/tvshell/shared/TVShellApp.kt`
- Create: `platforms/compose/shared-ui/src/commonMain/kotlin/dev/tvshell/shared/RemoteCommandDispatcher.kt`
- Modify: `platforms/compose/android-app/src/main/kotlin/dev/tvshell/android/MainActivity.kt`
- Modify: Android manifests/resources under `platforms/compose/*/src/main`
- Test: `platforms/compose/shared-ui/src/commonTest/.../LauncherStateTest.kt`

**Interfaces:**
- Produces: `RemoteCommandDispatcher.dispatch(RemoteCommand)` and Android `KeyEvent.toRemoteCommand()`.

- [ ] Add failing common tests for external OK/Back dispatch and initial focus.
- [ ] Add Activity-level Android key mapping for DPAD_CENTER, ENTER, BACK, MENU, media, and long-Back Home alternative.
- [ ] Add a `FocusRequester` and request focus on first composition for Windows/common input.
- [ ] Add separate normal LAUNCHER filters, adaptive icons, TV banners, and scaling by reference resolution.
- [ ] Assemble Play, Launcher, Anime APKs and run desktop tests.
- [ ] Inspect merged manifests; Play must exclude HOME and all apps must include openable launch activities.
- [ ] Commit `fix: make Compose platform shells operable`.

### Task 3: AniGamer and scrollbar recovery

**Files:**
- Modify: `Sources/TVShellCore/Anime/AniGamerRemoteBridge.swift`
- Modify: `Sources/TVShellCore/Anime/AniGamerOfficialPlayerView.swift`
- Modify: `Sources/TVShellCore/Runtime/WebAppRuntimeView.swift`
- Test: `Sources/TVShellChecks/main.swift`

**Interfaces:**
- Produces: rating-gate semantic matcher and `window.tvShellOfficialKey(key, code)`.

- [ ] Add local HTML checks for the exact 15+ prompt and rejection of unrelated `同意` buttons.
- [ ] Add checks proving the script dispatches DOM ArrowLeft/ArrowRight/F/K/Escape without `currentTime`, m3u8, or ad skipping.
- [ ] Implement visible prompt scoping, mutation/iframe retries, player focus, and DOM keyboard dispatch.
- [ ] Inject global scrollbar-hiding CSS into web runtimes without disabling wheel/remote scrolling.
- [ ] Run checks and commit `fix: restore AniGamer official controls`.

### Task 4: Anime history, official sources, and YouTube navigation

**Files:**
- Modify: `Sources/TVShellCore/Anime/AnimeRuntimeView.swift`
- Create: `Sources/TVShellCore/Anime/AnimeHistoryAdapter.swift`
- Modify: `Sources/TVShellCore/YouTube/YouTubeRuntimeView.swift`
- Create: `Sources/TVShellCore/YouTube/YouTubeTopNavigationState.swift`
- Test: `Sources/TVShellChecks/main.swift`

**Interfaces:**
- Produces: history-to-title adapter and clamped top-nav reducer.

- [ ] Add failing tests for history card mapping/resume identity and YouTube top-nav focus transitions.
- [ ] Load history cards on tab activation and restore recommendations when leaving history.
- [ ] Make official-source entry load default AniGamer content and visibly animate focused controls.
- [ ] Replace YouTube's constant `focusedID` with reducer state and activate real tab loads/search/history.
- [ ] Run checks and commit `fix: make media navigation actionable`.

### Task 5: Real Bilibili tabs and detail actions

**Files:**
- Modify: `Sources/TVShellCore/Bilibili/BilibiliAPI.swift`
- Modify: `Sources/TVShellCore/Bilibili/BilibiliModels.swift`
- Modify: `Sources/TVShellCore/Bilibili/BilibiliRuntimeView.swift`
- Test: `Sources/TVShellChecks/main.swift`

**Interfaces:**
- Produces: `recommended()`, `ranking()`, `like(episode:)`, `coin(episode:)`, and a detail-action reducer.

- [ ] Add JSON fixtures and failing request/decoder tests for recommendation and ranking endpoints.
- [ ] Add failing reducer tests for moving focus from episodes to detail actions.
- [ ] Implement distinct tab loads and authenticated CSRF POST actions.
- [ ] Show action success/API failure and refresh action state.
- [ ] Run checks and commit `feat: activate Bilibili feeds and actions`.

### Task 6: Vector icon system

**Files:**
- Create: `Assets/Brand/*.svg`
- Create: `scripts/build-icons.sh`
- Modify: Android resource folders and `.github/workflows/release.yml`

**Interfaces:**
- Produces: TVShell/Anime platform icons, adaptive foreground/background, banners, and macOS icns.

- [ ] Add a check for required icon outputs and non-placeholder asset dimensions.
- [ ] Create deterministic vector masters matching the tvOS 18 dark/light rectangular visual system.
- [ ] Convert them with platform tools during packaging and reference them in manifests/Info.plist.
- [ ] Build macOS and Android packages and commit `feat: add TVShell platform icons`.

### Task 7: Declarative and native third-party apps

**Files:**
- Modify: `Sources/TVShellCore/Apps/PortableAppPackage.swift`
- Create: `Sources/TVShellCore/Apps/DeclarativeAppManifest.swift`
- Create: `Sources/TVShellCore/Runtime/DeclarativeAppRuntimeView.swift`
- Test: `Sources/TVShellChecks/main.swift`

**Interfaces:**
- Produces: signed `portableWeb` and `declarative` runtime kinds; native apps remain OS-installed separate processes.

- [ ] Add failing signature/permission tests for declarative pages and native-target rejection inside portable packages.
- [ ] Decode signed declarative rows, grids, actions, and approved network/media bridges.
- [ ] Route installed declarative apps to a native SwiftUI runtime.
- [ ] Document native APK/MSIX/.app registration and commit `feat: add declarative third-party apps`.

### Task 8: CSS1/BT cross-platform playback continuation

**Files:**
- Create Kotlin provider/cache/player contracts under `platforms/compose/shared-ui/src/commonMain/.../anime`
- Add Android implementations under `androidMain`
- Add Windows implementations under `desktopMain`
- Test: common and platform tests under `shared-ui/src/*Test`

**Interfaces:**
- Produces: selector search, RSS/BT results, torrent cache lifecycle, stream candidates, history, and player commands.

- [ ] Port existing Swift CSS1 fixtures into common Kotlin parser tests and make them fail.
- [ ] Port RSS/BT normalization and candidate ranking tests.
- [ ] Implement HTTP selector and RSS providers with per-host health/skip state.
- [ ] Add Android Media3 and Windows VLC/desktop player adapters plus automatic cache deletion.
- [ ] Build all platform artifacts, update CI, commit `feat: continue cross-platform anime playback`.

### Task 9: Final verification

- [ ] Run `swift run TVShellChecks`.
- [ ] Run `swift build -c release --product TVShell`.
- [ ] Run Compose desktop tests and all Android debug assemblies.
- [ ] Run `git diff --check`, confirm clean `main`, push `origin/main`, and monitor all GitHub Actions jobs to success.

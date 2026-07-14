# Compose Platform Parity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Windows and Android TV render and behave as the same TVShell product as the macOS reference, beginning with the launcher and shared navigation shell.

**Architecture:** macOS remains the visual and behavioural reference; the Kotlin Compose targets share the same implementation and platform adapters only provide operating-system services. Common reducers own remote focus, route transitions, error states, history, and persisted settings so Android TV and Windows cannot drift in navigation behaviour.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Android TV, Compose Desktop, Swift `TVShellChecks`, Kotlin common tests.

## Global Constraints

- Use the 1920×1080 values in `Contracts/tvshell-contract.json` and `TVShellDesign` as the canonical layout contract.
- All visible strings remain Traditional Chinese unless they are a product name.
- Android TV and Windows use one common Compose UI; adapters may not add product-specific routes or focus rules.
- Every remote action has matching keyboard and Android TV D-pad behaviour.
- MacOS is the feature and visual reference; platform-native permission and app-launch dialogs are the only allowed visual exceptions.
- Each task is test-first, commits independently, and pushes `main`.

---

### Task 1: Shared launcher scene and focus reducer

**Files:**
- Modify: `platforms/compose/shared-ui/src/commonMain/kotlin/dev/tvshell/shared/TVShellContract.kt`
- Modify: `platforms/compose/shared-ui/src/commonMain/kotlin/dev/tvshell/shared/TVShellApp.kt`
- Modify: `platforms/compose/shared-ui/src/commonTest/kotlin/dev/tvshell/shared/LauncherStateTest.kt`

**Interfaces:**
- Consumes: `ShellApp`, `RemoteCommand`, `TVShellDesign`.
- Produces: section-aware launcher focus that moves left/right within the dock and up/down between dock and recent history.

- [ ] **Step 1: Write the failing reducer test**

```kotlin
@Test
fun launcherMovesBetweenDockAndHistoryLikeMacTvshell() {
    var state = LauncherState(apps, historyCount = 2)
    state = state.reduce(RemoteCommand.Down)
    assertEquals(LauncherFocus.History, state.focus)
    state = state.reduce(RemoteCommand.Up)
    assertEquals(LauncherFocus.Apps, state.focus)
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :shared-ui:desktopTest --tests dev.tvshell.shared.LauncherStateTest`

Expected: compilation failure because `LauncherFocus` and `historyCount` do not exist.

- [ ] **Step 3: Add the smallest state model**

```kotlin
enum class LauncherFocus { Apps, History }

data class LauncherState(
    val apps: List<ShellApp>,
    val historyCount: Int = 0,
    val focus: LauncherFocus = LauncherFocus.Apps,
    val focusedIndex: Int = 0,
    val focusedHistoryIndex: Int = 0,
) {
    fun reduce(command: RemoteCommand): LauncherState = when (command) {
        RemoteCommand.Down -> if (focus == LauncherFocus.Apps && historyCount > 0) copy(focus = LauncherFocus.History) else this
        RemoteCommand.Up -> if (focus == LauncherFocus.History) copy(focus = LauncherFocus.Apps) else this
        else -> this
    }
}
```

- [ ] **Step 4: Render macOS-equivalent launcher regions**

In `Launcher`, render a wallpaper backdrop, focused-app title, rectangular dock, recent-history row, status hint, and focus-centred automatic scroll. Use `LauncherFocus` instead of one flat app row.

- [ ] **Step 5: Verify and commit**

Run: `./gradlew :shared-ui:desktopTest`

Expected: all common tests pass.

```sh
git add platforms/compose/shared-ui
git commit -m "feat: add shared tvOS launcher scene"
```

### Task 2: Shared tvOS 18 Compose design system

**Files:**
- Create: `platforms/compose/shared-ui/src/commonMain/kotlin/dev/tvshell/shared/TVShellVisualSystem.kt`
- Modify: `platforms/compose/shared-ui/src/commonMain/kotlin/dev/tvshell/shared/TVShellApp.kt`
- Test: `platforms/compose/shared-ui/src/commonTest/kotlin/dev/tvshell/shared/LauncherStateTest.kt`

**Interfaces:**
- Consumes: `TVShellDesign` and focus booleans from Task 1.
- Produces: shared `TVSurface`, `TVFocusScale`, `TVStatusClock`, and `TVScreenBackdrop` composables.

- [ ] **Step 1: Write a failing contract test**

```kotlin
@Test
fun tvosVisualTokensMatchTheCanonicalContract() {
    assertEquals(28f, TVShellVisual.CornerRadius)
    assertEquals(180, TVShellVisual.FocusAnimationMilliseconds)
}
```

- [ ] **Step 2: Run the focused test**

Run: `./gradlew :shared-ui:desktopTest --tests dev.tvshell.shared.LauncherStateTest`

Expected: compilation failure because `TVShellVisual` does not exist.

- [ ] **Step 3: Implement reusable surfaces**

Create common Compose modifiers that use the existing dark tvOS palette, rectangular card ratios, white focus surface, 1.06 focus scale, and 180 ms transitions. Replace duplicated tile and panel styling in `TVShellApp.kt` with those components.

- [ ] **Step 4: Verify and commit**

Run: `./gradlew :shared-ui:desktopTest :shared-ui:compileKotlinDesktop`

Expected: build succeeds without warnings.

```sh
git add platforms/compose/shared-ui
git commit -m "feat: share tvOS visual surfaces across platforms"
```

### Task 3: Media apps, history, and player behaviour

**Files:**
- Modify: `platforms/compose/shared-ui/src/commonMain/kotlin/dev/tvshell/shared/NativeMedia.kt`
- Modify: `platforms/compose/shared-ui/src/commonMain/kotlin/dev/tvshell/shared/TVShellApp.kt`
- Modify: `platforms/compose/shared-ui/src/commonTest/kotlin/dev/tvshell/shared/NativeMediaTest.kt`

**Interfaces:**
- Consumes: shared top-navigation and focus surface from Tasks 1–2.
- Produces: independent YouTube/Bilibili routes, real thumbnail cards, playback status, watch-history entries, and player remote commands.

- [ ] **Step 1: Write failing state tests**

```kotlin
@Test
fun mediaPlayerRecordsHistoryAndBackReturnsToBrowser() {
    var state = NativeMediaState(cardCount = 2)
    state = state.reduce(RemoteCommand.Down).reduce(RemoteCommand.Select)
    assertEquals("play:0", state.pendingAction)
}
```

- [ ] **Step 2: Run the focused test**

Run: `./gradlew :shared-ui:desktopTest --tests dev.tvshell.shared.NativeMediaTest`

Expected: compilation failure because `pendingAction` is not available.

- [ ] **Step 3: Implement explicit player route and history reducer**

Keep YouTube and Bilibili feeds independent, route `Select` to a player state rather than external navigation, map play/pause, seek, volume, source picker, and Back in a common reducer, then store a bounded watch-history list shown by Task 1.

- [ ] **Step 4: Render actual thumbnails**

Use Compose image loading already available to the project or a small platform-neutral image component. Empty and failed thumbnails retain the same 16:9 surface rather than changing layout.

- [ ] **Step 5: Verify and commit**

Run: `./gradlew :shared-ui:desktopTest :shared-ui:compileKotlinDesktop`

Expected: all media tests pass.

```sh
git add platforms/compose/shared-ui
git commit -m "feat: make shared media apps remote-first"
```

### Task 4: Anime source, episode, stream, danmaku, and cache parity

**Files:**
- Modify: `platforms/compose/shared-ui/src/commonMain/kotlin/dev/tvshell/shared/CrossPlatformAnimeBrowser.kt`
- Modify: `platforms/compose/shared-ui/src/commonMain/kotlin/dev/tvshell/shared/anime/*.kt`
- Modify: `platforms/compose/shared-ui/src/commonTest/kotlin/dev/tvshell/shared/CrossPlatformAnimeTest.kt`
- Modify: platform adapters under `androidMain`, `desktopMain`, `android-app`, and `anime-desktop`.

**Interfaces:**
- Consumes: player route and history support from Task 3.
- Produces: CSS1/BT/official source selection, source health, episodes, quality picker, player HUD, danmaku settings, and automatic cache cleanup.

- [ ] **Step 1: Write failing source-selection test**

```kotlin
@Test
fun animeEpisodeKeepsMasterStreamAndQualityAlternatives() {
    val state = AnimePlayerState().loaded(master, variants)
    assertEquals(master.url, state.selectedCandidate.url)
    assertEquals(3, state.candidates.size)
}
```

- [ ] **Step 2: Run the focused test**

Run: `./gradlew :shared-ui:desktopTest --tests dev.tvshell.shared.CrossPlatformAnimeTest`

Expected: failure until candidate selection state is exposed.

- [ ] **Step 3: Implement parity reducer and platform players**

Reuse the existing CSS1, BT, cache, and player modules; extend the Compose route to expose every macOS animation source and source-switch action. Android uses its media adapter; Windows uses the VLC adapter. Do not extract or bypass official AniGamer streams or advertising.

- [ ] **Step 4: Verify and commit**

Run: `./gradlew :shared-ui:desktopTest :anime-desktop:compileKotlin :anime-android-app:assembleDebug`

Expected: source, cache, and player tests pass and Android Anime APK builds.

```sh
git add platforms/compose
git commit -m "feat: align Compose anime playback with macOS"
```

### Task 5: Control center, settings, credentials, and release parity

**Files:**
- Modify: `platforms/compose/shared-ui/src/commonMain/kotlin/dev/tvshell/shared/TVShellApp.kt`
- Modify: `platforms/compose/shared-ui/src/commonMain/kotlin/dev/tvshell/shared/TVShellContract.kt`
- Modify: Android and Windows platform adapters.
- Modify: `.github/workflows/release.yml`
- Test: Kotlin common tests and `Sources/TVShellChecks/main.swift`.

**Interfaces:**
- Consumes: player and settings state from Tasks 1–4.
- Produces: focusable control center, settings routes, persisted preferences, credential import validation, and release artifacts for every platform.

- [ ] **Step 1: Write failing control-center test**

```kotlin
@Test
fun controlCenterChangesDanmakuWithoutLeavingPlayback() {
    val state = ControlCenterState().reduce(RemoteCommand.Right)
    assertTrue(state.danmakuOpacity > ControlCenterState().danmakuOpacity)
}
```

- [ ] **Step 2: Run focused test**

Run: `./gradlew :shared-ui:desktopTest --tests dev.tvshell.shared.LauncherStateTest`

Expected: compilation failure until the persisted control-center state exists.

- [ ] **Step 3: Implement settings and platform adapters**

Expose macOS-equivalent control center tiles, player danmaku controls, media volume, source management, keyboard/input preferences, cookie validation, and platform settings escape routes. Android launcher retains the safe Android Settings card; Windows retains Windows Settings.

- [ ] **Step 4: Verify release matrix and commit**

Run: `swift run TVShellChecks`

Run: `./gradlew -p platforms/compose :shared-ui:desktopTest :android-app:assemblePlayDebug :android-app:assembleLauncherDebug :anime-android-app:assembleDebug`

Expected: checks and all Android artifacts succeed; CI builds Windows MSI on its Windows runner and publishes all three platforms together.

```sh
git add platforms/compose .github/workflows/release.yml Sources/TVShellChecks/main.swift
git commit -m "feat: complete cross-platform TVShell controls"
```

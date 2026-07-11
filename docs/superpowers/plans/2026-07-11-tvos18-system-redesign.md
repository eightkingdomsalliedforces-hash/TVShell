# tvOS 18 System Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 將 MacTV 首頁、設定、管理頁、內容瀏覽、控制中心、鍵盤與播放器 HUD 統一改造成使用者指定的 tvOS 18 系統風格。

**Architecture:** 新增一個只負責顏色、表面、背景與焦點的共用 SwiftUI 視覺層，既有 `AppState`、controller 與遙控器狀態機維持不變。各功能頁逐批遷移到共用元件；每批都用 `TVShellChecks` 驗證結構、比例、焦點與既有功能契約，最後移除主要畫面的 Liquid Glass 呼叫。

**Tech Stack:** Swift 6、SwiftUI、AppKit、Swift Package Manager、既有 `TVShellChecks` executable test harness。

## Global Constraints

- 1920×1080 基準畫面保留左右 80 點、上下 60 點安全區，並沿用 `TVMetrics` 等比例縮放。
- 不加入 tvOS 26 Liquid Glass、粗白框或強烈發光。
- 列表焦點採近白底深色文字；影像焦點採約 1.06 倍縮放、上浮、提亮與柔和陰影。
- App 卡片比例為約 1.55:1，1920 寬畫面一列約顯示 6 張。
- 不修改來源解析、BT、彈幕、播放引擎、遙控器命令或既有 state machine。
- 桌布失敗時回退低飽和深色漸層；降低透明度時回退不透明深灰。
- 每一 task 完成後執行完整 `swift run TVShellChecks`，提交到 `main` 並 `git push origin main`。

---

### Task 1: 共用 tvOS 18 視覺系統

**Files:**
- Create: `Sources/TVShellCore/Design/TVOS18VisualSystem.swift`
- Modify: `Sources/TVShellCore/Design/TVMetrics.swift`
- Modify: `Sources/TVShellCore/Design/TVMotion.swift`
- Modify: `Sources/TVShellChecks/main.swift`

**Interfaces:**
- Consumes: `TVMetrics.scale`、SwiftUI `ViewModifier`、系統 Reduce Motion environment。
- Produces: `TVOS18SurfaceRole`、`TVOS18Backdrop`、`View.tvOS18Surface(role:isFocused:cornerRadius:)`、`View.tvOS18ContentFocus(isFocused:)`、`TVMetrics.appTileWidth`、`TVMetrics.appTileHeight`。

- [ ] **Step 1: Write the failing visual-system contracts**

在 `checkBigScreenViewsStayScrollableAndWindowIsResizable()` 前加入並從 `run()` 呼叫：

```swift
static func checkTVOS18VisualSystem() throws {
    let root = URL(fileURLWithPath: FileManager.default.currentDirectoryPath, isDirectory: true)
    let source = try String(contentsOf: root.appending(path: "Sources/TVShellCore/Design/TVOS18VisualSystem.swift"))
    try expect(source.contains("enum TVOS18SurfaceRole"), "tvOS 18 surfaces use semantic roles")
    try expect(source.contains("case row") && source.contains("case panel") && source.contains("case alert"), "tvOS 18 exposes row, panel, and alert surfaces")
    try expect(source.contains("reduceMotion"), "tvOS 18 focus respects Reduce Motion")
    try expect(source.contains("Color.white") && source.contains("Color.black"), "focused system rows can use light backgrounds and dark content")

    let metrics = TVMetrics(size: CGSize(width: 1_920, height: 1_080))
    try expect(abs(metrics.appTileWidth / metrics.appTileHeight - 1.55) < 0.02, "launcher app tiles use the approved 1.55:1 ratio")
    try expect(metrics.horizontalPadding >= 80, "tvOS layout keeps the horizontal safe area")
}
```

- [ ] **Step 2: Run the test to verify RED**

Run: `swift run TVShellChecks`

Expected: compilation fails because `appTileWidth`/`appTileHeight` and `TVOS18VisualSystem.swift` do not exist.

- [ ] **Step 3: Implement semantic surfaces and metrics**

Create `TVOS18VisualSystem.swift` with these public interfaces and behaviors:

```swift
import SwiftUI

public enum TVOS18SurfaceRole: Sendable {
    case content, row, panel, alert
}

public struct TVOS18Backdrop: View {
    public let accent: Color?
    public init(accent: Color? = nil) { self.accent = accent }
    public var body: some View {
        ZStack {
            Color(red: 0.055, green: 0.06, blue: 0.07)
            if let accent { accent.opacity(0.22) }
            LinearGradient(colors: [.black.opacity(0.05), .black.opacity(0.48)], startPoint: .top, endPoint: .bottom)
        }
        .ignoresSafeArea()
    }
}

public extension View {
    func tvOS18Surface(role: TVOS18SurfaceRole, isFocused: Bool = false, cornerRadius: CGFloat = 12) -> some View {
        modifier(TVOS18SurfaceModifier(role: role, isFocused: isFocused, cornerRadius: cornerRadius))
    }

    func tvOS18ContentFocus(isFocused: Bool) -> some View {
        modifier(TVOS18ContentFocusModifier(isFocused: isFocused))
    }
}
```

The row role uses `Color.white.opacity(0.94)` and dark foreground when focused, otherwise `Color.white.opacity(0.11)`. Panel uses `Color(red: 0.10, green: 0.11, blue: 0.12).opacity(0.94)`. Alert uses a light neutral fill. `TVOS18ContentFocusModifier` reads `@Environment(\.accessibilityReduceMotion)` and disables offset/scale when enabled.

Add to `TVMetrics`:

```swift
public var appTileWidth: Double { 222 * scale }
public var appTileHeight: Double { appTileWidth / 1.55 }
public var systemRowHeight: Double { 72 * scale }
```

Adjust `TVMotion.focus` to a restrained spring with response `0.26` and damping fraction `0.80`.

- [ ] **Step 4: Run tests and verify GREEN**

Run: `swift run TVShellChecks && git diff --check`

Expected: `TVShellChecks passed` and exit 0.

- [ ] **Step 5: Commit and push**

```bash
git add Sources/TVShellCore/Design/TVOS18VisualSystem.swift Sources/TVShellCore/Design/TVMetrics.swift Sources/TVShellCore/Design/TVMotion.swift Sources/TVShellChecks/main.swift
git commit -m "feat: add tvOS 18 visual system"
git push origin main
```

### Task 2: tvOS 18 Home Screen and rectangular App Dock

**Files:**
- Create: `Sources/TVShellCore/Launcher/TVOS18WallpaperView.swift`
- Modify: `Sources/TVShellCore/Launcher/LauncherView.swift`
- Modify: `Sources/TVShellCore/Launcher/AppCardView.swift`
- Modify: `Sources/TVShellChecks/main.swift`

**Interfaces:**
- Consumes: Task 1 `TVOS18Backdrop`, `tvOS18Surface`, `tvOS18ContentFocus`, AppState `wallpaperSource` and launcher focus state.
- Produces: `TVOS18WallpaperView(source:)`, rectangular `AppCardView`, bottom `TVOSAppDock`, compact `TVStatusClockOverlay`.

- [ ] **Step 1: Write failing launcher contracts**

Extend `checkBigScreenViewsStayScrollableAndWindowIsResizable()`:

```swift
try expect(launcher.contains("TVOS18WallpaperView(source: appState.wallpaperSource)"), "launcher renders the selected wallpaper edge to edge")
try expect(launcher.contains("TVOSHeroHeader") == false, "tvOS 18 home removes the oversized hero header")
try expect(launcher.contains("alignment: .bottom"), "launcher anchors the app dock at the bottom")
try expect(appCard.contains("metrics.appTileWidth") && appCard.contains("metrics.appTileHeight"), "launcher apps are rectangular tvOS 18 tiles")
try expect(appCard.contains("liquidGlassCard") == false, "launcher app tiles do not use Liquid Glass")
```

- [ ] **Step 2: Run to verify RED**

Run: `swift run TVShellChecks`

Expected: failure at the first new launcher contract.

- [ ] **Step 3: Implement wallpaper rendering**

Create `TVOS18WallpaperView` that switches on `WallpaperSource`: built-in palettes render a full-screen `LinearGradient`; local files render `NSImage(contentsOf:)` with `.scaledToFill()`; remote URLs use `AsyncImage` and show the same fallback gradient for empty/failure phases. Add a bottom dark gradient so Dock labels remain legible.

- [ ] **Step 4: Rebuild launcher and App tiles**

Replace the hero structure with a bottom-aligned vertical scroll layout:

```swift
ZStack(alignment: .bottom) {
    TVOS18WallpaperView(source: appState.wallpaperSource)
    ScrollViewReader { scrollProxy in
        ScrollView(.vertical) {
            VStack(spacing: 38 * metrics.scale) {
                Spacer(minLength: max(420 * metrics.scale, proxy.size.height * 0.56))
                Text(focusedApp?.name ?? "MacTV")
                    .font(.system(size: 30 * metrics.scale, weight: .semibold))
                    .foregroundStyle(.white)
                TVOSAppDock(
                    apps: appState.apps.filter(\.isVisibleOnHome),
                    focusedAppID: appState.focusedAppID,
                    metrics: dockMetrics
                )
                    .id("launcher-top")
                if appState.watchingHistory.isEmpty == false {
                    WatchHistoryRowView(
                        entries: appState.watchingHistory,
                        focusedEntryID: appState.focusedWatchHistoryID,
                        isFocused: appState.launcherFocus == .history,
                        metrics: metrics
                    )
                    .id("launcher-history")
                }
            }
            .padding(.horizontal, metrics.horizontalPadding)
            .padding(.bottom, 60 * metrics.scale)
        }
    }
}
```

`AppCardView` must frame its colored tile with `appTileWidth`/`appTileHeight`, use corner radius `18 * scale`, apply `tvOS18ContentFocus`, and show its title only while focused. Dock uses a dark `.panel` surface, contains one horizontal row, and preserves stable IDs and existing focus scrolling.

- [ ] **Step 5: Verify launcher behavior**

Run: `swift run TVShellChecks && git diff --check`

Expected: all checks pass; existing launcher top/history focus contracts remain green.

- [ ] **Step 6: Commit and push**

```bash
git add Sources/TVShellCore/Launcher/TVOS18WallpaperView.swift Sources/TVShellCore/Launcher/LauncherView.swift Sources/TVShellCore/Launcher/AppCardView.swift Sources/TVShellChecks/main.swift
git commit -m "feat: redesign launcher for tvOS 18"
git push origin main
```

### Task 3: Settings and management split layout

**Files:**
- Create: `Sources/TVShellCore/Settings/TVOS18SettingsComponents.swift`
- Modify: `Sources/TVShellCore/Settings/SettingsView.swift`
- Modify: `Sources/TVShellCore/Settings/AppManagementView.swift`
- Modify: `Sources/TVShellCore/Settings/AnimeSourceManagementView.swift`
- Modify: `Sources/TVShellCore/Settings/PermissionStatusView.swift`
- Modify: `Sources/TVShellCore/Settings/RemoteLearningView.swift`
- Modify: `Sources/TVShellChecks/main.swift`

**Interfaces:**
- Consumes: Task 1 semantic surfaces and existing settings focus enums.
- Produces: `TVOS18SettingsSplitView<Sidebar: View, Content: View>` and `TVOS18SettingsRow`.

- [ ] **Step 1: Write failing settings contracts**

Add source contracts that require `TVOS18SettingsSplitView`, require `TVOS18SettingsRow`, require focused row foreground to be black, and reject `liquidGlassCard` in all five settings/management source files.

```swift
try expect(settings.contains("TVOS18SettingsSplitView"), "settings uses the tvOS 18 split layout")
try expect(settingsComponents.contains("isFocused ? Color.black : Color.white"), "focused settings rows use dark content")
for source in managementSources {
    try expect(source.contains("liquidGlassCard") == false, "settings management screens remove Liquid Glass")
}
```

- [ ] **Step 2: Run to verify RED**

Run: `swift run TVShellChecks`

Expected: failure because the settings component source does not exist.

- [ ] **Step 3: Create shared split and row components**

`TVOS18SettingsSplitView` uses a 40/60 `HStack`, keeps the left sidebar fixed, and places only the right content in a vertical `ScrollView`. `TVOS18SettingsRow` accepts `symbolName`, `title`, `value`, `isFocused`, and `showsChevron`; it uses `tvOS18Surface(role: .row, isFocused:)`, a minimum height of `metrics.systemRowHeight`, 10-point scaled corner radius, and switches all foreground colors based on focus.

- [ ] **Step 4: Migrate settings and management pages**

Replace `SettingsHero` plus full-width card stack with the split component. Migrate App management, source management, permissions, and remote learning to the same row style without changing their IDs, focus bindings, actions, scrolling anchors, or status text.

- [ ] **Step 5: Verify all focus contracts**

Run: `swift run TVShellChecks && git diff --check`

Expected: all existing settings persistence, focus, source-management, and permission checks pass.

- [ ] **Step 6: Commit and push**

```bash
git add Sources/TVShellCore/Settings Sources/TVShellChecks/main.swift
git commit -m "feat: adopt tvOS 18 settings layout"
git push origin main
```

### Task 4: Control Center, keyboard, notifications, and system alerts

**Files:**
- Create: `Sources/TVShellCore/Design/TVOS18SystemAlert.swift`
- Modify: `Sources/TVShellCore/ControlCenter/ControlCenterView.swift`
- Modify: `Sources/TVShellCore/Input/VirtualKeyboardView.swift`
- Modify: `Sources/TVShellCore/Launcher/LauncherView.swift`
- Modify: `Sources/TVShellChecks/main.swift`

**Interfaces:**
- Consumes: Task 1 panel/row/alert surfaces and existing ControlCenterFocus/VirtualKeyboardState.
- Produces: `TVOS18AlertAction` (`id: String`, `title: String`, `role: ButtonRole?`), `TVOS18SystemAlert` (`title`, `message`, `actions`, `focusedActionID`), compact Control Center, tvOS 18 keyboard focus, `TVOS18StatusNotification` (`message: String`).

- [ ] **Step 1: Write failing overlay contracts**

Require the alert to dim and blur the background, require horizontal actions, require compact Control Center width no greater than 560 at 1920 baseline, and reject `liquidGlassCard`/`.ultraThinMaterial` in the control center and keyboard.

- [ ] **Step 2: Run to verify RED**

Run: `swift run TVShellChecks`

Expected: the new alert file and source contracts fail.

- [ ] **Step 3: Implement system alert and notification**

Create `TVOS18SystemAlert` with title, message, `[TVOS18AlertAction]`, focused action ID, an alert surface, and horizontal buttons. Its background uses a black overlay plus SwiftUI blur/material fallback; focused buttons use near-white fill and black text. Add a small top-center `TVOS18StatusNotification` used by launcher status messages and opening-app state.

- [ ] **Step 4: Restyle Control Center and keyboard**

Control Center remains top-trailing, uses a maximum width of 560 scaled points, 2-column compact tiles, panel surface, and light focused tiles. Keyboard uses dark unselected keys, light focused keys, 10–14 point corners, and preserves candidate/key stable IDs and every existing input action.

- [ ] **Step 5: Verify overlays and input**

Run: `swift run TVShellChecks && git diff --check`

Expected: control-center command tests and virtual-keyboard composition/navigation tests pass.

- [ ] **Step 6: Commit and push**

```bash
git add Sources/TVShellCore/Design/TVOS18SystemAlert.swift Sources/TVShellCore/ControlCenter/ControlCenterView.swift Sources/TVShellCore/Input/VirtualKeyboardView.swift Sources/TVShellCore/Launcher/LauncherView.swift Sources/TVShellChecks/main.swift
git commit -m "feat: add tvOS 18 system overlays"
git push origin main
```

### Task 5: Anime, YouTube, and Bilibili content browsers

**Files:**
- Modify: `Sources/TVShellCore/Anime/AnimeRuntimeView.swift`
- Modify: `Sources/TVShellCore/YouTube/YouTubeRuntimeView.swift`
- Modify: `Sources/TVShellCore/Bilibili/BilibiliRuntimeView.swift`
- Modify: `Sources/TVShellChecks/main.swift`

**Interfaces:**
- Consumes: Task 1 content focus/row/panel surfaces and existing runtime controllers.
- Produces: consistent poster, thumbnail, episode, loading, error, and search visuals across three runtimes.

- [ ] **Step 1: Write failing browser migration contracts**

For each runtime source, require `TVOS18Backdrop` or an image-backed dark backdrop, require `tvOS18ContentFocus`, and reject `liquidGlassCard`. Require episode cards to use the row/content role with light-focused/dark-text semantics.

- [ ] **Step 2: Run to verify RED**

Run: `swift run TVShellChecks`

Expected: source contracts fail while runtime state tests continue compiling.

- [ ] **Step 3: Migrate Anime browser**

Keep title, detail, episode, stream-picker, torrent manager, and player phase switches unchanged. Replace title/episode/download cards with tvOS 18 content or row surfaces; remove decorative glass behind headings; use compact loading/error notification rows. Preserve every `anime-title-*`, `anime-episode-*`, `torrent-download-*`, and stream-choice stable ID.

- [ ] **Step 4: Migrate YouTube and Bilibili browsers**

Use edge-to-edge thumbnail/artwork backdrops when available with dark gradients, apply content focus to video/bangumi cards, and convert status/controls to panel/row surfaces. Preserve `youtube-video-*`, Bilibili focus movement, playback transitions, watch history, and API/error messages.

- [ ] **Step 5: Verify runtime regressions**

Run: `swift run TVShellChecks && git diff --check`

Expected: anime source, CSS1, Dandanplay, YouTube API/runtime, Bilibili API/runtime, focus-scroll, and playback checks all pass.

- [ ] **Step 6: Commit and push**

```bash
git add Sources/TVShellCore/Anime/AnimeRuntimeView.swift Sources/TVShellCore/YouTube/YouTubeRuntimeView.swift Sources/TVShellCore/Bilibili/BilibiliRuntimeView.swift Sources/TVShellChecks/main.swift
git commit -m "feat: restyle media browsers for tvOS 18"
git push origin main
```

### Task 6: Player HUD and source menus

**Files:**
- Create: `Sources/TVShellCore/Runtime/TVOS18PlayerHUD.swift`
- Modify: `Sources/TVShellCore/Anime/AnimeRuntimeView.swift`
- Modify: `Sources/TVShellCore/YouTube/YouTubeRuntimeView.swift`
- Modify: `Sources/TVShellCore/Bilibili/BilibiliRuntimeView.swift`
- Modify: `Sources/TVShellCore/Runtime/MediaRuntimeView.swift`
- Modify: `Sources/TVShellChecks/main.swift`

**Interfaces:**
- Consumes: Task 1 panel/row surfaces and each controller's current title, time, duration, play state, subtitle state, danmaku state, and stream choices.
- Produces: reusable `TVOS18PlayerHUD` (`title`, `eyebrow`, `currentTime`, `duration`, `isPlaying`, `isVisible`, `tools`), `TVOS18PlayerTool` (`id`, `symbolName`, `label`, `isSelected`), and compact checked `TVOS18PlayerMenu` (`items`, `focusedID`).

- [ ] **Step 1: Write failing HUD contracts**

Require the shared HUD file to contain a bottom-aligned gradient, `ProgressView`, tool row, checked menu item, and an `isVisible` gate. Require all four player views to use the shared HUD or its shared components and reject Liquid Glass controls.

- [ ] **Step 2: Run to verify RED**

Run: `swift run TVShellChecks`

Expected: compilation/source contract failure because the shared HUD does not exist.

- [ ] **Step 3: Implement shared HUD primitives**

`TVOS18PlayerHUD` accepts title, eyebrow, current time, duration, isPlaying, isVisible, and `[TVOS18PlayerTool]`. It renders nothing when hidden; when visible it renders a bottom black gradient, title hierarchy, thin progress, time labels, and a compact tool row. `TVOS18PlayerMenu` is a dark panel whose focused item becomes light with dark text and whose selected item shows a checkmark.

- [ ] **Step 4: Integrate all players**

Map existing Anime/Bilibili/YouTube/Media state into the shared components. Keep five-second HUD hiding, restart-on-first-select, seeking, source selection, subtitle selection, danmaku overlay, watch-history recording, AVPlayer/VLC/YouTube rendering, and Back behavior unchanged.

- [ ] **Step 5: Verify player behavior**

Run: `swift run TVShellChecks && git diff --check`

Expected: all playback HUD timing, resume, subtitle, source-picker, renderer, danmaku, and watch-history checks pass.

- [ ] **Step 6: Commit and push**

```bash
git add Sources/TVShellCore/Runtime/TVOS18PlayerHUD.swift Sources/TVShellCore/Runtime/MediaRuntimeView.swift Sources/TVShellCore/Anime/AnimeRuntimeView.swift Sources/TVShellCore/YouTube/YouTubeRuntimeView.swift Sources/TVShellCore/Bilibili/BilibiliRuntimeView.swift Sources/TVShellChecks/main.swift
git commit -m "feat: add tvOS 18 player HUD"
git push origin main
```

### Task 7: Remove visual leftovers and perform full verification

**Files:**
- Modify: `Sources/TVShellCore/Design/LiquidGlass.swift`
- Modify: any `Sources/TVShellCore/**/*.swift` still containing active `liquidGlassCard` calls
- Modify: `Sources/TVShellChecks/main.swift`
- Update: `docs/superpowers/plans/2026-07-11-tvos18-system-redesign.md`

**Interfaces:**
- Consumes: all Task 1–6 components.
- Produces: no active Liquid Glass calls in primary UI, complete regression evidence, checked-off implementation plan.

- [ ] **Step 1: Add the final migration contract**

Enumerate Swift sources under `Sources/TVShellCore` and fail if any UI source outside the deprecated compatibility file still contains `.liquidGlassCard(` or `.ultraThinMaterial`. Also assert that `TVControlBackdrop` delegates to `TVOS18Backdrop` so unvisited fallback screens remain consistent.

- [ ] **Step 2: Run to identify remaining files**

Run: `swift run TVShellChecks`

Expected: failure message names every remaining unmigrated source.

- [ ] **Step 3: Migrate remaining call sites and preserve compatibility**

Replace every reported call with the appropriate semantic tvOS 18 surface. Keep `liquidGlassCard` only as a deprecated source-compatible wrapper around `.tvOS18Surface(role: .content, isFocused:)`; remove `.ultraThinMaterial` from active UI paths.

- [ ] **Step 4: Run fresh full verification**

Run:

```bash
git diff --check
swift run TVShellChecks
swift build --target TVShellCore
rg -n "liquidGlassCard|ultraThinMaterial" Sources/TVShellCore --glob '*.swift'
```

Expected: formatting check exit 0; `TVShellChecks passed`; target build exit 0; `rg` reports only the deprecated compatibility declaration if retained.

- [ ] **Step 5: Inspect app screenshots**

Launch the debug App and capture homepage, settings, control center, Anime title grid, episode grid, player HUD, and system alert at the current window size. Confirm rectangular App tiles, bottom Dock, white focused rows, dark panels, safe-area spacing, no overlap, and no Liquid Glass appearance. If a screen violates a listed criterion, correct that screen and repeat Step 4.

- [ ] **Step 6: Check off the plan and commit**

Mark completed checkboxes in this plan, then run:

```bash
git add Sources/TVShellCore Sources/TVShellChecks/main.swift docs/superpowers/plans/2026-07-11-tvos18-system-redesign.md
git commit -m "fix: complete tvOS 18 visual migration"
git push origin main
git status --short
git rev-list --left-right --count origin/main...main
```

Expected: clean status and `0 0` synchronization count.

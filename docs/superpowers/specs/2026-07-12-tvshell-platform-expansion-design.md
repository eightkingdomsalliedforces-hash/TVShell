# TVShell Platform Expansion Design

## Goal

Turn the current macOS application into the canonical TVShell product, add a real Android TV launcher and a Windows shell, keep the three platforms visually equivalent, and complete the requested anime, playback, wallpaper, control-center, Bilibili, and developer-app features.

## Product Boundaries

TVShell is one product with three platform shells:

- macOS uses the existing SwiftUI, AppKit, WebKit, and AVFoundation implementation.
- Android TV uses Kotlin and Compose for TV. It ships as a Play-compatible TV app and as a sideloadable launcher build that declares `CATEGORY_HOME`; both declare `CATEGORY_LEANBACK_LAUNCHER`.
- Windows uses Kotlin Compose Desktop for the shared TV UI and platform adapters for installed applications, media keys, volume, and packaging.

TVShell Anime is an independently buildable application composed from the same anime modules and design contract. It is not a divergent UI. On Android TV it can also be included as an internal TVShell app.

## Visual Contract

The macOS TVShell UI is the canonical reference. Android TV and Windows must use the same:

- 1920×1080 safe areas and layout grid;
- rectangular app cards and media aspect ratios;
- typography scale, colors, opacity, corner radii, shadows, and focus enlargement;
- tvOS 18 navigation, settings, control-center, alert, keyboard, and player-HUD composition;
- remote focus order, scroll anchoring, animation duration, and boundary behavior.

The repository stores language-neutral design tokens. SwiftUI and Compose render from equivalent generated constants. Golden screenshots cover launcher, settings, control center, anime, source selection, details, player HUD, and empty/error states at 1080p. Platform-native system dialogs are permitted only when system authorization requires them.

## Shared Architecture

### TVShell Contract

A platform-neutral contract defines:

- design tokens;
- remote commands and focus transitions;
- launcher app metadata;
- developer-app manifests and permissions;
- anime titles, episodes, stream candidates, history, errors, and provider capabilities;
- wallpaper metadata and cache policy;
- player commands, volume, danmaku settings, and HUD visibility.

Swift retains native models and adds JSON contract fixtures. Kotlin consumes the same fixtures and implements equivalent state reducers. Network and platform UI implementations remain native.

### Platform Adapters

Each shell supplies adapters for:

- installed-app discovery and launch;
- Home, Back, menu, D-pad, media keys, and volume;
- WebView and official-site sessions;
- media playback;
- secure credential storage;
- filesystem cache and application installation;
- wallpaper downloading and local fallback.

Android queries leanback launcher activities and launches selected packages. The launcher build handles `CATEGORY_HOME`; unsupported vendor devices fall back to regular leanback-app mode. Windows discovers registered Start Menu applications and explicit user-added executables. macOS keeps its existing native and web targets.

## Developer App Platform

TVShell supports two installable app classes.

### Portable TVShell Packages

A signed `.tvshellapp` archive contains:

- `manifest.json` with stable app ID, name, version, entry point, minimum TVShell version, permissions, and allowed hosts;
- 16:9 and launcher artwork;
- declarative TV pages or sandboxed web assets;
- optional anime/content provider declarations;
- package signature and publisher identity.

Portable apps run in a restricted host. They receive only approved remote, storage, network, and playback bridges. They cannot access another app's cookies, credentials, filesystem, or TVShell internals. Installation shows publisher, signature status, permissions, and network hosts. Apps can be disabled, removed, updated, or have their data cleared.

### Native Platform Apps

Native apps remain separate processes: `.app` on macOS, APK on Android TV, and MSIX/EXE on Windows. TVShell registers and launches them but never loads arbitrary native code into its own process. Platform signing and installation rules apply.

The first SDK release includes a manifest schema, sample app, validation CLI, package builder, and local installation flow. A remote catalog can be added after local package security is verified.

## Branding and Migration

All visible `MacTV` names become `TVShell`, including bundle name, window title, release archives, documentation, control-center labels, status text, and user agents. Existing storage under `Application Support/MacTV` is migrated atomically to `Application Support/TVShell`. If migration cannot complete, TVShell reads the legacy location without deleting it and reports the problem. Existing credentials, CSS1 health state, history, settings, and caches remain usable.

## Anime and Playback

### AniGamer Official Player

The official page remains responsible for advertising, authentication, entitlement, age checks, and region restrictions. TVShell may acknowledge the site's ordinary age-confirmation prompt but must not skip advertising, extract streams, forge entitlement, or alter playback time through page internals.

The WebView remote bridge first focuses the official player and dispatches standard keyboard events. It supports:

- OK and play/pause;
- left/right seek;
- up/down volume;
- fullscreen toggle;
- Escape and two-stage Back;
- a cursor fallback when the official player does not accept keyboard focus.

Age confirmation is detected only by visible prompt semantics and activated as a normal user click. The choice is stored by the official site's persistent WebView session.

### Source Navigation and Scrolling

The anime top source capsule becomes an interactive focus region. Up from the first content row enters it; left/right switches pages; down returns to the last content focus. Each source keeps independent query, focused item, scroll position, results, and history.

Official YouTube results use native TVShell cards and details, then the supported YouTube embed player. They never navigate the whole application to a raw YouTube page. Result grids scroll to keep the focused item centered.

### Bilibili Anime Migration

Bilibili bangumi providers, search results, season details, episodes, playback, and danmaku move into TVShell Anime. The standalone Bilibili app removes the bangumi top-level category and home section. General Bilibili search still returns bangumi matches; selecting one opens the anime detail route. Bilibili dynamic and profile pages use authenticated APIs when a valid cookie is present and show explicit login/API errors otherwise.

### Player Behavior

The status clock and all shell overlays automatically hide during any anime playback and restore on exit or failure. The HUD hides after inactivity and reappears on remote input. Up/down changes system volume where permitted and app-player volume otherwise. Danmaku configuration updates apply immediately.

## Wallpaper and Control Center

The Bing daily image provider retrieves official image metadata, resolves the full image URL, caches the image and attribution, and keeps the last successful image for offline startup. A failed download never replaces a valid cache. The user can choose Bing Daily, local image, or built-in gradients.

Control Center adds focusable danmaku size, speed, opacity, density, and visibility controls. It edits the same persisted `DanmakuDisplaySettings` used by Anime and Bilibili playback. Values are clamped, saved immediately, and announced in the compact status notification.

## Android TV Launcher

The Android project produces two application variants:

- `play`: leanback launcher activity, Play-compatible permissions, and system-home fallback;
- `launcher`: leanback plus `CATEGORY_HOME`, default-home onboarding, boot-safe startup, and launcher-specific installed-app discovery.

Both variants share the exact Compose TVShell UI and internal apps. The launcher handles D-pad focus, app launch failures, missing banners, package changes, safe mode after repeated startup crashes, and a route back to Android settings so users cannot become trapped.

## Windows Shell

The Windows build uses the shared Compose UI and lists Start Menu applications plus user-added executables. It launches apps as separate processes, maps keyboard/media/remote input to TVShell commands, supports fullscreen-window mode, and uses the same package and anime contracts. It does not attempt to replace the Windows desktop shell in the first release.

## Error Handling

Every network provider distinguishes timeout, unavailable result, authentication, age restriction, region restriction, unsupported playback, and parser change. UI messages are Traditional Chinese and include the affected source. Provider failures do not block other sources. Plugin crashes or invalid manifests cannot terminate the launcher. Cached wallpaper and settings writes use temporary files and atomic replacement.

## Testing

- Existing `TVShellChecks` gains branding migration, package validation, focus, HUD, volume, source navigation, Bing cache, and Bilibili-routing checks.
- AniGamer tests use local HTML fixtures for age prompt and player focus; no automated test clicks real ads.
- Kotlin common tests run the shared focus, manifest, anime, and error fixtures.
- Android instrumentation tests verify HOME/LEANBACK manifests, installed-app discovery, D-pad navigation, and safe fallback.
- Windows tests verify app discovery and process launch adapters without launching untrusted binaries.
- Golden 1080p screenshots compare platform layouts against canonical scene fixtures with explicit tolerance for font rasterization.
- CI builds macOS release, Android Play APK/AAB, Android launcher APK, Windows package, and standalone TVShell Anime artifacts.

## Delivery Order

1. Stabilize current macOS behavior: AniGamer remote/age/fullscreen/volume, source navigation and scrolling, HUD hiding, native YouTube cards, Bing wallpaper, and danmaku controls.
2. Complete TVShell branding and legacy-data migration.
3. Move Bilibili bangumi into Anime and connect authenticated dynamic/profile APIs.
4. Add portable app manifest, validator, local installer, permissions, and sample SDK app.
5. Extract design and behavior contracts plus the standalone TVShell Anime product boundary.
6. Build the Android TV launcher variants with UI parity.
7. Build Windows TVShell and standalone Anime packages.
8. Add cross-platform golden tests and release artifacts.


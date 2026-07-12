# TVShell Input, Runtime, and Media Recovery Design

## Scope and order

This recovery is split into four independently shippable layers so a broken platform shell cannot block macOS media fixes:

1. input capture, remapping, Android/Windows launchability, icons, and scrollbar removal;
2. AniGamer age/player control, Anime official-source/history behavior, and YouTube top navigation;
3. real Bilibili recommendation/ranking/dynamic/actions;
4. native/declarative third-party apps and CSS1/BT playback on Android TV and Windows.

## Input

macOS owns a persisted `RemoteMappingCenter`. Every raw keyboard, media, and HID event passes through it before the fallback mapper. Remote Learning selects a TVShell command, arms capture, consumes the next raw event, stores the mapping atomically, and supports reset. Unknown events must still be capturable.

Compose owns a `RemoteCommandDispatcher`. Android translates `KeyEvent` at the Activity boundary so OK and Back do not depend on Compose focus. Windows and common Compose request initial focus explicitly. Android Home remains a system-reserved key; launcher Home behavior is provided through the system role, while a configurable long-Back/Menu alternative can dispatch TVShell Home inside the app.

## Platform packaging and appearance

Android main activities expose separate `LEANBACK_LAUNCHER` and normal `LAUNCHER` filters, adaptive icons, banners, and raster launcher resources. The sideload launcher flavor additionally exposes `HOME` and `DEFAULT`. Windows opens fullscreen and requests keyboard focus. Common layouts scale from the 1920×1080 contract instead of treating dp as physical pixels.

All scrollable SwiftUI and WebKit surfaces hide indicators. Web content receives injected CSS that hides scrollbars without disabling scrolling.

## Official playback and focus

AniGamer detects the exact visible rating sentence (`未滿15歲之人不宜觀賞`) and clicks only its adjacent `同意` control. The helper also focuses the official video/player and dispatches standard DOM keyboard events for arrows, Enter/Space, K, F, and Escape. It never changes `currentTime`, extracts media URLs, or skips advertising.

Anime official sources load default content on entry, expose visible focused-button motion, and show explicit source errors. Anime history maps persisted `.anime` entries into selectable cards and resumes through the existing history route. YouTube gains a real top-navigation reducer and independent content focus.

## Bilibili

Recommended, popular, ranking, and dynamic tabs call distinct endpoints. Detail actions have a separate focus row and authenticated like/coin requests with CSRF extracted from the configured cookie. API and authentication failures remain visible.

## Extensibility and cross-platform media

Portable apps support both restricted web manifests and signed declarative page manifests. Native apps remain separate signed OS packages/processes. Android/Windows Anime share provider contracts for HTTP selector sources, RSS/BT feeds, torrent cache state, player candidates, and history. Platform adapters implement HTTP headers, torrent downloading, and native playback without embedding arbitrary native code into TVShell.

## Verification

Each reducer/parser/request is test-first. macOS runs `TVShellChecks` plus release build. Compose runs common tests, both TVShell Android variants, standalone Anime APK, and Windows compilation/package CI. Android merged manifests are inspected for launcher category separation.

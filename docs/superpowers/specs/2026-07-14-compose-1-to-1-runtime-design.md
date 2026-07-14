# Windows and Android TV 1:1 Runtime Design

## Goal

Make the Windows and Android TV builds reproduce the macOS TVShell navigation, built-in apps, settings hierarchy, anime sources, playback, history, browser, focus behavior, and visible error states. macOS remains Swift and is the canonical product reference.

## Root Causes

The current Compose shell has only five routes: Launcher, Anime, YouTube, Bilibili, and Settings. Launcher selection therefore aliases Remote, App Management, and Anime Source Management to Settings. Browser and media cards delegate to the operating system, the main Windows and Android adapters do not implement anime services, history is memory-only and has no deletion command, and CSS1 configuration is hidden inside a resolver constant rather than exposed as product state.

## Considered Approaches

1. Continue adding launcher ID special cases. This is small initially, but repeats the existing routing defect and makes keyboard, persistence, and Back behavior diverge further.
2. Use a shared typed runtime router and shared reducers, with platform adapters limited to WebView, media, storage, credentials, installed apps, and system settings. This is the selected approach because one implementation defines Windows and Android behavior while macOS remains the reference.
3. Rewrite the macOS shell in Kotlin. This could share rendering code, but contradicts the product requirement and would discard working SwiftUI, AppKit, WebKit, and AVFoundation behavior.

## Navigation Contract

The Compose shell gains distinct routes for Launcher, Browser, Media Player, Anime, Anime Source Management, YouTube, Bilibili, Remote Settings, General Settings, App Management, and Control Center. Every built-in app ID maps to exactly one route. Back returns one level, Home returns to Launcher, and Menu opens Control Center unless the active player owns the command.

Remote Settings and App Management may show platform-specific capabilities, but never reuse the General Settings content. Android remote learning reports fixed Android key mappings; Windows reports the keyboard mapping table. App Management lists discovered and built-in apps, and provides platform-supported refresh or system-management actions.

## CSS1 and Credentials

CSS1 is enabled by default with the same built-in subscription URL as macOS:

`https://sub.creamycake.org/v1/css1.json`

Anime Source Management displays this URL, enabled state, health, and reset action. The resolver consumes the configured URL rather than an inaccessible constant. Credentials pages display their canonical path and provide import. Windows stores them at `%APPDATA%\\TVShell\\credentials.json` with a home-directory fallback. Android stores them in the app-private files directory as `credentials.json` and displays the full path plus an import action.

## Browser and Playback

Browser, YouTube, Bilibili, official anime sources, and direct video playback remain inside a TVShell route. Android uses an embedded WebView and native MediaPlayer surface. Windows uses an embedded JavaFX WebView/media surface packaged with the application. Platform WebViews hide scrollbars, accept D-pad commands, and support OK, Back, directional scrolling, play/pause, seeking, and fullscreen-compatible layout.

CSS1 and direct media use the internal player route. Official services retain their normal authentication, advertising, age, entitlement, and region behavior; TVShell does not bypass those controls.

## History and Persistence

History is shared product state, capped at eight entries, rendered as compact 340×116-equivalent cards like macOS, and persisted through the platform adapter. Menu on a focused entry deletes that entry. A focusable Clear action removes all history. Deleting updates focus deterministically and survives restart.

Settings, CSS1 configuration, and credentials location also survive restart. Failed storage reads use defaults and show an explicit Traditional Chinese reason without destroying existing files.

## Visual Parity

All Compose routes use the existing 1920×1080 design tokens, tvOS 18 dark material surfaces, rectangular app cards, spring focus motion, centered focus scrolling, hidden scrollbars, matching split settings layout, and the macOS text hierarchy. Platform-native file pickers and system settings remain the only allowed visual exceptions.

## Error Handling and Testing

State reducers are tested before implementation for route uniqueness, Back/Home semantics, CSS1 defaults and reset, credentials path presentation, history deletion and focus, browser/player ownership, and installed-app management. Adapter contract tests verify main Windows and Android shells receive the same anime capabilities as the standalone Anime app. Existing parser, danmaku, remote-key, build, packaging, and release tests remain required.

The final audit also checks every built-in app ID, every remote command on every route, empty/error/loading states, history persistence, and 1280×720 plus 1920×1080 layout behavior so unreported regressions are included.

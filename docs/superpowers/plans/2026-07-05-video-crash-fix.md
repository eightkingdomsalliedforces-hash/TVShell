# Video Crash Fix

## Issue

Opening the Video runtime could crash or exit unexpectedly.

## Fix

Replaced SwiftUI `VideoPlayer` with an AppKit `AVPlayerView` wrapped in `NSViewRepresentable`.

## Reasoning

The rest of the media control path was pure state and already covered by `TVShellChecks`. The fragile part was the player surface. `AVPlayerView` is the native macOS AppKit player view and is a better fit for this SwiftPM macOS executable shell.

## Verification

- `swift run TVShellChecks`
- `swift build --product TVShell`
- `swift build -c release --product TVShell`
- Short launch smoke test with `swift run TVShell`

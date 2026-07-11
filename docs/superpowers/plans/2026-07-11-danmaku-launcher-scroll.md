# Danmaku and Launcher Scroll Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Keep scrolling danmaku alive until it fully exits and return the launcher viewport to the top whenever focus enters the App section.

**Architecture:** Introduce public pure danmaku geometry helpers backed by AppKit text measurement and use them in the timeline overlay. Add a stable launcher top anchor and focus-driven vertical scroll behavior.

**Tech Stack:** SwiftUI, AppKit text metrics, TVShellChecks.

## Global Constraints

- Work directly on `main`.
- Preserve pause, seek, speed and density settings.
- Small independent Launcher work may use a subagent.
- Commit and push to `origin/main`.

---

### Task 1: Distance-based danmaku lifetime

**Files:**
- Modify: `Sources/TVShellChecks/main.swift`
- Modify: `Sources/TVShellCore/Anime/AnimeRuntimeView.swift`
- Modify: `Sources/TVShellCore/Bilibili/BilibiliRuntimeView.swift`

- [ ] Add RED tests for text-width-dependent lifetime, fully off-screen ending offset and retention-window coverage.
- [ ] Add `DanmakuMotion` text width, travel distance, pixels-per-second, lifetime and offset helpers.
- [ ] Replace fixed `4.2` and `620` calculations in `DanmakuOverlay`.
- [ ] Use `DanmakuMotion.retentionWindow` in Anime and Bilibili controllers.
- [ ] Run `swift run TVShellChecks` and confirm GREEN.

### Task 2: Launcher App focus returns to top

**Files:**
- Modify: `Sources/TVShellChecks/main.swift`
- Modify: `Sources/TVShellCore/Launcher/LauncherView.swift`

- [ ] Add a RED source contract requiring `launcher-top` and `.apps` scroll-to-top behavior.
- [ ] Add the top anchor and scroll to it with anchor `.top` whenever `launcherFocus` becomes `.apps`.
- [ ] Preserve horizontal dock focus and history scrolling.
- [ ] Run `git diff --check && swift run TVShellChecks`.
- [ ] Commit `fix: complete danmaku travel and reset launcher scroll`, push `origin main`, and verify synchronization.


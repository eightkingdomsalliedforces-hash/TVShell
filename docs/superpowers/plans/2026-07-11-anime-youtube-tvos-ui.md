# Anime and YouTube tvOS UI Implementation Plan

> **For Codex:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Give Anime and YouTube consistent tvOS 18 browsing surfaces while keeping their data and playback behavior independent.

**Architecture:** Add small reusable SwiftUI media primitives under `Sources/TVShellCore/DesignSystem/`, then compose them in each runtime. Runtime controllers remain responsible for loading, focus state, search, and playback; views only map that state to top navigation, shelves, grids, detail panels, and player HUD.

**Tech Stack:** Swift 6, SwiftUI, WebKit, TVShellChecks executable

---

## Task 1: Shared media primitives

- [ ] Add source-contract checks in `Sources/TVShellChecks/main.swift` for a centered capsule navigation bar, 16:9 focusable grid cards, hero/detail layout, and empty/loading state.
- [ ] Run `swift run TVShellChecks` and confirm the checks fail.
- [ ] Add the reusable components in `Sources/TVShellCore/DesignSystem/TVOSMediaComponents.swift` using existing tvOS 18 colors, focus rings, motion, and typography.
- [ ] Run the checks and confirm the component contract passes.

## Task 2: Anime runtime

- [ ] Restructure `Sources/TVShellCore/Anime/AnimeRuntimeView.swift` into source navigation, hero/shelf browsing, search results, poster detail, episodes, and player surfaces.
- [ ] Preserve CSS1 lazy search at episode selection and all current provider/error behavior.
- [ ] Ensure focus changes scroll the active shelf/card into view and Back returns one level at a time.
- [ ] Add checks for the new hierarchy and run `swift run TVShellChecks`.

## Task 3: YouTube runtime

- [ ] Restructure `Sources/TVShellCore/YouTube/YouTubeRuntimeView.swift` around the shared navigation and 4-column 16:9 grid.
- [ ] Preserve YouTube Data API search, independent watch history, embed playback, and remote commands.
- [ ] Show channel/title/metadata below cards and a tvOS player HUD during playback.
- [ ] Add checks and run `swift run TVShellChecks`.

## Task 4: Publish the completed stage

- [ ] Run the full checks and inspect `git diff`.
- [ ] Commit on `main` and push `origin main`.


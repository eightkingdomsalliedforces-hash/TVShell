# Bilibili Reference UI Implementation Plan

> **For Codex:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Rebuild the Bilibili runtime to match the supplied tvOS-style references for browsing, dynamic content, profile, subscriptions, detail, and playback.

**Architecture:** Extend `BilibiliContentMode` and the existing controller with explicit page/tab state. Keep networking and playback adapters unchanged; replace the monolithic view sections with focused SwiftUI page components that share the tvOS media primitives.

**Tech Stack:** Swift 6, SwiftUI, AVKit, TVShellChecks executable

---

## Task 1: Navigation and models

- [ ] Add failing checks for the tabs `推薦／熱門／番劇／排行榜／動態／我的／搜尋` and for clamped, spatial focus transitions.
- [ ] Extend `Sources/TVShellCore/Bilibili/BilibiliModels.swift` with the required content modes and stable focus identifiers.
- [ ] Update controller command handling in `Sources/TVShellCore/Bilibili/BilibiliRuntimeView.swift` and make the checks pass.

## Task 2: Browsing pages

- [ ] Implement the centered capsule tab bar and four-column 16:9 grid matching the references.
- [ ] Add duration, views, danmaku count, title, owner, and date metadata.
- [ ] Implement Dynamic and My pages, plus the poster-and-summary subscription layout.
- [ ] Add view contract checks and run `swift run TVShellChecks`.

## Task 3: Detail and player

- [ ] Implement the reference-style detail page with metadata/actions on the left, preview art on the right, tags, and recommendations below.
- [ ] Reuse the tvOS player HUD and preserve right-to-left danmaku, source selection, quality selection, and remote control behavior.
- [ ] Run the full checks and manually inspect representative view states.

## Task 4: Publish the completed stage

- [ ] Inspect `git diff`, commit on `main`, and push `origin main`.


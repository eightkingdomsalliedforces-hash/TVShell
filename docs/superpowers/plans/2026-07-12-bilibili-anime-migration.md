# Bilibili Anime Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move Bilibili bangumi into TVShell Anime, keep bangumi discoverable in general Bilibili search, and replace placeholder dynamic/profile pages with authenticated API data.

**Architecture:** `BilibiliAnimeSourceProvider` adapts existing Bilibili models to the Anime provider contract. The Bilibili app home requests and displays general videos only; its combined search keeps video and bangumi results and routes bangumi through existing details. Authenticated social APIs return focused profile/dynamic models and explicit login errors.

**Tech Stack:** Swift 6, URLSession, existing Bilibili decoders, Anime provider adapters, TVShellChecks

## Global Constraints

- Bilibili credentials remain optional and are never logged.
- No WBI-signature bypass, DRM bypass, or entitlement forgery is added.
- Unauthenticated API code `-101` is displayed as a login requirement.
- Existing video playback, danmaku, and combined search remain functional.

---

### Task 1: Bilibili Anime Provider

**Files:**
- Create: `Sources/TVShellCore/Anime/BilibiliAnimeSourceProvider.swift`
- Modify: `Sources/TVShellCore/Anime/AnimeSourceCatalog.swift`
- Modify: `Sources/TVShellCore/Anime/AnimeProviders.swift`
- Modify: `Sources/TVShellChecks/main.swift`

- [ ] Add failing adapter tests that map a bangumi search result, detail episodes, and playback candidate.
- [ ] Implement `BilibiliAnimeSourceProvider: AnimeMediaSourceAdapter` with ID `bilibili-bangumi`.
- [ ] Add Bilibili 番劇 as an available Anime source and construct it with stored credentials.
- [ ] Run checks and commit `feat: add Bilibili anime source`.

### Task 2: Remove Bilibili Home Bangumi Category

**Files:**
- Modify: `Sources/TVShellCore/Bilibili/BilibiliModels.swift`
- Modify: `Sources/TVShellCore/Bilibili/BilibiliRuntimeView.swift`
- Modify: `Sources/TVShellChecks/main.swift`

- [ ] Add failing tests asserting top tabs omit 番劇 and home results filter to `.video`.
- [ ] Remove the bangumi top tab and home section while preserving `.bangumi` item rendering after search.
- [ ] Route search-selected bangumi to the existing season detail path.
- [ ] Run checks and commit `refactor: move Bilibili bangumi into Anime`.

### Task 3: Authenticated Dynamic and Profile APIs

**Files:**
- Modify: `Sources/TVShellCore/Bilibili/BilibiliModels.swift`
- Modify: `Sources/TVShellCore/Bilibili/BilibiliAPI.swift`
- Modify: `Sources/TVShellCore/Bilibili/BilibiliRuntimeView.swift`
- Modify: `Sources/TVShellChecks/main.swift`

- [ ] Add JSON fixtures for `/x/web-interface/nav`, `/x/relation/stat`, and `/x/polymer/web-dynamic/v1/feed/all`.
- [ ] Add request/decoder tests for profile name, avatar, coins, following, followers, dynamic title, cover, author, play count, and danmaku count.
- [ ] Extend the provider with `profile()` and `dynamics()` and reject missing credentials before network access.
- [ ] Load these endpoints when their tabs activate and replace placeholder counts/cards with returned data.
- [ ] Run `swift run TVShellChecks`, release build, and commit `feat: load Bilibili dynamic and profile data`.


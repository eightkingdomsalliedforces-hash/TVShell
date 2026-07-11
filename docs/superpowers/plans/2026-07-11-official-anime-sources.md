# Official Anime Sources Implementation Plan

> **For Codex:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add Animation Crazy and official YouTube anime channels as separate source pages inside the Anime app without bypassing advertisements, authentication, region restrictions, or official playback controls.

**Architecture:** Add an Anime source-page router with independent state stores. Animation Crazy uses native catalog/detail presentation followed by a persistent official-page `WKWebView`; YouTube official channels use the existing YouTube Data API/embed infrastructure. No aniGamerPlus download, decryption, ad-skip, token-unlock, or stream extraction code is copied.

**Tech Stack:** Swift 6, SwiftUI, WebKit, YouTube Data API, TVShellChecks executable

---

## Task 1: Source routing and independent state

- [ ] Add failing tests for distinct `.aniGamer` and `.officialYouTube` source pages with independent query, focus, results, and history.
- [ ] Add the source-page state/router under `Sources/TVShellCore/Anime/` and surface it in `AnimeRuntimeView.swift`.
- [ ] Ensure switching sources restores the last focus for that source.
- [ ] Run `swift run TVShellChecks`.

## Task 2: Animation Crazy catalog and official playback page

- [ ] Add catalog/search/detail adapters using only public official page metadata and links; report network, login, region, and unavailable-result failures in Chinese.
- [ ] Add `AniGamerOfficialPlayerView.swift` with a persistent `WKWebsiteDataStore.default()` session.
- [ ] Map OK/play-pause, arrows, and Escape to standard keyboard events only; never set media time directly, click ad controls, extract media URLs, or alter official entitlement checks.
- [ ] Keep the official player visible when fullscreen automation is unavailable and expose a remote-controlled virtual cursor fallback.
- [ ] Add source-contract and command-mapping tests, then run `swift run TVShellChecks`.

## Task 3: Official YouTube anime channels

- [ ] Add separate Muse 木棉花 and Ani-One catalog presets backed by YouTube Data API queries/channel filters.
- [ ] Reuse official YouTube embed playback and preserve YouTube ads and account behavior.
- [ ] Keep results/history separate from the general YouTube app and from Animation Crazy.
- [ ] Add tests and run `swift run TVShellChecks`.

## Task 4: Publish the completed stage

- [ ] Run the full checks and inspect legal-boundary source assertions.
- [ ] Commit on `main` and push `origin main`.


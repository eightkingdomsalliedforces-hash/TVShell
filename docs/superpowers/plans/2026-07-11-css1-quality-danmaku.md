# CSS1 Quality and Danmaku Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Preserve high-quality CSS1 playback lines and resolve the correct Dandanplay episode so users receive the best available picture and the full related danmaku set.

**Architecture:** Merge CSS1 playback lines by episode number instead of discarding duplicate-title sources. Resolve each line with its own source config and rank inferred resolution. Store the parsed episode number in CSS1 identity and let Dandanplay retry clean title aliases until an exact episode match is found.

**Tech Stack:** Swift, Swift concurrency, existing CSS1 selector engine, Dandanplay v2 API, TVShellChecks.

## Global Constraints

- Work directly on `main`.
- Do not use subagents.
- Preserve CSS1 request headers and failure reporting.
- Keep Dandanplay `withRelated=true`.
- Commit and push completed work to `origin/main`.

---

### Task 1: Preserve and rank CSS1 quality lines

**Files:**
- Modify: `Sources/TVShellChecks/main.swift`
- Modify: `Sources/TVShellCore/Anime/AniSubsCSS1SubscriptionProvider.swift`

**Interfaces:**
- Consumes: `AnimeSearchResult.episodes`, `AnimeEpisode.playbackLines`, `AnimeEpisodePlaybackLine.sourceName`.
- Produces: merged playback lines and quality-ranked `AnimeStreamCandidate` values.

- [ ] **Step 1: Add failing tests**

Create two test CSS1 sources that return the same title and episode. One resolves a URL containing `480p`; the other source name and URL contain `1080p`. Assert the merged episode has two lines, the identity episode ID equals `"1"`, and the first stream is the 1080p URL with quality `"1080p"`.

- [ ] **Step 2: Confirm RED**

Run `swift run TVShellChecks` and expect failure because duplicate-title CSS1 results currently retain only one source.

- [ ] **Step 3: Implement line merging and quality ranking**

Add private helpers that merge episodes by number, deduplicate playback URLs, infer a quality label from source name plus URL, and assign larger priorities to 2160p, 1080p, 720p and 480p in that order. In `streams(for:)`, call `source(named: line.sourceName)` for each line and return candidates sorted through `AnimeStreamSelector.bestCandidate`-compatible priority values.

- [ ] **Step 4: Confirm GREEN**

Run `swift run TVShellChecks` and expect `TVShellChecks passed`.

### Task 2: Match Dandanplay with the real episode number and aliases

**Files:**
- Modify: `Sources/TVShellChecks/main.swift`
- Modify: `Sources/TVShellCore/Anime/DandanplayProvider.swift`
- Modify: `Sources/TVShellCore/Anime/AnimeExternalIntegrations.swift`

**Interfaces:**
- Consumes: `AnimeEpisodeIdentity.subjectID`, `subjectAliases`, `episodeID`.
- Produces: exact-only Dandanplay episode search and alias fallback.

- [ ] **Step 1: Add failing alias test**

Configure a transport where the primary title search returns episode 2, an alias search returns episode 1 with ID `123450001`, and that comment endpoint returns multiple comments. Assert the provider requests the alias, selects `123450001`, and returns every decoded comment.

- [ ] **Step 2: Confirm RED**

Run `swift run TVShellChecks` and expect failure because the provider currently accepts the primary search response's first wrong episode.

- [ ] **Step 3: Implement exact alias resolution**

Add an `exactOnly` argument to `DandanplayAPI.decodeEpisodeSearch`. Build a stable unique title list from `subjectID` and non-marker aliases, search each title with the parsed episode number, and return only an exact match. Throw the existing missing-route error if none match.

- [ ] **Step 4: Verify and publish**

Run `git diff --check && swift run TVShellChecks`. Commit with `fix: improve CSS1 quality and danmaku matching`, push `origin main`, and verify `origin/main...main` is `0 0`.


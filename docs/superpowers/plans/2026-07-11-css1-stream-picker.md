# CSS1 Stream Resolution Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prevent CSS1 playback-line resolution delays from accumulating before the existing stream picker appears.

**Architecture:** Resolve independent CSS1 playback lines concurrently into indexed candidates, then preserve existing quality and source ordering before returning them to the runtime.

**Tech Stack:** Swift task groups, SwiftUI runtime controller, TVShellChecks.

## Global Constraints

- Work directly on `main`.
- Do not use subagents.
- Preserve quality priority, request headers and playback fallback.
- Commit and push to `origin/main`.

---

### Task 1: Parallel CSS1 playback-line resolution

**Files:**
- Modify: `Sources/TVShellChecks/main.swift`
- Modify: `Sources/TVShellCore/Anime/AniSubsCSS1SubscriptionProvider.swift`

- [ ] Add a RED timing assertion using two 150ms delayed watch pages; require two candidates in less than 260ms.
- [ ] Extract one-line resolution into an async helper returning index and optional candidate.
- [ ] Resolve all lines with `withTaskGroup`, then sort candidates by priority and stable index.
- [ ] Run `swift run TVShellChecks` and confirm the timing test passes.

- [ ] Run `git diff --check && swift run TVShellChecks`, commit with `perf: parallelize CSS1 stream resolution`, push `origin main`, and verify synchronization.

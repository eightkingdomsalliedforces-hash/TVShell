# Dandanplay v2 Comment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Decode every valid Dandanplay v2 four-field comment while preserving legacy five-field compatibility.

**Architecture:** Select the color field by `p` field count inside `DandanplayRawComment.danmakuComment`; keep all other decoding and aggregation behavior unchanged.

**Tech Stack:** Swift Codable, Dandanplay v2 API, TVShellChecks.

## Global Constraints

- Work directly on `main`.
- Do not use subagents.
- Preserve `withRelated=true` and legacy comment fixtures.
- Commit and push to `origin/main`.

---

### Task 1: Decode four-field v2 comments

**Files:**
- Modify: `Sources/TVShellChecks/main.swift`
- Modify: `Sources/TVShellCore/Anime/AnimeExternalIntegrations.swift`

- [ ] Add a RED fixture with `p` values such as `1.000,1,16777215,b-ep341309-1`; assert all entries and colors decode.
- [ ] Confirm `swift run TVShellChecks` fails because index 3 is not numeric.
- [ ] Choose color index 2 for exactly four fields and index 3 for five or more fields.
- [ ] Run `git diff --check && swift run TVShellChecks`.
- [ ] Commit `fix: decode Dandanplay v2 comments`, push `origin main`, and verify synchronization.


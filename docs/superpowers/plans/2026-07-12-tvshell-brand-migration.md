# TVShell Brand Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the remaining MacTV product identity while preserving all user data through an atomic TVShell storage migration.

**Architecture:** A pure `TVShellStorageMigration` copies the legacy application-support directory only when the TVShell destination does not exist, verifies the copy, and leaves the legacy source intact. Every default store points to TVShell after migration; visible product, release, remote-page, and internal control names use TVShell.

**Tech Stack:** Swift 6, Foundation FileManager, SwiftUI, GitHub Actions, TVShellChecks

## Global Constraints

- Existing `Application Support/MacTV` data must remain readable and must never be deleted automatically.
- Migration must not overwrite an existing `Application Support/TVShell` directory.
- Existing YouTube embed origin remains stable only if changing it would invalidate the embed contract; its display name must still become TVShell.
- Run `swift run TVShellChecks`, release build, and workflow source checks before pushing.

---

### Task 1: Storage Migration

**Files:**
- Create: `Sources/TVShellCore/App/TVShellStorageMigration.swift`
- Modify: `Sources/TVShellCore/App/AppSettingsStore.swift`
- Modify: `Sources/TVShellCore/App/AppCredentialsStore.swift`
- Modify: `Sources/TVShellCore/Anime/AniSubsCSS1SubscriptionProvider.swift`
- Modify: `Sources/TVShell/TVShellApp.swift`
- Modify: `Sources/TVShellChecks/main.swift`

**Interfaces:**
- Produces: `TVShellStorageMigration.migrateApplicationSupport(baseURL:) throws -> TVShellStorageMigrationResult`

- [ ] Add a failing test with a temporary `MacTV/settings.json`, run migration, and assert the corresponding `TVShell/settings.json` exists while the legacy file remains.
- [ ] Add a destination-first test proving existing TVShell data is not overwritten.
- [ ] Implement copy-to-temporary, verification, and atomic move into `TVShell`.
- [ ] Invoke migration before constructing application-support stores and change all default store paths to `TVShell`.
- [ ] Run `swift run TVShellChecks` and commit `fix: migrate MacTV data to TVShell`.

### Task 2: Visible and Internal Brand Rename

**Files:**
- Modify: `Sources/TVShell/TVShellApp.swift`
- Modify: `Sources/TVShellCore/App/ShellWindowManager.swift`
- Modify: `Sources/TVShellCore/Launcher/LauncherView.swift`
- Modify: `Sources/TVShellCore/ControlCenter/ControlCenterView.swift`
- Modify: `Sources/TVShellCore/Input/NetworkRemoteControlServer.swift`
- Modify: `Sources/TVShellCore/YouTube/YouTubeRuntimeView.swift`
- Modify: `README.md`
- Modify: `Sources/TVShellChecks/main.swift`

**Interfaces:**
- Produces: TVShell-only user-facing strings and `TVShellYouTubeControls`

- [ ] Add a failing source scan that rejects `MacTV` in active source files and asserts the network remote identifies TVShell.
- [ ] Rename all visible strings, queue labels, remote HTML, fallback launcher labels, and internal custom-control types.
- [ ] Update README paths to TVShell while documenting the legacy storage migration.
- [ ] Run checks and commit `refactor: complete TVShell product rename`.

### Task 3: Release Bundle Rename

**Files:**
- Modify: `.github/workflows/release.yml`
- Modify: `Sources/TVShellChecks/main.swift`

**Interfaces:**
- Produces: `TVShell.app`, `com.tvshell.app`, TVShell release names and archive contents

- [ ] Add failing workflow assertions for `dist/TVShell.app`, `CFBundleName` TVShell, and absence of `MacTV`.
- [ ] Rename bundle paths, identifier, notices, zip contents, and release titles.
- [ ] Run `git diff --check`, `swift run TVShellChecks`, and `swift build -c release --product TVShell`.
- [ ] Commit `build: package TVShell app bundle` and push `origin main`.


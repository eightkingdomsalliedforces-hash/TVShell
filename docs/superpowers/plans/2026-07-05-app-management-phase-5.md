# App Management Phase 5 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the first remote-friendly App Management screen and evolve app cards toward poster-style tvOS surfaces.

**Architecture:** Extend app profiles with launcher visibility, add pure `AppCatalog` operations covered by `TVShellChecks`, route a Manage Apps tool card to a new SwiftUI runtime, and use poster-style card metadata without copying Apple assets or interface pixels.

**Tech Stack:** Swift Package Manager, SwiftUI, `TVShellChecks`.

---

## Tasks

1. Add `isVisibleOnHome` to `TVAppProfile`.
2. Add `AppCatalog` operations for visible apps, move left/right, and toggle visibility.
3. Add `TVShellChecks` coverage for AppCatalog behavior.
4. Add Manage Apps seed card and `.appManagement` runtime.
5. Add `AppManagementView` with large focused rows and Liquid Glass actions.
6. Update launcher to display only visible apps while keeping management access.
7. Verify with checks, release build, and short launch.

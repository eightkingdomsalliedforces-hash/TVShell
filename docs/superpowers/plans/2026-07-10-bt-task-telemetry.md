# BT Task Telemetry Implementation Plan

> **For agentic workers:** Execute inline in this session; do not use subagents.

**Goal:** Show aria2 peers, tracker, pieces, speed, ETA, and errors for active BT tasks.

**Architecture:** aria2c enables JSON-RPC on loopback only. The torrent engine maps `aria2.tellStatus` into an optional value type and merges it into existing physical-byte progress. The runtime shows task detail when available and retains the current buffering status otherwise.

**Tech Stack:** Swift 6, Foundation URLSession, aria2 JSON-RPC, SwiftUI, SwiftPM.

## Global Constraints

- RPC binds only to 127.0.0.1.
- Tracker, peer, and RPC data are never persisted.
- Existing aria2c process management and playback readiness remain intact.

### Task 1: Decode task state

**Files:**

- Modify: Sources/TVShellCore/Anime/TorrentPlaybackEngine.swift
- Test: Sources/TVShellChecks/main.swift

- [ ] Write a failing check for `Aria2RPCStatusDecoder.decode(sampleJSON)` that expects 12 peers, 80 of 100 pieces, 4 MiB/s, a tracker URL, and 20 seconds ETA.
- [ ] Run `swift run TVShellChecks`; expect the decoder symbol to be missing.
- [ ] Add `TorrentTaskStatus` with peer count, tracker URL, completed and total pieces, speed, ETA, and error. Decode aria2 numeric strings; calculate ETA only with positive speed and remaining bytes.
- [ ] Run `swift run TVShellChecks`; expect `TVShellChecks passed`.
- [ ] Commit: `git add Sources/TVShellCore/Anime/TorrentPlaybackEngine.swift Sources/TVShellChecks/main.swift && git commit -m "feat: decode BT task telemetry"`.

### Task 2: Query loopback RPC

**Files:**

- Modify: Sources/TVShellCore/Anime/TorrentPlaybackEngine.swift
- Test: Sources/TVShellChecks/main.swift

- [ ] Write failing checks that aria2 arguments contain `--enable-rpc=true` and `--rpc-listen-all=false`.
- [ ] Run `swift run TVShellChecks`; expect the argument assertions to fail.
- [ ] Register the stable stream id with an ephemeral loopback RPC port at process launch. Implement `taskStatus(for:)` with a two-second URLSession request to aria2.tellStatus. Return nil on connection, status, or decoding errors.
- [ ] Run `swift run TVShellChecks`; expect `TVShellChecks passed`.
- [ ] Commit: `git add Sources/TVShellCore/Anime/TorrentPlaybackEngine.swift Sources/TVShellChecks/main.swift && git commit -m "feat: query active BT task status"`.

### Task 3: Show telemetry

**Files:**

- Modify: Sources/TVShellCore/Anime/TorrentPlaybackEngine.swift
- Modify: Sources/TVShellCore/Anime/AnimeRuntimeView.swift
- Test: Sources/TVShellChecks/main.swift

- [ ] Write a failing progress-text check that includes peers, speed, ETA, and pieces when a task status exists.
- [ ] Run `swift run TVShellChecks`; expect the task-status progress initializer to be missing.
- [ ] Add optional task status to progress, populate it on every torrent poll, and render a compact task-detail line in TorrentDownloadRow. Preserve existing file buffering text when RPC is unavailable.
- [ ] Run `swift run TVShellChecks`; expect `TVShellChecks passed`.
- [ ] Commit: `git add Sources/TVShellCore/Anime/TorrentPlaybackEngine.swift Sources/TVShellCore/Anime/AnimeRuntimeView.swift Sources/TVShellChecks/main.swift && git commit -m "feat: show BT task telemetry"`.

### Task 4: Verify delivery

- [ ] Run `swift run TVShellChecks`; expect `TVShellChecks passed`.
- [ ] Run `swift build -c release --product TVShell`; expect `Build complete!`.
- [ ] Run `git diff --check && git status --short`; expect no whitespace errors and a clean tree.

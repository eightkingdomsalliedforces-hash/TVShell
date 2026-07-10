# BT Task Telemetry Design

## Goal

Expose peer, tracker, piece, speed, ETA, and error state for each active BT
task while retaining the current aria2c streaming and cache workflow.

## Architecture

`Aria2TorrentPlaybackEngine` starts aria2c with an RPC endpoint bound only to
`127.0.0.1`. Each download receives a stable task identifier and an ephemeral
RPC port. A small JSON-RPC client reads `aria2.tellStatus` and maps its response
to a value-type `TorrentTaskStatus`; no tracker or peer data is persisted.

`TorrentDownloadProgress` carries an optional task status. The playback HUD and
BT manager render that status when available, but preserve the current physical
byte progress and terminal-error messages when RPC is unavailable.

## State and failure handling

- RPC never listens on a non-loopback interface and no RPC secret is stored.
- Missing metadata, zero peers, or a tracker error are displayed as task state,
  not treated as a completed download.
- RPC request or decode failures do not stop aria2c; the UI falls back to the
  existing buffering status and records the latest terminal process output.
- Deleting a cache entry stops its aria2c process and removes the corresponding
  in-memory RPC task registration.

## Verification

- Check aria2c launch arguments bind RPC to localhost and enable status RPC.
- Decode a representative aria2 status response into peers, pieces, speed, ETA,
  and tracker state.
- Verify unavailable RPC retains byte progress and a terminal process failure
  remains visible.
- Run `swift run TVShellChecks` and `swift build -c release --product TVShell`.

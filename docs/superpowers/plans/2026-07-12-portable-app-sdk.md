# Portable TVShell App SDK Implementation Plan

**Goal:** Let third-party developers distribute signed, sandboxed `.tvshellapp` web applications that users can inspect, trust, install, launch, update, and remove.

## Package and trust model

- A `.tvshellapp` is a directory package containing `manifest.json`, `public-key.ed25519`, and `signature.ed25519`.
- The Ed25519 signature covers the exact `manifest.json` bytes.
- The manifest declares a stable reverse-DNS id, display name, semantic version, HTTPS entry point, and allowed hosts.
- First install shows the developer-key fingerprint. Trust is explicit and persisted; updates must use the same key.
- Portable apps run only in TVShell's web runtime. Native executable payloads are rejected.

## Implementation

1. Add manifest models, validation, signature verification, trust store, atomic installer, and installed-app metadata.
2. Add red/green checks for valid signatures, tampering, invalid HTTP/host declarations, and key mismatch on update.
3. Add App Management import flow and trust confirmation, then merge installed packages into the launcher catalog.
4. Document the package format and provide a signing/sample-package command-line tool.
5. Run `TVShellChecks`, release build, commit, and push `main`.

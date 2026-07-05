# Remote-First TV Shell Phase 1 Verification

## Automated

- `swift run TVShellChecks`: passing
- `swift build --product TVShell`: passing
- `swift build -c release --product TVShell`: passing

## Manual Smoke

- Short launch with `swift run TVShell`: passing, no immediate crash.
- Launcher, directional focus, web runtime, native app launching, remote setup, and Accessibility permission UI are implemented for Phase 1.

## Environment Note

This machine's Command Line Tools installation does not provide `XCTest` or the Swift `Testing` module, so Phase 1 uses a `TVShellChecks` executable for automated checks. It validates remote command mapping, remote learning round-trip, FocusEngine movement and recovery, and native launch request modeling.

## Known Phase 1 Limits

- CoreHID device-specific remote discovery is planned for Phase 2.
- Native app deep control currently starts with permission status and AX scanning foundation.
- Full AVPlayer media runtime is planned for Phase 2.
- tvOS-like Liquid Glass polish is planned for the visual polish phase after the remote-control foundation.

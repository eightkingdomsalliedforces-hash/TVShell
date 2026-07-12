# TVShell Android TV and Windows

This project renders Android TV and Windows from the same Compose Multiplatform UI. Layout, card ratio, spacing, focus animation, and remote commands mirror `Contracts/tvshell-contract.json` and the macOS TVShell UI.

## Android TV

Requirements: JDK 17+, Android SDK 36, and accepted Android SDK licenses.

```sh
cd platforms/compose
./gradlew :android-app:assemblePlayRelease
./gradlew :android-app:assembleLauncherRelease
```

- `play` declares `LEANBACK_LAUNCHER` and behaves as a normal Android TV app.
- `launcher` declares `LEANBACK_LAUNCHER`, `HOME`, and `DEFAULT`, so Android can offer it as the system Home app. It always includes an Android Settings card as an escape route.
- Both variants discover installed Leanback activities and launch them as separate Android processes.

Debug APKs are under `android-app/build/outputs/apk/{play,launcher}/debug/`.

## Windows

The desktop target discovers Start Menu `.lnk`/`.exe` entries and starts them as separate processes. Build and test on any JDK host; create the MSI on Windows:

```sh
cd platforms/compose
./gradlew :shared-ui:desktopTest
./gradlew :shared-ui:packageMsi
```

The MSI is written under `shared-ui/build/compose/binaries/main/msi/`.

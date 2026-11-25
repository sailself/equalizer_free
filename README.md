# Quiet EQ

An ad-free, offline Android equalizer built with Kotlin and Jetpack Compose. Designed for a Pixel 9 Pro and YouTube Music over Bluetooth. No accounts, purchases, analytics, recording, root, or internet permission.

Build an APK using the instructions below. Local APKs, device captures, and signing credentials are excluded from this source repository. See [verification results](docs/COMPATIBILITY.md) for the tested build's checksum and behavior.

## Use

1. Install the signed APK and open Quiet EQ.
2. Turn on the audio engine. Allow notifications to expose bypass/stop controls.
3. Start your music player. If it was already playing, pause/restart playback so it can announce its audio session.
4. Choose a preset, adjust the ten-band curve or selected-band slider, and save your sound under Profiles.
5. To avoid doubled processing, bypass other equalizers during listening comparisons.

Under **Settings → Appearance**, choose Original, Ocean, Forest, or Lavender and select Dark, Light, or System mode. Quiet EQ follows your device language by default. You can also choose English, Spanish, French, Japanese, Simplified Chinese, or Traditional Chinese there.

Automatic mode prefers player-announced sessions, with best-effort global fallback. Players-only and experimental global modes are available in Settings. Attachment does **not** prove a particular player is being processed: compare the sound using bypass. Android cannot guarantee effects on every application, route, call, or cast stream.

The modern backend uses DynamicsProcessing; the fallback maps the ten-band curve onto the actual bands reported by the legacy equalizer. Optional effects are capability-dependent. Reverb is unavailable because the standard public auxiliary API requires cooperation from the playback app. Bluetooth assignments apply while the engine is running, only when a connected audio device is unambiguous. Profiles do not change device volume.

## Build

Requirements: JDK 17 or newer compatible with Gradle 9.4.1, Android SDK platform `android-37.0`, Build Tools `37.0.0`, and Android Platform Tools for device testing. The project uses AGP 9.2.1 and targets API 37; minimum API 31.

Set `ANDROID_HOME`, or add an ignored `local.properties` with `sdk.dir=C\:/path/to/Android/Sdk`.

```powershell
.\scripts\build.ps1
.\gradlew.bat :app:assembleDebugAndroidTest :app:connectedDebugAndroidTest --console=plain
```

Debug output: `app/build/outputs/apk/debug/app-debug.apk`. Unit results: `app/build/reports/tests/`. Lint reports: `app/build/reports/`.

GitHub Actions runs unit tests, debug and release lint, and debug APK builds on pull requests to `main` and pushes to `main`. Download the debug APK and reports from the CI run. CI also compiles the instrumentation APK for device testing.

## Release signing

Use a private keystore outside this repository. The release Gradle configuration reads `QUIET_EQ_KEYSTORE`, `QUIET_EQ_KEY_ALIAS`, `QUIET_EQ_STORE_PASSWORD`, and `QUIET_EQ_KEY_PASSWORD`; never commit them.

On this Windows workstation, the personal signing key and a DPAPI-encrypted password configuration live under `%USERPROFILE%\.android\quiet-eq\`. `scripts/build.ps1 -Release` reads that configuration for the current Windows user. Back up the keystore and password securely: future updates must use the same key. The encrypted configuration alone cannot be moved to another user or computer.

```powershell
.\scripts\build.ps1 -Release
adb install -r app/build/outputs/apk/release/app-release.apk
```

A debug installation and a release installation have different signing keys. Export profiles before uninstalling a debug version to switch to the release APK. Ordinary same-key updates preserve data.

## Publish a release

For a new version, update `versionName` and increment `versionCode` in `app/build.gradle.kts`. Merge the version change through a pull request and check CI for that commit. The release tag uses the APK version, for example `v1.1.0`.

### Sign locally and upload

Use this path to keep the signing key on your workstation. Run from the updated `main` checkout and use a new version tag.

```powershell
$ErrorActionPreference = 'Stop'
.\scripts\build.ps1 -Release
$version = '1.1.0'  # Match versionName in app/build.gradle.kts
$name = "Quiet-EQ-$version.apk"
$apk = "artifacts/$name"
New-Item -ItemType Directory -Path artifacts -Force | Out-Null
Copy-Item -LiteralPath app/build/outputs/apk/release/app-release.apk -Destination $apk
$digest = (Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash.ToLowerInvariant()
"$digest  $name" | Set-Content -LiteralPath "$apk.sha256" -Encoding ascii
gh release create "v$version" $apk "$apk.sha256" --target (git rev-parse HEAD) --generate-notes
```

### Sign and publish on GitHub Actions

Restrict the GitHub Actions environment named `release` to the `main` branch. Add the following secrets to that environment using the existing release key.

- `QUIET_EQ_KEYSTORE_BASE64` contains the base64-encoded keystore file.
- `QUIET_EQ_KEY_ALIAS` identifies the release key.
- `QUIET_EQ_STORE_PASSWORD` and `QUIET_EQ_KEY_PASSWORD` unlock the keystore and key.

Set the environment variable `QUIET_EQ_SIGNING_CERT_SHA256` to the public certificate fingerprint reported by `apksigner verify --print-certs` on a known-good release APK. Then run **Actions → Release APK → Run workflow** on `main` as the repository owner. The workflow verifies the signing certificate, publishes the APK and its SHA-256 file, and checks both the release tag and the downloaded APK.

## Structure and verification

- `core`: settings, engine contracts, status and capabilities.
- `audio`: serialized effects ownership, session tracking, foreground lifecycle and gain mapping.
- `data`: DataStore, presets, validated JSON backups and Bluetooth assignments.
- `ui`: Sound, Profiles and Settings, plus the document-picker activity.

See [device test checklist](docs/DEVICE_TESTS.md) and [compatibility results](docs/COMPATIBILITY.md). Source/build verification and listening verification are reported separately.

## License

MIT License. Copyright (c) 2026 Kevin Z. See [LICENSE](LICENSE).
Third-party dependencies and the Gradle wrapper retain their respective licenses.

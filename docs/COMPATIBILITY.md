# Compatibility and verification

Test device: Pixel 9 Pro, Android 17 / API 37, ARM64, 960 x 2142 display. Verified on 2026-11-15.

## Verified

- 13 JVM unit tests pass: gain interpolation and normalization, headroom/balance, session ownership/routing, backup round-trip and rejection, profile reference cleanup.
- Four Android instrumentation tests pass on the Pixel: navigation, gain editing with profile save/load/delete, volume-visibility persistence, and a private-session effect probe. Test profile/settings changes are restored after each test.
- Android debug lint passes with no errors. Remaining warnings are dependency-update suggestions, compatibility deprecations, and style guidance; warnings are not hidden by a baseline.
- The final signed, minified release builds successfully and passes release lint with no errors. Its APK signature verifies, and the APK pulled back from the Pixel matches the delivered file's SHA-256.
- Final release screens were visually reviewed in dark and light themes, including corrected system-bar icon contrast. Updating in place preserved the selected Balanced curve and System theme. The user-started foreground service was checked again after the update.
- The app starts its foreground service and attaches Dynamics Processing to session 0 using ordinary app permissions. This verifies attachment, not audible processing of every player.
- YouTube Music direct-session attachment was verified: Quiet EQ and YouTube Music referenced the same session, containing a Dynamics Processing effect.
- The user confirmed an audible difference between the EQ preset and bypass during the requested YouTube Music/Bluetooth listening comparison.
- In an app-owned nonplaying audio session, Dynamics Processing reports two channels and ten EQ bands; input gain written as -1 dB reads back as -1 dB.
- The legacy equalizer reports five bands at 60, 230, 910, 3600 and 14000 Hz, with a -15 to +15 dB platform range. Quiet EQ maps its ten-point curve to those bands and labels the mapping.

## Boundaries

- The Bluetooth listening result is user-confirmed and separate from API tests. It is not inferred from a successful effect constructor, enable flag, or parameter readback, and does not establish compatibility with every app/output.
- Automatic mode depends on voluntary player session broadcasts and best-effort global fallback. It cannot guarantee calls, casting, every player, or every audio route.
- Reverb is not enabled: the standard public auxiliary API requires the player to attach a send. An insert reverb descriptor alone does not establish a supported third-party control path.
- Virtualizer is a deprecated optional compatibility effect. Runtime availability does not guarantee perceptible spatialization on every route.
- Bluetooth auto-profile selection uses connected A2DP/LE Audio profile proxies and applies only when unambiguous. Multiple-device and physical reconnection behavior still require hardware scenario testing.
- Extended screen-off playback, TalkBack navigation, Bluetooth reconnection, and a second Android device are not yet verified.

Local raw logs and captures are in ignored `artifacts/`; no device serials, Bluetooth addresses, or music history are included in this report.

## Delivered release

`artifacts/Quiet-EQ-1.0.apk` (2,482,343 bytes), installed on the Pixel. SHA-256:

```text
2c684bf13722d18a89d45bbc34147d2c60d8a565e090df5f3feba768c370660e
```

The four device tests ran before the final system-bar/color-only correction; that correction was validated visually on the final signed release. Unit tests and release lint were rerun for the final build.

## Language and palette update

Verified on the Pixel 9 Pro (Android 17, API 37) on 2026-11-25.

- The signed, minified update installed over the previous release. The app UID, selected preset, and appearance preferences survived. The installed APK matched the local build's SHA-256.
- The Settings picker switched among all six languages. French, Lavender, and Light persisted after a process restart. The device language option reset the per-app language preference.
- The rendered backgrounds matched Ocean Dark, Forest Dark, and Lavender Light.
- Dynamics Processing attached to global session 0. Switching to French updated the foreground notification while the audio process remained active. Bypass, Resume, and Stop worked from the French notification. The original appearance, preset, and stopped engine state were restored.
- All 14 JVM unit tests passed. Release lint reported zero errors. An audible A/B comparison remains pending for this build.

The deployed APK is `artifacts/live-20261125214715/Quiet-EQ-1.0-localized.apk`. The previous installed APK is saved in the same directory as `previous-base.apk`.

```text
918811e952d345292a60678e052553c4f78c3ca48a2363a06e31a92245c4a714
```

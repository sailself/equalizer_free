# Pixel acceptance checks

Target: Pixel 9 Pro, Android 17 (API 37), ARM64. Primary player: YouTube Music. Primary output: Bluetooth.

An effect descriptor, enabled flag, or successful construction does not prove audible processing. Record platform attachment/control evidence separately from listening confirmation. Do not increase media volume as part of automated testing.

## Matrix

| Scenario | Expected result |
| --- | --- |
| First launch | Processing off; flat EQ; no permission request until needed |
| Enable from visible activity | Foreground notification and accurate attached/waiting/error state |
| YouTube Music started after EQ | Session announced and attached if supported; no global/session double processing |
| YouTube Music already playing | Honest waiting or best-effort global fallback; restart playback if session was missed |
| Bypass | Effects disabled; remembered settings retained; listening A/B changes sound |
| Screen off for at least 10 minutes | Processing retained without crashes or unexpected volume changes |
| Pause/resume and track skip | No leaked sessions, control restored when available |
| Bluetooth disconnect/reconnect | Correct effect capabilities and unambiguous assigned profile |
| Multiple Bluetooth connections | No guessing at which profile to apply |
| Competing equalizer | Control conflict reported; never force-stop or uninstall other apps automatically |
| Stop from notification | Effects and foreground service released |
| Service/process termination | No false active state; settings retained; explicit restart works |
| Notification/Bluetooth permission denied | Graceful explanation; ordinary EQ remains usable |
| Legacy mode | Actual band count/range represented; unsupported effects disabled |
| Global mode | Tested separately from player sessions; limitations documented |
| Profile backup/restore | Exact round-trip; malformed import leaves existing data untouched |
| Large text/TalkBack | Controls labeled, scroll reachable, no clipped critical text |

## Evidence

Store local build logs, UI captures, and filtered diagnostic output under ignored `artifacts/`. Summarize actual results in `docs/COMPATIBILITY.md`. Do not include device serial numbers, Bluetooth addresses, or listening history in committed reports.

# Tooler

[![Build APK](https://github.com/jehan593/tooler/actions/workflows/build-apk.yml/badge.svg)](https://github.com/jehan593/tooler/actions/workflows/build-apk.yml)
[![Latest release](https://img.shields.io/github/v/release/jehan593/tooler)](https://github.com/jehan593/tooler/releases/latest)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

Quick Settings tiles that Android leaves out — no root needed. Nord palette, Martian Mono font, one
home-screen shortcut too. No `INTERNET` permission, no sync, no database: tiles read the system's
live state where Android allows it, with small local preferences for configuration and restoration.

> **FYI:** This project is fully vibe coded for my personal use and taste. It's shared publicly in case it's useful to you. Feel free to fork it and adapt it to your needs.

## Tiles

- **Screenshot** — take one straight from Quick Settings. One-time Accessibility grant.
- **Keep Screen On** — keep the screen awake until you turn it off.
- **Volume Mode** — cycle Normal, Vibrate, and Silent.
- **Battery Charge Optimization** — switch between Adaptive Charging and Limit to 80%. Pixel only.
- **Private DNS** — switch between Automatic and a hostname you set.
- **Grayscale** — temporarily use monochrome, then restore your previous color correction settings.
- **Custom tiles** — ten tiles you make, each running a shell command via Shizuku.

There's also a **Lock Quick Settings** toggle in the app (hide Quick Settings while the screen is
locked, needs Shizuku), and a home-screen **Lock Screen** shortcut that locks the screen instantly.

Grayscale observes Android's color correction settings (including developer-options monochromacy).
Independent manufacturer or Bedtime grayscale effects are not exposed by those settings.
A manual change to another correction mode takes precedence and discards the saved restore state
when observed. Without a saved state, toggling off manually enabled grayscale disables correction.

Battery Charge reads the current mode through Shizuku when available; otherwise it uses the last
mode Tooler saved, which can differ from changes made in Settings.

## Permissions

A few tiles need one-time setup:

- **Accessibility service** — Screenshot tile and Lock Screen shortcut.
- **Notifications** — shows Keep Screen On's notification and its Turn off button.
- **Do Not Disturb access** — needed for Volume Mode to enter or leave Silent.
- **`WRITE_SECURE_SETTINGS`** — Battery Charge, Private DNS, and Grayscale. Grant it from the app with Shizuku
  running, or on a computer:
  ```sh
  adb shell pm grant com.tooler.app android.permission.WRITE_SECURE_SETTINGS
  ```
- **Shizuku** — the Lock Quick Settings toggle and Custom tiles ([shizuku.rikka.app](https://shizuku.rikka.app/)).

## Build

Requires JDK 17 and the Android SDK. The app runs on Android 9 / API 28+.

```sh
./gradlew assembleDebug
```

The APK lands at `app/build/outputs/apk/debug/app-debug.apk`.

For a minified release APK, run `./gradlew assembleRelease`. The APK lands at
`app/build/outputs/apk/release/app-release.apk`.

Run local unit tests with `./gradlew testDebugUnitTest`.

## Device verification

- Start with each color correction mode enabled, then disabled. Toggle Grayscale on/off and verify
  both the original mode and enabled state return. Repeat after killing Tooler's background process
  while grayscale is on.
- With the panel open, change monochromacy on/off externally and verify the tile updates; repeat
  with the panel closed and reopen it. Select a different correction mode during an override and
  verify Tooler leaves that manual choice intact.
- Cycle Volume Mode repeatedly, then change it using hardware/settings controls with the panel
  open. Check entry to and exit from Silent with and without DND access.
- Compare first-tap response after process death and normal repeated taps, with Shizuku running.
  System process startup can still delay the first tap; shell commands no longer block the main thread.
- For Lock Quick Settings, unlock immediately after screen-off, unlock after staying on the lock
  screen for more than four seconds, and test fingerprint/face/Smart Lock. Confirm the panel remains
  available after unlocking. Repeat with Shizuku reconnecting and after toggling the feature off.
  Recovery still requires Tooler to be alive and Shizuku to be available; Android killing the process
  while locked cannot be repaired until Tooler starts again.

## License

MIT — see [`LICENSE`](LICENSE). Martian Mono Nerd Font ships under the SIL Open Font License 1.1 —
see [`MARTIAN_MONO_LICENSE.txt`](MARTIAN_MONO_LICENSE.txt).
# Tooler

[![Build APK](https://github.com/jehan593/tooler/actions/workflows/build-apk.yml/badge.svg)](https://github.com/jehan593/tooler/actions/workflows/build-apk.yml)
[![Latest release](https://img.shields.io/github/v/release/jehan593/tooler)](https://github.com/jehan593/tooler/releases/latest)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

Quick Settings tiles that Android leaves out — no root needed. Nord palette, Martian Mono font, one
home-screen shortcut too. No `INTERNET` permission, no sync, no database: tiles read the system's
live state on every tap.

> FYI: this project is fully vibe-coded.

## Tiles

- **Screenshot** — take one straight from Quick Settings. One-time Accessibility grant.
- **Keep Screen On** — keep the screen awake until you turn it off.
- **Volume Mode** — cycle Normal, Vibrate, and Silent.
- **Battery Charge Optimization** — switch between Adaptive Charging and Limit to 80%. Pixel only.
- **Private DNS** — switch between Automatic and a hostname you set.
- **Lock Quick Settings** — hide Quick Settings while the screen is locked. Needs Shizuku.
- **Custom tiles** — ten tiles you make, each running a shell command via Shizuku.
- **Lock Screen shortcut** — a home-screen icon that locks the screen instantly.

## Permissions

A few tiles need one-time setup:

- **Accessibility service** — Screenshot tile and Lock Screen shortcut.
- **Do Not Disturb access** — needed before Volume Mode can switch to Silent.
- **`WRITE_SECURE_SETTINGS`** — Battery Charge and Private DNS. Grant it from the app with Shizuku
  running, or on a computer:
  ```sh
  adb shell pm grant com.tooler.app android.permission.WRITE_SECURE_SETTINGS
  ```
- **Shizuku** — Lock Quick Settings and Custom tiles ([shizuku.rikka.app](https://shizuku.rikka.app/)).

## Build

Requires JDK 17 and the Android SDK (Android 9 / API 28+):

```sh
./gradlew assembleDebug
```

The APK lands at `app/build/outputs/apk/debug/app-debug.apk`.

## License

MIT — see [`LICENSE`](LICENSE). Martian Mono Nerd Font ships under the SIL Open Font License 1.1 —
see [`MARTIAN_MONO_LICENSE.txt`](MARTIAN_MONO_LICENSE.txt).
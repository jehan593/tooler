# Tooler

[![Build APK](https://github.com/jehan593/tooler/actions/workflows/build-apk.yml/badge.svg)](https://github.com/jehan593/tooler/actions/workflows/build-apk.yml)
[![Latest release](https://img.shields.io/github/v/release/jehan593/tooler)](https://github.com/jehan593/tooler/releases/latest)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

Quick Settings tiles that stock Android — and a lot of OEM skins — leave out, none of which need
root. Nord color palette, Martian Mono Nerd Font, Jetpack Compose UI, plus one home-screen launcher
shortcut.

Tooler requests no `INTERNET` permission and has no background service beyond the one you
explicitly turn on (Keep Screen On). Nearly everything reads live system state on every tile click
— there's no database, no settings to sync, nothing to go stale, with one forced exception (see
Battery Charge Optimization below).

## Tiles

- **Screenshot** — takes a screenshot instantly. Needs a one-time Accessibility Service grant
  (Settings → Accessibility) the first time you use it; the tile itself walks you there. No root,
  no MediaProjection consent dialog on every capture.
- **Keep Screen On** — holds the screen awake until you toggle it off again, from the tile or the
  app. Shows a low-priority ongoing notification with its own "Turn off" action while active.
- **Volume Mode** — cycles Normal → Vibrate → Silent → Normal. The first time you switch into
  Silent it'll ask for Do Not Disturb access (required by Android to set that ringer mode); Normal
  and Vibrate need nothing.
- **Battery Charge Optimization** — cycles Off → Adaptive Charging → Limit to 80% → Off, the same
  modes as Settings → Battery → Charging optimization. **Pixel only** (Android 15 QPR1+, Pixel 6a
  and later) and there's no public Android API for it — Tooler writes the same undocumented
  `Settings.Secure` keys the Settings screen itself does. That requires `WRITE_SECURE_SETTINGS`,
  which no app can request at runtime; grant it once with the phone connected:
  ```sh
  adb shell pm grant com.tooler.app android.permission.WRITE_SECURE_SETTINGS
  ```
  (the app's own card has a "Copy adb grant command" button for this). Android also won't let a
  normal app read this setting back, only write it, so the tile's status reflects the last mode
  *Tooler itself* set rather than a live read — if you change it from Settings directly, the tile
  won't notice until you tap it again.

## Shortcuts

- **Lock Screen** — a home-screen shortcut icon (not a widget) that locks the screen instantly,
  using the same Accessibility Service as the Screenshot tile. Add it via your launcher's
  widgets/shortcuts picker, same place Screenshot/Keep Screen On/Volume Mode/Battery Charge
  Optimization tiles come from.

## Building

`./gradlew assembleDebug` produces `app/build/outputs/apk/debug/app-debug.apk`. The Gradle
wrapper (`gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`) is included, so no
regeneration step is needed.

1. Open this `tooler/` folder in Android Studio (it will pick up the existing wrapper and sync
   automatically), **or** from a terminal with JDK 17 and the Android SDK installed:
   ```sh
   # local.properties needs sdk.dir pointed at your Android SDK if Android Studio hasn't
   # already created one for you, e.g.:
   echo "sdk.dir=/path/to/Android/sdk" > local.properties
   ./gradlew assembleDebug
   ```
2. Install/run on a device or emulator running Android 9.0 (API 28) or newer:
   `adb install app/build/outputs/apk/debug/app-debug.apk`.
3. Add the tiles: pull down the Quick Settings panel twice, tap the pencil/edit icon, then drag
   the tiles you want into the active set. Add the Lock Screen shortcut from your launcher's
   widgets/shortcuts picker instead — it's not a Quick Settings tile.

### Fonts

Martian Mono Nerd Font (`.ttf`, Regular/Medium) is bundled under `app/src/main/res/font/`, sourced
from the [Nerd Fonts](https://github.com/ryanoasis/nerd-fonts) project releases (license in
`MARTIAN_MONO_LICENSE.txt` at the project root).

## Permissions

| Permission | Why |
|---|---|
| `WAKE_LOCK`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE` | Back the Keep Screen On tile's wake lock + foreground service — only held while that tile is toggled on. |
| `POST_NOTIFICATIONS` | Runtime-requested (Android 13+) right before Keep Screen On starts, so its ongoing notification actually shows. |
| `WRITE_SECURE_SETTINGS` | Backs the Battery Charge Optimization tile — must be granted manually via `adb shell pm grant`; there's no Settings screen for it. |
| Accessibility Service (granted via Settings, not a manifest permission) | Lets the Screenshot tile and Lock Screen shortcut call `performGlobalAction()` — the only non-root way to trigger a screenshot or lock from outside an active window. |
| Notification Policy Access / Do Not Disturb access (granted via Settings) | Required by Android before `setRingerMode(RINGER_MODE_SILENT)` will take effect. |

Deliberately not requested: `INTERNET`, `SYSTEM_ALERT_WINDOW`, `QUERY_ALL_PACKAGES`, any exact-alarm
permission, root.

## License

MIT — see [`LICENSE`](LICENSE). The bundled Martian Mono Nerd Font is licensed separately under
the SIL Open Font License 1.1 — see [`MARTIAN_MONO_LICENSE.txt`](MARTIAN_MONO_LICENSE.txt).

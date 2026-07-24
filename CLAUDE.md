# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

Tooler: an Android app collecting Quick Settings tiles that stock Android (and many OEM skins)
leave out — none of them require root. Nord color palette, Martian Mono Nerd Font, Jetpack Compose
UI (matches ownscreen/noter/linker's visual identity — see sibling repos at `../ownscreen`,
`../noter`, `../linker`). Package `com.tooler.app`, minSdk 28.

Currently ships five tiles: **Screenshot**, **Keep Screen On**, **Volume Mode** (cycles
Normal/Vibrate/Silent), **Battery Charge Optimization** (toggles Adaptive Charging/Limit to 80% —
deliberately no Off step), **Private DNS** (toggles Automatic/a hostname you've already set —
deliberately no Off step either), plus one home-screen launcher shortcut: **Lock Screen** (no
AppWidget — see Shortcuts below for why).

**No persisted state anywhere**, with one deliberate, OS-forced exception. Every tile reads live
system state (`AudioManager`, `PowerManager`, `AccessibilityManager`) on every click instead of
keeping its own copy of it — there's no Room, no DataStore, no dependency-injected repository,
because there's nothing here that needs one. Keep it that way: a new tile should default to "read
the system, act on the system," not "add a repository." The one exception is Battery Charge
Optimization's `ChargingModePrefs` (a bare `SharedPreferences` int) — see that section for why
reading the live value back is flatly impossible for a normal app here, not a design choice.

## Commands

```sh
./gradlew assembleRelease     # build app/build/outputs/apk/release/app-release.apk (R8-minified, resource-shrunk — what CI ships)
./gradlew assembleDebug       # build app/build/outputs/apk/debug/app-debug.apk (local iteration only)
./gradlew build                # full build incl. lint/checks
```

- No test suite exists in this repo currently.
- `local.properties` needs `sdk.dir` pointing at an Android SDK (Windows: use forward slashes with
  an escaped drive colon, e.g. `sdk.dir=C\:/Users/jehan/android-sdk` — a bare `C:\...` path fails
  Gradle property parsing with a cryptic "filename syntax is incorrect" error).
- Build the APK and hand it off rather than installing to an emulator/adb yourself — the user
  tests on their own device. Quick Settings tiles and the accessibility/DND grant flows in
  particular can't be meaningfully exercised on an emulator anyway.
- `versionCode`/`versionName` are overridable via `-PappVersionCode=`/`-PappVersionName=` — CI
  (`.github/workflows/build-apk.yml`) passes `github.run_number` so every push gets a strictly
  increasing versionCode (required for update checkers like Obtainium to see a new build) and its
  own tag/release rather than overwriting one shared release — same setup as ownscreen/noter/linker.
- `app/debug.keystore` is a committed keystore (not the AGP-generated one) so CI and local builds
  always sign with the same key; a fresh CI-generated debug keystore each run would give every
  release a different signature and break in-place updates. The `release` build type reuses that
  same pinned key (see `app/build.gradle.kts`) so in-place updates via Obtainium keep working even
  though the shipped variant is release, not debug.
- The release build is minified (`isMinifyEnabled`), resource-shrunk (`isShrinkResources`), and
  ABI-filtered to `arm64-v8a`/`armeabi-v7a` (real phones only) — same lightest-possible-APK pass
  done for the sibling apps. No custom `proguard-rules.pro` keep rules are needed: every component
  here (three `TileService`s, one `AccessibilityService`, one `Service`, one `BroadcastReceiver`)
  is manifest-declared, and AGP keeps those automatically — nothing in this app uses reflection.

## Why minSdk 28

`AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT` — the mechanism the Screenshot tile depends on
— was added in API 28 (Android 9). Rather than let that one tile silently no-op below API 28 while
the other two work, the whole app's floor is set there. If a future tile needs to support older
devices, gate it explicitly with `Build.VERSION.SDK_INT` checks rather than lowering `minSdk` back
down — Keep Screen On and Volume Mode don't need anything past API 26 on their own.

`Tile.setSubtitle()` specifically is API 29+, one release past this app's own floor — every tile
sets it through `util/TileCompat.kt`'s `setSubtitleCompat()` extension (a plain `SDK_INT >= Q`
guard) rather than the raw `subtitle =` property, since calling that directly on a real API 28
device throws `NoSuchMethodError` instead of just not showing a second line. Any new tile should use
`setSubtitleCompat()` too rather than the raw property.

## Architecture

### Screenshot (`tiles/ScreenshotTileService.kt`, `tiles/ScreenshotAccessibilityService.kt`)

There is no non-root, no-foreground-window way to trigger a screenshot from a `TileService`
directly — `TileService` isn't a caller Android trusts with `GLOBAL_ACTION_TAKE_SCREENSHOT`, and
`MediaProjection` needs a fresh user consent dialog per capture session (or a permanently-running
foreground service holding the projection token, which is heavier and needs a persistent
notification for something that should feel instant). The standard non-root workaround — used by
most "screenshot tile" apps on F-Droid — is a minimal, no-op `AccessibilityService`
(`ScreenshotAccessibilityService`) that exists purely to be a system-bound caller
`performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)` is allowed from. It declares no event types
worth reacting to and `canRetrieveWindowContent="false"` (`res/xml/accessibility_service_config.xml`)
— it never reads screen content, only issues the one global action.

`ScreenshotTileService.onClick()` calls `ScreenshotAccessibilityService.instance` directly (a
companion-object reference set in `onServiceConnected`/cleared in `onUnbind`) rather than going
through a broadcast or bound-service interface — both components run in the same process, so a
plain static reference is the simplest thing that works. If the service isn't enabled yet, the tile
opens Accessibility Settings instead (`startActivityAndCollapse`, using the API 34+
`PendingIntent` overload where available since the raw-`Intent` overload is deprecated there).

`ScreenshotAccessibilityService` isn't screenshot-exclusive despite the name: the Lock Screen
shortcut (`shortcuts/LockScreenShortcutActivity.kt`, see "Shortcuts" below) calls through the same
`instance` to invoke `GLOBAL_ACTION_LOCK_SCREEN`. Same trusted-caller problem as the screenshot
action, same service — adding a second no-op accessibility service just for one more global action
would mean a second entry in Settings > Accessibility for no benefit.

Before the actual capture, `onClick()` calls `performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)`
(API 30+; a no-op below that) and waits `SHADE_COLLAPSE_DELAY_MS` (450ms) before firing
`GLOBAL_ACTION_TAKE_SCREENSHOT` — otherwise the shade is still visibly mid-collapse (sometimes still
blurred, on OEM skins that layer a blur transition on top of the plain collapse animation) in the
resulting screenshot. Tune that constant up, not down, if a specific device still shows a remnant.

Tile state stays `STATE_INACTIVE` (the plain, uncolored look) at all times — this is a momentary
action with no "on" state to represent, so `STATE_ACTIVE` would misleadingly read as "currently on."
Only the subtitle ("Tap to enable") signals the one thing worth flagging: whether the accessibility
service still needs to be granted, checked live via `AccessibilityManager` in
`util/PermissionStatus.kt` — never cached. `ScreenshotAccessibilityService` calls
`TileService.requestListeningState()` on connect/disconnect so the tile redraws immediately after
the user flips the Settings toggle, instead of waiting for the next time the QS panel opens.

### Keep Screen On (`tiles/KeepScreenOnTileService.kt`, `tiles/KeepAwakeService.kt`)

No modern API keeps the screen on globally with no active foreground window —
`FLAG_KEEP_SCREEN_ON` only works on an activity's own window while it's frontmost. The non-root
fallback every "caffeine"-style app uses is a `PowerManager` wake lock
(`SCREEN_BRIGHT_WAKE_LOCK`, deprecated but with no non-deprecated replacement — suppressed
explicitly rather than worked around). The lock is held by `KeepAwakeService`, a foreground service,
rather than acquired directly from the `TileService` — a bare wake lock with nothing backing it can
still get torn down by the OS once the owning component stops being active; running as a foreground
service (type `specialUse`, since "keep the screen on" doesn't fit any of the typed FGS categories)
keeps the process out of background-execution limits for as long as the lock needs to be held.

Toggling: `KeepScreenOnTileService.onClick()` starts/stops `KeepAwakeService` based on
`KeepAwakeService.isRunning` (a plain companion-object `Boolean`, same same-process reasoning as
the screenshot service reference — no need for `ActivityManager.getRunningServices` or a
`Binder`). Crucially, `onClick()` paints the tile to the *intended* target state directly rather
than starting the service and then re-reading `isRunning` — `startForegroundService` is
asynchronous, so `isRunning` can still read stale for a beat after the call, and reading it there
briefly flashed the tile back to its old state right after tapping (read as the tile being slow to
respond). The service still calls `TileService.requestListeningState()` from `onCreate`/`onDestroy`
to resync the tile to ground truth right after, in case the optimistic guess and the real outcome
ever diverge. The ongoing notification (`IMPORTANCE_LOW`, so it doesn't make
noise or peek) carries its own "Turn off" action — `PendingIntent.getService` with
`ACTION_STOP`, handled in `onStartCommand` — so the wake lock can be released without reopening the
app or the QS panel. `MainActivity` can also start/stop the same service directly (its "Keep Screen
On" card), requesting `POST_NOTIFICATIONS` first on API 33+ since without it the foreground
service's notification silently never shows (the service still runs either way — the permission
only gates notification visibility, not the wake lock itself).

### Volume Mode (`tiles/VolumeModeTileService.kt`, `tiles/RingerModeChangeReceiver.kt`)

Cycles `AudioManager.ringerMode` Normal → Vibrate → Silent → Normal. Only the transition *into*
Silent needs Do Not Disturb / Notification Policy access (`NotificationManager
.isNotificationPolicyAccessGranted`) — Normal and Vibrate need nothing beyond what any app already
has. If that access isn't granted yet, the tile opens
`Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS` instead of changing the mode, mirroring the
Screenshot tile's "tap to grant, don't silently fail" pattern.

**This never actually turns on Do Not Disturb.** `setRingerMode(RINGER_MODE_SILENT)` is the same
call the system's own volume panel mute icon makes — it only silences ringer/notification sound and
vibration; nothing about notification *visibility* changes (they still post, show in the shade, on
the lock screen, everything). "Notification Policy Access" is just what Android calls the gate in
front of that one API — this code never calls `NotificationManager.setInterruptionFilter()` or
anything else that would actually suppress notifications. `MainActivity`'s copy for this card
spells that distinction out explicitly since the permission name alone reads as scarier than the
actual behavior.

Unlike Screenshot, this tile always represents a real current state — Normal isn't "off" any more
than Vibrate or Silent is, they're three positions of one switch — so `state` is unconditionally
`Tile.STATE_ACTIVE`; only the icon and subtitle change between the three modes.

Tile icon/subtitle are recomputed from `audioManager.ringerMode` on every `onStartListening()` and
`onClick()` — never stored — so the tile can't drift out of sync with reality.
`RingerModeChangeReceiver` is a **manifest-registered** (not dynamically registered — nothing needs
to keep the process alive just to listen) `BroadcastReceiver` for
`AudioManager.RINGER_MODE_CHANGED_ACTION`, whose only job is calling
`TileService.requestListeningState()` so the tile catches up promptly when the ringer mode changes
from hardware volume buttons or another app, not just from this tile's own clicks.

### Battery Charge Optimization (`tiles/BatteryChargeTileService.kt`, `tiles/ChargeOptimization.kt`)

Toggles Adaptive Charging ↔ Limit to 80% — two of the same three modes as Settings > Battery >
Charging optimization on Pixel (added in the December 2024 update / Android 15 QPR1, Pixel 6a and
later); `Off` is deliberately excluded from the tile's cycle (see `advanceChargingMode()` in
`ChargeOptimization.kt`) so the tile never disables optimization on its own — `Off` only ever
appears as the state before this app has written anything yet (`ChargingModePrefs`'s zero default),
same case as `WRITE_SECURE_SETTINGS` not being granted. **There is no public Android SDK API for
this feature at all** — no `BatteryManager` call,
no documented `Settings` constant. `ChargeOptimization.kt` writes the same two undocumented
`Settings.Secure` ints (`adaptive_charging_enabled`, `charge_optimization_mode`) the Settings screen
itself writes — reverse-engineered technique, credited to
[TebbeUbben/ChargeQuickTile](https://github.com/TebbeUbben/ChargeQuickTile), not something Google
documents or guarantees to keep stable across OS updates.

Writing either key needs `WRITE_SECURE_SETTINGS`. Unlike every other permission this app touches
(Accessibility, Do Not Disturb access, notifications, battery-optimization exemption), **there is
no Settings screen that grants this one** — a normal app can never prompt for it at runtime. The
only way to grant it is `adb shell pm grant com.tooler.app android.permission.WRITE_SECURE_SETTINGS`
from a computer with the phone connected. `BatteryChargeTileService` checks
`util/PermissionStatus.kt`'s `hasWriteSecureSettings()` before every write; if it's not granted the
tile stays `STATE_INACTIVE` with a "Setup needed" subtitle and tapping opens `MainActivity` instead
of touching Settings, whose Battery Charge Optimization card has a "Copy adb grant command" button
(`ClipboardManager`) so the user doesn't have to type the package name themselves. Unlike Volume
Mode's Normal/Vibrate/Silent, Off here reads as "optimization disabled" rather than an equally-valid
third state, so only Adaptive Charging/Limit to 80% paint the tile `Tile.STATE_ACTIVE`; Off and
"Setup needed" both stay `STATE_INACTIVE`.

**The mode can be written but not read back — confirmed on a real device, not theoretical.**
Reading either key through the public `Settings.Secure` API throws `SecurityException` for any
non-system app on Android 12+, logcat-confirmed on a Pixel 8a running Android 17/API 37:
`Settings key: <adaptive_charging_enabled> is not readable. From S+, settings keys annotated with
@hide are restricted to system_server and system apps only, unless they are annotated with
@Readable.` `WRITE_SECURE_SETTINGS` does **not** exempt a normal app from this — writing and reading
are gated completely separately, confirmed by a write-only test succeeding (and the real Settings
screen reflecting it) with the exact same permission grant that made every read throw.
ChargeQuickTile's own reads work only because its manifest declares `android:testOnly="true"` —
which is why its README requires `adb install -t` — but that's incompatible with how Tooler actually
ships (Obtainium / a plain signed APK), so that escape hatch isn't available here.

Because of that, `advanceChargingMode()` doesn't read the live value at all — it can't. Instead a
private `ChargingModePrefs` object (a single `SharedPreferences` int, in `ChargeOptimization.kt`)
remembers the last mode *this app itself* wrote, purely so the tile knows what to cycle to next.
This is the **one deliberate, OS-forced exception** to "never persist local state" in this app (see
"No persisted state anywhere" above) — every other tile can always read the real system value, this
one structurally cannot, for any normally-distributed app. The unavoidable consequence: if the mode
is changed from Settings directly instead of through this tile, `ChargingModePrefs` goes stale until
the next tap — there is no notification, broadcast, or `ContentObserver` that could catch that,
because the value can't be read to confirm what changed even if something did.

If a future change touches this function, don't try to "fix" it back into reading the live value —
that read is not currently possible for a normal app, not a bug in this codebase.

### Private DNS (`tiles/PrivateDnsTileService.kt`, `tiles/PrivateDns.kt`)

Toggles Private DNS Automatic (opportunistic) ↔ whatever hostname is already saved in Settings >
Network & internet > Private DNS, same two of three modes Battery Charge Optimization exposes for
its own setting — `Off` is deliberately excluded from the toggle here too, for the same reason: the
tile should never be the thing that turns a protection off, only the thing that switches between two
"on" positions. From `Off`, `togglePrivateDnsMode()` moves to `Auto` first (never straight to
`Hostname`) — see that function's doc in `PrivateDns.kt`.

This writes the same two undocumented `Settings.Global` keys (`private_dns_mode`,
`private_dns_specifier`) Android's own Private DNS screen writes — same reverse-engineered-settings
category as Battery Charge Optimization, credited to
[flashsphere/private-dns-qs](https://github.com/flashsphere/private-dns-qs) and its upstream
[joshuawolfsohn/Private-DNS-Quick-Tile](https://github.com/joshuawolfsohn/Private-DNS-Quick-Tile).
Writing either key needs `WRITE_SECURE_SETTINGS` — same permission, same no-Settings-screen-grants-it
situation, same `adb shell pm grant` flow as Battery Charge Optimization; a single grant covers both
tiles at once, and `PrivateDnsTileService` checks the same `hasWriteSecureSettings()`.

**Unlike Battery Charge Optimization, this one genuinely can read the live value back.**
`Settings.Global.getString` on `private_dns_mode`/`private_dns_specifier` is not `@hide`-restricted
the way `adaptive_charging_enabled` is — both reference implementations above read these two keys
unconditionally, with no special-cased permission or `android:testOnly` manifest flag needed. So
`PrivateDns.kt` has no local prefs object at all, no persisted-state exception to the rule stated
above — `currentPrivateDnsMode()`/`currentPrivateDnsHostname()` read straight from `Settings.Global`
on every call, same as every other tile in this app except Battery Charge Optimization.

**"User not set" scenario:** `togglePrivateDnsMode()` can only ever fail to compute a next mode when
it's at `Auto` and `private_dns_specifier` is empty — `Off`→`Auto` never needs a hostname, and
`Hostname`→`Auto` implies one already exists. When that happens the tile can't silently succeed, so
`onClick()` opens `MainActivity` instead — same "tap to grant, don't silently fail" pattern as every
other setup-needed case in this app. Unlike the rest of this app's tiles, MainActivity's Private DNS
card needs to *collect user input* (there's no Settings screen deep link for "Private DNS"
specifically to hand off to, and neither reference implementation above found one either — the older
one solves this with its own in-app hostname `EditText` for the same reason), so
`PrivateDnsHostnameCard` — a small `OutlinedTextField` + Save button, not the shared `FeatureCard` —
writes the typed hostname straight to `Settings.Global` via `setPrivateDnsHostname()`. This isn't a
persisted-state exception either: the text field is scratch UI input before a save action, not a
copy of app state: the moment it's saved, the single source of truth is the OS setting exactly as
for every other tile, and to change the hostname *later* the only path is Android's own Private DNS
screen — Tooler always just follows whatever is currently saved there.

### Shared refresh pattern

Three of the five tiles follow the same shape: **never persist state, always read the system live,
and use `TileService.requestListeningState()` from whatever component changed that state
out-of-band** (the accessibility service, the keep-awake service, the ringer-mode receiver) so the
tile catches up immediately instead of waiting for the QS panel to next be pulled down. Keep new
tiles on this pattern unless, like Battery Charge Optimization, the platform makes a live read
structurally impossible. Private DNS reads live too (see above) but, like Battery Charge
Optimization, has no equivalent out-of-band signal to hook — nothing broadcasts when
`private_dns_mode` changes from Settings directly, so both just re-read on `onStartListening()`
instead and can lag until the QS panel is next opened.

### Tile tap latency

Two separate things affect how fast a tile responds, and only one of them is fully ours to fix:

- **Within our control:** each `<service>`'s manifest entry carries
  `<meta-data android:name="android.service.quicksettings.TOGGLEABLE_TILE" android:value="true" />`
  — this tells System UI the tap is a toggle, so it can animate the press/state change in its own
  process immediately instead of waiting on a Binder round-trip into ours before showing any
  feedback at all. Combined with each tile's own optimistic state updates (see Keep Screen On
  above), this is what "fast" looks like when our process is already warm.
- **Not fully ours to fix:** if Android has killed the app's process for memory (it does this to
  every app, not just this one), the *first* tap after that has to wait for a fresh process to
  spin up before `onClick()` can run at all — a fixed cost of Zygote fork + class loading that's
  largely outside app-code control, and the reason literally every third-party QS tile app
  occasionally feels laggy right after being idle. `MainActivity`'s opt-in "Background reliability"
  card (`Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, same never-silent pattern as
  ownscreen's equivalent button) reduces how often that happens by making the OS less eager to kill
  the process between taps — it doesn't eliminate cold starts, just makes them rarer.

### UI (`MainActivity.kt`, `ui/FeatureCard.kt`)

One screen, no navigation. Five `FeatureCard`s (a small shared composable — title, live status,
description, optional action button) show the same status each tile reads plus the opt-in battery
card above, and a way to jump to the relevant Settings screen or (for Keep Screen On) toggle
directly from the app instead of the QS panel. Every status is re-read `ON_RESUME`
(`DisposableEffect` + `LifecycleEventObserver`, same pattern as linker's `MainActivity`) since all
of them can change from outside this screen — Settings, hardware buttons, or the tiles themselves.

## Shortcuts

### Lock Screen (`shortcuts/LockScreenShortcutActivity.kt`)

**Not an AppWidget.** An earlier version of this was a classic `AppWidgetProvider` + `RemoteViews`
1×1 widget, but that was replaced after directly comparing it against
[BLumia/pineapple-lock-screen](https://github.com/BLumia/pineapple-lock-screen) — that project
does the same "lock the screen from the home screen" job as a launcher **shortcut** instead: a
`Theme.NoDisplay` activity carrying an `ACTION_CREATE_SHORTCUT` intent-filter, which most launchers
surface in the same "add to home screen" picker as widgets. No widget host, no RemoteViews
view-hierarchy restrictions, no `AppWidgetManager` push-update plumbing to maintain — this is
simpler for something with no live state to show, which is exactly `pineapple-lock-screen`'s own
reasoning for it.

`LockScreenShortcutActivity.onCreate()` branches on `intent.action`:
- `ACTION_CREATE_SHORTCUT` (the launcher's own picker, when the user drags this onto the home
  screen): builds a `ShortcutInfo` via `ShortcutManager.createShortcutResultIntent()` (API 26+,
  always available at this app's `minSdk` 28 — the pre-26 `EXTRA_SHORTCUT_ICON_RESOURCE` fallback
  exists only so a null `ShortcutManager` can't silently produce a broken result) and returns it as
  the activity result. The pinned icon's own stored intent uses `ACTION_VIEW` with this activity as
  an *explicit* component (`Intent(ACTION_VIEW, null, this, LockScreenShortcutActivity::class.java)`)
  — no separate `VIEW` intent-filter is needed on the activity for that to resolve.
- Anything else (the user tapping the pinned icon later): performs the lock immediately through
  `ScreenshotAccessibilityService.instance` — the same accessibility service the Screenshot tile
  uses, reused rather than duplicated (see that section above) — then finishes. If the service
  isn't enabled, shows a `Toast` explaining why and opens Accessibility Settings instead.

Either path calls `finish()` at the end of `onCreate()`; combined with `Theme.NoDisplay` in the
manifest, this activity never actually draws a frame in either case.

Looks like a real launcher icon, not a bare glyph — nord0 background and nord8 frost-blue glyph,
same pairing as `mipmap-anydpi-v26/ic_launcher.xml`, so the shortcut reads as part of Tooler's own
icon family. `ic_lock_screen_foreground.xml` reuses the same Material Symbols Rounded "lock" path
data as a plain vector, wrapped in a `<group scaleX/scaleY/translateX/translateY>` to fit it inside
the ~66dp adaptive-icon safe zone of a 108×108 viewport — same convention `ic_launcher_foreground.xml`
already follows for the three-tiles glyph. There's also a real adaptive-icon *resource*
(`mipmap-anydpi-v26/ic_lock_screen_shortcut.xml`, background=color/foreground=drawable, same
structure as `ic_launcher.xml`) — but that mipmap is used only for the manifest `android:icon`
(the activity's entry in app-info-style listings), **not** for the actual pinned icon. See
[`LockScreenShortcutActivity.buildAdaptiveIcon()`] for that:

**Passing `Icon.createWithResource(this, R.mipmap.ic_lock_screen_shortcut)` to
`ShortcutInfo.Builder.setIcon()` still produced a visibly doubled/badged icon on-device** — the
nord0 background rendered separately from a second, differently-scaled copy of the glyph.
`ShortcutManager`'s icon handling doesn't reliably recognize a resource reference to a
`mipmap-anydpi-v26` `<adaptive-icon>` XML the way `PackageManager` does for full launcher icons —
apparently launcher-dependent, but broken on the device this was tested on. `buildAdaptiveIcon()`
instead rasterizes the same background+foreground pairing into a plain `Bitmap` at the 108dp
adaptive-icon canvas size (`canvas.drawColor` for the flat nord0 background, then draws
`ic_lock_screen_foreground` on top) and wraps it with `Icon.createWithAdaptiveBitmap()` — which
explicitly marks the bitmap as pre-composed adaptive content, so the launcher masks it directly
instead of guessing how to adapt a "legacy" resource reference. This is the fix that actually
resolved the doubling; the `<adaptive-icon>` XML resource alone (correct as it is, and still used
for the manifest icon) was not sufficient for `ShortcutInfo` specifically.

**An even earlier version used a flat `layer-list` drawable (background square drawn by us +
centered glyph) instead of any adaptive representation at all** — same doubled-icon symptom, worse,
since it also lacked a real background/foreground split. The lesson holds at two levels: a flat
drawable is never enough for anything the launcher itself renders (app icons, shortcuts), and for
`ShortcutInfo` specifically, even a proper `<adaptive-icon>` XML *resource reference* isn't
guaranteed to be recognized — `Icon.createWithAdaptiveBitmap()` on a rasterized `Bitmap` is the
version that's actually been confirmed working on a real device.

## Adding a widget

No AppWidget exists in this app (see Shortcuts above for why the one home-screen entry point here
is a launcher shortcut instead). If a future feature genuinely needs a resizable, live-updating
home-screen surface — something a shortcut icon can't do — follow noter's pattern (`../noter`'s
`CLAUDE.md` and `widget/` package), not Jetpack Glance: classic `AppWidgetProvider` + `RemoteViews`,
refreshed by pushing an update from whatever action changed the relevant state (same
"push-from-the-source-of-truth" shape the tiles already use here), with an `AlarmManager` fallback
only if OEM throttling turns out to drop updates in practice. Don't add Room/DataStore for a widget
unless it needs to show something this app doesn't already read live from the system, and don't
reach for an AppWidget at all if a plain shortcut (no live state, just a tap action) would do.

## Theme

`ui/theme/Theme.kt`'s `NordDarkColorScheme`/`NordLightColorScheme` and `ui/theme/Color.kt`'s
palette are copied verbatim from linker/noter/ownscreen — see linker's `CLAUDE.md` "Theme" section
for why every M3 color role is filled in explicitly rather than left to `darkColorScheme()`'s
purple-tinted defaults. Keep it in sync if the sibling apps' palette ever changes.

## Fonts

Martian Mono Nerd Font ships as bundled `.ttf`s under `res/font/` (Regular + Medium — no bold
anywhere in this app; license in `MARTIAN_MONO_LICENSE.txt` at repo root), applied via
`ui/theme/Type.kt` exactly like the sibling apps.

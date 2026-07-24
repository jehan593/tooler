package com.tooler.app.tiles

import android.content.ContentResolver
import android.content.Context
import android.provider.Settings

/**
 * `Settings.Secure` keys behind Pixel's Settings > Battery > Charging optimization screen
 * (Adaptive Charging / Limit to 80%, added in the December 2024 update / Android 15 QPR1, Pixel
 * 6a and later). There is no public Android SDK API for this feature — no `BatteryManager` call,
 * no documented `Settings` constant — so this writes the same two hidden ints that screen itself
 * writes. Both are plain 0/1 flags; "Off" is simply both at 0.
 *
 * Writing either key requires `WRITE_SECURE_SETTINGS`, which — unlike the Accessibility or DND
 * access this app's other tiles gate on — a normal app can never prompt for at runtime; the only
 * way to grant it is `adb shell pm grant com.tooler.app android.permission.WRITE_SECURE_SETTINGS`
 * from a computer. See [BatteryChargeTileService] for how the tile handles that not being granted
 * yet, and `MainActivity`'s Battery Charge Optimization card for the copy-the-adb-command flow.
 *
 * **This mode can be written but not read back.** Confirmed on a real Pixel via logcat: reading
 * either key through the public `Settings.Secure` API throws `SecurityException` for any
 * non-system app — "Settings key: <adaptive_charging_enabled> is not readable. From S+, settings
 * keys annotated with @hide are restricted to system_server and system apps only, unless they are
 * annotated with @Readable." This is an Android 12+ platform restriction, not something
 * `WRITE_SECURE_SETTINGS` grants an exemption from — writes and reads are gated separately, and
 * only the write side is open to a normal app. [TebbeUbben/ChargeQuickTile](https://github.com/TebbeUbben/ChargeQuickTile)
 * (the technique this was reverse-engineered from) only reads successfully because its manifest
 * declares `android:testOnly="true"`, which apparently exempts it — but that requires installing
 * via `adb install -t`, incompatible with how Tooler actually ships (Obtainium / a plain signed
 * APK). So instead of reading the live value, [ChargingModePrefs] remembers the last mode *this
 * app itself* wrote, purely to compute what to cycle to next — the one deliberate exception to
 * "never persist local state" in this codebase, forced by an OS restriction with no workaround
 * available to a normally-distributed app. If the mode is ever changed from Settings directly
 * (not through this tile), this local record silently goes stale until the next tap — there is no
 * way to detect that without the ability to read.
 */
private const val KEY_ADAPTIVE_CHARGING = "adaptive_charging_enabled"
private const val KEY_CHARGE_OPTIMIZATION = "charge_optimization_mode"

enum class ChargingMode { OFF, ADAPTIVE, LIMIT_80 }

private object ChargingModePrefs {
    private const val PREFS_NAME = "charging_mode"
    private const val KEY_LAST_MODE = "last_mode"

    fun read(context: Context): ChargingMode {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return ChargingMode.entries.getOrElse(prefs.getInt(KEY_LAST_MODE, 0)) { ChargingMode.OFF }
    }

    fun write(context: Context, mode: ChargingMode) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_LAST_MODE, mode.ordinal)
            .apply()
    }
}

/** The last mode this app itself set — see the class doc above for why this isn't a live read. */
fun lastKnownChargingMode(context: Context): ChargingMode = ChargingModePrefs.read(context)

/**
 * Toggles Adaptive Charging <-> Limit to 80%, writing the two keys in the exact per-transition
 * order [TebbeUbben/ChargeQuickTile](https://github.com/TebbeUbben/ChargeQuickTile) uses, then
 * records the new mode locally (see the class doc above — this is the only way this tile can know
 * what to cycle to next, since reading the keys back is blocked for a normal app). `OFF` is only
 * ever the pre-first-tap default (`ChargingModePrefs` has never been written) — deliberately not a
 * reachable step in this cycle, so the tile never turns optimization off on its own; from `OFF`
 * the first tap moves to `ADAPTIVE`, same as everywhere else `OFF` shows up. Returns false instead
 * of throwing if `WRITE_SECURE_SETTINGS` isn't actually granted; callers check
 * [com.tooler.app.util.hasWriteSecureSettings] first, this is just the last line of defense.
 */
fun advanceChargingMode(context: Context): Boolean {
    val contentResolver: ContentResolver = context.contentResolver
    val next = when (ChargingModePrefs.read(context)) {
        ChargingMode.OFF -> ChargingMode.ADAPTIVE
        ChargingMode.ADAPTIVE -> ChargingMode.LIMIT_80
        ChargingMode.LIMIT_80 -> ChargingMode.ADAPTIVE
    }
    return try {
        when (next) {
            ChargingMode.ADAPTIVE -> {
                Settings.Secure.putInt(contentResolver, KEY_CHARGE_OPTIMIZATION, 0)
                Settings.Secure.putInt(contentResolver, KEY_ADAPTIVE_CHARGING, 1)
            }
            ChargingMode.LIMIT_80 -> {
                Settings.Secure.putInt(contentResolver, KEY_ADAPTIVE_CHARGING, 0)
                Settings.Secure.putInt(contentResolver, KEY_CHARGE_OPTIMIZATION, 1)
            }
            ChargingMode.OFF -> {
                Settings.Secure.putInt(contentResolver, KEY_CHARGE_OPTIMIZATION, 0)
                Settings.Secure.putInt(contentResolver, KEY_ADAPTIVE_CHARGING, 0)
            }
        }
        ChargingModePrefs.write(context, next)
        true
    } catch (e: SecurityException) {
        false
    }
}

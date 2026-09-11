package com.tooler.app.tiles

import android.content.ContentResolver
import android.content.Context
import android.provider.Settings

/**
 * The hidden `Settings.Secure` keys behind Pixel's charging-optimization screen (Adaptive Charging /
 * Limit to 80%). There is no public API for this — this writes the same two 0/1 flags that screen
 * writes. Writing needs `WRITE_SECURE_SETTINGS`, which a normal app can only get via `pm grant`.
 *
 * The mode can be **written but not read back**: reading throws `SecurityException` for any
 * non-system app on Android 12+ (confirmed on a real device), and `WRITE_SECURE_SETTINGS` doesn't
 * exempt reads. So [ChargingModePrefs] remembers the last mode this app wrote — pure OS-forced
 * persistence; if the mode is changed from Settings directly, this goes stale until the next tap.
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

/** The last mode this app set — can't be a live read (see the file doc). */
fun lastKnownChargingMode(context: Context): ChargingMode = ChargingModePrefs.read(context)

/**
 * Cycles Adaptive Charging <-> Limit to 80%, writing the two keys in the same per-transition order
 * as [TebbeUbben/ChargeQuickTile](https://github.com/TebbeUbben/ChargeQuickTile). Off is never a
 * reachable step — from the pre-first-tap default the first tap goes to Adaptive, so the tile never
 * turns optimization off on its own. Returns false if the write throws (permission missing).
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

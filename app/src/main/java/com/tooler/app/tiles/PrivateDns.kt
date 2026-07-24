package com.tooler.app.tiles

import android.content.Context
import android.provider.Settings

/**
 * `Settings.Global` keys behind Settings > Network & internet > Private DNS. Same undocumented
 * category as [ChargeOptimization]'s `Settings.Secure` writes — no `BatteryManager`-style public
 * API, `WRITE_SECURE_SETTINGS` required to write (see [com.tooler.app.util.hasWriteSecureSettings])
 * — but **unlike** those keys, `Settings.Global.getString` on these two is not gated on read the
 * way `adaptive_charging_enabled`/`charge_optimization_mode` are: they aren't `@hide`-restricted to
 * system apps, so this app can read live values back with no local cache at all. Confirmed by
 * [flashsphere/private-dns-qs](https://github.com/flashsphere/private-dns-qs) and its upstream
 * [joshuawolfsohn/Private-DNS-Quick-Tile](https://github.com/joshuawolfsohn/Private-DNS-Quick-Tile),
 * both of which read these same two keys unconditionally with no special-cased permission or
 * `testOnly` manifest flag.
 */
private const val PRIVATE_DNS_MODE = "private_dns_mode"
private const val PRIVATE_DNS_SPECIFIER = "private_dns_specifier"

private const val VALUE_AUTO = "opportunistic"
private const val VALUE_HOSTNAME = "hostname"

enum class PrivateDnsMode { OFF, AUTO, HOSTNAME }

/** Live read, every time — nothing about Private DNS state is ever cached in this app. */
fun currentPrivateDnsMode(context: Context): PrivateDnsMode {
    return when (Settings.Global.getString(context.contentResolver, PRIVATE_DNS_MODE)) {
        VALUE_AUTO -> PrivateDnsMode.AUTO
        VALUE_HOSTNAME -> PrivateDnsMode.HOSTNAME
        else -> PrivateDnsMode.OFF // covers "off", null (never touched), and any unrecognized value
    }
}

/** The hostname currently saved in Settings, regardless of which mode is active right now. */
fun currentPrivateDnsHostname(context: Context): String? =
    Settings.Global.getString(context.contentResolver, PRIVATE_DNS_SPECIFIER)?.trim()?.takeIf { it.isNotEmpty() }

/**
 * Toggles Automatic <-> the hostname already saved in [PRIVATE_DNS_SPECIFIER] — this app never
 * invents a hostname of its own; it only switches modes using whatever is already there, whether
 * that was set from Android's own Private DNS screen or from [setPrivateDnsHostname] below. `Off`
 * is deliberately not a step this toggle can reach (same reasoning as Battery Charge
 * Optimization's excluded `Off` — see `advanceChargingMode()`'s doc in ChargeOptimization.kt): from
 * `Off` the tile moves to `Auto` first, same as from `Hostname`, so the tap target is always
 * exactly one of the two real toggle positions.
 *
 * Returns false — the "user not set" case this needs to handle — when the next step would be
 * `Hostname` but [currentPrivateDnsHostname] is null. Callers should route to
 * [com.tooler.app.MainActivity]'s Private DNS card, which is the only place in this app that can
 * prompt for a hostname to type in.
 */
fun togglePrivateDnsMode(context: Context): Boolean {
    val next = if (currentPrivateDnsMode(context) == PrivateDnsMode.AUTO) {
        if (currentPrivateDnsHostname(context) == null) return false
        VALUE_HOSTNAME
    } else {
        VALUE_AUTO
    }
    return try {
        Settings.Global.putString(context.contentResolver, PRIVATE_DNS_MODE, next)
        true
    } catch (e: SecurityException) {
        false
    }
}

/**
 * Saves a new hostname and switches straight to hostname mode. Used only by MainActivity's Private
 * DNS card — the tile itself has no UI to type one in, so this is how the "user not set" scenario
 * gets resolved the first time.
 */
fun setPrivateDnsHostname(context: Context, hostname: String): Boolean {
    val trimmed = hostname.trim()
    if (trimmed.isEmpty()) return false
    return try {
        Settings.Global.putString(context.contentResolver, PRIVATE_DNS_SPECIFIER, trimmed)
        Settings.Global.putString(context.contentResolver, PRIVATE_DNS_MODE, VALUE_HOSTNAME)
        true
    } catch (e: SecurityException) {
        false
    }
}

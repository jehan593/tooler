package com.tooler.app.tiles

import android.content.Context
import android.provider.Settings

/**
 * The `Settings.Global` keys behind Settings > Network & internet > Private DNS. Writing needs
 * WRITE_SECURE_SETTINGS (same as ChargeOptimization), but unlike those keys these two can be read
 * back by any app — so nothing about Private DNS state is ever cached here.
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
 * invents a hostname itself. Off isn't reachable from here (same reasoning as Battery Charge
 * Optimization): from Off the toggle moves to Automatic first. Returns false — the "user not set"
 * case — when the next step needs a hostname but none is saved; callers should open the app's
 * Private DNS card, the one place a hostname can be typed.
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

/** Saves a new hostname and switches to hostname mode. Used by MainActivity's Private DNS card. */
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

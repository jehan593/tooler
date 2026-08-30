package com.tooler.app.customtiles

import com.tooler.app.R

/**
 * The icon picker for user-created tiles. Every built-in tile in this app has its own bespoke
 * drawable; a *user-created* tile can't, so this offers a curated set of Material Symbols Rounded
 * glyphs (the same icon family as the rest of the app — see the comments on each `.xml`) the user
 * can pick from when creating a tile. [res] degrades to the default terminal glyph rather than ever
 * crashing on a stale stored id.
 */
object CustomTileIcons {
    data class Entry(val id: String, val label: String, val keywords: List<String>)

    const val DEFAULT_ID = "terminal"

    val icons =
        listOf(
            Entry("terminal", "Terminal", listOf("shell", "cmd", "command", "adb", "terminal")),
            Entry("code", "Code", listOf("code", "dev", "script")),
            Entry("android", "Android", listOf("android", "system", "os")),
            Entry("power", "Power", listOf("power", "shutdown", "off")),
            Entry("restart", "Restart", listOf("restart", "reboot")),
            Entry("battery", "Battery", listOf("battery", "power", "charge", "energy")),
            Entry("wifi", "Wi-Fi", listOf("wifi", "network", "connect")),
            Entry("wifi_off", "Wi-Fi off", listOf("wifi", "network", "disconnect", "off")),
            Entry("bluetooth", "Bluetooth", listOf("bluetooth", "bt", "connect")),
            Entry("brightness", "Brightness", listOf("brightness", "display", "light")),
            Entry("screenshot", "Screenshot", listOf("screenshot", "capture", "screen")),
            Entry("dark_mode", "Dark mode", listOf("dark", "night", "theme")),
            Entry("light_mode", "Light mode", listOf("light", "day", "theme")),
            Entry("lock", "Lock", listOf("lock", "keyguard", "secure", "screen")),
            Entry("dns", "DNS", listOf("dns", "hostname", "network")),
            Entry("bolt", "Bolt", listOf("fast", "energy", "boost", "speed")),
            Entry("schedule", "Schedule", listOf("time", "timer", "sleep", "schedule")),
            Entry("settings", "Settings", listOf("settings", "config", "tune")),
            Entry("security", "Security", listOf("security", "permission", "auth")),
            Entry("memory", "Memory", listOf("memory", "ram", "performance")),
        )

    private val byId = icons.associateBy { it.id }

    private val resById =
        mapOf(
            "terminal" to R.drawable.ic_terminal,
            "code" to R.drawable.ic_custom_code,
            "android" to R.drawable.ic_custom_android,
            "power" to R.drawable.ic_custom_power,
            "restart" to R.drawable.ic_custom_restart,
            "battery" to R.drawable.ic_custom_battery,
            "wifi" to R.drawable.ic_custom_wifi,
            "wifi_off" to R.drawable.ic_custom_wifi_off,
            "bluetooth" to R.drawable.ic_custom_bluetooth,
            "brightness" to R.drawable.ic_custom_brightness,
            "screenshot" to R.drawable.ic_custom_screenshot,
            "dark_mode" to R.drawable.ic_custom_dark_mode,
            "light_mode" to R.drawable.ic_custom_light_mode,
            "lock" to R.drawable.ic_custom_lock,
            "dns" to R.drawable.ic_custom_dns,
            "bolt" to R.drawable.ic_custom_bolt,
            "schedule" to R.drawable.ic_custom_schedule,
            "settings" to R.drawable.ic_custom_settings,
            "security" to R.drawable.ic_custom_security,
            "memory" to R.drawable.ic_custom_memory,
        )

    fun res(id: String): Int = resById[id] ?: R.drawable.ic_terminal

    fun label(id: String): String = byId[id]?.label ?: "Terminal"
}
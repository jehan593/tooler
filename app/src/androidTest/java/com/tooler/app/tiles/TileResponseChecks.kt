package com.tooler.app.tiles

import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import com.tooler.app.customtiles.CustomTileConfig
import com.tooler.app.customtiles.CustomTilePrefs

/** Exercise real TileService callbacks through System UI and observe the published tile state. */
open class TileResponseChecks : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    private val results = mutableListOf<String>()
    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
        getUiAutomation(android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).executeShellCommand(command)).bufferedReader().use { it.readText().trim() }
    // UiAutomation executes argv directly, without shell quote parsing. Test values contain no spaces.
    private fun quote(value: String): String {
        require(value.none { it.isWhitespace() })
        return value
    }
    private fun setting(table: String, key: String) = shell("settings get $table $key")
    private fun put(table: String, key: String, value: String) {
        shell(if (value == "null") "settings delete $table $key" else "settings put $table $key ${quote(value)}")
    }
    private fun tile(component: String): String = shell("dumpsys activity service com.android.systemui/.SystemUIService")
        .lineSequence().firstOrNull { it.contains("spec=custom($component)") }.orEmpty()
    private fun expectTile(component: String, subtitle: String) {
        val start = SystemClock.elapsedRealtime()
        while (SystemClock.elapsedRealtime() - start < 4000) {
            if (tile(component).contains("secondaryLabel=$subtitle,")) return
            SystemClock.sleep(50)
        }
        error("Expected $subtitle; actual ${tile(component)}")
    }
    private fun awaitState(predicate: () -> Boolean) {
        val start = SystemClock.elapsedRealtime()
        while (!predicate()) {
            check(SystemClock.elapsedRealtime() - start < 5000) { "Underlying device state did not change" }
            SystemClock.sleep(50)
        }
    }
    private fun checkCase(name: String, action: () -> Unit) {
        val start = SystemClock.elapsedRealtime()
        try { action(); results += "PASS $name (${SystemClock.elapsedRealtime() - start} ms including adb/dump overhead)" }
        catch (t: Throwable) { results += "FAIL $name: ${t.message}" }
        sendStatus(0, Bundle().apply { putString("stream", results.last() + "\n") })
    }
    private fun click(component: String) { shell("cmd statusbar click-tile $component") }

    override fun onStart() {
        val c = targetContext
        val audio = c.getSystemService(AudioManager::class.java)
        val originalRinger = audio.ringerMode
        val originalTiles = setting("secure", "sysui_qs_tiles")
        val keys = listOf("secure" to Grayscale.ENABLED, "secure" to Grayscale.MODE,
            "secure" to "adaptive_charging_enabled", "secure" to "charge_optimization_mode",
            "global" to "private_dns_mode", "global" to "private_dns_specifier")
        val originalSettings = keys.associateWith { setting(it.first, it.second) }
        val originalCustom = CustomTilePrefs.load(c, 9)
        val chargingPrefs = c.getSharedPreferences("charging_mode", Context.MODE_PRIVATE)
        val originalCharging = chargingPrefs.getInt("last_mode", 0)
        val prefix = "com.tooler.app/.tiles."
        val volume = prefix + "VolumeModeTileService"
        val grayscale = prefix + "GrayscaleTileService"
        val keep = prefix + "KeepScreenOnTileService"
        val battery = prefix + "BatteryChargeTileService"
        val dns = prefix + "PrivateDnsTileService"
        val screenshot = prefix + "ScreenshotTileService"
        val custom = "com.tooler.app/.customtiles.CustomTile10Service"
        val tested = listOf(volume, grayscale, keep, battery, dns, screenshot, custom).map { "custom($it)" }
        fun show(component: String) {
            shell("cmd statusbar collapse")
            val first = "custom($component)"
            val roster = (listOf(first) + tested.filter { it != first } + originalTiles.split(",").filter { it !in tested }).joinToString(",")
            shell("cmd statusbar set-tiles ${quote(roster)}")
            shell("cmd statusbar expand-settings")
            SystemClock.sleep(600)
        }
        try {
            val keyguard = c.getSystemService(android.app.KeyguardManager::class.java)
            check(!keyguard.isKeyguardLocked) { "Unlock the device before running tile interaction checks" }
            CustomTilePrefs.save(c, CustomTileConfig(9, "Response test", "terminal", true, false,
                "true", "true", "Test on", "Test off"))
            val reordered = (tested + originalTiles.split(",").filter { it !in tested }).joinToString(",")
            shell("cmd statusbar set-tiles ${quote(reordered)}")
            shell("cmd statusbar expand-settings")
            SystemClock.sleep(1200)
            checkCase("Volume manual Normal") { audio.ringerMode = 2; expectTile(volume, "Normal") }
            checkCase("Volume tap Vibrate") { click(volume); expectTile(volume, "Vibrate"); check(audio.ringerMode == 1) }
            checkCase("Volume tap Silent") { click(volume); expectTile(volume, "Silent"); check(audio.ringerMode == 0) }
            checkCase("Volume tap Normal") { click(volume); expectTile(volume, "Normal"); check(audio.ringerMode == 2) }
            for ((mode, label) in listOf(1 to "Vibrate", 0 to "Silent", 2 to "Normal")) {
                checkCase("Volume external $label") { audio.ringerMode = mode; expectTile(volume, label) }
            }
            show(grayscale)
            put("secure", Grayscale.ENABLED, "0")
            put("secure", Grayscale.MODE, "12")
            checkCase("Grayscale tap on") { click(grayscale); awaitState { Grayscale.read(c).isGrayscale }; expectTile(grayscale, "On") }
            checkCase("Grayscale tap restore") { click(grayscale); awaitState { Grayscale.read(c) == ColorCorrectionState("0", "12") }; expectTile(grayscale, "Off") }
            checkCase("Grayscale external on") { put("secure", Grayscale.MODE, "0"); put("secure", Grayscale.ENABLED, "1"); expectTile(grayscale, "On") }
            checkCase("Grayscale external off") { put("secure", Grayscale.ENABLED, "0"); expectTile(grayscale, "Off") }
            show(keep)
            c.stopService(Intent(c, KeepAwakeService::class.java))
            awaitState { !KeepAwakeService.isRunning }
            expectTile(keep, "Off")
            checkCase("Keep Screen On tap on") { click(keep); awaitState { KeepAwakeService.isRunning }; expectTile(keep, "On") }
            checkCase("Keep Screen On external stop") { c.stopService(Intent(c, KeepAwakeService::class.java)); expectTile(keep, "Off") }
            checkCase("Keep Screen On tap on again") { click(keep); expectTile(keep, "On") }
            checkCase("Keep Screen On tap off") { click(keep); expectTile(keep, "Off") }
            show(battery)
            checkCase("Battery tap") { click(battery); SystemClock.sleep(300); check(!tile(battery).contains("secondaryLabel=Updating")); check(setting("secure", "charge_optimization_mode") in listOf("0", "1")) }
            checkCase("Battery external Adaptive") { put("secure", "charge_optimization_mode", "0"); put("secure", "adaptive_charging_enabled", "1"); expectTile(battery, "Adaptive Charging") }
            checkCase("Battery external Limit") { put("secure", "adaptive_charging_enabled", "0"); put("secure", "charge_optimization_mode", "1"); expectTile(battery, "Limit to 80%") }
            show(dns)
            checkCase("DNS external Off") { put("global", "private_dns_mode", "off"); expectTile(dns, "Off") }
            checkCase("DNS tap Automatic") { click(dns); expectTile(dns, "Automatic") }
            checkCase("DNS external hostname") { put("global", "private_dns_mode", "hostname"); expectTile(dns, setting("global", "private_dns_specifier")) }
            show(custom)
            checkCase("Custom command on") { click(custom); expectTile(custom, "Test on") }
            checkCase("Custom command off") { click(custom); expectTile(custom, "Test off") }
            // Screenshot is handled separately so the image and capture timing can be inspected.
            show(screenshot)
            results += "INFO Screenshot ready=" + tile(screenshot).contains("secondaryLabel=null")
        } finally {
            shell("cmd statusbar collapse")
            audio.ringerMode = originalRinger
            c.stopService(Intent(c, KeepAwakeService::class.java))
            originalSettings.forEach { (key, value) -> put(key.first, key.second, value) }
            chargingPrefs.edit().putInt("last_mode", originalCharging).commit()
            if (originalCustom == null) CustomTilePrefs.delete(c, 9) else CustomTilePrefs.save(c, originalCustom)
            shell("cmd statusbar set-tiles ${quote(originalTiles)}")
            results += "RESTORED original settings, tile order, and custom slot"
        }
        finish(-1, Bundle().apply { putString("stream", results.joinToString("\n", postfix="\n")) })
    }
}

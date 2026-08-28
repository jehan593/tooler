package com.tooler.app

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.tooler.app.tiles.ChargingMode
import com.tooler.app.tiles.KeepAwakeService
import com.tooler.app.tiles.PrivateDnsMode
import com.tooler.app.tiles.ScreenshotAccessibilityService
import com.tooler.app.tiles.currentPrivateDnsHostname
import com.tooler.app.tiles.currentPrivateDnsMode
import com.tooler.app.tiles.isLockedQsEnabled
import com.tooler.app.tiles.lastKnownChargingMode
import com.tooler.app.tiles.setLockedQsEnabled
import com.tooler.app.tiles.setPrivateDnsHostname
import com.tooler.app.tiles.togglePrivateDnsMode
import com.tooler.app.ui.FeatureCard
import com.tooler.app.ui.StatusTone
import com.tooler.app.ui.theme.ToolerTheme
import com.tooler.app.util.ShizukuUtils
import com.tooler.app.util.hasNotificationPolicyAccess
import com.tooler.app.util.hasWriteSecureSettings
import com.tooler.app.util.isAccessibilityServiceEnabled
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startKeepAwakeService()
        }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ToolerTheme {
                var accessibilityEnabled by remember {
                    mutableStateOf(isAccessibilityServiceEnabled(this, ScreenshotAccessibilityService::class.java))
                }
                var policyAccessGranted by remember { mutableStateOf(hasNotificationPolicyAccess(this)) }
                var keepAwakeOn by remember { mutableStateOf(KeepAwakeService.isRunning) }
                var ringerMode by remember { mutableStateOf(currentRingerModeLabel()) }
                var batteryUnrestricted by remember { mutableStateOf(isIgnoringBatteryOptimizations()) }
                var writeSecureSettingsGranted by remember { mutableStateOf(hasWriteSecureSettings(this)) }
                var chargingMode by remember { mutableStateOf(lastKnownChargingMode(this)) }
                var privateDnsMode by remember { mutableStateOf(currentPrivateDnsMode(this)) }
                var privateDnsHostname by remember { mutableStateOf(currentPrivateDnsHostname(this)) }
                var shizukuAvailable by remember { mutableStateOf(ShizukuUtils.isAvailable()) }
                var shizukuGranted by remember { mutableStateOf(ShizukuUtils.isGranted()) }
                var lockedQsEnabled by remember { mutableStateOf(isLockedQsEnabled(this)) }

                // Refreshes the Shizuku state the moment the shell-access grant dialog closes —
                // the toggle + tile re-read isGranted() whenever they paint, but the card's status
                // and button should update without waiting for the next ON_RESUME.
                DisposableEffect(Unit) {
                    val listener = Shizuku.OnRequestPermissionResultListener { requestCode, _ ->
                        if (requestCode == ShizukuUtils.REQUEST_CODE) {
                            shizukuGranted = ShizukuUtils.isGranted()
                        }
                    }
                    Shizuku.addRequestPermissionResultListener(listener)
                    onDispose { Shizuku.removeRequestPermissionResultListener(listener) }
                }

                // Re-reads every status on return from Settings/back-from-panel instead of only
                // once at launch — these can all change outside this screen (Settings, hardware
                // volume buttons, the tiles themselves).
                DisposableEffect(Unit) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            accessibilityEnabled =
                                isAccessibilityServiceEnabled(this@MainActivity, ScreenshotAccessibilityService::class.java)
                            policyAccessGranted = hasNotificationPolicyAccess(this@MainActivity)
                            keepAwakeOn = KeepAwakeService.isRunning
                            ringerMode = currentRingerModeLabel()
                            batteryUnrestricted = isIgnoringBatteryOptimizations()
                            writeSecureSettingsGranted = hasWriteSecureSettings(this@MainActivity)
                            chargingMode = lastKnownChargingMode(this@MainActivity)
                            privateDnsMode = currentPrivateDnsMode(this@MainActivity)
                            privateDnsHostname = currentPrivateDnsHostname(this@MainActivity)
                            // Shizuku availability can change out from under us too — the Shizuku app
                            // might be started/stopped, or its server restarted between visits.
                            shizukuAvailable = ShizukuUtils.isAvailable()
                            shizukuGranted = ShizukuUtils.isGranted()
                            lockedQsEnabled = isLockedQsEnabled(this@MainActivity)
                        }
                    }
                    lifecycle.addObserver(observer)
                    onDispose { lifecycle.removeObserver(observer) }
                }

                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainerLowest) {
                    Scaffold(
                        topBar = { TopAppBar(title = { Text("Tooler") }) }
                    ) { padding ->
                        LazyColumn(
                            modifier = Modifier
                                .padding(padding)
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            item {
                                Text(
                                    "Quick Settings tiles that many stock ROMs leave out — add them from the " +
                                        "Quick Settings panel: pull down twice, tap the pencil/edit icon, then " +
                                        "drag a tile in.",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            item {
                                FeatureCard(
                                    title = "Screenshot",
                                    status = if (accessibilityEnabled) "Ready" else "Setup needed",
                                    statusTone = if (accessibilityEnabled) StatusTone.SUCCESS else StatusTone.WARNING,
                                    iconRes = R.drawable.ic_screenshot,
                                    description = "Uses an Accessibility Service to trigger a screenshot — the " +
                                        "only non-root way to do it from a Quick Settings tile. The same " +
                                        "service also powers the Lock Screen home-screen shortcut.",
                                    actionLabel = if (accessibilityEnabled) null else "Enable accessibility service",
                                    onAction = if (accessibilityEnabled) null else {
                                        { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                                    }
                                )
                            }
                            item {
                                FeatureCard(
                                    title = "Keep Screen On",
                                    status = if (keepAwakeOn) "On" else "Off",
                                    statusTone = if (keepAwakeOn) StatusTone.SUCCESS else StatusTone.NEUTRAL,
                                    iconRes = R.drawable.ic_keep_screen_on,
                                    description = "Holds the screen awake until you turn it off again, from the " +
                                        "tile or here.",
                                    actionLabel = if (keepAwakeOn) "Turn off" else "Turn on",
                                    onAction = {
                                        if (keepAwakeOn) {
                                            stopService(Intent(this@MainActivity, KeepAwakeService::class.java))
                                            keepAwakeOn = false
                                        } else {
                                            requestNotificationPermissionThenStart()
                                            keepAwakeOn = true
                                        }
                                    }
                                )
                            }
                            item {
                                FeatureCard(
                                    title = "Volume Mode",
                                    status = ringerMode,
                                    iconRes = when (ringerMode) {
                                        "Vibrate" -> R.drawable.ic_volume_vibrate
                                        "Silent" -> R.drawable.ic_volume_silent
                                        else -> R.drawable.ic_volume_normal
                                    },
                                    description = if (policyAccessGranted) {
                                        "Cycles Normal → Vibrate → Silent from the tile — same effect as the " +
                                            "mute icon in Android's own volume panel. Notifications still show " +
                                            "normally; only their sound and vibration are affected."
                                    } else {
                                        "Silent needs Do Not Disturb access — that's just Android's gate on " +
                                            "this API, not Do Not Disturb itself: notifications will keep " +
                                            "showing normally either way."
                                    },
                                    actionLabel = if (policyAccessGranted) null else "Grant Do Not Disturb access",
                                    onAction = if (policyAccessGranted) null else {
                                        { startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) }
                                    }
                                )
                            }
                            item {
                                FeatureCard(
                                    title = "Battery Charge Optimization",
                                    status = when (chargingMode) {
                                        ChargingMode.OFF -> "Off"
                                        ChargingMode.ADAPTIVE -> "Adaptive Charging"
                                        ChargingMode.LIMIT_80 -> "Limit to 80%"
                                    },
                                    statusTone = if (writeSecureSettingsGranted) StatusTone.NEUTRAL else StatusTone.WARNING,
                                    iconRes = when (chargingMode) {
                                        ChargingMode.ADAPTIVE -> R.drawable.ic_battery_adaptive
                                        ChargingMode.LIMIT_80 -> R.drawable.ic_battery_limit_80
                                        else -> R.drawable.ic_battery_off
                                    },
                                    description = if (writeSecureSettingsGranted) {
                                        "Toggles Adaptive Charging ↔ Limit to 80% from the tile — the same " +
                                            "modes as Settings > Battery > Charging optimization on Pixel, " +
                                            "minus Off (the tile never turns optimization off on its own). " +
                                            "There's no public Android API for this; Tooler writes the same " +
                                            "hidden system settings that screen does. Android won't let a " +
                                            "normal app read those settings back, though, so the status above " +
                                            "is just the last mode Tooler itself set — if you change it from " +
                                            "Settings directly, this won't notice until you tap again."
                                    } else {
                                        "Pixel only (Android 15 QPR1+), and there's no public API for it — " +
                                            "Android won't let a normal app request this permission at all, " +
                                            "so it has to be granted once over ADB from a computer. Tap to " +
                                            "copy the command, run it with the phone connected, then reopen " +
                                            "this app."
                                    },
                                    actionLabel = if (writeSecureSettingsGranted) null else "Copy adb grant command",
                                    onAction = if (writeSecureSettingsGranted) null else {
                                        { copyWriteSecureSettingsGrantCommand() }
                                    }
                                )
                            }
                            item {
                                when {
                                    !writeSecureSettingsGranted -> FeatureCard(
                                        title = "Private DNS",
                                        status = "Setup needed",
                                        statusTone = StatusTone.WARNING,
                                        iconRes = R.drawable.ic_dns_off,
                                        description = "Toggles Private DNS Automatic ↔ a hostname you set, from " +
                                            "the tile — same modes as Settings > Network & internet > Private " +
                                            "DNS. Needs the same WRITE_SECURE_SETTINGS permission as Battery " +
                                            "Charge Optimization above; the same adb command grants both at " +
                                            "once.",
                                        actionLabel = "Copy adb grant command",
                                        onAction = { copyWriteSecureSettingsGrantCommand() }
                                    )
                                    privateDnsHostname == null -> PrivateDnsHostnameCard(
                                        onSave = { hostname ->
                                            if (setPrivateDnsHostname(this@MainActivity, hostname)) {
                                                privateDnsHostname = currentPrivateDnsHostname(this@MainActivity)
                                                privateDnsMode = currentPrivateDnsMode(this@MainActivity)
                                            }
                                        }
                                    )
                                    else -> FeatureCard(
                                        title = "Private DNS",
                                        status = when (privateDnsMode) {
                                            PrivateDnsMode.HOSTNAME -> "Custom: $privateDnsHostname"
                                            PrivateDnsMode.AUTO -> "Automatic"
                                            PrivateDnsMode.OFF -> "Off"
                                        },
                                        iconRes = when (privateDnsMode) {
                                            PrivateDnsMode.HOSTNAME -> R.drawable.ic_dns_on
                                            PrivateDnsMode.AUTO -> R.drawable.ic_dns_auto
                                            else -> R.drawable.ic_dns_off
                                        },
                                        description = "Toggles Automatic ↔ \"$privateDnsHostname\" from the " +
                                            "tile or here. To use a different hostname, change it in Settings " +
                                            "> Network & internet > Private DNS — Tooler always follows " +
                                            "whatever's saved there, live, with nothing of its own to go stale.",
                                        actionLabel = if (privateDnsMode == PrivateDnsMode.AUTO) {
                                            "Switch to hostname"
                                        } else {
                                            "Switch to Automatic"
                                        },
                                        onAction = {
                                            if (togglePrivateDnsMode(this@MainActivity)) {
                                                privateDnsMode = currentPrivateDnsMode(this@MainActivity)
                                            }
                                        }
                                    )
                                }
                            }
                            item {
                                FeatureCard(
                                    title = "Lock Quick Settings",
                                    status = when {
                                        !shizukuAvailable -> "Shizuku not running"
                                        !shizukuGranted -> "Setup needed"
                                        lockedQsEnabled -> "On"
                                        else -> "Off"
                                    },
                                    statusTone = when {
                                        lockedQsEnabled -> StatusTone.SUCCESS
                                        else -> StatusTone.WARNING
                                    },
                                    iconRes = R.drawable.ic_locked_qs,
                                    description = if (shizukuGranted) {
                                        "Collapses the Quick Settings panel while the screen is locked, so it " +
                                            "can't be pulled down from the lock screen, and brings it back the " +
                                            "moment you unlock. There's no Android API for this — it runs a " +
                                            "hidden OS command (cmd statusbar send-disable-flag) as the shell " +
                                            "user through Shizuku. It only catches the lock/unlock events while " +
                                            "Tooler's process is alive (see the Background reliability card " +
                                            "below), and the flag resets on every reboot."
                                    } else {
                                        "Collapses the Quick Settings panel while the screen is locked. Needs " +
                                            "Shizuku because the only way to do this is a hidden OS command that " +
                                            "just the shell user may run — no Settings screen or adb grant can " +
                                            "substitute. Grant it below; the same grant also powers the tile."
                                    },
                                    actionLabel = when {
                                        !shizukuAvailable -> "Open Shizuku"
                                        !shizukuGranted -> "Grant shell access"
                                        lockedQsEnabled -> "Turn off"
                                        else -> "Turn on"
                                    },
                                    onAction = {
                                        when {
                                            !shizukuAvailable -> openShizuku()
                                            !shizukuGranted -> ShizukuUtils.requestPermission()
                                            lockedQsEnabled -> {
                                                setLockedQsEnabled(this@MainActivity, false)
                                                lockedQsEnabled = false
                                            }
                                            else -> {
                                                setLockedQsEnabled(this@MainActivity, true)
                                                lockedQsEnabled = true
                                            }
                                        }
                                    }
                                )
                            }
                            item {
                                FeatureCard(
                                    title = "Background reliability",
                                    status = if (batteryUnrestricted) "Exempted" else "Optimized",
                                    statusTone = if (batteryUnrestricted) StatusTone.SUCCESS else StatusTone.NEUTRAL,
                                    description = "Optional. Android occasionally kills this app to save memory, " +
                                        "which is what makes a tile feel slow to respond right after — the next " +
                                        "tap has to wait for the app to restart first. Excluding it from battery " +
                                        "optimization makes that far less frequent.",
                                    actionLabel = if (batteryUnrestricted) null else "Exclude from battery optimization",
                                    onAction = if (batteryUnrestricted) null else {
                                        {
                                            startActivity(
                                                Intent(
                                                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                                    Uri.parse("package:$packageName")
                                                )
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun requestNotificationPermissionThenStart() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startKeepAwakeService()
        }
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(packageName)
    }

    private fun startKeepAwakeService() {
        ContextCompat.startForegroundService(this, Intent(this, KeepAwakeService::class.java))
    }

    private fun copyWriteSecureSettingsGrantCommand() {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(
            ClipData.newPlainText(
                "adb command",
                "adb shell pm grant $packageName android.permission.WRITE_SECURE_SETTINGS"
            )
        )
        Toast.makeText(this, "Command copied", Toast.LENGTH_SHORT).show()
    }

    private fun openShizuku() {
        val launchIntent = packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
        if (launchIntent != null) {
            startActivity(launchIntent)
        } else {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/")))
        }
    }

    private fun currentRingerModeLabel(): String {
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        return when (audioManager.ringerMode) {
            AudioManager.RINGER_MODE_VIBRATE -> "Vibrate"
            AudioManager.RINGER_MODE_SILENT -> "Silent"
            else -> "Normal"
        }
    }
}

/**
 * The "user not set" case the Private DNS tile can't handle on its own: no hostname is saved yet
 * (`private_dns_specifier` is empty), so there's nothing for the tile to toggle into besides
 * Automatic. This is the only place in Tooler that writes a value the user typed rather than just
 * flipping a mode on something already set — everywhere else, "no persisted state" holds because
 * every other tile only ever mirrors a value that already exists somewhere in the system.
 */
@Composable
private fun PrivateDnsHostnameCard(onSave: (String) -> Unit) {
    var hostname by remember { mutableStateOf("") }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(R.drawable.ic_dns_auto),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("Private DNS", style = MaterialTheme.typography.titleMedium)
            }
            Text(
                "No hostname saved yet, so the tile has nothing to switch to besides Automatic. " +
                    "Type your Private DNS provider's hostname (e.g. dns.google) and save it here " +
                    "once — after that, the tile toggles Automatic ↔ this hostname on its own.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
            )
            OutlinedTextField(
                value = hostname,
                onValueChange = { hostname = it },
                label = { Text("Hostname") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(modifier = Modifier.padding(top = 8.dp)) {
                Button(
                    onClick = {
                        onSave(hostname)
                        hostname = ""
                    },
                    enabled = hostname.isNotBlank()
                ) {
                    Text("Save & enable")
                }
            }
        }
    }
}

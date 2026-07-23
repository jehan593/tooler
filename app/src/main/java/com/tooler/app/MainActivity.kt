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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.tooler.app.tiles.ChargingMode
import com.tooler.app.tiles.KeepAwakeService
import com.tooler.app.tiles.ScreenshotAccessibilityService
import com.tooler.app.tiles.lastKnownChargingMode
import com.tooler.app.ui.FeatureCard
import com.tooler.app.ui.theme.ToolerTheme
import com.tooler.app.util.hasNotificationPolicyAccess
import com.tooler.app.util.hasWriteSecureSettings
import com.tooler.app.util.isAccessibilityServiceEnabled

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
                        }
                    }
                    lifecycle.addObserver(observer)
                    onDispose { lifecycle.removeObserver(observer) }
                }

                Surface(modifier = Modifier.fillMaxSize()) {
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
                                    description = if (writeSecureSettingsGranted) {
                                        "Cycles Off → Adaptive Charging → Limit to 80% from the tile — the " +
                                            "same modes as Settings > Battery > Charging optimization on " +
                                            "Pixel. There's no public Android API for this; Tooler writes the " +
                                            "same hidden system settings that screen does. Android won't let " +
                                            "a normal app read those settings back, though, so the status " +
                                            "above is just the last mode Tooler itself set — if you change it " +
                                            "from Settings directly, this won't notice until you tap again."
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
                                FeatureCard(
                                    title = "Background reliability",
                                    status = if (batteryUnrestricted) "Exempted" else "Optimized",
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

    private fun currentRingerModeLabel(): String {
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        return when (audioManager.ringerMode) {
            AudioManager.RINGER_MODE_VIBRATE -> "Vibrate"
            AudioManager.RINGER_MODE_SILENT -> "Silent"
            else -> "Normal"
        }
    }
}

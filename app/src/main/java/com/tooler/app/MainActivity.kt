package com.tooler.app

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.service.quicksettings.TileService
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.tooler.app.customtiles.CustomTilePrefs
import com.tooler.app.customtiles.QsTilesActivity
import com.tooler.app.customtiles.TileComponentManager
import com.tooler.app.shortcuts.LockScreenShortcut
import com.tooler.app.tiles.BatteryChargeTileService
import com.tooler.app.tiles.ChargingMode
import com.tooler.app.tiles.KeepAwakeService
import com.tooler.app.tiles.KeepScreenOnTileService
import com.tooler.app.tiles.PrivateDnsMode
import com.tooler.app.tiles.PrivateDnsTileService
import com.tooler.app.tiles.ScreenshotAccessibilityService
import com.tooler.app.tiles.ScreenshotTileService
import com.tooler.app.tiles.VolumeModeTileService
import com.tooler.app.tiles.currentPrivateDnsHostname
import com.tooler.app.tiles.currentPrivateDnsMode
import com.tooler.app.tiles.isLockedQsEnabled
import com.tooler.app.tiles.lastKnownChargingMode
import com.tooler.app.tiles.setLockedQsEnabled
import com.tooler.app.tiles.setPrivateDnsHostname
import com.tooler.app.ui.FeatureCard
import com.tooler.app.ui.SectionHeader
import com.tooler.app.ui.StatusTone
import com.tooler.app.ui.TilePermissionAction
import com.tooler.app.ui.TileCard
import com.tooler.app.ui.TilePermission
import com.tooler.app.ui.theme.ToolerTheme
import com.tooler.app.util.ShizukuUtils
import com.tooler.app.util.StatusNotifier
import com.tooler.app.util.TilePanelPrefs
import com.tooler.app.util.copyWriteSecureSettingsGrantCommand
import com.tooler.app.util.grantWriteSecureSettings
import com.tooler.app.util.hasNotificationPolicyAccess
import com.tooler.app.util.hasWriteSecureSettings
import com.tooler.app.util.isAccessibilityServiceEnabled
import com.tooler.app.util.openShizuku
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

/**
 * One screen showing every tile's status, its setup permission, and an add-to-panel button. All
 * state is re-read live on every resume — tiles, shortcuts, and panel membership can all change
 * from outside this app.
 */
class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ToolerTheme {
                val components = remember {
                    listOf(
                        ComponentName(this, ScreenshotTileService::class.java),
                        ComponentName(this, KeepScreenOnTileService::class.java),
                        ComponentName(this, VolumeModeTileService::class.java),
                        ComponentName(this, BatteryChargeTileService::class.java),
                        ComponentName(this, PrivateDnsTileService::class.java),
                    )
                }
                // Re-read on resume; snapshot backed so a panel change recomposes the cards.
                var panelState by remember {
                    mutableStateOf(TilePanelPrefs.statusMap(this, components))
                }
                val tileInPanel: (ComponentName) -> Boolean = { c ->
                    panelState[c.flattenToString()] == true
                }

                var accessibilityEnabled by remember {
                    mutableStateOf(isAccessibilityServiceEnabled(this, ScreenshotAccessibilityService::class.java))
                }
                var policyAccessGranted by remember { mutableStateOf(hasNotificationPolicyAccess(this)) }
                var keepAwakeOn by remember { mutableStateOf(KeepAwakeService.isRunning) }
                var ringerMode by remember { mutableStateOf(currentRingerModeLabel()) }
                var writeSecureSettingsGranted by remember { mutableStateOf(hasWriteSecureSettings(this)) }
                var chargingMode by remember { mutableStateOf(lastKnownChargingMode(this)) }
                var privateDnsMode by remember { mutableStateOf(currentPrivateDnsMode(this)) }
                var privateDnsHostname by remember { mutableStateOf(currentPrivateDnsHostname(this)) }
                var shizukuAvailable by remember { mutableStateOf(ShizukuUtils.isAvailable()) }
                var shizukuGranted by remember { mutableStateOf(ShizukuUtils.isGranted()) }
                var lockedQsEnabled by remember { mutableStateOf(isLockedQsEnabled(this)) }
                var customTileCount by remember { mutableStateOf(CustomTilePrefs.usedCount(this)) }
                var shortcutPinned by remember { mutableStateOf(LockScreenShortcut.isPinned(this)) }

                val scope = rememberCoroutineScope()

                // Fires the system "add tile to Quick Settings?" prompt and records the result.
                val addTileToPanel: (ComponentName, String, Int) -> Unit = { component, label, iconRes ->
                    TileComponentManager.promptAddTile(this, component, label, iconRes) { result ->
                        if (result != StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED) {
                            TilePanelPrefs.setInPanel(applicationContext, component, true)
                            panelState = panelState + (component.flattenToString() to true)
                        }
                    }
                }
                val canPromptTileAdd: (ComponentName) -> Boolean = { component ->
                    Build.VERSION.SDK_INT >= 33 && !tileInPanel(component)
                }

                // Grants WRITE_SECURE_SETTINGS via Shizuku on a background thread; a refused grant
                // just leaves the card in its setup-needed state.
                val requestWriteSecureSettingsGrant: () -> Unit = {
                    scope.launch {
                        val granted = withContext(Dispatchers.IO) {
                            grantWriteSecureSettings(this@MainActivity)
                        }
                        writeSecureSettingsGranted = granted
                        if (!granted) {
                            Toast.makeText(
                                this@MainActivity,
                                "Grant failed — check that Shizuku has shell permission, or use:\n" +
                                    "adb shell pm grant $packageName android.permission.WRITE_SECURE_SETTINGS",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }

                val addAccessibilityPermission = TilePermission(
                    label = "Accessibility service",
                    description = "Lets the Screenshot tile and Lock Screen shortcut work.",
                    granted = accessibilityEnabled,
                    actions = listOf(
                        TilePermissionAction(
                            label = "Open settings",
                            onClick = {
                                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                            }
                        )
                    )
                )
                val addWriteSecureSettingsPermission = TilePermission(
                    label = "WRITE_SECURE_SETTINGS",
                    description = "One-time shell grant for Battery Charge Optimization and Private " +
                        "DNS. Do it through Shizuku, or copy an adb command.",
                    granted = writeSecureSettingsGranted,
                    actions = listOf(
                        TilePermissionAction(
                            label = "Grant with Shizuku",
                            primary = true,
                            onClick = {
                                when {
                                    !shizukuAvailable -> openShizuku(this@MainActivity)
                                    !shizukuGranted -> ShizukuUtils.requestPermission()
                                    else -> requestWriteSecureSettingsGrant()
                                }
                            }
                        ),
                        TilePermissionAction(
                            label = "Copy ADB command",
                            onClick = { copyWriteSecureSettingsGrantCommand(this@MainActivity) }
                        )
                    )
                )

                // Refreshes Shizuku state when the grant dialog closes; a fresh grant is followed
                // straight through to the WRITE_SECURE_SETTINGS pm grant.
                DisposableEffect(Unit) {
                    val listener = Shizuku.OnRequestPermissionResultListener { requestCode, _ ->
                        if (requestCode == ShizukuUtils.REQUEST_CODE) {
                            shizukuGranted = ShizukuUtils.isGranted()
                            if (shizukuGranted && !hasWriteSecureSettings(this@MainActivity)) {
                                requestWriteSecureSettingsGrant()
                            }
                        }
                    }
                    Shizuku.addRequestPermissionResultListener(listener)
                    onDispose { Shizuku.removeRequestPermissionResultListener(listener) }
                }

                // Re-reads every status from the system: on resume, and on every StatusNotifier tick
                // (a tile tapped from the QS overlay, the Keep Awake notification's action, or a
                // hardware volume key while this screen sits underneath).
                val refreshStatuses: () -> Unit = {
                    accessibilityEnabled =
                        isAccessibilityServiceEnabled(this@MainActivity, ScreenshotAccessibilityService::class.java)
                    policyAccessGranted = hasNotificationPolicyAccess(this@MainActivity)
                    keepAwakeOn = KeepAwakeService.isRunning
                    ringerMode = currentRingerModeLabel()
                    writeSecureSettingsGranted = hasWriteSecureSettings(this@MainActivity)
                    chargingMode = lastKnownChargingMode(this@MainActivity)
                    privateDnsMode = currentPrivateDnsMode(this@MainActivity)
                    privateDnsHostname = currentPrivateDnsHostname(this@MainActivity)
                    shizukuAvailable = ShizukuUtils.isAvailable()
                    shizukuGranted = ShizukuUtils.isGranted()
                    lockedQsEnabled = isLockedQsEnabled(this@MainActivity)
                    customTileCount = CustomTilePrefs.usedCount(this@MainActivity)
                    shortcutPinned = LockScreenShortcut.isPinned(this@MainActivity)
                    panelState = TilePanelPrefs.statusMap(this@MainActivity, components)
                }

                // Flush pending add/remove callbacks, then re-read panel state.
                val refreshPanelMembership: () -> Unit = {
                    components.forEach {
                        try {
                            TileService.requestListeningState(this@MainActivity, it)
                        } catch (@Suppress("UNUSED_PARAMETER") e: Exception) {
                        }
                    }
                    scope.launch {
                        delay(700)
                        panelState = TilePanelPrefs.statusMap(this@MainActivity, components)
                    }
                }

                DisposableEffect(Unit) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            refreshStatuses()
                            refreshPanelMembership()
                        }
                    }
                    lifecycle.addObserver(observer)
                    onDispose { lifecycle.removeObserver(observer) }
                }

                // Collects StatusNotifier ticks, which fire when a tile, the Keep Awake notification,
                // or a hardware volume key changes state underneath this screen.
                LaunchedEffect(Unit) {
                    StatusNotifier.ticks.collect { refreshStatuses() }
                }

                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainerLowest) {
                    Scaffold(
                        topBar = {
                            TopAppBar(
                                title = { Text("Tooler") },
                                actions = {
                                    IconButton(onClick = {
                                        startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
                                    }) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_custom_settings),
                                            contentDescription = "Settings",
                                        )
                                    }
                                }
                            )
                        }
                    ) { padding ->
                        LazyColumn(
                            modifier = Modifier
                                .padding(padding)
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            item { SectionHeader("Quick Settings Tiles") }

                            item {
                                TileCard(
                                    title = "Screenshot",
                                    iconRes = R.drawable.ic_screenshot,
                                    status = if (accessibilityEnabled) "Ready" else "Setup needed",
                                    statusTone = if (accessibilityEnabled) StatusTone.SUCCESS else StatusTone.WARNING,
description = "Take a screenshot from Quick Settings. The shade is hidden " +
                        "first so it's not in the picture.",
                                    permission = addAccessibilityPermission,
                                    addIcon = R.drawable.ic_add,
                                    addDescription = "Add to panel",
                                    added = tileInPanel(components[0]),
                                    onAdd = if (canPromptTileAdd(components[0])) {
                                        {
                                            addTileToPanel(
                                                components[0],
                                                "Screenshot",
                                                R.drawable.ic_screenshot
                                            )
                                        }
                                    } else null
                                )
                            }

                            item {
                                TileCard(
                                    title = "Keep Screen On",
                                    iconRes = R.drawable.ic_keep_screen_on,
                                    status = if (keepAwakeOn) "On" else "Off",
                                    statusTone = if (keepAwakeOn) StatusTone.SUCCESS else StatusTone.NEUTRAL,
                                    description = "Keep the screen awake until you turn it off.",
                                    addIcon = R.drawable.ic_add,
                                    addDescription = "Add to panel",
                                    added = tileInPanel(components[1]),
                                    onAdd = if (canPromptTileAdd(components[1])) {
                                        {
                                            addTileToPanel(
                                                components[1],
                                                "Keep Screen On",
                                                R.drawable.ic_keep_screen_on
                                            )
                                        }
                                    } else null
                                )
                            }

                            item {
                                TileCard(
                                    title = "Volume Mode",
                                    iconRes = when (ringerMode) {
                                        "Vibrate" -> R.drawable.ic_volume_vibrate
                                        "Silent" -> R.drawable.ic_volume_silent
                                        else -> R.drawable.ic_volume_normal
                                    },
                                    status = ringerMode,
                                    description = if (policyAccessGranted) {
                                        "Cycle Normal, Vibrate, and Silent. Same as the mute icon — " +
                                            "only sound and vibration change."
                                    } else {
                                        "Silent mode needs a one-time permission."
                                    },
                                    permission = TilePermission(
                                        label = "Do Not Disturb access",
                                        description = "Lets the tile switch to Silent.",
                                        granted = policyAccessGranted,
                                        actions = listOf(
                                            TilePermissionAction(
                                                label = "Grant access",
                                                onClick = {
                                                    startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                                                }
                                            )
                                        )
                                    ),
                                    addIcon = R.drawable.ic_add,
                                    addDescription = "Add to panel",
                                    added = tileInPanel(components[2]),
                                    onAdd = if (canPromptTileAdd(components[2])) {
                                        {
                                            addTileToPanel(
                                                components[2],
                                                "Volume Mode",
                                                when (ringerMode) {
                                                    "Vibrate" -> R.drawable.ic_volume_vibrate
                                                    "Silent" -> R.drawable.ic_volume_silent
                                                    else -> R.drawable.ic_volume_normal
                                                }
                                            )
                                        }
                                    } else null
                                )
                            }

                            item {
                                TileCard(
                                    title = "Battery Charge Optimization",
                                    iconRes = when (chargingMode) {
                                        ChargingMode.ADAPTIVE -> R.drawable.ic_battery_adaptive
                                        ChargingMode.LIMIT_80 -> R.drawable.ic_battery_limit_80
                                        else -> R.drawable.ic_battery_off
                                    },
                                    status = when (chargingMode) {
                                        ChargingMode.OFF -> "Off"
                                        ChargingMode.ADAPTIVE -> "Adaptive Charging"
                                        ChargingMode.LIMIT_80 -> "Limit to 80%"
                                    },
                                    statusTone = if (writeSecureSettingsGranted) StatusTone.NEUTRAL else StatusTone.WARNING,
                                    description = if (writeSecureSettingsGranted) {
                                        "Switch between Adaptive Charging and Limit to 80%. Pixel only."
                                    } else {
                                        "Pixel only. Needs one-time shell access — grant it here " +
                                            "with Shizuku, or copy an adb command."
                                    },
                                    permission = addWriteSecureSettingsPermission,
                                    addIcon = R.drawable.ic_add,
                                    addDescription = "Add to panel",
                                    added = tileInPanel(components[3]),
                                    onAdd = if (canPromptTileAdd(components[3])) {
                                        {
                                            addTileToPanel(
                                                components[3],
                                                "Battery Charge Optimization",
                                                when (chargingMode) {
                                                    ChargingMode.ADAPTIVE -> R.drawable.ic_battery_adaptive
                                                    ChargingMode.LIMIT_80 -> R.drawable.ic_battery_limit_80
                                                    else -> R.drawable.ic_battery_off
                                                }
                                            )
                                        }
                                    } else null
                                )
                            }

                            item {
                                if (!writeSecureSettingsGranted) {
                                    TileCard(
                                        title = "Private DNS",
                                        iconRes = R.drawable.ic_dns_off,
                                        status = "Setup needed",
                                        statusTone = StatusTone.WARNING,
                                        description = "Needs the same one-time grant as Battery Charge " +
                                            "Optimization.",
                                        permission = addWriteSecureSettingsPermission,
                                        addIcon = R.drawable.ic_add,
                                        addDescription = "Add to panel",
                                        added = tileInPanel(components[4]),
                                        onAdd = if (canPromptTileAdd(components[4])) {
                                            {
                                                addTileToPanel(
                                                    components[4],
                                                    "Private DNS",
                                                    R.drawable.ic_dns_off
                                                )
                                            }
                                        } else null
                                    )
                                } else if (privateDnsHostname == null) {
                                    PrivateDnsHostnameCard(
                                        onSave = { hostname ->
                                            if (setPrivateDnsHostname(this@MainActivity, hostname)) {
                                                privateDnsHostname = currentPrivateDnsHostname(this@MainActivity)
                                                privateDnsMode = currentPrivateDnsMode(this@MainActivity)
                                            }
                                        }
                                    )
                                } else {
                                    TileCard(
                                        title = "Private DNS",
                                        iconRes = when (privateDnsMode) {
                                            PrivateDnsMode.HOSTNAME -> R.drawable.ic_dns_on
                                            PrivateDnsMode.AUTO -> R.drawable.ic_dns_auto
                                            else -> R.drawable.ic_dns_off
                                        },
                                        status = when (privateDnsMode) {
                                            PrivateDnsMode.HOSTNAME -> privateDnsHostname.toString()
                                            PrivateDnsMode.AUTO -> "Automatic"
                                            PrivateDnsMode.OFF -> "Off"
                                        },
                                        description = "Switch between Automatic and " +
                                            "\"$privateDnsHostname\".",
                                        addIcon = R.drawable.ic_add,
                                        addDescription = "Add to panel",
                                        added = tileInPanel(components[4]),
                                        onAdd = if (canPromptTileAdd(components[4])) {
                                            {
                                                addTileToPanel(
                                                    components[4],
                                                    "Private DNS",
                                                    R.drawable.ic_dns_auto
                                                )
                                            }
                                        } else null
                                    )
                                }
                            }

                            item {
                                FeatureCard(
                                    title = "Custom Quick Settings Tiles",
                                    status = when {
                                        !shizukuAvailable -> "Shizuku not running"
                                        !shizukuGranted -> "Setup needed"
                                        else -> "$customTileCount/10 tiles configured"
                                    },
                                    statusTone = if (shizukuGranted) StatusTone.NEUTRAL else StatusTone.WARNING,
                                    iconRes = R.drawable.ic_terminal,
                                    description = "Create up to 10 of your own tiles, each running a " +
                                        "shell command with its own icon and label.",
                                    accented = true,
                                    actionLabel = when {
                                        !shizukuAvailable -> null
                                        !shizukuGranted -> "Grant access"
                                        else -> "Manage"
                                    },
                                    onAction = when {
                                        !shizukuAvailable -> null
                                        !shizukuGranted -> { { ShizukuUtils.requestPermission() } }
                                        else -> {
                                            {
                                                startActivity(Intent(this@MainActivity, QsTilesActivity::class.java))
                                            }
                                        }
                                    }
                                )
                            }

                            item { SectionHeader("Home Screen Shortcuts") }

                            item {
                                TileCard(
                                    title = "Lock Screen",
                                    iconRes = R.drawable.ic_locked_qs,
                                    status = if (shortcutPinned) "On home screen" else "Not added",
                                    statusTone = if (shortcutPinned) StatusTone.SUCCESS else StatusTone.NEUTRAL,
                                    description = "A home-screen icon that locks the screen instantly.",
                                    permission = addAccessibilityPermission,
                                    addIcon = R.drawable.ic_add,
                                    addDescription = "Add to home screen",
                                    added = shortcutPinned,
                                    onAdd = if (LockScreenShortcut.canPrompt(this@MainActivity)) {
                                        {
                                            LockScreenShortcut.promptAdd(this@MainActivity)
                                            // Launcher prompt fires async; assume success so the
                                            // icon doesn't invite a double-add. Re-read on resume.
                                            shortcutPinned = true
                                        }
                                    } else null
                                )
                            }

                            item { SectionHeader("Additional Tweaks") }

                            item {
                                FeatureCard(
                                    title = "Block Quick Settings on lock screen",
                                    status = null,
                                    statusTone = StatusTone.NEUTRAL,
                                    iconRes = R.drawable.ic_locked_qs,
                                    description = "Hide Quick Settings while the screen is locked.",
                                    actionLabel = when {
                                        !shizukuAvailable -> "Open Shizuku"
                                        !shizukuGranted -> "Grant access"
                                        else -> if (lockedQsEnabled) "Turn off" else "Turn on"
                                    },
                                    onAction = if (shizukuGranted) {
                                        {
                                            setLockedQsEnabled(this@MainActivity, !lockedQsEnabled)
                                            lockedQsEnabled = !lockedQsEnabled
                                        }
                                    } else {
                                        {
                                            if (shizukuAvailable) {
                                                ShizukuUtils.requestPermission()
                                            } else {
                                                openShizuku(this@MainActivity)
                                            }
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

    private fun currentRingerModeLabel(): String {
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        return when (audioManager.ringerMode) {
            AudioManager.RINGER_MODE_VIBRATE -> "Vibrate"
            AudioManager.RINGER_MODE_SILENT -> "Silent"
            else -> "Normal"
        }
    }
}

/** Collects the hostname when none is saved yet, so the Private DNS tile has something to toggle into. */
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
                "No hostname saved yet. Enter one (e.g. dns.google) so the tile can switch between " +
                    "Automatic and your hostname.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
            )
            OutlinedTextField(
                value = hostname,
                onValueChange = { hostname = it },
                label = { Text("Hostname") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Row(modifier = Modifier.padding(top = 8.dp)) {
                OutlinedButton(
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
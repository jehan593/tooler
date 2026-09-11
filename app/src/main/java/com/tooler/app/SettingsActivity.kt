package com.tooler.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.tooler.app.ui.PermissionCard
import com.tooler.app.ui.TilePermissionAction
import com.tooler.app.ui.theme.ToolerTheme
import com.tooler.app.util.ShizukuUtils
import com.tooler.app.util.copyWriteSecureSettingsGrantCommand
import com.tooler.app.util.grantWriteSecureSettings
import com.tooler.app.util.hasNotificationPolicyAccess
import com.tooler.app.util.hasWriteSecureSettings
import com.tooler.app.util.isAccessibilityServiceEnabled
import com.tooler.app.util.openShizuku
import com.tooler.app.tiles.ScreenshotAccessibilityService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

/**
 * Every permission that can't be granted by a normal runtime dialog, with its current state and
 * the action that grants it. The one place to deal with all of them at once.
 */
class SettingsActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ToolerTheme {
                var accessibilityEnabled by remember {
                    mutableStateOf(isAccessibilityServiceEnabled(this, ScreenshotAccessibilityService::class.java))
                }
                var policyAccessGranted by remember { mutableStateOf(hasNotificationPolicyAccess(this)) }
                var writeSecureSettingsGranted by remember { mutableStateOf(hasWriteSecureSettings(this)) }
                var shizukuAvailable by remember { mutableStateOf(ShizukuUtils.isAvailable()) }
                var shizukuGranted by remember { mutableStateOf(ShizukuUtils.isGranted()) }
                var batteryUnrestricted by remember { mutableStateOf(isIgnoringBatteryOptimizations()) }

                val scope = rememberCoroutineScope()

                val requestWriteSecureSettingsGrant: () -> Unit = {
                    scope.launch {
                        val granted = withContext(Dispatchers.IO) {
                            grantWriteSecureSettings(this@SettingsActivity)
                        }
                        writeSecureSettingsGranted = granted
                        if (!granted) {
                            Toast.makeText(
                                this@SettingsActivity,
                                "Grant failed — check that Shizuku has shell permission, or use:\n" +
                                    "adb shell pm grant $packageName android.permission.WRITE_SECURE_SETTINGS",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }

                DisposableEffect(Unit) {
                    val listener = Shizuku.OnRequestPermissionResultListener { requestCode, _ ->
                        if (requestCode == ShizukuUtils.REQUEST_CODE) {
                            shizukuGranted = ShizukuUtils.isGranted()
                            if (shizukuGranted && !hasWriteSecureSettings(this@SettingsActivity)) {
                                requestWriteSecureSettingsGrant()
                            }
                        }
                    }
                    Shizuku.addRequestPermissionResultListener(listener)

                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            accessibilityEnabled =
                                isAccessibilityServiceEnabled(this@SettingsActivity, ScreenshotAccessibilityService::class.java)
                            policyAccessGranted = hasNotificationPolicyAccess(this@SettingsActivity)
                            writeSecureSettingsGranted = hasWriteSecureSettings(this@SettingsActivity)
                            shizukuAvailable = ShizukuUtils.isAvailable()
                            shizukuGranted = ShizukuUtils.isGranted()
                            batteryUnrestricted = isIgnoringBatteryOptimizations()
                        }
                    }
                    lifecycle.addObserver(observer)

                    onDispose {
                        Shizuku.removeRequestPermissionResultListener(listener)
                        lifecycle.removeObserver(observer)
                    }
                }

                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainerLowest) {
                    Scaffold(
                        topBar = {
                            TopAppBar(
                                title = { Text("Settings") },
                                navigationIcon = {
                                    IconButton(onClick = { finish() }) {
                                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = null)
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
                            item {
                                PermissionCard(
                                    title = "Accessibility service",
                                    iconRes = R.drawable.ic_screenshot,
                                    granted = accessibilityEnabled,
                                    description = "Lets the Screenshot tile and Lock Screen " +
                                        "shortcut work. Tooler never reads screen content.",
                                    actionLabel = if (accessibilityEnabled) null else "Open settings",
                                    onAction = if (accessibilityEnabled) null else {
                                        { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                                    }
                                )
                            }
                            item {
                                PermissionCard(
                                    title = "Do Not Disturb access",
                                    iconRes = R.drawable.ic_volume_normal,
                                    granted = policyAccessGranted,
                                    description = "Lets the Volume Mode tile switch to Silent. " +
                                        "Notifications keep showing.",
                                    actionLabel = if (policyAccessGranted) null else "Open settings",
                                    onAction = if (policyAccessGranted) null else {
                                        { startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) }
                                    }
                                )
                            }
                            item {
                                PermissionCard(
                                    title = "WRITE_SECURE_SETTINGS",
                                    iconRes = R.drawable.ic_custom_settings,
                                    granted = writeSecureSettingsGranted,
                                    description = "Lets Battery Charge Optimization and Private DNS change " +
                                        "system settings. No Settings screen exists for it — grant it " +
                                        "with Shizuku here, or copy an adb command.",
                                    actions = if (writeSecureSettingsGranted) emptyList() else listOf(
                                        TilePermissionAction(
                                            label = "Grant with Shizuku",
                                            primary = true,
                                            onClick = {
                                                when {
                                                    !shizukuAvailable -> openShizuku(this@SettingsActivity)
                                                    !shizukuGranted -> ShizukuUtils.requestPermission()
                                                    else -> requestWriteSecureSettingsGrant()
                                                }
                                            }
                                        ),
                                        TilePermissionAction(
                                            label = "Copy adb command",
                                            onClick = { copyWriteSecureSettingsGrantCommand(this@SettingsActivity) }
                                        )
                                    )
                                )
                            }
                            item {
                                PermissionCard(
                                    title = "Shizuku shell access",
                                    iconRes = R.drawable.ic_terminal,
                                    granted = shizukuGranted,
                                    description = "Runs shell commands for Lock Quick Settings and the " +
                                        "custom tiles. Needs the Shizuku app running.",
                                    actionLabel = when {
                                        shizukuGranted -> null
                                        !shizukuAvailable -> "Open Shizuku"
                                        else -> "Grant access"
                                    },
                                    onAction = if (shizukuGranted) null else {
                                        {
                                            if (shizukuAvailable) {
                                                ShizukuUtils.requestPermission()
                                            } else {
                                                openShizuku(this@SettingsActivity)
                                            }
                                        }
                                    }
                                )
                            }
                            item {
                                PermissionCard(
                                    title = "Battery optimization exemption",
                                    iconRes = R.drawable.ic_custom_battery,
                                    granted = batteryUnrestricted,
                                    description = "Optional. Stops Android from closing the app when idle, " +
                                        "so tiles respond faster.",
                                    actionLabel = if (batteryUnrestricted) null else "Exclude",
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

    private fun isIgnoringBatteryOptimizations(): Boolean {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(packageName)
    }
}
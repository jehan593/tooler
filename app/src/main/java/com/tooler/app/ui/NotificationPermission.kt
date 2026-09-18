package com.tooler.app.ui

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/** Shared permission action for the tile card and the complete permissions screen. */
@Composable
fun ComponentActivity.rememberNotificationPermission(): TilePermission {
    fun enabled(): Boolean {
        val manager = getSystemService(NotificationManager::class.java)
        return NotificationManagerCompat.from(this).areNotificationsEnabled() &&
            manager.getNotificationChannel("keep_screen_on")?.importance != NotificationManager.IMPORTANCE_NONE
    }
    fun openSettings() {
        val blockedChannel = getSystemService(NotificationManager::class.java)
            .getNotificationChannel("keep_screen_on")?.importance == NotificationManager.IMPORTANCE_NONE
        startActivity(Intent(if (blockedChannel) Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS
            else Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            .apply { if (blockedChannel) putExtra(Settings.EXTRA_CHANNEL_ID, "keep_screen_on") })
    }
    var granted by remember { mutableStateOf(enabled()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        granted = enabled()
        // A permanently denied runtime request cannot display another dialog.
        if (!allowed && Build.VERSION.SDK_INT >= 33 &&
            !shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) openSettings()
    }
    DisposableEffect(this) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) granted = enabled()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    return TilePermission(
        label = "Notifications",
        description = "Show the Keep Screen On notification with its Turn off button.",
        granted = granted,
        actions = listOf(TilePermissionAction("Allow notifications", primary = true, onClick = {
            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this,
                    Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else openSettings()
        })),
    )
}

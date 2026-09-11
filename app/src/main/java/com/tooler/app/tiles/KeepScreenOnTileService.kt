package com.tooler.app.tiles

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import androidx.core.content.ContextCompat
import com.tooler.app.util.setSubtitleCompat

class KeepScreenOnTileService : BaseTileService() {

    override fun refresh() {
        applyState(KeepAwakeService.isRunning)
    }

    override fun onClick() {
        super.onClick()
        // Paint the *intended* next state rather than re-reading isRunning right after starting:
        // startForegroundService is async, so the old code briefly flashed the tile back to its
        // prior state. The service resyncs the tile to ground truth right after anyway.
        val turningOn = !KeepAwakeService.isRunning
        if (turningOn) {
            ContextCompat.startForegroundService(this, Intent(this, KeepAwakeService::class.java))
            // On API 33+ a denied notification permission silently hides the foreground
            // notification (the wake lock still works) — send the user to grant it.
            if (needsNotificationPermission()) {
                startActivityAndCollapseCompat(notificationSettingsIntent())
            }
        } else {
            stopService(Intent(this, KeepAwakeService::class.java))
        }
        applyState(turningOn)
    }

    private fun needsNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED

    private fun notificationSettingsIntent(): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun startActivityAndCollapseCompat(intent: Intent) {
        if (Build.VERSION.SDK_INT >= 34) {
            val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun applyState(on: Boolean) {
        qsTile?.apply {
            state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            setSubtitleCompat(if (on) "On" else "Off")
            updateTile()
        }
    }
}

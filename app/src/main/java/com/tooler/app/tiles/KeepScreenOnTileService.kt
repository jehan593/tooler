package com.tooler.app.tiles

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat
import com.tooler.app.util.setSubtitleCompat

class KeepScreenOnTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        applyState(KeepAwakeService.isRunning)
    }

    override fun onClick() {
        super.onClick()
        // Toggling off the *intended* next state rather than re-reading KeepAwakeService.isRunning
        // right after starting it: startForegroundService is async, so isRunning can still read
        // stale for a beat after this call — reading it here painted the tile back to "Off" for a
        // moment before the service's own requestTileRefresh() caught up, which read as the tile
        // being slow/unresponsive. Setting the target state directly makes the tap feel instant;
        // onStartListening()/the service's own refresh still resync it to the real state right after.
        val turningOn = !KeepAwakeService.isRunning
        if (turningOn) {
            ContextCompat.startForegroundService(this, Intent(this, KeepAwakeService::class.java))
            // The whole point of this feature is the visible eye icon + "Turn off" notification, so
            // don't let it silently never appear: on API 33+ a denied notification permission means
            // the foreground notification quietly doesn't post at all (the wake lock still works —
            // the permission only gates notification visibility). Same "tap to grant, don't
            // silently fail" pattern as the other setup-needed tiles.
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

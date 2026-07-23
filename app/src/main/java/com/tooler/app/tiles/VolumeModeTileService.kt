package com.tooler.app.tiles

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.tooler.app.R
import com.tooler.app.util.hasNotificationPolicyAccess

class VolumeModeTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    override fun onClick() {
        super.onClick()
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val next = when (audioManager.ringerMode) {
            AudioManager.RINGER_MODE_NORMAL -> AudioManager.RINGER_MODE_VIBRATE
            AudioManager.RINGER_MODE_VIBRATE -> AudioManager.RINGER_MODE_SILENT
            else -> AudioManager.RINGER_MODE_NORMAL
        }
        // Only the transition into silent is gated behind Do Not Disturb policy access on modern
        // Android — Normal and Vibrate need nothing beyond what every app already has.
        if (next == AudioManager.RINGER_MODE_SILENT && !hasNotificationPolicyAccess(this)) {
            openNotificationPolicySettings()
        } else {
            audioManager.ringerMode = next
        }
        refresh()
    }

    private fun openNotificationPolicySettings() {
        val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
        if (Build.VERSION.SDK_INT >= 34) {
            val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun refresh() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        qsTile?.apply {
            val (iconRes, label) = when (audioManager.ringerMode) {
                AudioManager.RINGER_MODE_VIBRATE -> R.drawable.ic_volume_vibrate to "Vibrate"
                AudioManager.RINGER_MODE_SILENT -> R.drawable.ic_volume_silent to "Silent"
                else -> R.drawable.ic_volume_normal to "Normal"
            }
            icon = Icon.createWithResource(this@VolumeModeTileService, iconRes)
            subtitle = label
            // Unlike Screenshot, this tile always reflects a real current state — Normal isn't
            // "off" any more than Vibrate or Silent is, they're three positions of the same
            // switch — so it stays STATE_ACTIVE (colored) in all three, not just two of them.
            state = Tile.STATE_ACTIVE
            updateTile()
        }
    }
}

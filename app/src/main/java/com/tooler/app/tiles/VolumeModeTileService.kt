package com.tooler.app.tiles

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import com.tooler.app.R
import com.tooler.app.util.hasNotificationPolicyAccess
import com.tooler.app.util.setSubtitleCompat
import com.tooler.app.util.StatusNotifier

class VolumeModeTileService : BaseTileService() {

    override fun onClick() {
        super.onClick()
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val next = when (audioManager.ringerMode) {
            AudioManager.RINGER_MODE_NORMAL -> AudioManager.RINGER_MODE_VIBRATE
            AudioManager.RINGER_MODE_VIBRATE -> AudioManager.RINGER_MODE_SILENT
            else -> AudioManager.RINGER_MODE_NORMAL
        }
        // Only Silent needs Do Not Disturb access; Normal and Vibrate need nothing extra.
        if (next == AudioManager.RINGER_MODE_SILENT && !hasNotificationPolicyAccess(this)) {
            openNotificationPolicySettings()
        } else {
            audioManager.ringerMode = next
            StatusNotifier.notifyChanged()
        }
        refresh()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
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

    override fun refresh() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        qsTile?.apply {
            val (iconRes, label) = when (audioManager.ringerMode) {
                AudioManager.RINGER_MODE_VIBRATE -> R.drawable.ic_volume_vibrate to "Vibrate"
                AudioManager.RINGER_MODE_SILENT -> R.drawable.ic_volume_silent to "Silent"
                else -> R.drawable.ic_volume_normal to "Normal"
            }
            icon = Icon.createWithResource(this@VolumeModeTileService, iconRes)
            setSubtitleCompat(label)
            // Three positions of one switch — always STATE_ACTIVE, matching that. Not "on" vs "off".
            state = Tile.STATE_ACTIVE
            updateTile()
        }
    }
}

package com.tooler.app.tiles

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import com.tooler.app.MainActivity
import com.tooler.app.R
import com.tooler.app.util.hasWriteSecureSettings
import com.tooler.app.util.setSubtitleCompat
import com.tooler.app.util.StatusNotifier

/**
 * Toggles Private DNS Automatic <-> the hostname saved on the device (Off moves to Auto first —
 * see PrivateDns.kt). Unlike Battery Charge Optimization it can read the live mode back. Same
 * WRITE_SECURE_SETTINGS gate as the Battery tile; with no hostname saved to toggle into, tapping
 * opens the app where one can be typed.
 */
class PrivateDnsTileService : BaseTileService() {

    override fun onClick() {
        super.onClick()
        if (hasWriteSecureSettings(this)) {
            if (togglePrivateDnsMode(this)) {
                StatusNotifier.notifyChanged()
            } else {
                openAppForSetup()
            }
        } else {
            openAppForSetup()
        }
        refresh()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openAppForSetup() {
        val intent = Intent(this, MainActivity::class.java)
        if (Build.VERSION.SDK_INT >= 34) {
            val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    override fun refresh() {
        qsTile?.apply {
            if (!hasWriteSecureSettings(this@PrivateDnsTileService)) {
                state = Tile.STATE_INACTIVE
                icon = Icon.createWithResource(this@PrivateDnsTileService, R.drawable.ic_dns_off)
                setSubtitleCompat("Setup needed")
                updateTile()
                return
            }
            val mode = currentPrivateDnsMode(this@PrivateDnsTileService)
            val hostname = currentPrivateDnsHostname(this@PrivateDnsTileService)
            when (mode) {
                PrivateDnsMode.HOSTNAME -> {
                    icon = Icon.createWithResource(this@PrivateDnsTileService, R.drawable.ic_dns_on)
                    setSubtitleCompat(hostname ?: "Custom")
                    state = Tile.STATE_ACTIVE
                }
                PrivateDnsMode.AUTO -> {
                    icon = Icon.createWithResource(this@PrivateDnsTileService, R.drawable.ic_dns_auto)
                    setSubtitleCompat(if (hostname == null) "Tap to set hostname" else "Automatic")
                    state = Tile.STATE_ACTIVE
                }
                PrivateDnsMode.OFF -> {
                    icon = Icon.createWithResource(this@PrivateDnsTileService, R.drawable.ic_dns_off)
                    setSubtitleCompat("Off")
                    state = Tile.STATE_INACTIVE
                }
            }
            updateTile()
        }
    }
}

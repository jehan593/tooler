package com.tooler.app.tiles

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.tooler.app.MainActivity
import com.tooler.app.R
import com.tooler.app.util.hasWriteSecureSettings
import com.tooler.app.util.setSubtitleCompat

/**
 * Toggles Private DNS Automatic <-> the hostname already saved on the device (`Off` moves to
 * `Auto` first, same as `Hostname` does — see `togglePrivateDnsMode()`'s doc) — see PrivateDns.kt
 * for what that reads/writes and why, unlike Battery Charge Optimization, it can read the live
 * mode back with no local prefs of its own. Same `WRITE_SECURE_SETTINGS` gate and "Setup needed"
 * fallback as [BatteryChargeTileService]. If there's no hostname saved yet to toggle into (the
 * "user not set" case — only reachable from `Auto`, since `Off`/`Hostname` never need one), tapping
 * opens the app instead of silently doing nothing — MainActivity's Private DNS card is where a
 * hostname can actually be typed in.
 */
class PrivateDnsTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    override fun onClick() {
        super.onClick()
        if (hasWriteSecureSettings(this)) {
            if (!togglePrivateDnsMode(this)) {
                openAppForSetup()
            }
        } else {
            openAppForSetup()
        }
        refresh()
    }

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

    private fun refresh() {
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

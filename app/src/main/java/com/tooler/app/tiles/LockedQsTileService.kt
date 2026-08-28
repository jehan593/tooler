package com.tooler.app.tiles

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.tooler.app.MainActivity
import com.tooler.app.R
import com.tooler.app.util.ShizukuUtils
import com.tooler.app.util.setSubtitleCompat

/**
 * Toggles Lock Quick Settings — the "keep the Quick Settings panel off the lock screen" tile. Unlike
 * every other tile in this app, the permission gate is Shizuku shell access rather than something a
 * Settings screen (or an accessibility/DND screen) can grant: the OS knob this tile turns
 * (`cmd statusbar send-disable-flag quick-settings`, see util/StatusBarFlags.kt) only obeys callers
 * holding the platform `STATUS_BAR` permission, which no normal app can hold and `pm grant` can't
 * hand out — Shizuku is the only non-root route. So:
 *
 * - Shell access not granted yet → tapping opens MainActivity (which hosts the install/grant flow)
 *   instead of silently doing nothing, same "tap to set up, don't silently fail" pattern as the
 *   Battery Charge tile's `WRITE_SECURE_SETTINGS` case.
 * - Granted → flip the toggle (see [setLockedQsEnabled] for why "on" doesn't do anything right now
 *   and "off" restores immediately).
 *
 * Tile state deliberately mirrors only the *user's toggle*, never the live panel state: a "the panel
 * is actually hidden right now" bit can't be read back (see LockedQs.kt's doc on why), and showing
 * "Off" while the screen is merely unlocked would read as a bug even though it's correct — the flag
 * is only meant to exist between screen-off and the next unlock.
 */
class LockedQsTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    override fun onClick() {
        super.onClick()
        if (ShizukuUtils.isGranted()) {
            setLockedQsEnabled(this, !isLockedQsEnabled(this))
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
            icon = Icon.createWithResource(this@LockedQsTileService, R.drawable.ic_locked_qs)
            if (!ShizukuUtils.isGranted()) {
                state = Tile.STATE_INACTIVE
                setSubtitleCompat(
                    if (isLockedQsEnabled(this@LockedQsTileService)) "On" else "Setup needed"
                )
                updateTile()
                return
            }
            val enabled = isLockedQsEnabled(this@LockedQsTileService)
            state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            setSubtitleCompat(if (enabled) "On" else "Off")
            updateTile()
        }
    }
}
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
 * Toggles Adaptive Charging <-> Limit to 80% — see ChargeOptimization.kt for what that writes and
 * why the tile tracks the last mode it wrote rather than reading it back (a normal app can write
 * but not read these keys). Off is not part of the cycle; it only shows as the pre-first-tap
 * default or when WRITE_SECURE_SETTINGS isn't granted.
 */
class BatteryChargeTileService : BaseTileService() {

    override fun onClick() {
        super.onClick()
        if (hasWriteSecureSettings(this)) {
            advanceChargingMode(this)
            StatusNotifier.notifyChanged()
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
            if (!hasWriteSecureSettings(this@BatteryChargeTileService)) {
                state = Tile.STATE_INACTIVE
                icon = Icon.createWithResource(this@BatteryChargeTileService, R.drawable.ic_battery_off)
                setSubtitleCompat("Setup needed")
                updateTile()
                return
            }
            val mode = lastKnownChargingMode(this@BatteryChargeTileService)
            val (iconRes, label) = when (mode) {
                ChargingMode.OFF -> R.drawable.ic_battery_off to "Off"
                ChargingMode.ADAPTIVE -> R.drawable.ic_battery_adaptive to "Adaptive Charging"
                ChargingMode.LIMIT_80 -> R.drawable.ic_battery_limit_80 to "Limit to 80%"
            }
            icon = Icon.createWithResource(this@BatteryChargeTileService, iconRes)
            setSubtitleCompat(label)
            state = if (mode == ChargingMode.OFF) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
            updateTile()
        }
    }
}

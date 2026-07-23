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

/**
 * Cycles Off -> Adaptive Charging -> Limit to 80% -> Off — see ChargeOptimization.kt for what
 * those actually write, and for why this tracks the last mode *it wrote* rather than reading the
 * system's live value back (reading is blocked for a normal app; only writing works). Unlike
 * Volume Mode, Off here reads as "optimization disabled" rather than a third equally-valid
 * position, so only Adaptive Charging/Limit to 80% paint the tile STATE_ACTIVE; Off and the
 * "Setup needed" (WRITE_SECURE_SETTINGS not granted) case both stay STATE_INACTIVE.
 */
class BatteryChargeTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    override fun onClick() {
        super.onClick()
        if (hasWriteSecureSettings(this)) {
            advanceChargingMode(this)
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
            if (!hasWriteSecureSettings(this@BatteryChargeTileService)) {
                state = Tile.STATE_INACTIVE
                icon = Icon.createWithResource(this@BatteryChargeTileService, R.drawable.ic_battery_off)
                subtitle = "Setup needed"
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
            subtitle = label
            state = if (mode == ChargingMode.OFF) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
            updateTile()
        }
    }
}

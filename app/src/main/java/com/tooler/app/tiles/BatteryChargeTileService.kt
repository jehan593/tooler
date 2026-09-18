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

/**
 * Toggles Adaptive Charging <-> Limit to 80% — see ChargeOptimization.kt for what that writes and
 * why the tile tracks the last mode it wrote rather than reading it back (a normal app can write
 * but not read these keys). Off is not part of the cycle; it only shows as the pre-first-tap
 * default or when WRITE_SECURE_SETTINGS isn't granted.
 */
class BatteryChargeTileService : BaseTileService() {
    override val observedSettings = listOf(
        android.provider.Settings.Secure.getUriFor("adaptive_charging_enabled"),
        android.provider.Settings.Secure.getUriFor("charge_optimization_mode"),
    )
    private val reader = java.util.concurrent.Executors.newSingleThreadExecutor()
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var destroyed = false

    override fun onDestroy() {
        destroyed = true
        reader.shutdown()
        super.onDestroy()
    }

    override fun onClick() {
        super.onClick()
        if (hasWriteSecureSettings(this)) {
            changeSetting({ advanceChargingMode(applicationContext) }) { success ->
                if (!success) android.widget.Toast.makeText(this, "Could not change charging mode", android.widget.Toast.LENGTH_SHORT).show()
            }
            return
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
        reader.execute {
            syncChargingMode(applicationContext)
            mainHandler.post { if (!destroyed) renderState() }
        }
    }

    private fun renderState() {
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

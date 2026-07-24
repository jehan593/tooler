package com.tooler.app.tiles

import android.content.Intent
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
        } else {
            stopService(Intent(this, KeepAwakeService::class.java))
        }
        applyState(turningOn)
    }

    private fun applyState(on: Boolean) {
        qsTile?.apply {
            state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            setSubtitleCompat(if (on) "On" else "Off")
            updateTile()
        }
    }
}

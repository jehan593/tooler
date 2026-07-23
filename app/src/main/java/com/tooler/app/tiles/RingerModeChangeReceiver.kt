package com.tooler.app.tiles

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.service.quicksettings.TileService

/**
 * Manifest-registered (not a runtime registration, since nothing needs to hold the process open
 * for this) so the volume tile redraws the moment the ringer mode changes via hardware buttons or
 * another app — without this it would only catch up the next time the Quick Settings panel opens.
 */
class RingerModeChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        TileService.requestListeningState(context, ComponentName(context, VolumeModeTileService::class.java))
    }
}

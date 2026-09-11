package com.tooler.app.tiles

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.service.quicksettings.TileService
import com.tooler.app.util.StatusNotifier

/** Repaints the volume tile when the ringer mode changes via hardware buttons or another app. */
class RingerModeChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        TileService.requestListeningState(context, ComponentName(context, VolumeModeTileService::class.java))
        StatusNotifier.notifyChanged()
    }
}

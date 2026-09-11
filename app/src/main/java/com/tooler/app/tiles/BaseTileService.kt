package com.tooler.app.tiles

import android.content.ComponentName
import android.service.quicksettings.TileService
import com.tooler.app.util.StatusNotifier
import com.tooler.app.util.TilePanelPrefs

/**
 * Shared base for every QS tile: repaints from live state on `onStartListening()` and tracks panel
 * membership in [TilePanelPrefs] via the tile lifecycle callbacks. Emits a [StatusNotifier] tick
 * on add/remove so open screens update their panel state instantly.
 */
abstract class BaseTileService : TileService() {

    /** Repaints this tile from live state. Must never persist anything. */
    abstract fun refresh()

    final override fun onStartListening() {
        super.onStartListening()
        TilePanelPrefs.setInPanel(this, ComponentName(this, javaClass), true)
        refresh()
    }

    final override fun onTileAdded() {
        super.onTileAdded()
        TilePanelPrefs.setInPanel(this, ComponentName(this, javaClass), true)
        StatusNotifier.notifyChanged()
    }

    final override fun onTileRemoved() {
        super.onTileRemoved()
        TilePanelPrefs.setInPanel(this, ComponentName(this, javaClass), false)
        StatusNotifier.notifyChanged()
    }
}
package com.tooler.app.util

import android.content.ComponentName
import android.content.Context

/**
 * Tracks which tiles are on the Quick Settings panel. There's no live value a normal app can read
 * (the panel roster is read-restricted on modern Android), so this relies on the TileService
 * lifecycle: `onTileAdded()`/`onTileRemoved()` fire even from a cold process, and
 * `onStartListening()` only runs for tiles on the panel, which self-heals drift. Read by the UI to
 * decide whether to show an "Add to Quick Settings" button.
 */
object TilePanelPrefs {
    private const val PREFS_NAME = "tile_panel"

    fun isInPanel(context: Context, component: ComponentName): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(component.flattenToString(), false)

    fun setInPanel(context: Context, component: ComponentName, added: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(component.flattenToString(), false) == added) return
        prefs.edit().putBoolean(component.flattenToString(), added).apply()
    }

    /** Snapshot of several components at once, keyed by flattened ComponentName. */
    fun statusMap(context: Context, components: List<ComponentName>): Map<String, Boolean> =
        components.associate { it.flattenToString() to isInPanel(context, it) }
}
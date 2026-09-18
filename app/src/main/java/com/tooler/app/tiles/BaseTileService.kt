package com.tooler.app.tiles

import android.content.ComponentName
import android.service.quicksettings.TileService
import com.tooler.app.util.StatusNotifier
import com.tooler.app.util.TilePanelPrefs
import com.tooler.app.util.setSubtitleCompat

/**
 * Shared base for every QS tile: repaints from live state on `onStartListening()` and tracks panel
 * membership in [TilePanelPrefs] via the tile lifecycle callbacks. Emits a [StatusNotifier] tick
 * on add/remove so open screens update their panel state instantly.
 */
abstract class BaseTileService : TileService() {

    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var settingsWorker: java.util.concurrent.ExecutorService? = null
    private var settingChangePending = false
    private var destroyed = false
    protected open val observedSettings: List<android.net.Uri> = emptyList()
    private var settingsObserved = false
    private val settingsObserver = object : android.database.ContentObserver(mainHandler) {
        override fun onChange(selfChange: Boolean) { if (!settingChangePending) refresh() }
    }

    private fun stopSettingsObserver() {
        if (settingsObserved) contentResolver.unregisterContentObserver(settingsObserver)
        settingsObserved = false
    }

    /** Settings-provider IPC must not stall the shared main thread or other tile callbacks. */
    protected fun changeSetting(action: () -> Boolean, completed: (Boolean) -> Unit) {
        if (settingChangePending) return
        settingChangePending = true
        qsTile?.apply {
            setSubtitleCompat("Updating…")
            updateTile()
        }
        val worker = settingsWorker ?: java.util.concurrent.Executors.newSingleThreadExecutor()
            .also { settingsWorker = it }
        worker.execute {
            val success = runCatching(action).getOrDefault(false)
            mainHandler.post {
                settingChangePending = false
                StatusNotifier.notifyChanged()
                if (!destroyed) {
                    completed(success)
                    refresh()
                }
            }
        }
    }

    /** Repaints this tile from live state. */
    abstract fun refresh()

    final override fun onStartListening() {
        super.onStartListening()
        TilePanelPrefs.setInPanel(this, ComponentName(this, javaClass), true)
        if (!settingsObserved && observedSettings.isNotEmpty()) {
            observedSettings.forEach { contentResolver.registerContentObserver(it, false, settingsObserver) }
            settingsObserved = true
        }
        startObserving()
        refresh()
    }

    protected open fun startObserving() {}
    protected open fun stopObserving() {}

    override fun onStopListening() {
        stopSettingsObserver()
        stopObserving()
        super.onStopListening()
    }

    override fun onDestroy() {
        destroyed = true
        settingsWorker?.shutdown()
        stopSettingsObserver()
        stopObserving()
        super.onDestroy()
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
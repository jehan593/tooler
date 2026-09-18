package com.tooler.app.tiles

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.database.ContentObserver
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.quicksettings.Tile
import android.widget.Toast
import com.tooler.app.MainActivity
import com.tooler.app.R
import com.tooler.app.util.StatusNotifier
import com.tooler.app.util.hasWriteSecureSettings
import com.tooler.app.util.setSubtitleCompat
import java.util.concurrent.Executors

class GrayscaleTileService : BaseTileService() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private var observing = false
    private var changing = false
    private var destroyed = false
    private val observer = object : ContentObserver(mainHandler) {
        override fun onChange(selfChange: Boolean) {
            if (!changing) refresh()
        }
    }

    override fun startObserving() {
        if (observing) return
        listOf(Grayscale.ENABLED, Grayscale.MODE).forEach {
            contentResolver.registerContentObserver(Settings.Secure.getUriFor(it), false, observer)
        }
        observing = true
    }

    override fun stopObserving() {
        if (!observing) return
        contentResolver.unregisterContentObserver(observer)
        observing = false
    }

    override fun onDestroy() {
        destroyed = true
        worker.shutdown()
        super.onDestroy()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        super.onClick()
        if (changing) return
        if (!hasWriteSecureSettings(this)) {
            val intent = Intent(this, MainActivity::class.java)
            if (Build.VERSION.SDK_INT >= 34) {
                startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
            return
        }
        changing = true
        worker.execute {
            val success = Grayscale.toggle(applicationContext)
            mainHandler.post {
                changing = false
                StatusNotifier.notifyChanged()
                if (!destroyed) {
                    if (!success) Toast.makeText(this, "Could not change grayscale", Toast.LENGTH_SHORT).show()
                    refresh()
                }
            }
        }
    }

    override fun refresh() {
        if (changing) return
        val active = Grayscale.read(this).isGrayscale
        // Run persistence off the main thread, serialized with toggles; reconcile reads live state.
        worker.execute { Grayscale.reconcile(applicationContext) }
        qsTile?.apply {
            icon = Icon.createWithResource(this@GrayscaleTileService, R.drawable.ic_grayscale)
            state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            setSubtitleCompat(if (!hasWriteSecureSettings(this@GrayscaleTileService)) "Setup needed"
                else if (active) "On" else "Off")
            updateTile()
        }
        StatusNotifier.notifyChanged()
    }
}

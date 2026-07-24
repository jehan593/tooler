package com.tooler.app.tiles

import android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE
import android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.tooler.app.util.isAccessibilityServiceEnabled
import com.tooler.app.util.setSubtitleCompat

class ScreenshotTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    override fun onClick() {
        super.onClick()
        val service = ScreenshotAccessibilityService.instance
        if (service != null) {
            // Dismiss the Quick Settings shade first so it isn't itself part of the capture, then
            // wait out its collapse animation before firing the actual screenshot action. Below
            // API 30 (where GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE doesn't exist yet) this call
            // is a harmless no-op and the screenshot just fires immediately, same as before.
            service.performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
            Handler(Looper.getMainLooper()).postDelayed({
                service.performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
            }, SHADE_COLLAPSE_DELAY_MS)
        } else {
            openAccessibilitySettings()
        }
        refresh()
    }

    private fun refresh() {
        // This is a momentary action, not a toggle — it never has an "on" state to represent, so
        // it stays STATE_INACTIVE (the plain, uncolored look) even when fully set up and ready.
        // Using STATE_ACTIVE for "ready" would read as "currently on", which is misleading for a
        // tile with no ongoing state; the subtitle carries the setup-needed distinction instead.
        val enabled = isAccessibilityServiceEnabled(this, ScreenshotAccessibilityService::class.java)
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            setSubtitleCompat(if (enabled) null else "Tap to enable")
            updateTile()
        }
    }

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        if (Build.VERSION.SDK_INT >= 34) {
            val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private companion object {
        // The shade's own collapse animation runs ~250-300ms on stock AOSP, but several OEM
        // System UI skins layer a blur transition on top that outlasts the plain collapse —
        // capturing before that settles is what left a blurry/ghosted remnant of the panel in the
        // screenshot at the old 250ms value. 450ms gives more margin against that without the
        // extra wait being noticeable — if a specific device still shows a remnant, raise this
        // further rather than reintroducing a race.
        const val SHADE_COLLAPSE_DELAY_MS = 450L
    }
}

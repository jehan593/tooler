package com.tooler.app.tiles

import android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE
import android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.quicksettings.Tile
import com.tooler.app.util.isAccessibilityServiceEnabled
import com.tooler.app.util.setSubtitleCompat

class ScreenshotTileService : BaseTileService() {

    override fun onClick() {
        super.onClick()
        val service = ScreenshotAccessibilityService.instance
        if (service != null) {
            // Dismiss the shade first so it isn't part of the capture, and wait out its collapse
            // (no-op below API 30, where the action doesn't exist).
            service.performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
            Handler(Looper.getMainLooper()).postDelayed({
                service.performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
            }, SHADE_COLLAPSE_DELAY_MS)
        } else {
            openAccessibilitySettings()
        }
        refresh()
    }

    override fun refresh() {
        // A momentary action — no "on" state, so it stays STATE_INACTIVE even when ready; the
        // subtitle carries the setup-needed distinction instead.
        val enabled = isAccessibilityServiceEnabled(this, ScreenshotAccessibilityService::class.java)
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            setSubtitleCompat(if (enabled) null else "Tap to enable")
            updateTile()
        }
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
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
        // The shade's collapse runs ~250-300ms, but some OEM skins add a blur transition on top.
        // 450ms gives margin against that; raise it further if a device still shows a remnant.
        const val SHADE_COLLAPSE_DELAY_MS = 450L
    }
}

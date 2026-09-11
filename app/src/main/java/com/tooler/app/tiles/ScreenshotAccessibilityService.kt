package com.tooler.app.tiles

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.service.quicksettings.TileService
import android.view.accessibility.AccessibilityEvent
import com.tooler.app.util.StatusNotifier

/**
 * Does nothing with accessibility events or screen content — it exists purely so the Screenshot
 * tile has a system-trusted caller for `performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)`, the
 * only non-root way to take a screenshot externally. The Lock Screen shortcut reuses it for
 * `GLOBAL_ACTION_LOCK_SCREEN` — a second service for one more action isn't worth a second entry in
 * Settings > Accessibility.
 */
class ScreenshotAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        requestTileRefresh()
        StatusNotifier.notifyChanged()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {}

    override fun onInterrupt() {}

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        requestTileRefresh()
        StatusNotifier.notifyChanged()
        return super.onUnbind(intent)
    }

    // Repaints the tile right after the user flips the accessibility toggle in Settings.
    private fun requestTileRefresh() {
        TileService.requestListeningState(
            applicationContext, ComponentName(applicationContext, ScreenshotTileService::class.java)
        )
    }

    companion object {
        var instance: ScreenshotAccessibilityService? = null
            private set
    }
}

package com.tooler.app.tiles

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.service.quicksettings.TileService
import android.view.accessibility.AccessibilityEvent

/**
 * Deliberately does nothing with accessibility events or window content — it exists purely so
 * [ScreenshotTileService] has a bound, system-trusted caller for performGlobalAction
 * (GLOBAL_ACTION_TAKE_SCREENSHOT), the only non-root way to trigger a screenshot from outside an
 * app with an active window. `res/xml/accessibility_service_config.xml` reflects that: no event
 * types, no window content, no gesture capability.
 *
 * [com.tooler.app.shortcuts.LockScreenShortcutActivity] also calls through [instance] for
 * GLOBAL_ACTION_LOCK_SCREEN — same trusted-caller problem, same solution, so it reuses this
 * service rather than standing up a second one just for one more global action.
 */
class ScreenshotAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        requestTileRefresh()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {}

    override fun onInterrupt() {}

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        requestTileRefresh()
        return super.onUnbind(intent)
    }

    // Nudges the tile to redraw immediately after the user flips the accessibility toggle in
    // Settings, rather than waiting for the QS panel to next be pulled down.
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

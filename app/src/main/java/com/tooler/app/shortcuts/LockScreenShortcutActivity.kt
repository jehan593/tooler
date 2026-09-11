package com.tooler.app.shortcuts

import android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN
import android.app.Activity
import android.content.Intent
import android.content.pm.ShortcutManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.tooler.app.R
import com.tooler.app.tiles.ScreenshotAccessibilityService

/**
 * A launcher shortcut, not an AppWidget (same approach as
 * [BLumia/pineapple-lock-screen](https://github.com/BLumia/pineapple-lock-screen)): a
 * `Theme.NoDisplay` activity carrying an `ACTION_CREATE_SHORTCUT` intent-filter, which launchers
 * surface in the widgets/shortcuts picker. `onCreate()` either returns the [ShortcutInfo] result,
 * or — when the pinned icon is tapped — locks the screen through the shared accessibility service,
 * then always `finish()`es without ever drawing a frame.
 *
 * **Icon note:** `ShortcutInfo` doesn't reliably recognize a reference to a `mipmap-anydpi-v26`
 * `<adaptive-icon>` XML (produced a doubled/badged icon on-device), so the shortcut icon is
 * rasterized to a [Bitmap] and wrapped in `Icon.createWithAdaptiveBitmap()` instead.
 */
class LockScreenShortcutActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.action == Intent.ACTION_CREATE_SHORTCUT) {
            setResult(RESULT_OK, buildShortcutResultIntent())
        } else {
            performLock()
        }
        finish()
    }

    private fun buildShortcutResultIntent(): Intent {
        val shortcutManager = ContextCompat.getSystemService(this, ShortcutManager::class.java)
        if (shortcutManager != null) {
            return shortcutManager.createShortcutResultIntent(LockScreenShortcut.shortcutInfo(this))
        }
        // ShortcutManager is API 25+, never actually null at minSdk 28 — kept so a null result
        // can't silently produce a broken shortcut.
        @Suppress("DEPRECATION")
        return Intent()
            .putExtra(Intent.EXTRA_SHORTCUT_INTENT, Intent(this, LockScreenShortcutActivity::class.java))
            .putExtra(Intent.EXTRA_SHORTCUT_NAME, getString(R.string.lock_screen_shortcut_label))
            .putExtra(
                Intent.EXTRA_SHORTCUT_ICON_RESOURCE,
                Intent.ShortcutIconResource.fromContext(this, R.mipmap.ic_lock_screen_shortcut)
            )
    }

    private fun performLock() {
        val service = ScreenshotAccessibilityService.instance
        if (service != null) {
            service.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
        } else {
            Toast.makeText(this, R.string.lock_screen_shortcut_setup_needed, Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }
}

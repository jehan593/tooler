package com.tooler.app.shortcuts

import android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN
import android.app.Activity
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Icon
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.tooler.app.R
import com.tooler.app.tiles.ScreenshotAccessibilityService

/**
 * A launcher **shortcut**, not an AppWidget — replaces an earlier RemoteViews-based 1x1 widget
 * after user feedback pointed at
 * [BLumia/pineapple-lock-screen](https://github.com/BLumia/pineapple-lock-screen)'s approach
 * instead: a `Theme.NoDisplay` activity with an `ACTION_CREATE_SHORTCUT` intent-filter, which
 * launchers surface in the same "add to home screen" picker as widgets, producing a plain icon —
 * no widget host, no RemoteViews view-hierarchy restrictions.
 *
 * `onCreate()` is entered through two entirely different paths depending on `intent.action`:
 * - `ACTION_CREATE_SHORTCUT` (the launcher's picker, when the user drags this onto the home
 *   screen): builds and returns a [ShortcutInfo] result describing the pinned icon. This branch
 *   never draws anything — same as the other branch, just a different reason to skip UI.
 * - Anything else (the user tapping the pinned icon on their home screen — the pinned icon's own
 *   intent uses `ACTION_VIEW` with this activity as an explicit component, see
 *   [buildShortcutResultIntent]): performs the lock action immediately through
 *   [ScreenshotAccessibilityService.instance] — the same accessibility service the Screenshot tile
 *   already uses — then finishes.
 *
 * Either way this activity never actually becomes visible: `Theme.NoDisplay` in the manifest plus
 * `finish()` at the end of `onCreate()` in both branches.
 *
 * **Icon note:** `ShortcutInfo.Builder.setIcon()` does not reliably recognize a resource reference
 * to a `mipmap-anydpi-v26` `<adaptive-icon>` XML the way `PackageManager` does for full launcher
 * icons — passing `Icon.createWithResource(this, R.mipmap.ic_lock_screen_shortcut)` still produced
 * a visibly doubled/badged icon on-device (the nord0 background rendered separately from a second,
 * differently-scaled copy of the lock glyph). [buildAdaptiveIcon] instead rasterizes the same
 * background+foreground pairing into a plain `Bitmap` at the 108dp adaptive-icon canvas size and
 * wraps it with `Icon.createWithAdaptiveBitmap()`, which explicitly tells Android "this bitmap is
 * already adaptive content, mask it yourself, don't legacy-adapt it further" — the officially
 * documented, launcher-agnostic way to hand adaptive-style content to `ShortcutInfo` specifically.
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
            val shortcutInfo = ShortcutInfo.Builder(this, SHORTCUT_ID)
                .setShortLabel(getString(R.string.lock_screen_shortcut_label))
                .setIcon(buildAdaptiveIcon())
                .setIntent(Intent(Intent.ACTION_VIEW, null, this, LockScreenShortcutActivity::class.java))
                .build()
            return shortcutManager.createShortcutResultIntent(shortcutInfo)
        }
        // ShortcutManager has existed since API 25 — this app's minSdk 28 means it's never
        // actually null. Kept only so a null result can't silently produce a broken shortcut.
        @Suppress("DEPRECATION")
        return Intent()
            .putExtra(Intent.EXTRA_SHORTCUT_INTENT, Intent(this, LockScreenShortcutActivity::class.java))
            .putExtra(Intent.EXTRA_SHORTCUT_NAME, getString(R.string.lock_screen_shortcut_label))
            .putExtra(
                Intent.EXTRA_SHORTCUT_ICON_RESOURCE,
                Intent.ShortcutIconResource.fromContext(this, R.mipmap.ic_lock_screen_shortcut)
            )
    }

    /** See the class doc's "Icon note" for why this rasterizes rather than referencing the mipmap directly. */
    private fun buildAdaptiveIcon(): Icon {
        val sizePx = (108 * resources.displayMetrics.density).toInt()
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(ContextCompat.getColor(this, R.color.ic_launcher_background))
        ContextCompat.getDrawable(this, R.drawable.ic_lock_screen_foreground)?.apply {
            setBounds(0, 0, sizePx, sizePx)
            draw(canvas)
        }
        return Icon.createWithAdaptiveBitmap(bitmap)
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

    private companion object {
        const val SHORTCUT_ID = "lock_screen_shortcut"
    }
}

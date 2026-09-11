package com.tooler.app.shortcuts

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Icon
import androidx.core.content.ContextCompat
import com.tooler.app.R

/**
 * Programmatic side of the Lock Screen launcher shortcut. MainActivity uses it to check pinned
 * state (`pinnedShortcuts` is a live read) and to fire `requestPinShortcut`; the picker flow in
 * [LockScreenShortcutActivity] builds the exact same shortcut via [shortcutInfo]/[buildAdaptiveIcon].
 */
object LockScreenShortcut {

    const val SHORTCUT_ID = "lock_screen_shortcut"

    fun isPinned(context: Context): Boolean {
        val manager = ContextCompat.getSystemService(context, ShortcutManager::class.java) ?: return false
        return manager.pinnedShortcuts.any { it.id == SHORTCUT_ID }
    }

    /** Whether the launcher supports programmatic pinning at all (requestPinShortcut). */
    fun canPrompt(context: Context): Boolean =
        ContextCompat.getSystemService(context, ShortcutManager::class.java)
            ?.isRequestPinShortcutSupported ?: false

    /** Fires the launcher's "pin this shortcut?" prompt. On a non-pinning launcher it's a no-op. */
    fun promptAdd(context: Context) {
        ContextCompat.getSystemService(context, ShortcutManager::class.java)?.let {
            if (it.isRequestPinShortcutSupported) {
                it.requestPinShortcut(shortcutInfo(context), null)
            }
        }
    }

    fun shortcutInfo(context: Context): ShortcutInfo =
        ShortcutInfo.Builder(context, SHORTCUT_ID)
            .setShortLabel(context.getString(R.string.lock_screen_shortcut_label))
            .setIcon(buildAdaptiveIcon(context))
            .setIntent(Intent(Intent.ACTION_VIEW, null, context, LockScreenShortcutActivity::class.java))
            .build()

    /** See LockScreenShortcutActivity's doc for why this rasterizes instead of referencing the mipmap:
     *  `ShortcutInfo` doesn't reliably recognize an `<adaptive-icon>` resource and produced a doubled icon. */
    fun buildAdaptiveIcon(context: Context): Icon {
        val sizePx = (108 * context.resources.displayMetrics.density).toInt()
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(ContextCompat.getColor(context, R.color.ic_launcher_background))
        ContextCompat.getDrawable(context, R.drawable.ic_lock_screen_foreground)?.apply {
            setBounds(0, 0, sizePx, sizePx)
            draw(canvas)
        }
        return Icon.createWithAdaptiveBitmap(bitmap)
    }
}
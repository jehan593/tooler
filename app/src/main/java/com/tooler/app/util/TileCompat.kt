package com.tooler.app.util

import android.os.Build
import android.service.quicksettings.Tile

/**
 * `Tile.subtitle` is API 29+; this app's `minSdk` is 28 (set for the Screenshot tile's
 * `GLOBAL_ACTION_TAKE_SCREENSHOT` accessibility action, not for any QS tile API) — setting it
 * directly on a real API 28 device throws `NoSuchMethodError` instead of just no-opping. No-ops
 * below Q here so the tile still renders correctly via its icon/state, just without the second
 * line, rather than crashing.
 */
fun Tile.setSubtitleCompat(text: CharSequence?) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        subtitle = text
    }
}

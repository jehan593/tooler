package com.tooler.app.util

import android.os.Build
import android.service.quicksettings.Tile

/** `Tile.subtitle` is API 29+, one past this app's `minSdk` 28 — the raw property throws on a
 *  real API 28 device. No-ops below Q so the tile still renders, just without the second line. */
fun Tile.setSubtitleCompat(text: CharSequence?) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        subtitle = text
    }
}

package com.tooler.app.tiles

import android.content.Context
import android.provider.Settings

/** Raw strings preserve unset values as well as disabled, preselected correction modes. */
internal data class ColorCorrectionState(val enabled: String?, val mode: String?) {
    val isGrayscale: Boolean get() = enabled == "1" && mode == "0"
    fun grayscaleOn() = ColorCorrectionState("1", "0")
    fun grayscaleOff(previous: ColorCorrectionState?): ColorCorrectionState =
        previous?.takeUnless { it.isGrayscale } ?: copy(enabled = "0")
}

// All mutations run on the tile worker; synchronous commits keep snapshots ordered with system writes.
@android.annotation.SuppressLint("ApplySharedPref")
internal object Grayscale {
    const val ENABLED = "accessibility_display_daltonizer_enabled"
    const val MODE = "accessibility_display_daltonizer"

    fun read(context: Context) = ColorCorrectionState(
        Settings.Secure.getString(context.contentResolver, ENABLED),
        Settings.Secure.getString(context.contentResolver, MODE),
    )

    private fun prefs(context: Context) = context.getSharedPreferences("grayscale_restore", Context.MODE_PRIVATE)

    // Manual changes take precedence. Never restore a stale snapshot over another correction mode.
    fun reconcile(context: Context) {
        if (!read(context).isGrayscale && prefs(context).contains("saved")) {
            prefs(context).edit().clear().commit()
        }
    }

    fun toggle(context: Context): Boolean {
        val before = read(context)
        val prefs = prefs(context)
        val previous = if (prefs.getBoolean("saved", false)) {
            ColorCorrectionState(prefs.getString("enabled", null), prefs.getString("mode", null))
        } else null
        val target = if (before.isGrayscale) before.grayscaleOff(previous) else before.grayscaleOn()
        if (!before.isGrayscale) {
            // Commit before changing the system so restoration survives process death.
            if (!prefs.edit().clear().putBoolean("saved", true)
                    .putString("enabled", before.enabled).putString("mode", before.mode).commit()) return false
        }
        return try {
            write(context, target)
            if (before.isGrayscale) prefs.edit().clear().commit()
            true
        } catch (_: Exception) {
            // Best effort rollback if permission is revoked or a provider rejects either write.
            runCatching { write(context, before) }
            false
        }
    }

    private fun write(context: Context, state: ColorCorrectionState) {
        val resolver = context.contentResolver
        // Disable first to avoid briefly activating the wrong color correction during restoration.
        check(Settings.Secure.putString(resolver, ENABLED, "0"))
        check(Settings.Secure.putString(resolver, MODE, state.mode))
        check(Settings.Secure.putString(resolver, ENABLED, state.enabled))
    }
}

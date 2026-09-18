package com.tooler.app.tiles

import android.app.Instrumentation
import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings

/** Runs against real SettingsProvider; restores device settings and Tooler's snapshot even on failure. */
class DeviceChecks : TileResponseChecks() {
    private var tileSuite = false
    override fun onCreate(arguments: Bundle?) {
        tileSuite = arguments?.getString("suite") == "tiles"
        super.onCreate(arguments)
    }

    override fun onStart() {
        if (tileSuite) { super.onStart(); return }
        val context = targetContext
        val original = Grayscale.read(context)
        val prefs = context.getSharedPreferences("grayscale_restore", Context.MODE_PRIVATE)
        val saved = prefs.all
        val results = mutableListOf<String>()
        var failure: Throwable? = null
        try {
            for (enabled in listOf("0", "1")) {
                for (mode in listOf("-1", "0", "11", "12", "13")) {
                    val before = ColorCorrectionState(enabled, mode)
                    if (before.isGrayscale) continue
                    setState(before)
                    check(Grayscale.toggle(context)) { "Enable failed: $before" }
                    check(Grayscale.read(context).isGrayscale) { "Not grayscale: $before" }
                    check(prefs.getBoolean("saved", false)) { "Snapshot missing" }
                    check(Grayscale.toggle(context)) { "Restore failed: $before" }
                    check(Grayscale.read(context) == before) { "Restore mismatch: $before" }
                    results += "PASS override/restore enabled=$enabled mode=$mode"
                }
            }
            prefs.edit().clear().commit()
            setState(ColorCorrectionState("1", "0"))
            check(Grayscale.read(context).isGrayscale)
            check(Grayscale.toggle(context))
            check(!Grayscale.read(context).isGrayscale)
            results += "PASS disable manually enabled grayscale"

            setState(ColorCorrectionState("1", "12"))
            check(Grayscale.toggle(context))
            setState(ColorCorrectionState("1", "13"))
            Grayscale.reconcile(context)
            check(!prefs.contains("saved"))
            check(Grayscale.read(context) == ColorCorrectionState("1", "13"))
            check(Grayscale.toggle(context))
            check(Grayscale.toggle(context))
            check(Grayscale.read(context) == ColorCorrectionState("1", "13"))
            results += "PASS manual correction takes precedence and becomes next restore state"
        } catch (t: Throwable) {
            failure = t
        } finally {
            try {
                setState(original)
                val editor = prefs.edit().clear()
                saved.forEach { (key, value) ->
                    when (value) {
                        is Boolean -> editor.putBoolean(key, value)
                        is String -> editor.putString(key, value)
                    }
                }
                check(editor.commit())
                check(Grayscale.read(context) == original)
                results += "PASS original device settings restored"
            } catch (restore: Throwable) {
                failure = restore
            }
        }
        finish(if (failure == null) -1 else 0, Bundle().apply {
            putString("stream", results.joinToString("\n", postfix = "\n") + (failure?.stackTraceToString() ?: "ALL DEVICE CHECKS PASSED\n"))
        })
    }

    private fun setState(state: ColorCorrectionState) {
        val resolver = targetContext.contentResolver
        check(Settings.Secure.putString(resolver, Grayscale.ENABLED, "0"))
        check(Settings.Secure.putString(resolver, Grayscale.MODE, state.mode))
        check(Settings.Secure.putString(resolver, Grayscale.ENABLED, state.enabled))
        SystemClock.sleep(100)
    }
}

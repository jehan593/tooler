package com.tooler.app.customtiles

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.TileService

/**
 * The system-facing half of "creating" a custom tile — the direct port of aShellYou's
 * `TileComponentManager` (there, a Hilt `@Singleton`; here a plain object, since Tooler has no
 * dependency injection). A tile doesn't exist from System UI's perspective until its manifest
 * component is enabled and the user adds it to their panel, and that plumbing is the whole trick
 * this feature is about:
 *
 * - `setComponentEnabled` is the programmatic side of the QS "add tile" editor — the app must
 *   enable (or re-enable) the slot's service component before System UI will offer it at all.
 * - `promptAddTile` fires the system's own "Add <label> tile to Quick Settings?" dialog
 *   (`StatusBarManager.requestAddTileService`, Android 13+; on older versions there's no such
 *   dialog, so the tile just appears in the QS editor for the user to drag in manually).
 * - `refreshTile` asks System UI to re-listen to a slot so its label/icon/state repaint after an
 *   update without the panel having to close and reopen.
 * - `ensureAllEnabled` runs once at app start (see `ToolerApp`) so every slot is always pickable —
 *   same unconditional startup step as aShellYou's `App.onCreate`.
 *
 * `TileService`s can't be removed from the panel programmatically, which is why deletion only
 * clears the *config* (making the slot render `STATE_UNAVAILABLE`) rather than `setComponentEnabled
 * (false)` — the tile the user already dragged in stays where it is, just grey, exactly like
 * aShellYou.
 */
object TileComponentManager {

    private val tileServices =
        listOf(
            CustomTile01Service::class,
            CustomTile02Service::class,
            CustomTile03Service::class,
            CustomTile04Service::class,
            CustomTile05Service::class,
            CustomTile06Service::class,
            CustomTile07Service::class,
            CustomTile08Service::class,
            CustomTile09Service::class,
            CustomTile10Service::class,
        )

    fun componentName(context: Context, slotIndex: Int): ComponentName =
        ComponentName(context.packageName, tileServices[slotIndex].qualifiedName!!)

    fun setComponentEnabled(context: Context, slotIndex: Int, enabled: Boolean) {
        if (slotIndex !in tileServices.indices) return
        context.packageManager.setComponentEnabledSetting(
            componentName(context, slotIndex),
            if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
    }

    fun ensureAllEnabled(context: Context) {
        tileServices.indices.forEach { setComponentEnabled(context, it, true) }
    }

    /**
     * Fires the system "add this tile to Quick Settings?" dialog (API 33+). On 28-32 there is no
     * system dialog to invoke, so the codepath simply doesn't exist there — the enabled component
     * shows up in the QS editor instead, and the user adds it the same way they add any tile.
     */
    fun promptAddTile(context: Context, slotIndex: Int, label: CharSequence, iconResId: Int) {
        if (slotIndex !in tileServices.indices) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        try {
            val statusBarManager = context.getSystemService(StatusBarManager::class.java)
            val icon = Icon.createWithResource(context, iconResId)
            statusBarManager?.requestAddTileService(
                componentName(context, slotIndex),
                label,
                icon,
                context.mainExecutor,
            ) { _ ->
                // Result callback left intentionally empty — the dialog result itself is
                // impermanent and the tile repaints on next onStartListening either way.
            }
        } catch (@Suppress("UNUSED_PARAMETER") e: Exception) {
            // System managers can refuse; ignore — the tile still works via the QS editor.
        }
    }

    fun refreshTile(context: Context, slotIndex: Int) {
        if (slotIndex !in tileServices.indices) return
        try {
            TileService.requestListeningState(context, componentName(context, slotIndex))
        } catch (@Suppress("UNUSED_PARAMETER") e: Exception) {
            // Service not bound yet — the tile picks the config up on its next onStartListening.
        }
    }
}
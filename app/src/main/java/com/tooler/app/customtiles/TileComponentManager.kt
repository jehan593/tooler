package com.tooler.app.customtiles

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.TileService

/**
 * The system-facing half of creating a custom tile. A tile doesn't exist for System UI until its
 * manifest component is enabled and the user adds it to the panel:
 *
 * - [setComponentEnabled] enables a slot's service component so System UI offers it at all.
 * - [promptAddTile] fires the system's own add-to-panel dialog (API 33+; older systems just show
 *   the enabled component in the QS editor).
 * - [refreshTile] nudges System UI to repaint a slot's label/icon/state.
 * - [ensureAllEnabled] runs at app start so every slot is always pickable.
 *
 * Tiles can't be removed programmatically, so deleting only clears the config — the tile already on
 * the panel stays where it is, just grey.
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

    fun componentNames(context: Context): List<ComponentName> =
        tileServices.map { ComponentName(context.packageName, it.qualifiedName!!) }

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

    /** Fires the system add-to-panel dialog (API 33+) for any tile component. [onResult] carries the
     *  result code — the caller uses it to refresh its in-panel state. */
    fun promptAddTile(
        context: Context,
        component: ComponentName,
        label: CharSequence,
        iconResId: Int,
        onResult: ((Int) -> Unit)? = null,
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        try {
            val statusBarManager = context.getSystemService(StatusBarManager::class.java)
            val icon = Icon.createWithResource(context, iconResId)
            statusBarManager?.requestAddTileService(
                component,
                label,
                icon,
                context.mainExecutor,
            ) { result -> onResult?.invoke(result) }
        } catch (@Suppress("UNUSED_PARAMETER") e: Exception) {
            // System managers can refuse; ignore — the tile still works via the QS editor.
        }
    }

    /** Slot-based convenience — re-uses the component overload. */
    fun promptAddTile(context: Context, slotIndex: Int, label: CharSequence, iconResId: Int) {
        if (slotIndex !in tileServices.indices) return
        promptAddTile(context, componentName(context, slotIndex), label, iconResId)
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
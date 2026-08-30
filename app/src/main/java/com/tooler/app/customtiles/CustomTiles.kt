package com.tooler.app.customtiles

import android.content.Context
import org.json.JSONException
import org.json.JSONObject

/**
 * Number of user-programmable Quick Settings tile slots. Quick Settings tiles cannot be created
 * dynamically — a `TileService` must exist in the manifest for System UI to know about it at all.
 * So, exactly like aShellYou, Tooler pre-declares [CUSTOM_TILE_SLOT_COUNT] generic tile services
 * (`CustomTile01Service`..`CustomTile10Service`) and "creating" a tile means claiming one of these
 * fixed slots with a config — see `TileComponentManager` and `BaseCustomTileService`.
 */
const val CUSTOM_TILE_SLOT_COUNT = 10

/**
 * Everything a user-created tile is: which fixed slot (0-9) it lives in, what it looks like (label,
 * icon), and — mirroring aShellYou's `TileActiveState` — how it behaves when tapped.
 *
 * [isActive] is the tile's *initial state*, picked at creation (aShellYou's "Initial State"
 * switch), and has different meaning per tile kind:
 *
 * - **Toggleable tiles** ([isToggleable] = true) behave as an on/off switch: tapping while
 *   [isActive] runs [offCommand] (to turn the feature off) and flips the stored state on success;
 *   tapping while inactive runs [onCommand] instead. [isActive] is simply where the switch starts.
 * - **Static ("tap action") tiles** ([isToggleable] = false) only ever run [onCommand] on every
 *   tap and never touch [isActive], which stays fixed at its initial value and is *displayed* as
 *   the tile's [Tile.STATE_ACTIVE]/INACTIVE color exactly like aShellYou does.
 *
 * The tile draws [onSubtitle] ([offSubtitle] when inactive) — for static tiles [offSubtitle] is
 * mirrored to [onSubtitle] at save time so the subtitle doesn't dangle, same as aShellYou's
 * `activeTileSubtitle`-for-both behavior.
 */
data class CustomTileConfig(
    val slotIndex: Int,
    val name: String,
    val iconId: String,
    val isToggleable: Boolean,
    val isActive: Boolean,
    val onCommand: String,
    val offCommand: String,
    val onSubtitle: String,
    val offSubtitle: String,
) {
    /** Slot number as a user sees it (1-based), matching the "Tile N" labels and aShellYou's IDs. */
    val id: Int get() = slotIndex + 1

    val currentSubtitle: String get() = if (isActive) onSubtitle else offSubtitle
}

/**
 * The store for user-created tile definitions — the third deliberate exception to "no persisted
 * state" in this app, after `ChargingModePrefs`/`LockedQsPrefs`. Unlike every built-in tile, a
 * custom tile has no system value it mirrors: the definition *is* the feature, authored by the
 * user, so it has to live somewhere. Stored as one `JSONObject` string per slot in a plain
 * `SharedPreferences` (the only serialization this repo needs — no DataStore/Room, same "bare
 * prefs" pattern as the other two exceptions; `org.json` comes from the Android framework).
 */
object CustomTilePrefs {
    private const val PREFS_NAME = "custom_qs_tiles"

    fun load(context: Context, slotIndex: Int): CustomTileConfig? {
        val json =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(key(slotIndex), null)
                ?: return null
        return fromJson(slotIndex, json)
    }

    fun save(context: Context, config: CustomTileConfig) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(key(config.slotIndex), toJson(config))
            .apply()
    }

    fun delete(context: Context, slotIndex: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(key(slotIndex))
            .apply()
    }

    fun all(context: Context): List<CustomTileConfig> =
        (0 until CUSTOM_TILE_SLOT_COUNT).mapNotNull { load(context, it) }

    fun usedCount(context: Context): Int = all(context).size

    private fun key(slotIndex: Int) = "slot_$slotIndex"

    private fun toJson(config: CustomTileConfig): String =
        JSONObject()
            .put("name", config.name)
            .put("icon", config.iconId)
            .put("toggleable", config.isToggleable)
            .put("active", config.isActive)
            .put("onCommand", config.onCommand)
            .put("offCommand", config.offCommand)
            .put("onSubtitle", config.onSubtitle)
            .put("offSubtitle", config.offSubtitle)
            .toString()

    private fun fromJson(slotIndex: Int, json: String): CustomTileConfig? =
        try {
            val o = JSONObject(json)
            CustomTileConfig(
                slotIndex = slotIndex,
                name = o.optString("name"),
                iconId = o.optString("icon", "terminal"),
                isToggleable = o.optBoolean("toggleable", false),
                isActive = o.optBoolean("active", false),
                onCommand = o.optString("onCommand"),
                offCommand = o.optString("offCommand"),
                onSubtitle = o.optString("onSubtitle", "On"),
                offSubtitle = o.optString("offSubtitle", "Off"),
            )
        } catch (@Suppress("UNUSED_PARAMETER") e: JSONException) {
            null
        }
}
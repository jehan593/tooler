package com.tooler.app.customtiles

import android.content.Context
import org.json.JSONException
import org.json.JSONObject

/**
 * Number of user-programmable tile slots. QS tiles can't be created dynamically — a `TileService`
 * must exist in the manifest — so Tooler pre-declares [CUSTOM_TILE_SLOT_COUNT] generic services
 * and "creating" a tile means claiming one of these fixed slots.
 */
const val CUSTOM_TILE_SLOT_COUNT = 10

/**
 * What a user-created tile is: which slot it lives in, its label/icon, and how taps behave.
 *
 * [isActive] is the initial state, picked at creation. For toggleable tiles it becomes the live
 * on/off (tapping runs [offCommand]/[onCommand] and flips it on success); for static tiles it's a
 * fixed value that's only displayed as tile color, never changed by taps. Static tiles also get
 * [offSubtitle] mirrored to [onSubtitle] at save time so there's no dangling subtitle.
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
    /** Slot number as a user sees it (1-based), matching the "Tile N" labels. */
    val id: Int get() = slotIndex + 1

    val currentSubtitle: String get() = if (isActive) onSubtitle else offSubtitle
}

/**
 * Stores user-created tile definitions — one `JSONObject` string per slot in a plain
 * `SharedPreferences` (no DataStore/Room; `org.json` comes from the Android framework).
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
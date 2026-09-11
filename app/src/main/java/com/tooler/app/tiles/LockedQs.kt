package com.tooler.app.tiles

import android.content.Context
import com.tooler.app.util.StatusBarFlags

/** This requester's slot in StatusBarFlags' disable-request map (see util/StatusBarFlags.kt). */
const val LOCKED_QS_REQUESTER_ID = "LockedQs"

/**
 * The Lock Quick Settings toggle. There is no live system value to read — the disable flag lives
 * inside SystemUI, resets on reboot, and only exists between screen-off and unlock — so a
 * `SharedPreferences` boolean remembers the user's intent across process restarts and reboots, or
 * the screen-off receiver would have no way to know it should re-apply the flag on the next lock.
 */
private const val PREFS_NAME = "locked_qs"
private const val KEY_ENABLED = "enabled"

private object LockedQsPrefs {
    fun read(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)

    fun write(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
    }
}

/** Whether Lock Quick Settings is on — this is the only true state this feature has. */
fun isLockedQsEnabled(context: Context): Boolean = LockedQsPrefs.read(context)

/**
 * Flips the toggle. Turning off restores the panel immediately (in case the flag is applied right
 * now); turning on only records the intent — the next screen-off applies it, the next unlock clears
 * it.
 */
fun setLockedQsEnabled(context: Context, enabled: Boolean) {
    LockedQsPrefs.write(context, enabled)
    if (!enabled) {
        StatusBarFlags.requestRestore(LOCKED_QS_REQUESTER_ID)
    }
}
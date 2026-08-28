package com.tooler.app.tiles

import android.content.Context
import com.tooler.app.util.StatusBarFlags

/** This requester's slot in StatusBarFlags' disable-request map (see util/StatusBarFlags.kt). */
const val LOCKED_QS_REQUESTER_ID = "LockedQs"

/**
 * The Lock Quick Settings toggle. Keeps a single `SharedPreferences` boolean — the second
 * deliberate exception to "never persist local state" in this codebase, after `ChargingModePrefs`,
 * and for an even more fundamental reason: *there is no live system value to read at all*. The
 * disable flag this tile manipulates lives entirely inside SystemUI (it's `StatusBarManager`'s
 * `DISABLE2_QUICK_SETTINGS` bit over a binder, not a settings key), is wiped on every reboot, and
 * is only ever set momentarily between screen-off and the next unlock. Something has to remember the
 * user's actual intent — "keep QS off the lock screen" — across process restarts and reboots, or the
 * SCREEN_OFF receiver (see [LockedQsReceiver]) would have no way to know it should re-apply the flag
 * on the next lock. So this pref is not a stale cache of a readable system value like Private DNS's
 * hostname; it *is* the source of truth, written by this app, read by this app.
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
 * Flips the toggle. Turning *off* restores the QS panel immediately (the flag may be applied right
 * now if the screen is currently locked), mirroring essentials' `setScreenLockedSecurityEnabled`.
 * Turning *on* deliberately does nothing right away except record the intent: the disable flag only
 * makes sense while the screen is actually locked, and the next [Intent.ACTION_SCREEN_OFF] applies
 * it — same "apply on lock, clear on unlock" behavior as the reference implementation.
 */
fun setLockedQsEnabled(context: Context, enabled: Boolean) {
    LockedQsPrefs.write(context, enabled)
    if (!enabled) {
        StatusBarFlags.requestRestore(LOCKED_QS_REQUESTER_ID)
    }
}
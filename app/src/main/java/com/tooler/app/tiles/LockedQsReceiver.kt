package com.tooler.app.tiles

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tooler.app.util.StatusBarFlags

/**
 * Applies and removes the Quick Settings disable flag around lock/unlock — a trimmed, single-feature
 * port of essentials' `SecurityReceiver.kt` (MIT).
 *
 * [Intent.ACTION_SCREEN_OFF] fires when the screen turns off (which, on a secured phone, is what
 * you're in when the lock screen shows and someone could pull the QS panel down); that's when the
 * `quick-settings` disable flag gets applied, so the panel can't be expanded from the lock screen.
 * [Intent.ACTION_USER_PRESENT] fires on successful unlock; that's when the flag is removed and QS
 * comes back. Deliberately read the pref live on every event, never cached — the user can flip the
 * toggle from the tile or MainActivity while this receiver is registered.
 *
 * **Dynamically registered, not manifest-registered** (see [com.tooler.app.ToolerApp]): Android 8+
 * forbids manifest receivers for implicit system broadcasts, and these two specific actions are on
 * that forbidden list, so this must be registered in-process while the app lives. That means Lock
 * Quick Settings stops applying the flag if Android kills this app's process (it does, to every
 * app) — the flag also resets on every reboot — until the next time the process starts (opening the
 * app, or tapping any tile). That's the same trade-off essentials makes for the same feature, not a
 * bug in the port. The opt-in "Background reliability" card in MainActivity is the mitigation, same
 * as for tile cold-start latency.
 */
class LockedQsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SCREEN_OFF ->
                if (isLockedQsEnabled(context)) {
                    StatusBarFlags.requestDisable(LOCKED_QS_REQUESTER_ID, setOf(StatusBarFlags.FLAG_QUICK_SETTINGS))
                }
            // Unconditional on purpose — the safety net. Always clear every disable flag on unlock,
            // whether or not this app believes it holds one right now: if the process died while
            // armed, the flag lingers in SystemUI with no live owner, and only an unconditional
            // `send-disable-flag none` reliably lifts it.
            Intent.ACTION_USER_PRESENT ->
                StatusBarFlags.clearAll()
        }
    }
}
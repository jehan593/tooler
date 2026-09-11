package com.tooler.app.tiles

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import com.tooler.app.util.StatusBarFlags

/**
 * Applies and clears the Quick Settings disable flag around lock/unlock. `ACTION_SCREEN_OFF` sets
 * it (so the panel can't be pulled from the lock screen); clearing it runs on two paths because
 * `ACTION_USER_PRESENT` isn't fired on every unlock:
 *
 * - `ACTION_USER_PRESENT` (normal unlock) — clear unconditionally (the safety net, lifting even a
 *   stale flag whose owner died while armed), then poll the keyguard briefly.
 * - `ACTION_SCREEN_ON` — poll and clear only once the screen is on AND the keyguard is gone,
 *   covering smart-lock unlocks that never broadcast `USER_PRESENT`.
 *
 * **Dynamically registered** (in [com.tooler.app.ToolerApp]): Android 8+ forbids manifest receivers
 * for these protected broadcasts, so it only fires while the process is alive. If Android kills the
 * app mid-armed-state, the flag just isn't applied until the next screen-off; ToolerApp also
 * self-heals a stranded flag at startup.
 */
class LockedQsReceiver : BroadcastReceiver() {

    private var appContext: Context? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    private var pendingClearChecks = 0

    /** Polls the keyguard after screen-on/unlock and clears the flag once the screen is on and
     *  unlocked — gated so it can never clear while the keyguard is actually showing. */
    private val keyguardPoll = object : Runnable {
        override fun run() {
            if (screenOnAndUnlocked()) {
                StatusBarFlags.clearAll()
                stopKeyguardPoll()
            } else if (--pendingClearChecks > 0) {
                mainHandler.postDelayed(this, KEYGUARD_POLL_MS)
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (appContext == null) {
            appContext = context.applicationContext
        }
        when (intent.action) {
            Intent.ACTION_SCREEN_OFF -> {
                stopKeyguardPoll()
                if (isLockedQsEnabled(context)) {
                    StatusBarFlags.requestDisable(
                        LOCKED_QS_REQUESTER_ID,
                        setOf(StatusBarFlags.FLAG_QUICK_SETTINGS)
                    )
                }
            }
            Intent.ACTION_SCREEN_ON -> {
                if (isLockedQsEnabled(context)) {
                    startKeyguardPoll()
                }
            }
            // Unconditional on purpose — the safety net: the flag can outlive this app's process, so
            // always clear it on unlock, then poll to beat a straggler application that could
            // otherwise land after the clear and re-disable the panel while unlocked.
            Intent.ACTION_USER_PRESENT -> {
                stopKeyguardPoll()
                StatusBarFlags.clearAll()
                pendingClearChecks = USER_PRESENT_POLL_TICKS
                mainHandler.post(keyguardPoll)
            }
        }
    }

    private fun startKeyguardPoll() {
        stopKeyguardPoll()
        pendingClearChecks = SCREEN_ON_POLL_TICKS
        mainHandler.post(keyguardPoll)
    }

    private fun stopKeyguardPoll() {
        mainHandler.removeCallbacks(keyguardPoll)
        pendingClearChecks = 0
    }

    /** Only ever true when the display is visibly on and no keyguard is shown — clearing here is
     *  always safe, because the user is demonstrably present. */
    private fun screenOnAndUnlocked(): Boolean {
        val context = appContext ?: return false
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        return powerManager.isInteractive && !keyguardManager.isKeyguardLocked()
    }

    private companion object {
        const val KEYGUARD_POLL_MS = 500L
        const val SCREEN_ON_POLL_TICKS = 8
        const val USER_PRESENT_POLL_TICKS = 3
    }
}
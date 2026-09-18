package com.tooler.app.tiles

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import com.tooler.app.util.StatusBarFlags

/** Reconcile live device state; broadcasts are wake-up signals, not authoritative lock state. */
class LockedQsReceiver : BroadcastReceiver() {
    private var appContext: Context? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val keyguardPoll = object : Runnable {
        override fun run() {
            val context = appContext ?: return
            val power = context.getSystemService(PowerManager::class.java)
            val keyguard = context.getSystemService(KeyguardManager::class.java)
            if (!shouldLockQuickSettings(isLockedQsEnabled(context), power.isInteractive, keyguard.isKeyguardLocked)) {
                StatusBarFlags.clearAll()
            } else if (power.isInteractive) {
                // Keep watching throughout the lock screen, including a late biometric/Smart Lock
                // unlock. The old four-second timeout missed those when USER_PRESENT was absent.
                mainHandler.postDelayed(this, KEYGUARD_POLL_MS)
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        reconcile(context)
    }

    /** Also called on process startup and Shizuku reconnection to repair stranded system flags. */
    fun reconcile(context: Context) {
        appContext = context.applicationContext
        mainHandler.removeCallbacks(keyguardPoll)
        val power = context.getSystemService(PowerManager::class.java)
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        if (shouldLockQuickSettings(isLockedQsEnabled(context), power.isInteractive, keyguard.isKeyguardLocked)) {
            StatusBarFlags.requestDisable(LOCKED_QS_REQUESTER_ID, setOf(StatusBarFlags.FLAG_QUICK_SETTINGS))
            if (power.isInteractive) mainHandler.postDelayed(keyguardPoll, KEYGUARD_POLL_MS)
        } else {
            StatusBarFlags.clearAll()
        }
    }

    private companion object {
        const val KEYGUARD_POLL_MS = 500L
    }
}

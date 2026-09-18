package com.tooler.app.util

import android.app.KeyguardManager
import android.content.Context
import android.os.PowerManager
import com.tooler.app.tiles.isLockedQsEnabled
import com.tooler.app.tiles.shouldLockQuickSettings

/**
 * Mirrors System UI's disable-flag model for this app: each requester registers its flags, the
 * union is sent to the shell as `cmd statusbar send-disable-flag <flags>`. There's one requested
 * state per requester and who-last-wins across requesters, same as the platform. The
 * `quick-settings` flag is a no-op on Android before 2026 (that command arg only reached AOSP
 * then), and an empty aggregate becomes `send-disable-flag none` to clear everything.
 */
object StatusBarFlags {
    /** Hides the Quick Settings panel while set ("Hide QQS/QS", Android 2026+; no-op below). */
    const val FLAG_QUICK_SETTINGS = "quick-settings"

    // Serialize commands in submission order without blocking tile/service callbacks.
    private val commandExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()

    private lateinit var appContext: Context
    private var revision = 0L

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    private val disableRequests = mutableMapOf<String, Set<String>>()

    /** Register (or replace) requester [requesterId]'s desired flags and apply the new aggregate. */
    @Synchronized
    fun requestDisable(requesterId: String, flags: Set<String>) {
        disableRequests[requesterId] = flags
        update()
    }

    /** Drop requester [requesterId] and re-apply the aggregate; clears its flags if none remain. */
    @Synchronized
    fun requestRestore(requesterId: String) {
        disableRequests.remove(requesterId)
        // The system flag may outlive our in-memory map after process death.
        update()
    }

    /**
     * Forced full clear — the USER_PRESENT safety net. Unlike [requestRestore], it drops every
     * requester and always sends `none`, even if the map is already empty: a flag left in SystemUI
     * after this app's process died needs an unconditional clear to be lifted.
     */
    @Synchronized
    fun clearAll() {
        disableRequests.clear()
        update()
    }

    private fun canLockNow(): Boolean {
        val power = appContext.getSystemService(PowerManager::class.java)
        val keyguard = appContext.getSystemService(KeyguardManager::class.java)
        return shouldLockQuickSettings(isLockedQsEnabled(appContext), power.isInteractive, keyguard.isKeyguardLocked)
    }

    private fun update() {
        val scheduledRevision = ++revision
        commandExecutor.execute {
            val flags = synchronized(this) {
                // Drop superseded screen-off commands instead of replaying them after unlock.
                if (scheduledRevision != revision) return@execute
                disableRequests.values.flatten().toSet()
            }
            val safeFlags = if (canLockNow()) flags else flags - FLAG_QUICK_SETTINGS
            val args = safeFlags.takeIf { it.isNotEmpty() }?.joinToString(" ") ?: "none"
            ShizukuUtils.runCommand("cmd statusbar send-disable-flag $args")
            // Unlock can happen while the shell command is in flight, before a broadcast arrives.
            if (FLAG_QUICK_SETTINGS in safeFlags && !canLockNow()) {
                ShizukuUtils.runCommand("cmd statusbar send-disable-flag none")
            }
        }
    }
}

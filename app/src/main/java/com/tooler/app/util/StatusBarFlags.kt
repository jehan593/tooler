package com.tooler.app.util

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

    private val disableRequests = mutableMapOf<String, Set<String>>()

    /** Register (or replace) requester [requesterId]'s desired flags and apply the new aggregate. */
    fun requestDisable(requesterId: String, flags: Set<String>) {
        disableRequests[requesterId] = flags
        update()
    }

    /** Drop requester [requesterId] and re-apply the aggregate; clears its flags if none remain. */
    fun requestRestore(requesterId: String) {
        if (disableRequests.remove(requesterId) != null) {
            update()
        }
    }

    /**
     * Forced full clear — the USER_PRESENT safety net. Unlike [requestRestore], it drops every
     * requester and always sends `none`, even if the map is already empty: a flag left in SystemUI
     * after this app's process died needs an unconditional clear to be lifted.
     */
    fun clearAll() {
        disableRequests.clear()
        update()
    }

    private fun update() {
        val allFlags = disableRequests.values.flatten().toSet()
        val command =
            if (allFlags.isEmpty()) {
                "cmd statusbar send-disable-flag none"
            } else {
                "cmd statusbar send-disable-flag ${allFlags.joinToString(" ")}"
            }
        ShizukuUtils.runCommand(command)
    }
}
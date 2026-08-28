package com.tooler.app.util

/**
 * Manages System UI status-bar disable flags — a trimmed port of essentials'
 * `StatusBarManager.kt` (MIT), keeping only the flag bookkeeping Lock Quick Settings needs
 * (essentials also hosts expand/collapse helpers and a second requester on the same map; Tooler
 * has one requester and no panel commands).
 *
 * The mechanism: Android has no public API to disable the Quick Settings panel — the OS-level knob
 * is `cmd statusbar send-disable-flag <flag>...`, a shell command (STATUS_BAR permission, see
 * [ShizukuUtils]) that maps flags onto `StatusBarManager`'s hidden `DISABLE*` bits. There is
 * exactly one requested state per caller in the system (the "wake lock style" disable-flag model),
 * so whoever controls the flag last wins — and independently, multiple callers fold their requests
 * into one aggregate. This object mirrors that model for this app: each `requesterId` registers its
 * own flags, and [update] sends the union to the shell. `requestRestore` drops a requester and
 * re-sends the (smaller, possibly empty) union, so any flag this app previously set gets cleared.
 *
 * The `quick-settings` flag specifically was only added to AOSP in 2026 ("Hide QQS/QS when quick
 * settings is disabled"), so on older Android `send-disable-flag quick-settings` simply does
 * nothing (the arg isn't recognized) — a silent no-op, same as essentials, not an error worth
 * surfacing. When empty we send the literal `none` command, which clears all of this app's disable
 * bits the same way either reference implementation does.
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
     * Forced full clear — the USER_PRESENT safety net, deliberately different from [requestRestore]:
     * it drops *every* requester and always sends the `none` command even if this app's map is
     * already empty. If the process died while armed, SystemUI still holds the disable flag after the
     * map is gone; only an unconditional `send-disable-flag none` wins against that stale state.
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
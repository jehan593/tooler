package com.tooler.app

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import com.tooler.app.tiles.LockedQsReceiver
import com.tooler.app.util.ShizukuUtils

/**
 * Injected as `android:name=".ToolerApp"` so [LockedQsReceiver] has somewhere stable to live.
 *
 * The receiver must be registered dynamically rather than from the manifest: `ACTION_SCREEN_OFF`
 * and `ACTION_USER_PRESENT` are protected system broadcasts, and Android 8+ manifest-registered
 * receivers can't receive implicit broadcasts at all — these two included (essentials registers its
 * equivalent `SecurityReceiver` exactly this way, in `EssentialsApp.onCreate`). Application is the
 * right host because it's the one component guaranteed to be constructed whenever *any* of this
 * app's components starts (tiles, the shortcut, MainActivity), and it outlives them as long as the
 * process does.
 *
 * Downside inherited from that design (documented on [LockedQsReceiver]): dynamically-registered
 * receivers die with the process. If Android kills Tooler, screen-off stops being watched until the
 * next process start, and since the disable flag itself also resets at every reboot, an app that's
 * been killed sits idle until something brings it back — same behavior as essentials, same
 * mitigation (the "Background reliability" card).
 */
class ToolerApp : Application() {

    private val lockedQsReceiver = LockedQsReceiver()

    override fun onCreate() {
        super.onCreate()

        // Keeps ShizukuUtils' binder cache in sync when the Shizuku server (re)attaches to this
        // process — without it, the cached binder can go stale after the Shizuku app is restarted.
        ShizukuUtils.initialize()

        val filter =
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
            }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(lockedQsReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(lockedQsReceiver, filter)
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        unregisterReceiver(lockedQsReceiver)
    }
}
package com.tooler.app

import android.app.Application
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.PowerManager
import com.tooler.app.customtiles.TileComponentManager
import com.tooler.app.tiles.LockedQsReceiver
import com.tooler.app.util.ShizukuUtils
import com.tooler.app.util.StatusBarFlags

/**
 * Hosts the dynamically-registered [LockedQsReceiver]. It can't be manifest-registered: Android 8+
 * forbids manifest receivers for the protected `ACTION_SCREEN_OFF`/`ACTION_USER_PRESENT` broadcasts.
 * `Application` is the one component guaranteed to exist whenever any tile, shortcut, or activity
 * starts. A killed process means screen-off stops being watched, and the flag resets on reboot —
 * same trade-off as the reference implementation, mitigated by the "Background reliability" card.
 */
class ToolerApp : Application() {

    private val lockedQsReceiver = LockedQsReceiver()

    override fun onCreate() {
        super.onCreate()

        // Keeps ShizukuUtils' binder cache in sync when the Shizuku server (re)attaches.
        ShizukuUtils.initialize()

        // Enables all ten custom-tile slot components so System UI offers them in the add-tile
        // picker; an empty slot shows grey until configured.
        TileComponentManager.ensureAllEnabled(this)

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

        // If the process was killed while armed, the flag can linger in SystemUI with no live receiver
        // to lift it. Clearing it here on restart (screen on and unlocked) unsticks the panel.
        clearStaleDisableFlagIfUnlocked()
    }

    private fun clearStaleDisableFlagIfUnlocked() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (powerManager.isInteractive && !keyguardManager.isKeyguardLocked()) {
            StatusBarFlags.clearAll()
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        unregisterReceiver(lockedQsReceiver)
    }
}
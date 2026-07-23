package com.tooler.app.tiles

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import android.service.quicksettings.TileService
import androidx.core.app.NotificationCompat
import com.tooler.app.R

/**
 * Holds a [PowerManager] wake lock for as long as it's alive — the only non-root way to keep the
 * screen on with no active foreground window (there's no modern "keep screen on globally" API;
 * FLAG_KEEP_SCREEN_ON only works on an activity's own window while it's in front). Runs as a
 * foreground service (rather than acquiring the lock straight from the TileService) so the process
 * isn't eligible for background-execution limits or the cached-app freezer while the lock is held —
 * a bare WakeLock with no foreground service backing it can still get torn down by the OS shortly
 * after the owning component stops being active.
 */
class KeepAwakeService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        startForeground(NOTIFICATION_ID, buildNotification())
        acquireWakeLock()
        requestTileRefresh()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        releaseWakeLock()
        isRunning = false
        requestTileRefresh()
        super.onDestroy()
    }

    @Suppress("DEPRECATION") // SCREEN_BRIGHT_WAKE_LOCK has no non-deprecated replacement.
    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ON_AFTER_RELEASE,
            "tooler:keep_screen_on"
        ).apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(CHANNEL_ID, "Keep Screen On", NotificationManager.IMPORTANCE_LOW)
            .apply { setShowBadge(false) }
        manager.createNotificationChannel(channel)

        val stopIntent = Intent(this, KeepAwakeService::class.java).setAction(ACTION_STOP)
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Screen kept on")
            .setSmallIcon(R.drawable.ic_notification_keep_awake)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .addAction(0, "Turn off", stopPendingIntent)
            .build()
    }

    private fun requestTileRefresh() {
        TileService.requestListeningState(
            applicationContext, ComponentName(applicationContext, KeepScreenOnTileService::class.java)
        )
    }

    companion object {
        const val ACTION_STOP = "com.tooler.app.action.STOP_KEEP_AWAKE"
        private const val CHANNEL_ID = "keep_screen_on"
        private const val NOTIFICATION_ID = 1001

        var isRunning = false
            private set
    }
}

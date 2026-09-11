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
import com.tooler.app.util.StatusNotifier

/**
 * Holds a wake lock for as long as it's alive — the only non-root way to keep the screen on with
 * no active window. Runs as a foreground service so the OS won't tear the lock down while it's held.
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
        StatusNotifier.notifyChanged()
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
        StatusNotifier.notifyChanged()
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
            .setContentTitle("Keeping Screen On")
            // The eye glyph doubles as the status-bar sliver; the system renders its alpha only.
            .setSmallIcon(R.drawable.ic_keep_screen_on)
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

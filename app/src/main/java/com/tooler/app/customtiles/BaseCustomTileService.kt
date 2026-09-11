package com.tooler.app.customtiles

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.app.NotificationCompat
import com.tooler.app.MainActivity
import com.tooler.app.R
import com.tooler.app.tiles.BaseTileService
import com.tooler.app.util.ShizukuUtils
import com.tooler.app.util.setSubtitleCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Per-slot guard against duplicate taps — a tile already executing ignores a second tap. */
object CustomTileRunner {
    private val runningSlots = java.util.concurrent.ConcurrentHashMap.newKeySet<Int>()

    /** Returns false if the slot is already executing (duplicate tap). */
    fun begin(slotIndex: Int): Boolean = runningSlots.add(slotIndex)

    fun end(slotIndex: Int) {
        runningSlots.remove(slotIndex)
    }

    fun isRunning(slotIndex: Int): Boolean = slotIndex in runningSlots
}

/**
 * Base class for the ten custom tile slots. Each subclass owns one fixed [slotIndex] and this class
 * binds whichever config occupies that slot to the QS tile: `onStartListening()` paints it (empty
 * slots render "Tile N" grey), `onClick()` runs the config's command through [ShizukuUtils].
 * Tapping without shell access opens MainActivity for the grant flow; a failed command surfaces a
 * notification. Toggleable tiles only flip their stored `isActive` when the command succeeds.
 */
abstract class BaseCustomTileService : BaseTileService() {

    /** Fixed slot index this service instance owns (0-9). */
    abstract val slotIndex: Int

    private var serviceScope: CoroutineScope? = null

    override fun onCreate() {
        super.onCreate()
        serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    }

    override fun onDestroy() {
        serviceScope?.cancel()
        serviceScope = null
        super.onDestroy()
    }

    override fun onClick() {
        super.onClick()

        val config = CustomTilePrefs.load(this, slotIndex) ?: return
        if (!ShizukuUtils.isGranted()) {
            openAppForSetup()
            return
        }
        val scope = serviceScope ?: return
        if (!CustomTileRunner.begin(slotIndex)) return

        // Immediate feedback: flip to "Running…" right away instead of after the command finishes.
        paintRunningState()
        val tileName = config.name

        scope.launch(Dispatchers.IO) {
            var success = false
            var outcome: ShizukuUtils.CommandOutcome? = null
            try {
                val command =
                    when {
                        !config.isToggleable || !config.isActive -> config.onCommand
                        else -> config.offCommand
                    }
                outcome = ShizukuUtils.runCommandForOutput(command)
                success = outcome.exitCode == 0
            } finally {
                // Always release the slot, even if the service was destroyed mid-run.
                CustomTileRunner.end(slotIndex)
            }
            // Flip state only on success; NonCancellable so a destroyed service still lands the flip.
            withContext(NonCancellable) {
                if (success && config.isToggleable) {
                    CustomTilePrefs.save(this@BaseCustomTileService, config.copy(isActive = !config.isActive))
                } else if (!success) {
                    notifyFailure(tileName, outcome)
                }
            }
            // Repaint — immediately if alive, otherwise on the next panel open.
            if (scope.isActive) {
                scope.launch { refresh() }
            } else {
                TileService.requestListeningState(
                    this@BaseCustomTileService,
                    ComponentName(this@BaseCustomTileService, javaClass)
                )
            }
        }
    }

    /** Shows a Running… label while a command runs, without pre-flipping the on/off color. */
    private fun paintRunningState() {
        val tile = qsTile ?: return
        tile.label = "Running…"
        tile.setSubtitleCompat("Running…")
        tile.updateTile()
    }

    /** Surfaces a failed command as a notification — a failed tap is otherwise indistinguishable
     *  from a dead tile. Carries the shell's error line and exit code so the reason is visible. */
    private fun notifyFailure(tileName: String, outcome: ShizukuUtils.CommandOutcome?) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Custom tile errors",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Notifications for custom Quick Settings tile execution failures." }
        )

        val openIntent =
            packageManager.getLaunchIntentForPackage(packageName)?.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            } ?: Intent()
        val pendingIntent =
            PendingIntent.getActivity(
                this,
                slotIndex + 1,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        val exitCode = outcome?.exitCode
        val output = outcome?.errorOutput.orEmpty().trim()
        val contentText =
            when {
                exitCode == null ->
                    "Couldn't run the command — check that Shizuku has shell access."
                output.isNotEmpty() ->
                    "Exited with code $exitCode: $output"
                else ->
                    "Exited with code $exitCode with no output."
            }

        val notification =
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_terminal)
                .setContentTitle("Tile \"$tileName\" failed")
                .setContentText(contentText)
                .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()
        manager.notify(slotIndex + 1, notification)
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openAppForSetup() {
        val intent = Intent(this, MainActivity::class.java)
        if (Build.VERSION.SDK_INT >= 34) {
            val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    override fun refresh() {
        val tile = qsTile ?: return
        val config = CustomTilePrefs.load(this, slotIndex)

        if (config == null) {
            tile.label = "Tile ${slotIndex + 1}"
            tile.icon = Icon.createWithResource(this, R.drawable.ic_terminal)
            tile.state = Tile.STATE_UNAVAILABLE
            tile.setSubtitleCompat(null)
            tile.updateTile()
            return
        }

        tile.icon = Icon.createWithResource(this, CustomTileIcons.res(config.iconId))
        val running = CustomTileRunner.isRunning(slotIndex)
        tile.label = if (running) "Running…" else config.name
        // isActive drives the color, matching aShellYou: static tiles show their fixed initial state.
        tile.state = if (!running && config.isActive) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.setSubtitleCompat(if (running) "Running…" else config.currentSubtitle)
        tile.updateTile()
    }

    companion object {
        private const val CHANNEL_ID = "custom_tile_errors"
    }
}
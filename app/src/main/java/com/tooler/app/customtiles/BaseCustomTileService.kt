package com.tooler.app.customtiles

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.app.NotificationCompat
import com.tooler.app.MainActivity
import com.tooler.app.R
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

/**
 * Per-slot guard against duplicate taps. aShellYou's `TileExecutionManager` keeps a
 * `runningJobs` map; with no coroutine framework here the same job is done with a plain set —
 * a tile already executing ignores a second tap until it finishes. [begin] is always paired with
 * [end] in a `finally` (see [BaseCustomTileService.onClick]) so a slot can never stay locked.
 */
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
 * Base class for the ten pre-declared custom tile slots — the direct port of aShellYou's
 * `BaseTileService`. Each concrete subclass owns one fixed [slotIndex] (0-9) and this class binds
 * whichever `CustomTileConfig` currently occupies that slot to the QS tile:
 *
 * - `onStartListening()` paints the tile: configured slots get their label/icon/subtitle/state,
 *   empty slots render "Tile N" with `STATE_UNAVAILABLE` (QS tiles can't be removed from the panel
 *   programmatically, so an unclaimed slot just goes grey).
 * - `onClick()` runs the config's command through `ShizukuUtils` (shell access is the whole point
 *   of this feature — custom tiles can't do anything any normal app can already do, so every tap
 *   is a privileged command). If shell access was never granted, tapping opens MainActivity for
 *   the grant flow instead of silently failing — same "tap to set up, don't silently fail" pattern
 *   as `LockedQsTileService`. Toggleable tiles only flip their persisted `isActive` when the
 *   command actually succeeds, exactly like aShellYou's executor; a failed command surfaces a
 *   notification with the tile's name instead of dying silently.
 *
 * State semantics match aShellYou: the tile renders `Tile.STATE_ACTIVE` whenever its stored
 * `isActive` is true — for toggleable tiles that's the live on/off, for static tiles it's the
 * fixed "initial state" the user picked at creation (there's no separate "always inactive" look).
 */
abstract class BaseCustomTileService : TileService() {

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

    override fun onStartListening() {
        super.onStartListening()
        refresh()
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

        // Immediate "something happened" feedback — the tile flips to a Running… label right away
        // instead of only repainting when the command finishes (same tap-reads-as-responsible idea
        // as aShellYou's running-state flow).
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
                // Always release the slot, even if the service was destroyed mid-run. destroy()
                // cancels serviceScope, and a cancel left a fresh launch dead at the gate — the
                // old code released the lock only from a post-cancel coroutine, so a single
                // mid-run teardown locked the slot forever and every tap after silently no-oped.
                CustomTileRunner.end(slotIndex)
            }
            // Persist the toggle only on success, same as aShellYou — a failed command shouldn't
            // leave the tile claiming a state it never reached. NonCancellable: if the service was
            // destroyed while the command ran, the flip still lands so the next onStartListening
            // paints the right state.
            withContext(NonCancellable) {
                if (success && config.isToggleable) {
                    CustomTilePrefs.save(this@BaseCustomTileService, config.copy(isActive = !config.isActive))
                } else if (!success) {
                    notifyFailure(tileName, outcome)
                }
            }
            // Repaint. If the process is still alive and bound, refresh() applies immediately; if
            // the service got torn down mid-run, requestListeningState makes System UI re-listen on
            // next panel open, which reads the just-flipped config.
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

    /** Flips the tile to a Running… label for the duration of a command run (paint-only; [state] is
     *  left alone so the on/off color doesn't flash before the real result is known). */
    private fun paintRunningState() {
        val tile = qsTile ?: return
        tile.label = "Running…"
        tile.setSubtitleCompat("Running…")
        tile.updateTile()
    }

    /**
     * Surfaces a failed command the same way aShellYou's `TileNotificationHelper` does — a failed
     * tap is otherwise indistinguishable from a dead tile. Default-importance so the failure is
     * actually noticed; tapping it opens the app. The notification carries the shell's own output
     * and exit code rather than a generic "failed" so the reason is visible at a glance (a missing
     * binary, a permission error, a root-only command — all readable from the error line).
     */
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

    private fun refresh() {
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
        // isActive drives the color for both tile kinds, matching aShellYou: toggleable tiles show
        // their live state, static tiles show the fixed initial state the user chose at creation.
        tile.state = if (!running && config.isActive) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.setSubtitleCompat(if (running) "Running…" else config.currentSubtitle)
        tile.updateTile()
    }

    companion object {
        private const val CHANNEL_ID = "custom_tile_errors"
    }
}
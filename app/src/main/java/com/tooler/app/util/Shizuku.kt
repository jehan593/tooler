package com.tooler.app.util

import android.content.pm.PackageManager
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.RemoteException
import android.util.Log
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku
import java.util.ArrayDeque

/**
 * Thin wrapper over Shizuku (the app that exposes the shell user's uid to non-root apps) for
 * running privileged commands. Lock Quick Settings needs it for `cmd statusbar send-disable-flag`,
 * which requires the `STATUS_BAR` permission — a signature permission with no `pm grant` back door,
 * so shell access is the only route. The binder cache is refreshed from Shizuku's own listeners
 * ([ToolerApp] registers them); every is-available/is-granted check is live, never cached.
 */
object ShizukuUtils {
    private const val TAG = "ShizukuUtils"

    private var binder: IBinder? = null

    // Bounds retained output (the offending line is almost always near the end) so a chatty
    // command like `cmd statusbar` can't balloon memory.
    private const val MAX_TAIL_LINES = 40
    private const val MAX_TAIL_CHARS = 600

    /** Request code for the shell-access grant dialog. */
    const val REQUEST_CODE = 20231001

    private val binderReceivedListener =
        Shizuku.OnBinderReceivedListener {
            binder = Shizuku.getBinder()
        }

    private val binderDeadListener =
        Shizuku.OnBinderDeadListener {
            binder = null
        }

    /** Called once from [com.tooler.app.ToolerApp.onCreate]. */
    fun initialize() {
        Shizuku.addBinderReceivedListener(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
    }

    private val isBinderAlive: Boolean
        get() {
            if (binder?.isBinderAlive == true) return true
            return try {
                if (Shizuku.pingBinder()) {
                    binder = Shizuku.getBinder()
                    binder?.isBinderAlive == true
                } else {
                    false
                }
            } catch (@Suppress("UNUSED_PARAMETER") e: Exception) {
                false
            }
        }

    /** Whether the Shizuku server is running and attached to this process. */
    fun isAvailable(): Boolean =
        try {
            Shizuku.pingBinder() || (Shizuku.getBinder()?.isBinderAlive == true)
        } catch (@Suppress("UNUSED_PARAMETER") e: Exception) {
            false
        }

    /** Whether the user has granted Tooler shell access (server running AND permission granted). */
    fun isGranted(): Boolean {
        if (!isBinderAlive) return false
        return try {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (@Suppress("UNUSED_PARAMETER") e: Exception) {
            false
        }
    }

    /** Shows the one-time grant dialog. The result arrives through an
     *  `Shizuku.OnRequestPermissionResultListener`; MainActivity registers one to refresh its UI. */
    fun requestPermission() {
        try {
            Shizuku.requestPermission(REQUEST_CODE)
        } catch (@Suppress("UNUSED_PARAMETER") e: Exception) {
            // Permission request failed — Shizuku is probably not running/authorized.
        }
    }

    /**
     * The outcome of a Shizuku command run. [exitCode] is the child's exit code — 0 means success,
     * mirroring aShellYou's executor — or null when the command couldn't be run at all (Shizuku not
     * attached, shell access not granted, or a binder failure mid-run). [errorOutput] holds the tail
     * of the child's output to give a failed run something to say: stderr when present, otherwise
     * stdout (both are drained to EOF so a pipe can never deadlock `waitFor`).
     */
    data class CommandOutcome(val exitCode: Int?, val errorOutput: String)

    /**
     * Runs [command] as the shell user, draining output and returning [CommandOutcome]. The command
     * is lightly sanitized (`trim`, plus dropping a pasted `adb shell ` prefix) so users can paste
     * an adb-style command verbatim.
     */
    fun runCommandForOutput(command: String): CommandOutcome {
        val cleanCommand = command.trim().removePrefix("adb ").removePrefix("shell ")
        if (!isBinderAlive || !isGranted()) return CommandOutcome(null, "")
        val process = try {
            IShizukuService.Stub.asInterface(binder)
                ?.newProcess(arrayOf("sh", "-c", cleanCommand), null, "/")
        } catch (e: RemoteException) {
            null
        } ?: return CommandOutcome(null, "")

        // Read both streams on their own threads *before* waitFor, or a command emitting more than
        // the OS pipe buffer blocks forever (process waiting on write, us waiting on the process).
        var stderr = emptyList<String>()
        var stdout = emptyList<String>()
        val exitCode = try {
            val stderrFd = process.errorStream
            val stdoutFd = process.inputStream
            val stderrThread = Thread { stderr = drainTail(stderrFd) }.apply { start() }
            val stdoutThread = Thread { stdout = drainTail(stdoutFd) }.apply { start() }
            val code = process.waitFor()
            stderrThread.join(2000)
            stdoutThread.join(2000)
            code
        } catch (e: RemoteException) {
            return CommandOutcome(null, "")
        }

        val errorOutput = (if (stderr.isNotEmpty()) stderr else stdout)
            .joinToString("\n")
            .takeLast(MAX_TAIL_CHARS)
        Log.d(TAG, "$cleanCommand -> exit $exitCode:\n$errorOutput")
        return CommandOutcome(exitCode, errorOutput)
    }

    /** Reads [pfd] to EOF, keeping only the last [MAX_TAIL_LINES] lines. Closing the stream closes
     *  the file descriptor with it, so the pipe is released exactly once. */
    private fun drainTail(pfd: ParcelFileDescriptor?): List<String> {
        val tail = ArrayDeque<String>()
        try {
            pfd ?: return emptyList()
            ParcelFileDescriptor.AutoCloseInputStream(pfd).use { input ->
                input.bufferedReader(Charsets.UTF_8).forEachLine { line ->
                    if (tail.size == MAX_TAIL_LINES) tail.removeFirst()
                    tail.addLast(line)
                }
            }
        } catch (@Suppress("UNUSED_PARAMETER") e: Exception) {
            // A pipe that died mid-read just means short output — nothing to log.
        }
        return tail.toList()
    }

    /** Runs [command] and returns just its exit code, or null if it couldn't run at all. */
    fun runCommandForResult(command: String): Int? = runCommandForOutput(command).exitCode

    /** Runs [command] without caring about its exit code — issuing it is enough for callers like
     *  [com.tooler.app.util.StatusBarFlags]. False only if it couldn't be run at all. */
    fun runCommand(command: String): Boolean = runCommandForResult(command) != null
}
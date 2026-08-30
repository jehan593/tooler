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
 * Thin wrapper over the Shizuku API for running privileged shell commands — modified from
 * essentials' `ShizukuUtils.kt` (MIT), trimmed to the subset Tooler needs (no root path, no
 * permission self-granting, no "stop Shizuku" plumbing).
 *
 * What Shizuku buys here: Lock Quick Settings needs to run `cmd statusbar send-disable-flag`
 * (see [StatusBarFlags]), and that command is only callable by someone holding the `STATUS_BAR`
 * permission — a platform/signature permission that, like `WRITE_SECURE_SETTINGS`, no normal app
 * can request. Unlike `WRITE_SECURE_SETTINGS` there's no `pm grant` back door either — signature
 * permissions can't be granted to ordinary apps at all, so shell access is the only route. Shizuku
 * is the standard way to get it without root: the Shizuku app (once started, via adb or root)
 * exposes the device shell's uid, and grants per-app permission to run commands as it. When granted
 * through adb, commands run as the shell user; through root, as root — either uid holds STATUS_BAR,
 * so the mechanism works the same way in both modes.
 *
 * The binder reference is cached exactly like essentials does it: the API only pushes a fresh
 * binder into `Shizuku.getBinder()` when the server (re)attaches to this process
 * (via `rikka.shizuku.ShizukuProvider`), so [ToolerApp] registers the received/dead listeners below
 * and this object re-reads/caches on demand rather than trusting a static that can go stale when
 * the Shizuku app is restarted. Every is-available/is-granted call is also a live check, never a
 * cached answer — same "read the system, don't trust memory" rule as the rest of this app.
 *
 * `moe.shizuku.server.IShizukuService` (the AIDL stub used for `newProcess`) ships inside
 * `dev.rikka.shizuku:api`'s transitive `dev.rikka.shizuku:aidl` dependency — see app/build.gradle.kts.
 */
object ShizukuUtils {
    private const val TAG = "ShizukuUtils"

    private var binder: IBinder? = null

    // Bounds the amount of command output we ever retain (the offending line of a failed command is
    // almost always near the end), so a chatty wrapper like `cmd statusbar` can't balloon memory.
    private const val MAX_TAIL_LINES = 40
    private const val MAX_TAIL_CHARS = 600

    /** Request code for the shell-access grant dialog; only used to match results back to requests. */
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

    /** Shows the one-time "grant Tooler shell access?" dialog. Result arrives through an
     *  `Shizuku.OnRequestPermissionResultListener` — MainActivity registers one to refresh its UI. */
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
     * string has the same light sanitization as aShellYou's executor (`trim`, plus dropping a
     * pasted `adb shell ` prefix) so users can paste an adb-style command verbatim into a tile.
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

        // Standard java.lang.Process hygiene for the remote equivalent: read both streams on their
        // own threads *before* waitFor, or a command emitting more than the OS pipe buffer blocks
        // forever (process waiting on write, us waiting on the process).
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

    /**
     * Runs [command] as the shell user and returns just its exit code — null if the command
     * couldn't be run at all. Carries the same output-draining as [runCommandForOutput]; callers
     * that only need to know whether the command was issued (like `StatusBarFlags`) use
     * [runCommand] instead, which is exit-code-agnostic by design.
     */
    fun runCommandForResult(command: String): Int? = runCommandForOutput(command).exitCode

    /**
     * Runs [command] as the shell user without caring about its exit code — a started process is
     * "success" for callers like [com.tooler.app.util.StatusBarFlags] that just need the command
     * issued. Returns false only if the command couldn't be run at all (not bound, not granted,
     * binder died mid-run).
     */
    fun runCommand(command: String): Boolean = runCommandForResult(command) != null
}
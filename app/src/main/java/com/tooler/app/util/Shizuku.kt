package com.tooler.app.util

import android.content.pm.PackageManager
import android.os.IBinder
import android.os.RemoteException
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku

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
    private var binder: IBinder? = null

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
     * Runs [command] as the shell user. Returns success/failure rather than throwing — callers
     * gate on [isGranted] first anyway; this is just the last line of defense, same shape as
     * [com.tooler.app.tiles.advanceChargingMode]'s guarded write.
     */
    fun runCommand(command: String): Boolean {
        if (!isBinderAlive || !isGranted()) return false
        val process = try {
            IShizukuService.Stub.asInterface(binder)
                ?.newProcess(arrayOf("sh", "-c", command), null, "/")
        } catch (e: RemoteException) {
            null
        } ?: return false
        return try {
            process.waitFor()
            true
        } catch (e: RemoteException) {
            false
        }
    }
}
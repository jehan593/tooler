package com.tooler.app.util

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.view.accessibility.AccessibilityManager
import androidx.core.content.ContextCompat

/** Checked live against the system on every read — nothing here is cached or persisted. */
fun isAccessibilityServiceEnabled(context: Context, serviceClass: Class<*>): Boolean {
    val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        .any {
            it.resolveInfo.serviceInfo.packageName == context.packageName &&
                it.resolveInfo.serviceInfo.name == serviceClass.name
        }
}

/** Do Not Disturb / Notification Policy access — required before RINGER_MODE_SILENT can be set. */
fun hasNotificationPolicyAccess(context: Context): Boolean {
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    return manager.isNotificationPolicyAccessGranted
}

/**
 * Required to write the charging-optimization Settings.Secure keys (see ChargeOptimization.kt).
 * Unlike every other permission this app checks, there is no Settings screen that grants this one
 * — it can only be flipped via `pm grant`, which is what [grantWriteSecureSettings] runs as the
 * Shizuku user (the exact command a computer's `adb shell pm grant` would issue).
 */
fun hasWriteSecureSettings(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_SECURE_SETTINGS) ==
        PackageManager.PERMISSION_GRANTED

/**
 * Grants [hasWriteSecureSettings] through Shizuku — same effect as running
 * `adb shell pm grant <package> android.permission.WRITE_SECURE_SETTINGS` from a computer, so the
 * app can now ask for this without one. Returns whether the grant landed (re-read live, so a failed
 * or refused command yields false). Requires Shizuku to be running and this app granted shell access
 * — [ShizukuUtils.runCommandForResult] returns null in its absence.
 */
fun grantWriteSecureSettings(context: Context): Boolean {
    val granted =
        ShizukuUtils.runCommandForResult(
            "pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS"
        ) == 0
    return granted && hasWriteSecureSettings(context)
}

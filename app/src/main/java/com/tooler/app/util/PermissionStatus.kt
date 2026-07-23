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
 * — it can only be flipped via `adb shell pm grant` from a computer, so this is purely a read.
 */
fun hasWriteSecureSettings(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_SECURE_SETTINGS) ==
        PackageManager.PERMISSION_GRANTED

package com.tooler.app.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/** Opens the Shizuku app if installed, otherwise the Shizuku website. Shared by MainActivity and
 *  the custom-tile screen. */
fun openShizuku(context: Context) {
    val launchIntent = context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
    if (launchIntent != null) {
        context.startActivity(launchIntent)
    } else {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/")))
    }
}

/** No-Shizuku fallback for the WRITE_SECURE_SETTINGS grant: copies the `adb shell pm grant`
 *  command to the clipboard so it can be run from a computer. */
fun copyWriteSecureSettingsGrantCommand(context: Context) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(
        ClipData.newPlainText(
            "adb command",
            "adb shell pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS"
        )
    )
    Toast.makeText(context, "Command copied", Toast.LENGTH_SHORT).show()
}
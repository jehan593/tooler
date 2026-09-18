package com.tooler.app.tiles

/** An unlocked, interactive device must never retain our Quick Settings disable flag. */
internal fun shouldLockQuickSettings(enabled: Boolean, interactive: Boolean, keyguardLocked: Boolean): Boolean =
    enabled && (!interactive || keyguardLocked)

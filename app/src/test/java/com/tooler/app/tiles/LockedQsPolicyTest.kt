package com.tooler.app.tiles

import org.junit.Assert.*
import org.junit.Test

class LockedQsPolicyTest {
    @Test fun unlockedInteractiveDeviceIsNeverBlocked() {
        assertFalse(shouldLockQuickSettings(true, true, false))
    }

    @Test fun turningFeatureOffClearsEveryDeviceState() {
        for (interactive in listOf(false, true)) {
            for (locked in listOf(false, true)) {
                assertFalse(shouldLockQuickSettings(false, interactive, locked))
            }
        }
    }

    @Test fun delayedScreenOffWorkMustUseCurrentUnlockState() {
        assertTrue(shouldLockQuickSettings(true, false, true))
        assertFalse(shouldLockQuickSettings(true, true, false))
    }

    @Test fun screenOnDoesNotReleasePanelBeforeAuthentication() {
        assertTrue(shouldLockQuickSettings(true, true, true))
    }
}

package com.tooler.app.tiles

import org.junit.Assert.*
import org.junit.Test

class ColorCorrectionStateTest {
    @Test fun forcesGrayscaleAndRestoresEveryPriorCorrectionState() {
        for (enabled in listOf(null, "0", "1")) {
            for (mode in listOf(null, "-1", "0", "11", "12", "13", "99")) {
                val original = ColorCorrectionState(enabled, mode)
                if (original.isGrayscale) continue
                val on = original.grayscaleOn()
                assertTrue(on.isGrayscale)
                assertEquals(original, on.grayscaleOff(original))
            }
        }
    }

    @Test fun disabledMonochromeIsNotGrayscale() {
        assertFalse(ColorCorrectionState("0", "0").isGrayscale)
        assertFalse(ColorCorrectionState(null, "0").isGrayscale)
        assertFalse(ColorCorrectionState("1", "12").isGrayscale)
        assertTrue(ColorCorrectionState("1", "0").isGrayscale)
    }

    @Test fun manuallyEnabledGrayscaleCanBeDisabledWithoutASnapshot() {
        assertEquals(ColorCorrectionState("0", "0"),
            ColorCorrectionState("1", "0").grayscaleOff(null))
    }

    @Test fun invalidActiveSnapshotCannotKeepGrayscaleOn() {
        val on = ColorCorrectionState("1", "0")
        assertFalse(on.grayscaleOff(on).isGrayscale)
    }
}

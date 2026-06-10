package org.autojs.autojs.runtime.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureSessionOptionsTest {

    @Test
    fun presetsNormalizeAliases() {
        assertEquals(Images.CaptureSessionOptions.PRESET_SINGLE, Images.CaptureSessionOptions.forPreset(null).preset)
        assertEquals(Images.CaptureSessionOptions.PRESET_OCR, Images.CaptureSessionOptions.forPreset("continuous-ocr").preset)
        assertEquals(Images.CaptureSessionOptions.PRESET_COLOR, Images.CaptureSessionOptions.forPreset("high_frequency_color").preset)
        assertEquals(Images.CaptureSessionOptions.PRESET_LOW_POWER, Images.CaptureSessionOptions.forPreset("background-monitor").preset)
    }

    @Test
    fun constructorClampsLifecycleValues() {
        val options = Images.CaptureSessionOptions("unknown", 0, -1L, -1L, false, false)

        assertEquals(Images.CaptureSessionOptions.PRESET_SINGLE, options.preset)
        assertEquals(1, options.cacheSize)
        assertEquals(1L, options.defaultTimeoutMillis)
        assertEquals(0L, options.minIntervalMillis)
        assertFalse(options.logErrors)
        assertFalse(options.autoRequest)
        assertTrue(options.toMap().containsKey("cacheSize"))
    }
}

package app.chompass.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Live-scanner frame picking (#24 follow-up): a frame may decode several
 * texts (zxing result order unspecified) and a junk / half-read frame (internal
 * factory codes, partial EANs) must never be handed to the OFF lookup — the
 * scanner keeps scanning until a frame yields a code that normalizes.
 * Mirrored in the PWA (`barcode-detect.test.js` `pickNormalizable`).
 */
class BarcodeScannerContentTest {
    @Test
    fun junkOnlyFrame_returnsNull() {
        // Regression for the Bolognese repro: a frame with only non-normalizable
        // decodes used to stop the scan with "could not be read".
        assertNull(pickFirstNormalizable(listOf("1111201I", "https://brand.example.com/recipes")))
    }

    @Test
    fun mixedFrame_prefersNormalizableOverJunk() {
        // Jar case: EAN-13 + internal DataMatrix in the same frame.
        assertEquals("9339687206605", pickFirstNormalizable(listOf("1111201I", "9339687206605")))
        assertEquals("9339687206605", pickFirstNormalizable(listOf("9339687206605", "1111201I")))
    }

    @Test
    fun gs1PrefixedText_returnsRawText() {
        // The raw decoded text is passed on; lookup() re-normalizes (idempotent).
        val raw = "(01)09400597028233(15)260821(10)96735717"
        assertEquals(raw, pickFirstNormalizable(listOf(raw)))
    }

    @Test
    fun twoNormalizableCodes_firstWins() {
        assertEquals("9339687206605", pickFirstNormalizable(listOf("9339687206605", "9421011990608")))
    }

    @Test
    fun emptyOrBlankTexts_returnsNull() {
        assertNull(pickFirstNormalizable(emptyList()))
        assertNull(pickFirstNormalizable(listOf("  ")))
    }
}

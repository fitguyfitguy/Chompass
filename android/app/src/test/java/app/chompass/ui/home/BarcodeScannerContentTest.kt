package app.chompass.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import zxingcpp.BarcodeReader

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

    @Test
    fun mixedFrame_prefersEanOverQr() {
        // Jar case (#24 follow-up): a GS1 Digital Link QR (case-level GTIN) and
        // the EAN-13 in the same frame — the retail 1D code is the one OFF
        // indexes. Both orders must pick the EAN.
        val qr = BarcodeReader.Format.QR_CODE to "https://id.gs1.org/01/19300645111122"
        val ean = BarcodeReader.Format.EAN_13 to "9300645111125"
        assertEquals("9300645111125", pickPreferredCode(listOf(qr, ean)))
        assertEquals("9300645111125", pickPreferredCode(listOf(ean, qr)))
    }

    @Test
    fun qrOnlyFrame_usesQrGtin() {
        // 2D-only frames keep working (e.g. the mince DataMatrix from #24). The
        // raw GS1 text is returned; lookup() normalizes it to the GTIN.
        val raw = "https://id.gs1.org/01/19300645111122"
        assertEquals(
            raw,
            pickPreferredCode(listOf(BarcodeReader.Format.QR_CODE to raw)),
        )
    }

    @Test
    fun junkPlusEan_prefersEan() {
        // Regression from the 3.16.0 fix: junk-only frames still return null.
        val junk = BarcodeReader.Format.DATA_MATRIX to "1111201I"
        val ean = BarcodeReader.Format.EAN_13 to "9300645111125"
        assertEquals("9300645111125", pickPreferredCode(listOf(junk, ean)))
        assertNull(pickPreferredCode(listOf(junk)))
    }

    @Test
    fun twoEans_firstWins() {
        // Order preserved within a tier.
        assertEquals(
            "9339687206605",
            pickPreferredCode(
                listOf(
                    BarcodeReader.Format.EAN_13 to "9339687206605",
                    BarcodeReader.Format.EAN_13 to "9421011990608",
                ),
            ),
        )
    }
}

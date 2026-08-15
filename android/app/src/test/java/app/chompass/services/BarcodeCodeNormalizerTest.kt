package app.chompass.services

import app.chompass.parity.ParityFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 2D matrix-code (QR / DataMatrix) support: raw decoded text → OFF lookup code.
 * Table-driven over the shared fixture `testdata/parity/barcode-codes.json`
 * (mirrored in the PWA `barcode-code.test.js`), plus local extras for cases
 * the fixture does not pin.
 */
class BarcodeCodeNormalizerTest {
    @Test
    fun sharedFixture_casesMatchPwa() {
        val fixture = ParityFixtures.readJson("barcode-codes.json")
        val cases = fixture.getJSONArray("cases")
        for (i in 0 until cases.length()) {
            val case = cases.getJSONObject(i)
            val raw = case.getString("raw")
            val expected = if (case.isNull("expected")) null else case.getString("expected")
            val actual = BarcodeCodeNormalizer.normalize(raw)
            if (expected == null) {
                assertNull("raw=$raw", actual)
            } else {
                assertEquals("raw=$raw", expected, actual)
            }
        }
    }

    @Test
    fun localExtras_trimmingAndUrlVariants() {
        assertEquals("9339687206605", BarcodeCodeNormalizer.normalize("  9339687206605  "))
        assertEquals("9400597028233", BarcodeCodeNormalizer.normalize("(01)09400597028233"))
        assertEquals("9400597028233", BarcodeCodeNormalizer.normalize("?01=09400597028233"))
        assertEquals("9400597028233", BarcodeCodeNormalizer.normalize("https://id.gs1.org/01/09400597028233"))
        assertEquals("9400597028233", BarcodeCodeNormalizer.normalize("https://example.com/?gtin=9400597028233"))
        assertEquals("3017620422003", BarcodeCodeNormalizer.normalize("https://world.openfoodfacts.org/product/3017620422003?foo=bar"))
    }

    @Test
    fun localExtras_1dPassthrough() {
        // UPC-A 12 and EAN-8 8 keep current 1D behavior.
        assertEquals("036000291452", BarcodeCodeNormalizer.normalize("036000291452"))
        assertEquals("87123456", BarcodeCodeNormalizer.normalize("87123456"))
        // Bare GTIN-14 passes through as-is (rule 2; only GS1-form drops the leading zero).
        assertEquals("09400597028233", BarcodeCodeNormalizer.normalize("09400597028233"))
    }

    @Test
    fun localExtras_nonProductCodes() {
        assertNull(BarcodeCodeNormalizer.normalize(" "))
        assertNull(BarcodeCodeNormalizer.normalize("123"))
        assertNull(BarcodeCodeNormalizer.normalize("9400597028233x"))
        assertNull(BarcodeCodeNormalizer.normalize("https://id.gs1.org/01/09400597028234?17=260821"))
        assertNull(BarcodeCodeNormalizer.normalize("https://example.com/?gtin=9400597028234"))
    }
}

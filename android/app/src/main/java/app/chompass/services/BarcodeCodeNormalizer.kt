package app.chompass.services

/**
 * Normalizes raw decoded barcode / 2D matrix-code text into a code that can be
 * looked up on Open Food Facts (GTIN/EAN family), or null when the text is not
 * a product code (internal factory codes, brand URLs, junk). `null` means
 * "not a product code" — callers must not hit the network.
 *
 * Mirrored 1:1 in the PWA (`web/app/src/lib/chompass-core/barcode-code.js`);
 * both sides are pinned to `testdata/parity/barcode-codes.json`.
 *
 * Rules, first match wins:
 *  1. Trim; empty → null.
 *  2. Bare 8–14 digit strings with a valid mod-10 check digit (EAN-8, UPC-A,
 *     EAN-13, GTIN-14) pass through unchanged (current 1D behavior).
 *  3. GS1 AI (01) prefix — HRI `(01)…` (wasm default) or raw `01…` (Android
 *     `TextMode.PLAIN`) — followed by 14 digits → GTIN; a leading zero is
 *     dropped to get the 13-digit EAN-13, otherwise the 14 digits are kept
 *     (case-level GTIN; OFF will 404 honestly if not indexed).
 *  4. URL forms: GS1 Digital Link `/01/<14 digits>` (path segment, `?01=` or
 *     `?gtin=` query) and OFF-style `/product/<digits>` → same GTIN handling.
 *  5. Anything else → null.
 */
object BarcodeCodeNormalizer {
    private val BARE_DIGITS = Regex("""\d{8,14}""")
    private val GS1_PREFIX = Regex("""^(?:\(01\)|01)(\d{14})""")
    private val DIGITAL_LINK_PATH = Regex("""/01/(\d{14})""")
    private val DIGITAL_LINK_QUERY_AI = Regex("""[?&]01=(\d{14})""")
    private val DIGITAL_LINK_QUERY_GTIN = Regex("""[?&]gtin=(\d{8,14})""")
    private val OFF_PRODUCT_PATH = Regex("""/product/(\d{8,14})""")

    fun normalize(raw: String): String? {
        val text = raw.trim()
        if (text.isEmpty()) return null

        if (text.matches(BARE_DIGITS) && validCheckDigit(text)) return text

        GS1_PREFIX.find(text)?.let { match ->
            return normalizeGtin(match.groupValues[1])
        }

        val urlMatch = listOf(
            DIGITAL_LINK_PATH,
            DIGITAL_LINK_QUERY_AI,
            DIGITAL_LINK_QUERY_GTIN,
            OFF_PRODUCT_PATH,
        ).firstNotNullOfOrNull { it.find(text) }
        urlMatch?.let { return normalizeGtin(it.groupValues[1]) }

        return null
    }

    /** Check-digit-validates the extracted digits, then applies GTIN → EAN-13 leading-zero drop. */
    private fun normalizeGtin(digits: String): String? {
        if (!validCheckDigit(digits)) return null
        return if (digits.length == 14 && digits.startsWith('0')) digits.substring(1) else digits
    }

    /**
     * GS1 mod-10 check digit (weights 3/1 from the right; same algorithm for
     * EAN-8 / UPC-A / EAN-13 / GTIN-14). Guards against junk digit strings
     * pulled out of URLs.
     */
    private fun validCheckDigit(digits: String): Boolean {
        if (digits.length < 2) return false
        var sum = 0
        var weight = 3
        for (i in digits.length - 2 downTo 0) {
            sum += (digits[i] - '0') * weight
            weight = if (weight == 3) 1 else 3
        }
        return ((10 - (sum % 10)) % 10) == (digits.last() - '0')
    }
}

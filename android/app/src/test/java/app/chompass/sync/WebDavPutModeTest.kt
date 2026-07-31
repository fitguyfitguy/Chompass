package app.chompass.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebDavPutModeTest {
    @Test
    fun createOnlyWhenNotFound() {
        assertEquals(WebDavPutMode.CreateOnly, webDavPutMode(etag = null, notFound = true))
        assertEquals(WebDavPutMode.CreateOnly, webDavPutMode(etag = "\"x\"", notFound = true))
    }

    @Test
    fun unconditionalWhenExistsWithoutEtag() {
        // Second sync with no ETag must not use If-None-Match: *
        assertEquals(WebDavPutMode.Unconditional, webDavPutMode(etag = null, notFound = false))
        assertEquals(WebDavPutMode.Unconditional, webDavPutMode(etag = "  ", notFound = false))
    }

    @Test
    fun ifMatchWhenEtagPresent() {
        val mode = webDavPutMode(etag = "\"abc\"", notFound = false)
        assertTrue(mode is WebDavPutMode.IfMatch)
        assertEquals("\"abc\"", (mode as WebDavPutMode.IfMatch).etag)
    }

    @Test
    fun stripsWeakEtagPrefixForIfMatch() {
        val mode = webDavPutMode(etag = "W/\"abc\"", notFound = false)
        assertEquals("\"abc\"", (mode as WebDavPutMode.IfMatch).etag)
        assertEquals("\"abc\"", normalizeEtagForIfMatch("W/\"abc\""))
        assertEquals("\"abc\"", normalizeEtagForIfMatch("w/\"abc\""))
    }
}

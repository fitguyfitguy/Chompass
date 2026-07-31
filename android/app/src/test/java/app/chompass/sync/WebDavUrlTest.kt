package app.chompass.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class WebDavUrlTest {
    @Test
    fun addsHttpsWhenSchemeMissing() {
        assertEquals(
            "https://u123-sub1.your-storagebox.de/sync.json",
            normalizeWebDavUrl("u123-sub1.your-storagebox.de/sync.json"),
        )
    }

    @Test
    fun preservesHttps() {
        assertEquals(
            "https://u123-sub1.your-storagebox.de/sync.json",
            normalizeWebDavUrl("https://u123-sub1.your-storagebox.de/sync.json"),
        )
    }

    @Test
    fun preservesExplicitHttp() {
        assertEquals(
            "http://192.168.1.10/chompass/sync.json",
            normalizeWebDavUrl("http://192.168.1.10/chompass/sync.json"),
        )
    }

    @Test
    fun collapsesStackedHttps() {
        assertEquals(
            "https://u123-sub1.your-storagebox.de/sync.json",
            normalizeWebDavUrl("https://https://u123-sub1.your-storagebox.de/sync.json"),
        )
    }

    @Test
    fun collapsesHttpThenHttpsToHttps() {
        assertEquals(
            "https://u123-sub1.your-storagebox.de/sync.json",
            normalizeWebDavUrl("http://https://u123-sub1.your-storagebox.de/sync.json"),
        )
    }

    @Test
    fun collapsesHttpsThenHttpToHttps() {
        assertEquals(
            "https://u123-sub1.your-storagebox.de/sync.json",
            normalizeWebDavUrl("https://http://u123-sub1.your-storagebox.de/sync.json"),
        )
    }

    @Test
    fun trimsWhitespace() {
        assertEquals(
            "https://example.com/sync.json",
            normalizeWebDavUrl("  https://example.com/sync.json  "),
        )
    }

    @Test
    fun emptyStaysEmpty() {
        assertEquals("", normalizeWebDavUrl("   "))
    }
}

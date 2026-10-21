package app.chompass.utils

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Codeberg #43: on API 26-32 a blank (system) language must never replace
 * Configuration.locales with an empty list — locales[0] was null and crashed
 * Home. The legacy path now routes through the [LocaleHelper.legacyTag] cache
 * and [LocaleHelper.wrapIfLegacy], which falls back to identity instead of
 * ever setting an empty locale list.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = Application::class)
class LocaleHelperTest {
    @Test
    fun germanTag_selectsGerman() {
        val list = localeListForLanguageTag("de")
        assertEquals("de", list[0].language)
    }

    @Test
    fun repeatedTags_deduplicate() {
        // LocaleList rejects duplicates ("list[1] is a repetition"); the same
        // locale twice must collapse to one entry, not crash construction.
        val list = localeListForLanguageTag("de-DE,de-DE")
        assertEquals(1, list.size())
        assertEquals("de", list[0].language)
    }

    @Test
    fun garbageTags_parseToEmpty() {
        // Lenient parsing maps ill-formed tags to identical empty locales;
        // they must be dropped so the result is empty (identity wrap) rather
        // than a list the framework rejects.
        assertTrue(localeListForLanguageTag("!!!,,,").isEmpty)
    }

    @Test
    fun applyBlank_onApi29_clearsCache() {
        val app = RuntimeEnvironment.getApplication()
        LocaleHelper.apply(app, "")
        assertNull("blank apply must reset to follow-system (no wrap)", LocaleHelper.legacyTag)
        // And wrapping with a cleared cache is the identity: the context is
        // never handed an empty locale list.
        assertSame(app, LocaleHelper.wrapIfLegacy(app))
    }

    @Test
    fun wrapIfLegacy_blankOrWhitespaceCache_isIdentity() {
        val app = RuntimeEnvironment.getApplication()
        LocaleHelper.legacyTag = null
        assertSame(app, LocaleHelper.wrapIfLegacy(app))
        LocaleHelper.legacyTag = "  "
        try {
            assertSame(app, LocaleHelper.wrapIfLegacy(app))
        } finally {
            LocaleHelper.legacyTag = null
        }
    }

    @Test
    fun wrapIfLegacy_germanTag_wrapsConfiguration() {
        val app = RuntimeEnvironment.getApplication()
        LocaleHelper.legacyTag = "de"
        try {
            val wrapped = LocaleHelper.wrapIfLegacy(app)
            assertEquals("de", wrapped.resources.configuration.locales[0].language)
        } finally {
            LocaleHelper.legacyTag = null
        }
    }

    @Test
    fun wrapIfLegacy_unparseableTag_isIdentity() {
        val app = RuntimeEnvironment.getApplication()
        LocaleHelper.legacyTag = "!!!,,,"
        try {
            // An empty parsed list must fall back to identity, never
            // setLocales(empty) (#43).
            assertSame(app, LocaleHelper.wrapIfLegacy(app))
        } finally {
            LocaleHelper.legacyTag = null
        }
    }
}

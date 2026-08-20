package app.chompass.utils

import android.app.Application
import android.os.LocaleList
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Codeberg #43: on API 26-32, applying a blank (system) language must not
 * replace Configuration.locales with an empty list. That made locales[0]
 * null and crashed Home on the week strip.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = Application::class)
class LocaleHelperTest {
    @Test
    fun blankTag_isNeverEmpty() {
        val list = localeListForLanguageTag("")
        assertTrue(list.size() > 0)
        assertNotNull(list[0])
    }

    @Test
    fun whitespaceTag_isNeverEmpty() {
        val list = localeListForLanguageTag("  ")
        assertTrue(list.size() > 0)
        assertNotNull(list[0])
    }

    @Test
    fun germanTag_selectsGerman() {
        val list = localeListForLanguageTag("de")
        assertEquals("de", list[0].language)
    }

    @Test
    fun applyBlank_onApi29_doesNotWipeLocales() {
        val ctx = RuntimeEnvironment.getApplication()
        ctx.resources.configuration.setLocales(LocaleList())
        LocaleHelper.apply(ctx, "")
        val locales = ctx.resources.configuration.locales
        assertTrue("blank apply must not leave an empty locale list", locales.size() > 0)
        assertNotNull(locales[0])
    }

    @Test
    fun systemLocaleList_hasAFirstLocale() {
        val list = systemLocaleList()
        assertTrue(list.size() > 0)
        assertNotNull(list[0])
        // Fallback path still produces a usable Locale for DateTimeFormatter.
        java.time.format.DateTimeFormatter.ofPattern("EEEEE", list[0] ?: Locale.US)
            .format(java.time.DayOfWeek.MONDAY)
    }
}

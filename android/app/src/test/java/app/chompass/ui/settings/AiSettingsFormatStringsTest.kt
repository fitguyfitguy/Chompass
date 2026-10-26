package app.chompass.ui.settings

import android.app.Application
import app.chompass.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Device-pass regression (2026-10-12): SettingsAiSection passed an
 * already-grouped String into a %1$,d pattern and crashed with
 * IllegalFormatConversionException at first composition. Pin the
 * argument TYPE of every new formatted AI-settings string: resource pattern
 * on the left, real call-site argument on the right.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class AiSettingsFormatStringsTest {
    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun defaultValueStrings_formatWithIntArgs() {
        assertEquals("180s (default)", context.getString(R.string.settings_ai_timeout_default, 180))
    }

    @Test
    fun keyTestFailed_formatsWithStringArg() {
        assertEquals("Key rejected: nope", context.getString(R.string.settings_ai_key_test_failed, "nope"))
    }
}

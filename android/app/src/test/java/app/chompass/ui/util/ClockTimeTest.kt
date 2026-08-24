package app.chompass.ui.util

import android.app.Application
import java.time.ZoneOffset
import java.util.Locale
import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings

/**
 * The shared clock formatter must never throw for a raw epoch millis (the
 * fasting crash class, 46accde4): it applies the system zone internally and
 * honors the device 24-hour clock setting.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = Application::class)
class ClockTimeTest {
    private val defaultZone = TimeZone.getDefault()
    private val defaultLocale = Locale.getDefault()

    @Before
    fun setUp() {
        // Deterministic: fixed zone (so the "2:30" instant is unambiguous) and
        // a fixed locale for the 12-hour AM/PM marker.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        Locale.setDefault(Locale.US)
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(defaultZone)
        Locale.setDefault(defaultLocale)
    }

    private fun epochFor(hour: Int, minute: Int): Long =
        java.time.LocalDateTime.of(2026, 7, 24, hour, minute)
            .toInstant(ZoneOffset.UTC)
            .toEpochMilli()

    @Test
    fun twentyFourHourDevice_formatsHHmm() {
        ShadowSettings.set24HourTimeFormat(true)
        val ctx = RuntimeEnvironment.getApplication()
        assertEquals("14:30", formatClockMillis(ctx, epochFor(14, 30)))
        assertEquals("20:00", formatClockMillis(ctx, epochFor(20, 0)))
    }

    @Test
    fun twelveHourDevice_formatsHmmA() {
        ShadowSettings.set24HourTimeFormat(false)
        val ctx = RuntimeEnvironment.getApplication()
        assertEquals("2:30 PM", formatClockMillis(ctx, epochFor(14, 30)))
        assertEquals("8:00 PM", formatClockMillis(ctx, epochFor(20, 0)))
        assertEquals("12:05 AM", formatClockMillis(ctx, epochFor(0, 5)))
    }
}

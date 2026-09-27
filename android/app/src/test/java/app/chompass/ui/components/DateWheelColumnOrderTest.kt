package app.chompass.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class DateWheelColumnOrderTest {
    @Test
    fun pattern_mapsFirstFieldLettersInOrder() {
        assertEquals(
            listOf(DateWheelColumn.MONTH, DateWheelColumn.DAY, DateWheelColumn.YEAR),
            dateWheelColumnOrderFromPattern("MMMM d, yyyy"),
        )
        assertEquals(
            listOf(DateWheelColumn.DAY, DateWheelColumn.MONTH, DateWheelColumn.YEAR),
            dateWheelColumnOrderFromPattern("d. MMMM y"),
        )
        assertEquals(
            listOf(DateWheelColumn.YEAR, DateWheelColumn.MONTH, DateWheelColumn.DAY),
            dateWheelColumnOrderFromPattern("y年M月d日"),
        )
    }

    @Test
    fun pattern_ignoresQuotedLiterals() {
        assertEquals(
            listOf(DateWheelColumn.DAY, DateWheelColumn.MONTH, DateWheelColumn.YEAR),
            dateWheelColumnOrderFromPattern("d 'M' MMMM y"),
        )
    }

    @Test
    fun locale_englishIsMonthDayYear() {
        assertEquals(
            listOf(DateWheelColumn.MONTH, DateWheelColumn.DAY, DateWheelColumn.YEAR),
            dateWheelColumnOrder(Locale.US),
        )
    }

    @Test
    fun locale_germanIsDayMonthYear() {
        assertEquals(
            listOf(DateWheelColumn.DAY, DateWheelColumn.MONTH, DateWheelColumn.YEAR),
            dateWheelColumnOrder(Locale.GERMANY),
        )
    }

    @Test
    fun locale_japaneseIsYearMonthDay() {
        assertEquals(
            listOf(DateWheelColumn.YEAR, DateWheelColumn.MONTH, DateWheelColumn.DAY),
            dateWheelColumnOrder(Locale.JAPAN),
        )
    }
}

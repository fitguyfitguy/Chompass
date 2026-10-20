package app.chompass.progress

import app.chompass.models.BodyMeasurement
import app.chompass.ui.progress.tapeBodyFatEntry
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

/** The tape-derived body-fat row carries the measurement's date, not "now". */
class TapeBodyFatEntryTest {
    @Test
    fun entryDateEqualsMeasurementDate() {
        val measurement = BodyMeasurement(
            date = Instant.parse("2026-08-01T08:30:00Z"),
            waistCm = 85.0,
            neckCm = 38.0,
            hipsCm = 95.0,
        )
        val entry = tapeBodyFatEntry(18.4, measurement)
        assertEquals(Instant.parse("2026-08-01T08:30:00Z"), entry.date)
        assertEquals(0.184, entry.bodyFatFraction, 1e-9)
    }
}

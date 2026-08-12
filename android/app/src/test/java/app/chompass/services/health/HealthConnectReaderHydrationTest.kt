package app.chompass.services.health

import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Volume
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Water → Health Connect mirror (Codeberg #9): reader (reinstall restore) side. */
class HealthConnectReaderHydrationTest {
    private fun reader(client: HealthConnectFakeClient) = HealthConnectReader(
        client = { client },
        granted = { emptySet() },
        stepsRead = "steps",
        exerciseRead = "exercise",
        sleepRead = "sleep",
        restingHrRead = "resting_hr",
        hydrationRead = "hydration",
        hasEnergyRead = { false },
    )

    private fun hydrationRecord(
        at: Instant,
        liters: Double,
        clientRecordId: String?,
        recordId: String = "",
    ) = HydrationRecord(
        startTime = at,
        startZoneOffset = null,
        endTime = at.plusSeconds(60),
        endZoneOffset = null,
        volume = Volume.liters(liters),
        metadata = when {
            recordId.isNotEmpty() -> Metadata.manualEntryWithId(recordId)
            clientRecordId != null -> Metadata.manualEntry(clientRecordId = clientRecordId)
            else -> Metadata.manualEntry()
        },
    )

    private val from = Instant.parse("2026-08-01T00:00:00Z")
    private val to = Instant.parse("2026-08-12T00:00:00Z")

    @Test
    fun readHydration_mapsOwnAndExternalRecords() = runBlocking {
        val client = HealthConnectFakeClient()
        val ownId = UUID.randomUUID()
        val at = Instant.parse("2026-08-12T09:00:00Z")
        client.readPages.add(
            listOf(
                hydrationRecord(at, 0.3, "${HealthConnectManager.CLIENT_PREFIX}$ownId"),
                hydrationRecord(at.plusSeconds(3600), 0.5, null),
                hydrationRecord(at.plusSeconds(7200), 0.2, null, recordId = "hc-ext-1"),
            ) to null,
        )

        val out = reader(client).readHydration(from, to)!!

        assertEquals(3, out.size)
        assertEquals(at, out[0].time)
        assertEquals(300, out[0].milliliters)
        assertEquals("${HealthConnectManager.CLIENT_PREFIX}$ownId", out[0].clientRecordId)
        assertEquals("", out[0].recordId)
        assertEquals(500, out[1].milliliters)
        assertNull(out[1].clientRecordId)
        assertEquals(200, out[2].milliliters)
        assertEquals("hc-ext-1", out[2].recordId)
        assertNull(out[2].clientRecordId)
    }

    @Test
    fun readHydration_followsPageTokenUntilExhausted() = runBlocking {
        val client = HealthConnectFakeClient()
        val at = Instant.parse("2026-08-12T09:00:00Z")
        client.readPages.add(
            listOf(hydrationRecord(at, 0.25, null)) to "tok-1",
        )
        client.readPages.add(
            listOf(hydrationRecord(at.plusSeconds(7200), 0.75, null)) to null,
        )

        val out = reader(client).readHydration(from, to)!!

        assertEquals(2, out.size)
        assertEquals(250, out[0].milliliters)
        assertEquals(750, out[1].milliliters)
        // The second request carried the first page's token.
        assertEquals(2, client.readRequests.size)
        assertEquals("tok-1", client.readRequests[1].pageToken)
    }

    @Test
    fun readHydration_returnsNullWhenClientUnavailable() = runBlocking {
        val reader = HealthConnectReader(
            client = { null },
            granted = { emptySet() },
            stepsRead = "steps",
            exerciseRead = "exercise",
            sleepRead = "sleep",
            restingHrRead = "resting_hr",
            hydrationRead = "hydration",
            hasEnergyRead = { false },
        )
        assertNull(reader.readHydration(from, to))
    }

    @Test
    fun readHydration_returnsNullOnReadFailure() = runBlocking {
        val client = HealthConnectFakeClient()
        client.readError = IllegalStateException("binder died")

        assertNull(reader(client).readHydration(from, to))
    }

    @Test
    fun readHydration_returnsNullWhenPaginationFailsMidway() = runBlocking {
        // Page 1 succeeds with a next-token, but no page 2 is scripted: the
        // reader must return null (leaving the one-shot restore flag unset so
        // the retry re-reads everything) instead of trusting a partial history.
        val client = HealthConnectFakeClient()
        val at = Instant.parse("2026-08-12T09:00:00Z")
        client.readPages.add(
            listOf(hydrationRecord(at, 0.25, null)) to "tok-1",
        )

        assertNull(reader(client).readHydration(from, to))
        assertTrue(client.readRequests.size >= 2)
    }
}

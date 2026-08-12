package app.chompass.services.health

import androidx.health.connect.client.records.HydrationRecord
import app.chompass.models.WaterEntry
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Water → Health Connect mirror (Codeberg #9): writer side. */
class HealthConnectWriterHydrationTest {
    private fun waterEntry(
        milliliters: Int,
        date: Instant = Instant.parse("2026-08-12T10:30:00Z"),
        id: UUID = UUID.randomUUID(),
    ) = WaterEntry(id = id, date = date, milliliters = milliliters)

    @Test
    fun writeHydration_insertsTaggedVolumeRecordWithOneMinuteSpan() = runBlocking {
        val client = HealthConnectFakeClient()
        val writer = HealthConnectWriter(client = { client })
        val id = UUID.randomUUID()
        val at = Instant.parse("2026-08-12T10:30:00Z")

        assertTrue(writer.writeHydration(waterEntry(milliliters = 300, date = at, id = id)))

        val record = client.inserted.single() as HydrationRecord
        assertEquals(at, record.startTime)
        assertEquals(at.plusSeconds(60), record.endTime)
        assertEquals(0.3, record.volume.inLiters, 1e-9)
        assertEquals("${HealthConnectManager.CLIENT_PREFIX}$id", record.metadata.clientRecordId)
    }

    @Test
    fun writeHydration_rejectsNonPositiveMilliliters() = runBlocking {
        val client = HealthConnectFakeClient()
        val writer = HealthConnectWriter(client = { client })

        assertFalse(writer.writeHydration(waterEntry(milliliters = 0)))
        assertFalse(writer.writeHydration(waterEntry(milliliters = -50)))
        assertTrue(client.inserted.isEmpty())
    }

    @Test
    fun writeHydration_rejectsFutureDatedEntries() = runBlocking {
        val client = HealthConnectFakeClient()
        val writer = HealthConnectWriter(client = { client })

        val future = Instant.now().plusSeconds(60)
        assertFalse(writer.writeHydration(waterEntry(milliliters = 250, date = future)))
        assertTrue(client.inserted.isEmpty())
    }

    @Test
    fun writeHydration_returnsFalseWhenClientUnavailable() = runBlocking {
        val writer = HealthConnectWriter(client = { null })
        assertFalse(writer.writeHydration(waterEntry(milliliters = 250)))
    }

    @Test
    fun writeHydration_insertFailureReturnsFalse() = runBlocking {
        val client = HealthConnectFakeClient()
        client.insertError = IllegalStateException("binder died")
        val writer = HealthConnectWriter(client = { client })

        assertFalse(writer.writeHydration(waterEntry(milliliters = 250)))
        assertTrue(client.inserted.isEmpty())
    }

    @Test
    fun deleteHydration_targetsOwnTagOnHydrationRecord() = runBlocking {
        val client = HealthConnectFakeClient()
        val writer = HealthConnectWriter(client = { client })
        val id = UUID.randomUUID()

        assertTrue(writer.deleteHydration(id))

        val (recordType, clientRecordIds) = client.deleted.single()
        assertEquals(HydrationRecord::class, recordType)
        assertEquals(listOf("${HealthConnectManager.CLIENT_PREFIX}$id"), clientRecordIds)
    }

    @Test
    fun deleteHydration_returnsFalseWhenClientUnavailable() = runBlocking {
        val writer = HealthConnectWriter(client = { null })
        assertFalse(writer.deleteHydration(UUID.randomUUID()))
        assertFalse(writer.deleteHydration(UUID.randomUUID()))
    }

    @Test
    fun deleteHydration_deleteFailureReturnsFalse() = runBlocking {
        val client = HealthConnectFakeClient()
        client.deleteError = IllegalStateException("binder died")
        val writer = HealthConnectWriter(client = { client })

        assertFalse(writer.deleteHydration(UUID.randomUUID()))
        assertTrue(client.deleted.isEmpty())
    }
}

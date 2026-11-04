package com.health.openscale.scales

import com.health.openscale.core.bluetooth.data.ScaleMeasurement
import com.health.openscale.core.bluetooth.data.ScaleUser
import com.health.openscale.core.bluetooth.scales.BeurerSanitasHandler
import com.health.openscale.core.bluetooth.scales.ScaleDeviceHandler
import com.health.openscale.core.data.WeightUnit
import com.health.openscale.core.service.ScannedDeviceInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.UUID

/**
 * Replays the Beurer BF700/800 handshake from the shared fixture
 * (android/app/src/test/resources/openscale_beurer_frames.json) through the
 * vendored [BeurerSanitasHandler] and asserts the published ScaleMeasurement.
 * The fixture is synthetic (hand-built from the handler's protocol outline)
 * until the first live logcat capture replaces it — see SNAPSHOT.md.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class BeurerSanitasProtocolTest {
    @Serializable
    private data class Fixture(
        val synthetic: Boolean = true,
        val deviceName: String,
        val characteristic: String,
        val expected: Expected,
        val frames: List<Frame>,
    )

    @Serializable
    private data class Expected(
        val weightKg: Double,
        val bodyFatPercent: Double,
        val waterPercent: Double,
        val musclePercent: Double,
        val boneKg: Double,
        val impedanceOhm: Int,
        val dateTimeEpochMs: Long,
    )

    @Serializable
    private data class Frame(val role: String = "", val hex: String)

    /** Records writes/notifies; everything answers "yes, that characteristic exists". */
    private class StubTransport : ScaleDeviceHandler.Transport {
        val writes = mutableListOf<ByteArray>()
        val notifiedOn = mutableListOf<UUID>()

        override fun setNotifyOn(service: UUID, characteristic: UUID) {
            notifiedOn += characteristic
        }

        override fun write(service: UUID, characteristic: UUID, payload: ByteArray, withResponse: Boolean) {
            writes += payload
        }

        override fun read(service: UUID, characteristic: UUID) = Unit

        override fun disconnect() = Unit

        override fun hasCharacteristic(service: UUID, characteristic: UUID): Boolean = true
    }

    private class StubCallbacks : ScaleDeviceHandler.Callbacks {
        val published = mutableListOf<ScaleMeasurement>()

        override fun onPublish(measurement: ScaleMeasurement) {
            published += measurement
        }

        override fun resolveString(resId: Int, vararg args: Any): String = "res:$resId"
    }

    private class StubSettings : ScaleDeviceHandler.DriverSettings {
        private val map = mutableMapOf<String, Any?>()
        override fun getInt(key: String, default: Int): Int = map[key] as? Int ?: default
        override fun putInt(key: String, value: Int) {
            map[key] = value
        }

        override fun getString(key: String, default: String?): String? = map[key] as? String ?: default
        override fun putString(key: String, value: String) {
            map[key] = value
        }

        override fun remove(key: String) {
            map.remove(key)
        }
    }

    private fun localUser(): ScaleUser = ScaleUser(
        id = 0,
        userName = "Alex",
        birthday = Calendar.getInstance().apply { set(1990, Calendar.JUNE, 15) }.time,
        bodyHeight = 175f,
        initialWeight = 75f,
        goalWeight = 72f,
        scaleUnit = WeightUnit.KG,
    ).also {
        // beurerSanitas maps by name prefix + birth year; gender only affects user-create.
    }

    private fun fixture(): Fixture {
        val text = javaClass.getResourceAsStream("/openscale_beurer_frames.json")!!
            .bufferedReader(Charsets.UTF_8).readText()
        return Json { ignoreUnknownKeys = true }.decodeFromString(Fixture.serializer(), text)
    }

    private fun scannedBf700(): ScannedDeviceInfo = ScannedDeviceInfo(
        name = "BEURER BF700",
        address = "AA:BB:CC:DD:EE:FF",
        rssi = -60,
        serviceUuids = listOf(UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb")),
        manufacturerData = null,
    )

    @Test
    fun `supportFor claims the fixture device`() {
        val support = BeurerSanitasHandler().supportFor(scannedBf700())
        assertNotNull(support)
    }

    @Test
    fun `replay publishes measurement matching fixture expectations`() {
        val fx = fixture()
        val chr = UUID.fromString(fx.characteristic)

        val transport = StubTransport()
        val callbacks = StubCallbacks()
        val handler = BeurerSanitasHandler()

        assertNotNull(handler.supportFor(scannedBf700()))
        handler.attach(transport, callbacks, StubSettings(), object : ScaleDeviceHandler.DataProvider {
            override fun currentUser(): ScaleUser = localUser()
            override fun usersForDevice(): List<ScaleUser> = listOf(localUser())
            override fun lastMeasurementFor(userId: Int): ScaleMeasurement? = null
        }, CoroutineScope(Dispatchers.Unconfined))

        handler.handleConnected(localUser())
        assertTrue("handler subscribed to the measurement characteristic", transport.notifiedOn.contains(chr))

        for (frame in fx.frames) {
            handler.handleNotification(chr, frame.hex.hexToByteArray())
        }

        assertEquals("one published weigh-in", 1, callbacks.published.size)
        val m = callbacks.published.single()
        assertTrue(m.hasWeight())

        val e = fx.expected
        assertEquals(e.dateTimeEpochMs, m.dateTime!!.time)
        assertEquals(e.weightKg, m[com.health.openscale.core.data.MeasurementType.WEIGHT]!!.value.toDouble(), 0.5)
        assertEquals(e.bodyFatPercent, m[com.health.openscale.core.data.MeasurementType.BODY_FAT]!!.value.toDouble(), 0.01)
        assertEquals(e.waterPercent, m[com.health.openscale.core.data.MeasurementType.WATER]!!.value.toDouble(), 0.01)
        assertEquals(e.musclePercent, m[com.health.openscale.core.data.MeasurementType.MUSCLE]!!.value.toDouble(), 0.01)
        assertEquals(e.boneKg, m[com.health.openscale.core.data.MeasurementType.BONE]!!.value.toDouble(), 0.01)
        assertEquals(e.impedanceOhm.toFloat(), m[com.health.openscale.core.data.MeasurementType.IMPEDANCE]!!.value, 0.01f)
    }
}

private fun String.hexToByteArray(): ByteArray =
    chunked(2).map { it.toInt(16).toByte() }.toByteArray()

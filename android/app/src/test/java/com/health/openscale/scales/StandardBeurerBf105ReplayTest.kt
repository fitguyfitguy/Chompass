package com.health.openscale.scales

import com.health.openscale.core.bluetooth.BluetoothEvent.UserInteractionType
import com.health.openscale.core.bluetooth.data.ScaleMeasurement
import com.health.openscale.core.bluetooth.data.ScaleUser
import com.health.openscale.core.bluetooth.scales.ScaleDeviceHandler
import com.health.openscale.core.bluetooth.scales.StandardBeurerSanitasHandler
import com.health.openscale.core.data.MeasurementType
import com.health.openscale.core.data.WeightUnit
import com.health.openscale.core.service.ScannedDeviceInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
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
 * Replays the BF105 fixture (openscale_beurer_frames.json) through the vendored
 * [StandardBeurerSanitasHandler] with the BF105 profile active (service 0xFFFF,
 * chrUserList 0x0001, measurements on standard 2A9D/2A9C): empty user list →
 * app-side registration handshake → consent → weigh-in. The three measurement
 * frames are real bytes from the live BF105 session; this validates the HOLD
 * delta (repeated weight held until composition arrives) on those real bytes
 * through the actual BF105 code path. See docs/local/SCALE_SURVEY.md.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class StandardBeurerBf105ReplayTest {
    @Serializable
    private data class Fixture(
        val synthetic: Boolean = true,
        val deviceName: String,
        val characteristics: Map<String, String>,
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
    )

    @Serializable
    private data class Frame(val role: String = "", val chr: String, val hex: String)

    /** Records writes per characteristic; everything answers "yes, that characteristic exists". */
    private class RecordingTransport : ScaleDeviceHandler.Transport {
        val writes = mutableMapOf<UUID, MutableList<ByteArray>>()
        val notifiedOn = mutableListOf<UUID>()

        override fun setNotifyOn(service: UUID, characteristic: UUID) {
            notifiedOn += characteristic
        }

        override fun write(service: UUID, characteristic: UUID, payload: ByteArray, withResponse: Boolean) {
            writes.getOrPut(characteristic) { mutableListOf() }.add(payload.copyOf())
        }

        override fun read(service: UUID, characteristic: UUID) = Unit

        override fun disconnect() = Unit

        override fun hasCharacteristic(service: UUID, characteristic: UUID): Boolean = true
    }

    private class RecordingCallbacks : ScaleDeviceHandler.Callbacks {
        val published = mutableListOf<ScaleMeasurement>()
        val interactions = mutableListOf<Pair<UserInteractionType, Any?>>()

        override fun onPublish(measurement: ScaleMeasurement) {
            published += measurement
        }

        override fun onUserInteractionRequired(interactionType: UserInteractionType, data: Any?) {
            interactions += interactionType to data
        }

        override fun resolveString(resId: Int, vararg args: Any): String = "res:$resId"
    }

    private class RecordingSettings : ScaleDeviceHandler.DriverSettings {
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
        userName = "ALE",
        birthday = Calendar.getInstance().apply { set(1990, Calendar.JUNE, 15) }.time,
        bodyHeight = 178f,
        initialWeight = 89f,
        goalWeight = 80f,
        scaleUnit = WeightUnit.KG,
    )

    private fun fixture(): Fixture {
        val text = javaClass.getResourceAsStream("/openscale_beurer_frames.json")!!
            .bufferedReader(Charsets.UTF_8).readText()
        return Json { ignoreUnknownKeys = true }.decodeFromString(Fixture.serializer(), text)
    }

    private fun scannedBf105(): ScannedDeviceInfo = ScannedDeviceInfo(
        name = fixture().deviceName,
        address = "AA:BB:CC:DD:EE:FF",
        rssi = -60,
        serviceUuids = listOf(UUID.fromString("0000ffff-0000-1000-8000-00805f9b34fb")),
        manufacturerData = null,
    )

    @Test
    fun `supportFor claims the BF105 with the standard profile`() {
        val handler = StandardBeurerSanitasHandler()
        val support = handler.supportFor(scannedBf105())
        assertNotNull(support)
        assertEquals("Beurer BF105/720", support!!.displayName)
    }

    @Test
    fun `replay registers on empty scale and publishes weight with composition`() {
        val f = fixture()
        val chr = f.characteristics.mapValues { UUID.fromString(it.value) }
        val takeMeasurementChr = UUID.fromString("00000006-0000-1000-8000-00805f9b34fb")
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val transport = RecordingTransport()
        val callbacks = RecordingCallbacks()
        val handler = StandardBeurerSanitasHandler()
        handler.supportFor(scannedBf105()) // locks the BF105 profile
        handler.attach(
            transport,
            callbacks,
            RecordingSettings(),
            object : ScaleDeviceHandler.DataProvider {
                override fun currentUser(): ScaleUser = localUser()
                override fun usersForDevice(): List<ScaleUser> = listOf(currentUser())
                override fun lastMeasurementFor(userId: Int): ScaleMeasurement? = null
            },
            scope,
        )
        try {
            handler.handleConnected(localUser())

            // Connect handshake queried the Beurer user list on the 0xFFFF service.
            val userListChr = chr.getValue("chrUserList")
            assertEquals(listOf(byteArrayOf(0x00).toList()), transport.writes[userListChr]?.map { it.toList() })

            for (frame in f.frames) {
                // The empty scale asked CHOOSE_USER (create-only): answer by
                // registering a new user before continuing the replay.
                if (frame.chr == "userControlPoint" && frame.hex.replace(" ", "").startsWith("200101")) {
                    runBlocking {
                        handler.onUserInteractionFeedback(UserInteractionType.CHOOSE_USER, 0, -1)
                    }
                }
                handler.handleNotification(chr.getValue(frame.chr), frame.hex.hexToByteArray())

                if (frame.chr == "weightMeasurement") {
                    // Repeated weight stays held; nothing publishes before composition.
                    assertTrue("weight must stay held before composition", callbacks.published.isEmpty())
                }
            }

            // The scale accepted registration + consent and was asked to take a measurement.
            assertTrue(
                "take-measurement write expected",
                transport.writes[takeMeasurementChr]?.any { it.size == 1 && it[0] == 0x00.toByte() } == true,
            )

            assertEquals(1, callbacks.published.size)
            val m = callbacks.published.single()
            assertEquals(f.expected.weightKg, m[MeasurementType.WEIGHT]!!.value.toDouble(), 0.02)
            assertEquals(f.expected.bodyFatPercent, m[MeasurementType.BODY_FAT]!!.value.toDouble(), 0.05)
            assertEquals(f.expected.musclePercent, m[MeasurementType.MUSCLE]!!.value.toDouble(), 0.05)
            assertEquals(f.expected.waterPercent, m[MeasurementType.WATER]!!.value.toDouble(), 0.2)
            assertEquals(f.expected.boneKg, m[MeasurementType.BONE]!!.value.toDouble(), 0.05)

            // Empty scale surfaced the create-only CHOOSE_USER prompt.
            val choose = callbacks.interactions.single { it.first == UserInteractionType.CHOOSE_USER }
            val ids = (choose.second as? Pair<*, *>)?.second as? IntArray
            assertTrue("create-only choice expected", ids != null && ids.toList() == listOf(-1))
        } finally {
            scope.cancel()
        }
    }
}

private fun String.hexToByteArray(): ByteArray =
    replace(" ", "").chunked(2).map { it.toInt(16).toByte() }.toByteArray()

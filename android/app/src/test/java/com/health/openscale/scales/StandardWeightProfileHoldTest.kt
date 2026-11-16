package com.health.openscale.scales

import com.health.openscale.core.bluetooth.data.ScaleMeasurement
import com.health.openscale.core.bluetooth.data.ScaleUser
import com.health.openscale.core.bluetooth.scales.ScaleDeviceHandler
import com.health.openscale.core.bluetooth.scales.StandardWeightProfileHandler
import com.health.openscale.core.data.MeasurementType
import com.health.openscale.core.data.WeightUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.UUID

/**
 * The live BF105 sends the weight indication, repeats it, then sends body
 * composition a few milliseconds later. Publishing on the repeated weight
 * cleared the buffer and dropped fat. These bytes are from that session.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class StandardWeightProfileHoldTest {
    private val weightChr = UUID.fromString("00002a9d-0000-1000-8000-00805f9b34fb")
    private val bodyCompChr = UUID.fromString("00002a9c-0000-1000-8000-00805f9b34fb")

    private val weightHex = "0E 8E 45 EA 07 0B 09 17 11 26 04 18 01 F4 06"
    private val bodyCompHex = "98 03 13 01 C8 1E 65 01 C2 2F 9E 20 C6 16"

    /** Same frame with a later timestamp (2026-11-10 10:42:00) and 89.04 kg. */
    private val secondWeighInHex = "0E 90 45 EA 07 0B 0A 0A 2A 00 04 18 01 F4 06"

    @Test
    fun `repeated weight does not publish before body composition`() {
        withHandler { handler, callbacks ->
            val weight = weightHex.hexToByteArray()
            handler.handleNotification(weightChr, weight)
            handler.handleNotification(weightChr, weight)
            assertTrue("duplicate weight must stay held", callbacks.published.isEmpty())

            handler.handleNotification(bodyCompChr, bodyCompHex.hexToByteArray())

            assertEquals(1, callbacks.published.size)
            val m = callbacks.published.single()
            assertEquals(89.03, m[MeasurementType.WEIGHT]!!.value.toDouble(), 0.02)
            assertEquals(27.5, m[MeasurementType.BODY_FAT]!!.value.toDouble(), 0.05)
            assertEquals(35.7, m[MeasurementType.MUSCLE]!!.value.toDouble(), 0.05)
            assertEquals(46.9, m[MeasurementType.WATER]!!.value.toDouble(), 0.2)
            assertEquals(3.42, m[MeasurementType.BONE]!!.value.toDouble(), 0.05)
        }
    }

    /**
     * Regression guard for the first HOLD version's repeat-suppression, caught
     * by upstream's StandardWeightProfileHandlerTest ("consecutive weigh-ins in
     * one connection stay separate measurements"): a weight packet after a
     * completed publish must only be dropped when it repeats the published
     * row's indication. A different timestamp is a new weigh-in.
     */
    @Test
    fun `consecutive weigh-ins in one connection stay separate measurements`() {
        withHandler { handler, callbacks ->
            handler.handleNotification(weightChr, weightHex.hexToByteArray())
            handler.handleNotification(bodyCompChr, bodyCompHex.hexToByteArray())
            assertEquals(1, callbacks.published.size)

            handler.handleNotification(weightChr, secondWeighInHex.hexToByteArray())
            handler.handleNotification(bodyCompChr, bodyCompHex.hexToByteArray())

            assertEquals(2, callbacks.published.size)
            assertEquals(89.04, callbacks.published[1][MeasurementType.WEIGHT]!!.value.toDouble(), 0.02)
        }
    }

    /** The BF105 repeat can also arrive after the row already published; the
     *  same-timestamp weight packet is dropped, not treated as a new weigh-in. */
    @Test
    fun `same-timestamp weight after publish is dropped as the repeat`() {
        withHandler { handler, callbacks ->
            handler.handleNotification(weightChr, weightHex.hexToByteArray())
            handler.handleNotification(bodyCompChr, bodyCompHex.hexToByteArray())
            assertEquals(1, callbacks.published.size)

            handler.handleNotification(weightChr, weightHex.hexToByteArray())

            assertEquals("late repeat must not publish again", 1, callbacks.published.size)
        }
    }

    private fun withHandler(block: (StandardWeightProfileHandler, RecordingCallbacks) -> Unit) {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val callbacks = RecordingCallbacks()
        val handler = StandardWeightProfileHandler()
        handler.attach(
            object : ScaleDeviceHandler.Transport {
                override fun setNotifyOn(service: UUID, characteristic: UUID) = Unit
                override fun write(service: UUID, characteristic: UUID, payload: ByteArray, withResponse: Boolean) = Unit
                override fun read(service: UUID, characteristic: UUID) = Unit
                override fun disconnect() = Unit
                override fun hasCharacteristic(service: UUID, characteristic: UUID) = true
            },
            callbacks,
            object : ScaleDeviceHandler.DriverSettings {
                override fun getInt(key: String, default: Int) = default
                override fun putInt(key: String, value: Int) = Unit
                override fun getString(key: String, default: String?) = default
                override fun putString(key: String, value: String) = Unit
                override fun remove(key: String) = Unit
            },
            object : ScaleDeviceHandler.DataProvider {
                override fun currentUser(): ScaleUser = localUser()
                override fun usersForDevice(): List<ScaleUser> = listOf(localUser())
                override fun lastMeasurementFor(userId: Int): ScaleMeasurement? = null
            },
            scope,
        )
        try {
            block(handler, callbacks)
        } finally {
            scope.cancel()
        }
    }

    private fun localUser(): ScaleUser = ScaleUser(
        id = 0,
        userName = "Alex",
        birthday = Calendar.getInstance().apply { set(1990, Calendar.JUNE, 15) }.time,
        bodyHeight = 178f,
        initialWeight = 80f,
        goalWeight = 75f,
        scaleUnit = WeightUnit.KG,
    )

    private class RecordingCallbacks : ScaleDeviceHandler.Callbacks {
        val published = mutableListOf<ScaleMeasurement>()
        override fun onPublish(measurement: ScaleMeasurement) {
            published += measurement
        }
        override fun resolveString(resId: Int, vararg args: Any): String = "res:$resId"
    }
}

private fun String.hexToByteArray(): ByteArray =
    split(" ").map { it.toInt(16).toByte() }.toByteArray()

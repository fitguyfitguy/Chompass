package app.chompass.scale

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Round-trips the scale drivers' settings through the dedicated DataStore file. */
class ScaleDriverSettingsTest {
    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var scope: CoroutineScope
    private lateinit var settings: ScaleDriverSettings

    @Before
    fun setUp() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        settings = ScaleDriverSettings(File(folder.root, "scale_driver.preferences_pb"), scope)
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun putWritesThroughAndLoadReadsItBack() = runBlocking {
        assertEquals(-1, settings.getInt("userMap/userIdByIndex/3", -1))
        settings.putInt("userMap/userIdByIndex/3", 0)
        settings.putString("driver/name", "Beurer BF450")
        assertEquals(0, settings.getInt("userMap/userIdByIndex/3", -1))
        withTimeout(10_000) { settings.flush() }
        settings.load()
        assertEquals(0, settings.getInt("userMap/userIdByIndex/3", -1))
        assertEquals("Beurer BF450", settings.getString("driver/name", null))
    }

    @Test
    fun removePersistsAcrossReload() = runBlocking {
        settings.putInt("userMap/userIdByIndex/3", 5)
        withTimeout(10_000) { settings.flush() }
        settings.remove("userMap/userIdByIndex/3")
        withTimeout(10_000) { settings.flush() }
        settings.load()
        assertEquals(-1, settings.getInt("userMap/userIdByIndex/3", -1))
    }

    @Test
    fun corruptFileStartsEmpty() = runBlocking {
        File(folder.root, "scale_driver.preferences_pb").writeBytes(byteArrayOf(1, 2, 3, 4, 5))
        settings.load()
        assertEquals(-1, settings.getInt("anything", -1))
        assertEquals(null, settings.getString("anything", null))
    }
}

package app.chompass.services

import android.app.Application
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** [BarcodePhotoStore] put/get/retain round-trip over Robolectric's filesDir. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class BarcodePhotoStoreTest {
    private fun store() = BarcodePhotoStore(RuntimeEnvironment.getApplication())

    @Test
    fun putThenGet_roundTripsBytes() = runBlocking {
        val bytes = ByteArray(32) { (it * 7).toByte() }
        store().put("9339687206605", bytes)
        assertArrayEquals(bytes, store().get("9339687206605"))
    }

    @Test
    fun get_absentCode_returnsNull() = runBlocking {
        assertNull(store().get("0000000000000"))
    }

    @Test
    fun retain_keepsPresent_deletesAbsent() = runBlocking {
        val s = store()
        val kept = byteArrayOf(1)
        val dropped = byteArrayOf(2)
        s.put("1111111111111", kept)
        s.put("2222222222222", dropped)
        s.retain(setOf("1111111111111"))
        assertArrayEquals(kept, s.get("1111111111111"))
        assertNull(s.get("2222222222222"))
    }
}

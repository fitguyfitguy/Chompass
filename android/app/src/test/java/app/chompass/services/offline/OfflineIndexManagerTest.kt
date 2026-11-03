package app.chompass.services.offline

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import app.chompass.data.PreferencesStore
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.GZIPOutputStream
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * [OfflineIndexManager] install pipeline against a scripted release server:
 * manifest discovery by tag prefix + naming contract, sha gate (tampered
 * bytes fail and write nothing), the free-space gate, and the installed
 * pack answering lookups through [OfflineIndexManager.openPack].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class OfflineIndexManagerTest {
    private lateinit var server: MockWebServer
    private lateinit var prefs: PreferencesStore
    private lateinit var appContext: Context

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        appContext = RuntimeEnvironment.getApplication()
        prefs = PreferencesStore(appContext)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    /** Builds and gzips a tiny valid pack sqlite (shipped schema, one row). */
    private fun gzBytes(): ByteArray {
        val src = File(appContext.cacheDir, "packsrc").apply { mkdirs() }
        val dbFile = File(src, "src.sqlite")
        dbFile.delete()
        SQLiteDatabase.openOrCreateDatabase(dbFile.absolutePath, null).use { db ->
            db.execSQL(
                "CREATE TABLE barcodes(code TEXT PRIMARY KEY, name TEXT NOT NULL, brand TEXT, " +
                    "kcal REAL, protein REAL, carbs REAL, fat REAL, serving_g REAL) WITHOUT ROWID",
            )
            db.execSQL("CREATE TABLE meta(key TEXT PRIMARY KEY, value TEXT NOT NULL) WITHOUT ROWID")
            db.execSQL("INSERT INTO meta VALUES('pack_id', 'usda-gtins')")
            db.execSQL("INSERT INTO meta VALUES('dataset_version', '20261103')")
            db.execSQL("INSERT INTO barcodes VALUES('0885909092596','Canned Pinto Beans','TestBrand',91.0,6.0,16.0,0.5,130.0)")
        }
        val gz = ByteArrayOutputStream()
        GZIPOutputStream(gz).use { it.write(dbFile.readBytes()) }
        return gz.toByteArray()
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun releasesJson(manifestUrl: String): String = """
        [{"tag_name":"offline-index-20261103","assets":[
            {"name":"usda-gtins-20261103.json","browser_download_url":"$manifestUrl"},
            {"name":"usda-gtins-20261103.sqlite.gz","browser_download_url":"ignored"}]}]
    """.trimIndent()

    private fun manifestJson(url: String, sha: String, bytes: Long): String = """
        {"id":"usda-gtins","version":"20261103","sha256":"$sha","bytes":$bytes,"url":"$url","rows":1}
    """.trimIndent()

    private fun manager(diskFree: Long = Long.MAX_VALUE) = OfflineIndexManager(
        appContext,
        prefs,
        client = OkHttpClient(),
        releasesApiUrl = server.url("/").toString(),
        diskFreeBytes = { diskFree },
    )

    @Test
    fun download_manifestThenAsset_installsAndWritesPref() = runBlocking {
        val gz = gzBytes()
        val sha = sha256Hex(gz)
        val base = server.url("/")
        server.enqueue(MockResponse().setResponseCode(200).setBody(releasesJson("$base/usda-gtins-20261103.json")))
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody(manifestJson("$base/usda-gtins-20261103.sqlite.gz", sha, gz.size.toLong())),
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody(Buffer().write(gz)))

        val m = manager()
        m.download(OfflineIndexPacks.USDA_GTINS)

        val state = m.states.value["usda-gtins"] as OfflineIndexState.Installed
        assertEquals("20261103", state.version)
        assertEquals(sha, state.sha256)
        assertEquals(gz.size.toLong(), state.bytes)
        assertEquals("20261103", prefs.offlineIndexPacks.first()["usda-gtins"]?.version)

        // The installed sqlite answers lookups (raw row — brand prefixing
        // happens in toFoodAnalysis); intermediates are gone.
        val product = m.openPack("usda-gtins")?.lookup("0885909092596")
        assertEquals("Canned Pinto Beans", product?.name)
        assertEquals("TestBrand", product?.brand)
        assertFalse(File(appContext.filesDir, "offline_index/usda-gtins.sqlite.gz").exists())
    }

    @Test
    fun download_tamperedAsset_failsAndWritesNothing() = runBlocking {
        val gz = gzBytes()
        val base = server.url("/")
        server.enqueue(MockResponse().setResponseCode(200).setBody(releasesJson("$base/usda-gtins-20261103.json")))
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody(manifestJson("$base/usda-gtins-20261103.sqlite.gz", "0".repeat(64), gz.size.toLong())),
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody(Buffer().write(gz)))

        val m = manager()
        try {
            m.download(OfflineIndexPacks.USDA_GTINS)
            fail("expected download failure")
        } catch (_: Exception) {
        }
        assertTrue(m.states.value["usda-gtins"] is OfflineIndexState.Failed)
        assertNull(prefs.offlineIndexPacks.first()["usda-gtins"])
        assertNull(m.openPack("usda-gtins"))
        assertFalse(File(appContext.filesDir, "offline_index/usda-gtins.sqlite").exists())
    }

    @Test
    fun download_insufficientDisk_failsBeforeAssetFetch() = runBlocking {
        val gz = gzBytes()
        val base = server.url("/")
        server.enqueue(MockResponse().setResponseCode(200).setBody(releasesJson("$base/usda-gtins-20261103.json")))
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody(manifestJson("$base/usda-gtins-20261103.sqlite.gz", sha256Hex(gz), gz.size.toLong())),
        )

        val m = manager(diskFree = 1L)
        try {
            m.download(OfflineIndexPacks.USDA_GTINS)
            fail("expected download failure")
        } catch (_: Exception) {
        }
        assertTrue(m.states.value["usda-gtins"] is OfflineIndexState.Failed)
        // Release list + manifest fetched; the free-space gate stopped the asset fetch.
        assertEquals(2, server.requestCount)
    }

    @Test
    fun remove_dropsFileStateAndPref() = runBlocking {
        val gz = gzBytes()
        val base = server.url("/")
        server.enqueue(MockResponse().setResponseCode(200).setBody(releasesJson("$base/usda-gtins-20261103.json")))
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody(manifestJson("$base/usda-gtins-20261103.sqlite.gz", sha256Hex(gz), gz.size.toLong())),
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody(Buffer().write(gz)))
        val m = manager()
        m.download(OfflineIndexPacks.USDA_GTINS)
        assertTrue(m.openPack("usda-gtins") != null)

        m.remove("usda-gtins")
        assertEquals(OfflineIndexState.NotDownloaded, m.states.value["usda-gtins"])
        assertNull(m.openPack("usda-gtins"))
        assertFalse(File(appContext.filesDir, "offline_index/usda-gtins.sqlite").exists())
    }
}

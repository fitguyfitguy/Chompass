package app.chompass.services

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import app.chompass.data.PreferencesStore
import app.chompass.data.setBarcodeCacheImpl
import app.chompass.services.OpenFoodFactsService.LookupException
import app.chompass.services.ai.FoodAnalysis
import app.chompass.services.grounding.BarcodePackIndex
import app.chompass.services.grounding.PackResolver
import java.io.File
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The barcode lookup chain's offline-pack fallback (Codeberg WP5): OFF 404 →
 * GTIN pack hit (+ cache write); OFF network failure → regional pack; both
 * packs miss → the original OFF error is preserved; OFF success → packs are
 * never consulted.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class BarcodePackChainTest {
    private lateinit var server: MockWebServer
    private lateinit var prefs: PreferencesStore
    private lateinit var appContext: Context

    private val productJson =
        """{"code":"9339687206605","status":1,"product":{"product_name":"Spirals Australian Pasta","brands":"San Remo","quantity":"250 g","serving_quantity":100,"nutriments":{"energy-kcal_100g":356,"proteins_100g":12,"carbohydrates_100g":70,"fat_100g":2}}}"""

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        appContext = RuntimeEnvironment.getApplication()
        prefs = PreferencesStore(appContext)
        runBlocking { prefs.setBarcodeCacheImpl(emptyMap()) }
        OpenFoodFactsService.resetRateLimitForTest()
    }

    @After
    fun tearDown() {
        server.shutdown()
        OpenFoodFactsService.resetRateLimitForTest()
    }

    /** Builds a minimal pack sqlite with the shipped schema at filesDir/offline_index/<fileId>.sqlite. */
    private fun buildPack(
        fileId: String,
        packId: String,
        rows: List<Array<Any?>>,
    ): File {
        val dir = File(appContext.filesDir, "offline_index").apply { mkdirs() }
        val file = File(dir, "$fileId.sqlite")
        file.delete()
        SQLiteDatabase.openOrCreateDatabase(file.absolutePath, null).use { db ->
            db.execSQL(
                "CREATE TABLE barcodes(code TEXT PRIMARY KEY, name TEXT NOT NULL, brand TEXT, " +
                    "kcal REAL, protein REAL, carbs REAL, fat REAL, serving_g REAL) WITHOUT ROWID",
            )
            db.execSQL("CREATE TABLE meta(key TEXT PRIMARY KEY, value TEXT NOT NULL) WITHOUT ROWID")
            db.execSQL("INSERT INTO meta(key, value) VALUES('pack_id', ?)", arrayOf<Any?>(packId))
            db.execSQL("INSERT INTO meta(key, value) VALUES('dataset_version', ?)", arrayOf<Any?>("20261103"))
            for (row in rows) db.execSQL("INSERT INTO barcodes VALUES(?,?,?,?,?,?,?,?)", row)
        }
        return file
    }

    private fun resolver(gtins: BarcodePackIndex?, regional: BarcodePackIndex?) = object : PackResolver {
        override fun gtins(): BarcodePackIndex? = gtins
        override fun regional(): BarcodePackIndex? = regional
    }

    private fun lookup(code: String, packs: PackResolver?): FoodAnalysis = runBlocking {
        OpenFoodFactsService.lookupByCode(
            code = code,
            prefs = prefs,
            client = OkHttpClient(),
            baseUrl = server.url("/").toString(),
            packs = packs,
        )
    }

    @Test
    fun off404_gtinPackAnswers_andWritesCache() {
        val pack = buildPack(
            "usda-gtins",
            "usda-gtins",
            listOf(arrayOf<Any?>("9339687206605", "Chompass Oat Bar", "Chompass Foods", 420.0, 10.0, 55.0, 12.0, 40.0)),
        )
        server.enqueue(MockResponse().setResponseCode(404))
        val analysis = lookup("9339687206605", resolver(BarcodePackIndex(pack).apply { open() }, null))
        // Brand-prefixed name, per-serving macros (420 kcal/100g × 40g serving).
        assertEquals("Chompass Foods Chompass Oat Bar", analysis.name)
        assertEquals(168, analysis.calories)
        assertEquals(40.0, analysis.servingSizeGrams!!, 0.001)
        assertEquals(1, server.requestCount)

        // Cache write asserted: a second lookup resolves with no new request.
        val cached = lookup("9339687206605", resolver(null, null))
        assertEquals(analysis.name, cached.name)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun offNetworkFailure_regionalPackAnswers() {
        val pack = buildPack(
            "off-de",
            "off-de",
            listOf(arrayOf<Any?>("4006381333931", "Sprite Zitrone", "Coca-Cola", 40.0, 0.0, 10.0, 0.0, null)),
        )
        repeat(3) { server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START)) }
        val analysis = lookup("4006381333931", resolver(null, BarcodePackIndex(pack).apply { open() }))
        // No labeled serving → per-100g mapping (40 kcal, 100g serving).
        assertEquals("Coca-Cola Sprite Zitrone", analysis.name)
        assertEquals(40, analysis.calories)
        assertEquals(100.0, analysis.servingSizeGrams!!, 0.001)
    }

    @Test
    fun bothPacksMiss_preservesNotFound() {
        server.enqueue(MockResponse().setResponseCode(404))
        val empty = buildPack("usda-gtins", "usda-gtins", emptyList())
        try {
            lookup("9339687206605", resolver(BarcodePackIndex(empty).apply { open() }, null))
            fail("expected LookupException.NotFound")
        } catch (e: LookupException.NotFound) {
            assertEquals("9339687206605", e.code)
        }
    }

    @Test
    fun offSuccess_packNeverConsulted() {
        server.enqueue(MockResponse().setResponseCode(200).setBody(productJson))
        val pack = buildPack(
            "usda-gtins",
            "usda-gtins",
            listOf(arrayOf<Any?>("9339687206605", "Pack Impostor", "Pack", 999.0, null, null, null, null)),
        )
        val analysis = lookup("9339687206605", resolver(BarcodePackIndex(pack).apply { open() }, null))
        assertEquals("San Remo Spirals Australian Pasta", analysis.name)
        assertEquals(1, server.requestCount)
    }
}

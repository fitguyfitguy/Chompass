package app.chompass.export

import app.chompass.models.FoodEntry
import app.chompass.models.FoodSource
import app.chompass.models.MealType
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Slice 1 (Codeberg #122): opt-in photo refs in diary JSON (format 1.7) and
 * the ZIP archive path. Photo-less exports must stay byte-identical to 1.6.
 */
class DiaryExportPhotosTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val day: LocalDate = LocalDate.of(2026, 7, 20)

    private fun entry(name: String): FoodEntry = FoodEntry(
        name = name,
        calories = 100,
        protein = 10.0,
        carbs = 5.0,
        fat = 2.0,
        source = FoodSource.MANUAL,
        mealType = MealType.LUNCH.id,
        timestamp = day.atStartOfDay(java.time.ZoneId.of("UTC")).toInstant().plusSeconds(12 * 3600),
    )

    private fun photoFile(name: String, content: String): File =
        tmp.newFile(name).apply { writeText(content) }

    private fun sha16hex(content: String): String =
        MessageDigest.getInstance("SHA-256").digest(content.toByteArray()).take(8)
            .joinToString("") { "%02x".format(it) }

    private fun zipEntryNames(file: File): List<String> {
        val names = mutableListOf<String>()
        ZipInputStream(FileInputStream(file)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory) names += entry.name
            }
        }
        return names
    }

    @Test
    fun buildWithPhotoRefsEmitsPhotosAndVersion17() {
        val e = entry("Salad")
        val result = DiaryExporter.build(
            entries = listOf(e), start = day, end = day,
            format = DiaryFormat.JSON, profile = null, mealDisplay = { it },
            photoRefsByEntryId = mapOf(e.id.toString() to listOf("photos/0123456789abcdef.jpg")),
        ) ?: error("expected export")

        assertEquals("Fud-Food-Diary-2026-07-20_to_2026-07-20.json", result.first)
        val doc = JSONObject(result.second)
        assertEquals("1.7", doc.getJSONObject("export").getString("format_version"))
        val item = doc.getJSONArray("days").getJSONObject(0)
            .getJSONArray("meals").getJSONObject(0)
            .getJSONArray("items").getJSONObject(0)
        assertEquals(listOf("photos/0123456789abcdef.jpg"), item.getJSONArray("photos").let { a -> (0 until a.length()).map { a.getString(it) } })
    }

    @Test
    fun buildWithoutPhotosStays16AndPhotoFree() {
        val e = entry("Salad")
        val result = DiaryExporter.build(
            entries = listOf(e), start = day, end = day,
            format = DiaryFormat.JSON, profile = null, mealDisplay = { it },
        ) ?: error("expected export")

        assertEquals("1.6", JSONObject(result.second).getJSONObject("export").getString("format_version"))
        assertFalse(result.second.contains("\"photos\""))
    }

    @Test
    fun buildZipWritesDiaryJsonAndDedupedPhotoEntries() {
        val refA = "photos/${sha16hex("jpeg-a-bytes")}.jpg"
        val refB = "photos/${sha16hex("jpeg-b-bytes")}.jpg"
        val photoA = photoFile("a.jpg", "jpeg-a-bytes")
        val photoA2 = photoFile("a2.jpg", "jpeg-a-bytes") // same bytes → same ref
        val photoB = photoFile("b.jpg", "jpeg-b-bytes")
        val e1 = entry("One")
        val e2 = entry("Two")
        val out = tmp.newFile("out.zip")

        val name = DiaryExporter.buildZip(
            output = out,
            entries = listOf(e1, e2), start = day, end = day,
            format = DiaryFormat.JSON, profile = null, mealDisplay = { it },
            photoFilesByEntryId = mapOf(
                e1.id.toString() to listOf(photoA),
                e2.id.toString() to listOf(photoA2, photoB),
            ),
        ) ?: error("expected zip")

        assertEquals("Fud-Food-Diary-2026-07-20_to_2026-07-20.zip", name)
        assertEquals(setOf("diary.json", refA, refB), zipEntryNames(out).toSet())

        val (json, photos) = DiaryArchiveReader.read(out.readBytes()) ?: error("expected archive")
        val doc = JSONObject(json)
        assertEquals("1.7", doc.getJSONObject("export").getString("format_version"))
        assertEquals(setOf(refA, refB), photos.keys)
        // Both entries reference the shared ref; e2 additionally references refB.
        val items = doc.getJSONArray("days").getJSONObject(0).getJSONArray("meals")
            .getJSONObject(0).getJSONArray("items")
        val photosOf = { i: Int ->
            val a = items.getJSONObject(i).getJSONArray("photos")
            (0 until a.length()).map { a.getString(it) }
        }
        assertEquals(listOf(refA), photosOf(0))
        assertEquals(listOf(refA, refB), photosOf(1))
    }

    @Test
    fun buildZipSkipsUnreadablePhotosAndReturnsNullForEmptyRange() {
        val e = entry("Salad")
        val unreadable = photoFile("gone.jpg", "x")
        assertTrue(unreadable.delete())

        val out = tmp.newFile("out.zip")
        val name = DiaryExporter.buildZip(
            output = out,
            entries = listOf(e), start = day, end = day,
            format = DiaryFormat.JSON, profile = null, mealDisplay = { it },
            photoFilesByEntryId = mapOf(e.id.toString() to listOf(unreadable)),
        ) ?: error("expected zip (entry still exports without photo)")
        assertTrue(zipEntryNames(out).contains("diary.json"))
        assertFalse(zipEntryNames(out).any { it.startsWith("photos/") })
        val archived = DiaryArchiveReader.read(out.readBytes())!!
        assertEquals("1.6", JSONObject(archived.first).getJSONObject("export").getString("format_version"))

        // Empty range → null, matching build().
        val missing = tmp.newFile("missing.zip")
        assertNull(
            DiaryExporter.buildZip(
                output = missing,
                entries = listOf(e), start = day.plusDays(30), end = day.plusDays(31),
                format = DiaryFormat.JSON, profile = null, mealDisplay = { it },
            ),
        )
    }

    @Test
    fun importerAccepts17WithPhotos() {
        val doc = """
            {"export":{"app":"Chompass","format_version":"1.7",
              "date_range":{"start":"2026-07-20","end":"2026-07-20"}},
             "days":[{"date":"2026-07-20",
               "totals":{"calories":100,"protein_g":10,"carbs_g":5,"fat_g":2},
               "targets":{"calories":100,"protein_g":10,"carbs_g":5,"fat_g":2},
               "remaining":{"calories":0,"protein_g":0,"carbs_g":0,"fat_g":0},
               "meals":[{"type":"lunch","items":[
                 {"name":"Salad","calories":100,"protein_g":10,"carbs_g":5,"fat_g":2,
                  "time":"12:00","source":"manually_edited","note":null,
                  "photos":["photos/0123456789abcdef.jpg"," photos/deadbeefdeadbeef.jpg ","  "]}]}]}]}
        """.trimIndent()

        val result = DiaryImporter.parse(doc)
        assertTrue(result is DiaryImportResult.Success)
        val success = result as DiaryImportResult.Success
        assertEquals(1, success.entries.size)
        val refs = success.photosByEntryId[success.entries[0].id.toString()]
        assertEquals(listOf("photos/0123456789abcdef.jpg", "photos/deadbeefdeadbeef.jpg"), refs)
    }

    @Test
    fun importerStillAccepts16() {
        val doc = """
            {"export":{"app":"Chompass","format_version":"1.6",
              "date_range":{"start":"2026-07-20","end":"2026-07-20"}},
             "days":[{"date":"2026-07-20",
               "totals":{"calories":100,"protein_g":10,"carbs_g":5,"fat_g":2},
               "targets":{"calories":100,"protein_g":10,"carbs_g":5,"fat_g":2},
               "remaining":{"calories":0,"protein_g":0,"carbs_g":0,"fat_g":0},
               "meals":[{"type":"lunch","items":[
                 {"name":"Salad","calories":100,"protein_g":10,"carbs_g":5,"fat_g":2,
                  "time":"12:00","source":"manually_edited","note":null}]}]}]}
        """.trimIndent()

        val result = DiaryImporter.parse(doc)
        assertTrue(result is DiaryImportResult.Success)
        assertTrue((result as DiaryImportResult.Success).photosByEntryId.isEmpty())
    }

    @Test
    fun archiveReaderRoundTripAndRejectsNonZip() {
        val json = """{"export":{"app":"Chompass","format_version":"1.7"}}"""
        val out = tmp.newFile("round.zip")
        ZipOutputStream(out.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("nested/dir.json"))
            zip.write("ignored".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("diary.json"))
            zip.write(json.toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("photos/0123456789abcdef.jpg"))
            zip.write("jpeg-bytes".toByteArray())
            zip.closeEntry()
        }

        val (readJson, photos) = DiaryArchiveReader.read(out.readBytes()) ?: error("expected archive")
        assertEquals(json, readJson)
        assertEquals(1, photos.size)
        assertTrue("jpeg-bytes".toByteArray().contentEquals(photos["photos/0123456789abcdef.jpg"]))

        assertNull(DiaryArchiveReader.read("plain json".toByteArray()))
        assertNull(DiaryArchiveReader.read(byteArrayOf(0x50, 0x4B, 0x03, 0x04))) // truncated zip header
    }

    @Test
    fun csvAndMarkdownIgnorePhotoRefs() {
        val e = entry("Salad")
        val refs = mapOf(e.id.toString() to listOf("photos/0123456789abcdef.jpg"))
        for (format in listOf(DiaryFormat.CSV, DiaryFormat.MARKDOWN)) {
            val result = DiaryExporter.build(
                entries = listOf(e), start = day, end = day,
                format = format, profile = null, mealDisplay = { it },
                photoRefsByEntryId = refs,
            ) ?: error("expected export")
            assertFalse("$format must stay photo-free", result.second.contains("photos/"))
        }
    }
}

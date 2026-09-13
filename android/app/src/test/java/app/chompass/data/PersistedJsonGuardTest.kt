package app.chompass.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.UUID

/**
 * PersistedJsonGuard (port of upstream fud-ai c278c64d/736f25fc): Missing vs
 * Corrupt vs partially-Decoded outcomes, element-wise row dropping, enum
 * coercion, and the corrupt-blob archive (collision-free copies, length
 * verification, fallback location, fail-closed null).
 */
class PersistedJsonGuardTest {
    // Same configuration as PreferencesStore.
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @get:Rule
    val tmp = TemporaryFolder()

    @Serializable
    private enum class Kind { A, B }

    @Serializable
    private data class Row(
        val id: String,
        val name: String,
        val kind: Kind = Kind.A,
    )

    private val serializer = Row.serializer()

    private fun row(name: String = "Rice and beans") = Row(id = UUID.randomUUID().toString(), name = name)

    private fun encode(vararg rows: Row) = json.encodeToString(rows.toList())

    @Test
    fun missingKeyIsMissingNotCorrupt() {
        val decoded = LenientJsonList.decode(json, serializer, null)
        assertEquals(PersistedListDecode.Missing, decoded)
        assertFalse(decoded.needsPreservation)
        assertTrue(decoded.itemsOrEmpty.isEmpty())
    }

    @Test
    fun validListDecodesWithoutPreservation() {
        val decoded = LenientJsonList.decode(json, serializer, encode(row(), row("Toast")))
        val result = decoded as PersistedListDecode.Decoded
        assertEquals(listOf("Rice and beans", "Toast"), result.items.map { it.name })
        assertEquals(0, result.dropped)
        assertFalse(decoded.needsPreservation)
    }

    @Test
    fun garbageIsCorruptAndKeepsRawForPreservation() {
        val raw = "[{\"id\": \"broken\""
        val decoded = LenientJsonList.decode(json, serializer, raw)
        assertEquals(PersistedListDecode.Corrupt(raw), decoded)
        assertTrue(decoded.needsPreservation)
        assertEquals(raw, decoded.rawOrNull)
        // The bug this guards against: this used to be indistinguishable
        // from an empty store, so the next append wiped the data.
        assertTrue(decoded.itemsOrEmpty.isEmpty())
    }

    @Test
    fun oneBadRowIsDroppedNotTheWholeList() {
        val good = row()
        val goodJson = json.encodeToString(serializer, good)
        val raw = "[$goodJson, {\"name\": \"no id\"}, $goodJson]"

        val decoded = LenientJsonList.decode(json, serializer, raw) as PersistedListDecode.Decoded
        assertEquals(listOf(good, good), decoded.items)
        assertEquals(1, decoded.dropped)
        assertTrue(decoded.needsPreservation)
        assertEquals(raw, decoded.rawOrNull)
    }

    @Test
    fun allRowsUnreadableIsCorrupt() {
        val raw = "[{\"name\": \"x\"}, {\"name\": \"y\"}]"
        assertEquals(PersistedListDecode.Corrupt(raw), LenientJsonList.decode(json, serializer, raw))
    }

    @Test
    fun unknownEnumValueCoercesToDefaultInsteadOfDroppingRow() {
        val raw = """[{"id": "abc", "name": "Rice", "kind": "C"}]"""
        val decoded = LenientJsonList.decode(json, serializer, raw) as PersistedListDecode.Decoded
        assertEquals(Kind.A, decoded.items.single().kind)
    }

    @Test
    fun archivePreservesTextUnderCorruptSuffixAndNeverOverwrites() {
        val dir = tmp.newFolder("guard")
        val archive = CorruptBlobArchive(dir)
        val first = archive.preserveText("foodEntries.json", "one")
        val second = archive.preserveText("foodEntries.json", "two")
        assertNotNull(first)
        assertNotNull(second)
        assertTrue(first!!.name.startsWith("foodEntries.json.corrupt-"))
        assertTrue(second!!.name.startsWith("foodEntries.json.corrupt-"))
        assertEquals("one", first.readText())
        assertEquals("two", second.readText())
        assertTrue(first.path != second.path)
    }

    @Test
    fun archiveReturnsNullWhenDirectoryCannotBeCreated() {
        val blocker = File.createTempFile("blocker", null)
        try {
            // A regular file where the directory should be: mkdirs fails.
            val archive = CorruptBlobArchive(File(blocker, "nested"))
            assertNull(archive.preserveText("foodEntries.json", "raw"))
        } finally {
            blocker.delete()
        }
    }

    @Test
    fun archiveCopiesCorruptDataStoreFileNextToOriginal() {
        val dir = tmp.newFolder("guard")
        val source = File(dir, "fudai_prefs.preferences_pb").apply { writeText("not protobuf") }
        val backup = CorruptBlobArchive(File(dir, "unused")).preserveFile(source)
        assertNotNull(backup)
        assertTrue(backup!!.name.startsWith("fudai_prefs.preferences_pb.corrupt-"))
        assertEquals("not protobuf", backup.readText())
        assertEquals("not protobuf", source.readText())
    }

    @Test
    fun archiveNeverOverwritesAnEarlierDataStoreBackup() {
        val dir = tmp.newFolder("guard")
        val source = File(dir, "fudai_prefs.preferences_pb").apply { writeText("first") }
        val archive = CorruptBlobArchive(File(dir, "unused"))
        val first = archive.preserveFile(source)
        source.writeText("second")
        // Same millisecond timestamp is likely here; the name must still be unique.
        val second = archive.preserveFile(source)
        assertNotNull(first)
        assertNotNull(second)
        assertTrue(first!!.path != second!!.path)
        assertEquals("first", first.readText())
        assertEquals("second", second.readText())
    }

    @Test
    fun archiveFallsBackToArchiveDirectoryWhenSiblingCopyFails() {
        val dir = tmp.newFolder("guard")
        val parent = File(dir, "readonly").apply { mkdirs() }
        val source = File(parent, "fudai_prefs.preferences_pb").apply { writeText("bytes") }
        if (!parent.setWritable(false) || parent.canWrite()) return // running as root: cannot simulate
        val fallback = File(dir, "corrupt-backups")
        val backup = CorruptBlobArchive(fallback).preserveFile(source)
        assertNotNull(backup)
        assertEquals(fallback, backup!!.parentFile)
        assertEquals("bytes", backup.readText())
        parent.setWritable(true)
    }
}

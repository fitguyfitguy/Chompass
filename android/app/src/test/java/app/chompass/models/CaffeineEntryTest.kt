package app.chompass.models

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class CaffeineEntryTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun serializesRoundTrip() {
        val entry = CaffeineEntry(
            kind = "tea",
            mg = 28.0,
        )
        val decoded = json.decodeFromString<CaffeineEntry>(json.encodeToString(CaffeineEntry.serializer(), entry))
        assertEquals(entry.id, decoded.id)
        assertEquals("tea", decoded.kind)
        assertEquals(28.0, decoded.mg, 0.001)
    }

    @Test
    fun serializesKindAsStorageKeyOnWire() {
        // Bucket/wire JSON uses storage keys (lowercase), never enum names.
        val entry = CaffeineEntry(kind = "tea", mg = 28.0)
        val text = json.encodeToString(CaffeineEntry.serializer(), entry)
        assert(text.contains("\"kind\":\"tea\"")) { text }
        // Default kind is omitted by encodeDefaults=false and decodes back
        // to the same builtin id.
        val decodedDefault = json.decodeFromString<CaffeineEntry>("""{"mg":95.0}""")
        assertEquals("coffee", decodedDefault.kind)
    }

    @Test
    fun decodesLegacyEnumNameKinds() {
        // Enum-era month buckets stored the constant name ("TEA"); the kind
        // serializer normalizes builtin spellings back to storage keys.
        val dateMs = java.time.Instant.parse("2026-08-01T08:00:00Z").toEpochMilli()
        val legacy = """[{"id":"9f1d0a52-3a4c-4b6e-8f2a-1c2d3e4f5a6b","date":$dateMs,"kind":"TEA","mg":28.0}]"""
        val decoded = json.decodeFromString<List<CaffeineEntry>>(legacy)
        assertEquals("tea", decoded.single().kind)
        assertEquals(28.0, decoded.single().mg, 0.001)

        val legacyUpperOther = """{"kind":"OTHER","mg":40.0}"""
        assertEquals("other", json.decodeFromString<CaffeineEntry>(legacyUpperOther).kind)
    }

    @Test
    fun decodesCustomPresetIdsVerbatim() {
        val raw = """{"kind":"t_abcd1234","mg":65.0}"""
        assertEquals("t_abcd1234", json.decodeFromString<CaffeineEntry>(raw).kind)
    }

    @Test
    fun forNowUsesBuiltinDefaultMg() {
        assertEquals(95.0, CaffeineEntry.forNow("coffee").mg, 0.001)
        assertEquals(28.0, CaffeineEntry.forNow("tea").mg, 0.001)
        assertEquals(80.0, CaffeineEntry.forNow("energy").mg, 0.001)
        assertEquals(0.0, CaffeineEntry.forNow("other").mg, 0.001)

        val explicit = CaffeineEntry.forNow("coffee", mg = 150.0)
        assertEquals(150.0, explicit.mg, 0.001)
        // Negative mg clamps to 0 (repository drops such entries).
        assertEquals(0.0, CaffeineEntry.forNow("coffee", mg = -5.0).mg, 0.001)
        // Custom preset ids carry no builtin default: callers pass preset mg.
        assertEquals(0.0, CaffeineEntry.forNow("t_abcd1234").mg, 0.001)
        assertEquals(65.0, CaffeineEntry.forNow("t_abcd1234", mg = 65.0).mg, 0.001)
    }

    @Test
    fun kindStorageRoundTripAndFallback() {
        assertEquals(CaffeineKind.TEA, CaffeineKind.fromStorage("tea"))
        assertEquals(CaffeineKind.OTHER, CaffeineKind.fromStorage("espresso_future"))
        assertEquals(CaffeineKind.OTHER, CaffeineKind.fromStorage(null))
    }

    @Test
    fun normalizeKindIdPassesCustomsThrough() {
        assertEquals("tea", normalizeKindId("TEA"))
        assertEquals("tea", normalizeKindId("tea"))
        assertEquals("other", normalizeKindId("OTHER"))
        assertEquals("t_abcd1234", normalizeKindId("t_abcd1234"))
        // Non-builtin spellings pass through verbatim (custom ids are opaque).
        assertEquals("Espresso", normalizeKindId("Espresso"))
    }
}

package app.chompass.models

import app.chompass.R
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Catalog + resolver unit tests for custom tracker presets (#55 follow-up). */
class TrackerPresetsTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun caffeineWithCustoms(count: Int): HabitPresetCatalog {
        var catalog = HabitPresetDomain.CAFFEINE.defaultCatalog
        repeat(count) { i ->
            catalog = catalog.addCustom(
                HabitPresetDomain.CAFFEINE,
                label = "Espresso $i",
                defaultMg = 65.0 + i,
            )
        }
        return catalog
    }

    @Test
    fun defaultCatalogsMatchBuiltinKinds() {
        val caffeine = HabitPresetDomain.CAFFEINE.defaultCatalog
        assertEquals(listOf("coffee", "tea", "energy", "other"), caffeine.ids())
        assertEquals(null, caffeine.def("other")!!.defaultMg)
        assertEquals(95.0, caffeine.def("coffee")!!.defaultMg!!, 0.001)

        val nicotine = HabitPresetDomain.NICOTINE.defaultCatalog
        assertEquals(listOf("cigarette", "vape", "pouch", "gum", "patch", "other"), nicotine.ids())
        assertEquals(1, nicotine.def("cigarette")!!.defaultCount)
        assertNull(nicotine.def("vape")!!.defaultDoseMg)
    }

    @Test
    fun defaultCatalogsValidate() {
        assertNull(HabitPresetDomain.CAFFEINE.defaultCatalog.validate(HabitPresetDomain.CAFFEINE))
        assertNull(HabitPresetDomain.NICOTINE.defaultCatalog.validate(HabitPresetDomain.NICOTINE))
    }

    @Test
    fun validateRejectsDuplicateAndUnknownIds() {
        val dupes = HabitPresetCatalog(
            presets = listOf(HabitPreset("coffee"), HabitPreset("coffee")),
        )
        assertEquals("duplicate_id", dupes.validate(HabitPresetDomain.CAFFEINE))

        val nicotineIdInCaffeine = HabitPresetCatalog(presets = listOf(HabitPreset("cigarette")))
        assertEquals("bad_id", nicotineIdInCaffeine.validate(HabitPresetDomain.CAFFEINE))

        val blank = HabitPresetCatalog(presets = listOf(HabitPreset(" ")))
        assertEquals("blank_id", blank.validate(HabitPresetDomain.CAFFEINE))
    }

    @Test
    fun validateRejectsBadCustomIdsAndRanges() {
        assertEquals("bad_custom_id", HabitPresetCatalog(listOf(HabitPreset("t_ZZ"))).validate(HabitPresetDomain.CAFFEINE))
        assertEquals("mg", HabitPresetCatalog(listOf(HabitPreset("t_00000001", defaultMg = 501.0))).validate(HabitPresetDomain.CAFFEINE))
        assertEquals("mg", HabitPresetCatalog(listOf(HabitPreset("t_00000001", defaultMg = -1.0))).validate(HabitPresetDomain.CAFFEINE))
        assertEquals("count", HabitPresetCatalog(listOf(HabitPreset("t_00000001", defaultCount = 21))).validate(HabitPresetDomain.NICOTINE))
        assertEquals("dose", HabitPresetCatalog(listOf(HabitPreset("t_00000001", defaultDoseMg = 31.0))).validate(HabitPresetDomain.NICOTINE))
        assertEquals("label", HabitPresetCatalog(listOf(HabitPreset("t_00000001", label = "x".repeat(25)))).validate(HabitPresetDomain.CAFFEINE))
    }

    @Test
    fun validatedOrDefaultResetsCorruptCatalogs() {
        val corrupt = HabitPresetCatalog(presets = listOf(HabitPreset("cigarette")))
        assertEquals(
            HabitPresetDomain.CAFFEINE.defaultCatalog.ids(),
            corrupt.validatedOrDefault(HabitPresetDomain.CAFFEINE).ids(),
        )
    }

    @Test
    fun corruptStoredJsonResetsToDefaults() {
        assertEquals(
            HabitPresetDomain.CAFFEINE.defaultCatalog,
            parseHabitPresetCatalog("not json {", HabitPresetDomain.CAFFEINE, json),
        )
        assertEquals(
            HabitPresetDomain.NICOTINE.defaultCatalog,
            parseHabitPresetCatalog(null, HabitPresetDomain.NICOTINE, json),
        )
        val withCustom = caffeineWithCustoms(1)
        val roundTripped = json.encodeToString(HabitPresetCatalog.serializer(), withCustom)
        assertEquals(withCustom, parseHabitPresetCatalog(roundTripped, HabitPresetDomain.CAFFEINE, json))
    }

    @Test
    fun addCustomGeneratesIdsAndCapsAtEight() {
        val catalog = caffeineWithCustoms(HabitPresetCatalog.MAX_CUSTOM)
        assertEquals(HabitPresetCatalog.MAX_CUSTOM, catalog.customCount)
        // At cap: adding another is a no-op.
        val atCap = catalog.addCustom(HabitPresetDomain.CAFFEINE, label = "One too many", defaultMg = 10.0)
        assertEquals(catalog, atCap)

        val one = caffeineWithCustoms(1)
        val custom = one.presets.last()
        assertTrue(custom.isCustom)
        assertTrue(custom.id.startsWith("t_"))
        assertNotEquals(custom.id, one.addCustom(HabitPresetDomain.CAFFEINE, "Second").presets.last().id)
    }

    @Test
    fun addCustomClampsDefaultsToWheelRanges() {
        val clamped = HabitPresetDomain.CAFFEINE.defaultCatalog
            .addCustom(HabitPresetDomain.CAFFEINE, "Big", defaultMg = 900.0)
        assertEquals(500.0, clamped.presets.last().defaultMg!!, 0.001)

        val nicotine = HabitPresetDomain.NICOTINE.defaultCatalog
            .addCustom(HabitPresetDomain.NICOTINE, "Zyn 6", defaultCount = 40, defaultDoseMg = 90.0)
        val preset = nicotine.presets.last()
        assertEquals(20, preset.defaultCount)
        assertEquals(30.0, preset.defaultDoseMg!!, 0.001)
        // Mg-based presets never carry count/dose defaults.
        assertEquals(1, clamped.presets.last().defaultCount)
        assertNull(clamped.presets.last().defaultDoseMg)
    }

    @Test
    fun withLabelAndRenameApplyRenderTime() {
        val renamed = HabitPresetDomain.CAFFEINE.defaultCatalog.withLabel("coffee", "Doppio")
        assertEquals("Doppio", renamed.def("coffee")!!.label)
        // Ids stay stable: entries relabel for free because kind ids don't move.
        assertEquals(HabitPresetDomain.CAFFEINE.defaultCatalog.ids(), renamed.ids())
        // Blank label resets to the locale default.
        assertEquals("", renamed.withLabel("coffee", "  ").def("coffee")!!.label)
        // Labels are trimmed + capped at 24 chars.
        assertEquals("Long espresso name way p", renamed.withLabel("coffee", "  Long espresso name way past the cap  ").def("coffee")!!.label)
    }

    @Test
    fun withoutAndReordered() {
        val catalog = caffeineWithCustoms(2)
        val customId = catalog.presets.last { it.isCustom }.id
        assertEquals(catalog.presets.size - 1, catalog.without(customId).presets.size)
        assertNull(catalog.without(customId).def(customId))

        val reordered = catalog.reordered(catalog.ids().asReversed())
        assertEquals(catalog.ids().asReversed(), reordered.ids())
        // Unknown reorder ids keep every preset in its existing order.
        assertEquals(reordered.ids(), reordered.reordered(listOf("unknown")).ids())
    }

    @Test
    fun resolverPrecedence() {
        // Builtin labelRes -> override fallback -> unknown id falls back to Other.
        assertEquals(R.string.caffeine_kind_coffee, caffeineKindLabelRes("coffee"))
        assertEquals(R.string.caffeine_kind_other, caffeineKindLabelRes("t_abcd1234"))
        assertEquals(R.string.nicotine_kind_pouch, nicotineKindLabelRes("pouch"))
        assertEquals(R.string.nicotine_kind_other, nicotineKindLabelRes("snus_future"))
    }

    @Test
    fun quickKindSelectionRetainsCustomIds() {
        val caffeine = HabitPresetDomain.CAFFEINE
        assertEquals(listOf("coffee", "tea", "energy"), caffeine.quickKindIdsFromStorage(null))
        assertEquals(listOf("coffee", "tea", "energy"), caffeine.quickKindIdsFromStorage(""))
        assertEquals(listOf("coffee", "tea", "energy"), caffeine.quickKindIdsFromStorage(" , , "))

        val custom = caffeine.quickKindIdsFromStorage("tea,t_abcd1234,coffee,tea")
        assertEquals(listOf("tea", "t_abcd1234", "coffee"), custom)
        assertEquals("tea,t_abcd1234,coffee", caffeine.quickKindIdsToStorage(custom))

        // Garbage tokens drop; only builtins + t_ ids are retained.
        assertEquals(listOf("vape"), HabitPresetDomain.NICOTINE.quickKindIdsFromStorage("vape,snus_future"))
        // Everything invalid falls back to the defaults.
        assertEquals(
            HabitPresetDomain.NICOTINE.defaultQuickKindIds,
            HabitPresetDomain.NICOTINE.quickKindIdsFromStorage("snus_future, 9x"),
        )
    }

    @Test
    fun hubPresetsComposeChipsInCatalogOrder() {
        val domain = HabitPresetDomain.CAFFEINE
        var catalog = domain.defaultCatalog
            .withLabel("coffee", "Doppio")
            .addCustom(domain, "Espresso", defaultMg = 65.0)
        val espressoId = catalog.presets.last().id

        // Selection order is ignored: chips follow the catalog (user) order.
        val chips = domain.hubPresets(listOf("tea", espressoId, "coffee"), catalog)
        assertEquals(listOf("coffee", "tea", espressoId), chips.map { it.id })
        assertEquals("Doppio", chips.first().label)
        assertEquals(65.0, chips.last().defaultMg!!, 0.001)

        // Empty selection falls back to the default quick kinds (the
        // ifEmpty { Default } path keeps working with custom ids around).
        assertEquals(
            listOf("coffee", "tea", "energy"),
            domain.hubPresets(emptyList(), catalog).map { it.id },
        )

        // Deleted customs drop out of the chips without affecting the rest;
        // an "other" selection still resolves.
        assertEquals(
            listOf("other"),
            domain.hubPresets(listOf("other", "t_gone000"), catalog).map { it.id },
        )
    }
}

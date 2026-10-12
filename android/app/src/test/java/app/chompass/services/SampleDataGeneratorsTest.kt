package app.chompass.services

import app.chompass.models.BodyMeasurement
import app.chompass.models.CaffeineKind
import app.chompass.models.DailyNote
import app.chompass.models.FoodEntry
import app.chompass.models.FoodSource
import app.chompass.models.HabitPresetDomain
import app.chompass.models.MealType
import app.chompass.models.MicronutrientField
import app.chompass.models.MicronutrientValues
import app.chompass.models.MilkKind
import app.chompass.models.microsCompositionSignature
import app.chompass.models.microsStaleFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class SampleDataGeneratorsTest {
    private val today = LocalDate.of(2026, 8, 19)

    @Test
    fun fullFoodEntries_isDeterministicAndMixed() {
        val a = SampleDataGenerators.fullFoodEntries(totalDays = 365, today = today)
        val b = SampleDataGenerators.fullFoodEntries(totalDays = 365, today = today)
        assertEquals(a.size, b.size)
        assertEquals(a.map { it.name to it.calories }, b.map { it.name to it.calories })
        assertTrue(a.size in 1_000..1_600)
        assertTrue(a.any { it.source == FoodSource.TEXT_INPUT })
        assertTrue(a.any { it.source == FoodSource.MANUAL })
        assertTrue(a.any { it.source == FoodSource.BARCODE })
        assertTrue(a.any { it.source == FoodSource.SNAP_FOOD })
        assertTrue(a.any { it.fiber != null && it.sodium != null })
        assertTrue(a.any { it.recipeLogId != null })
        val recipeGroups = a.mapNotNull { it.recipeLogId }.distinct()
        assertTrue(recipeGroups.isNotEmpty())
        assertTrue(recipeGroups.all { id -> a.count { it.recipeLogId == id } >= 2 })
    }

    @Test
    fun waterEntries_coverMostDays() {
        val water = SampleDataGenerators.waterEntries(totalDays = 365, today = today)
        assertTrue(water.size in 1_200..3_000)
        assertTrue(water.all { it.milliliters in 200..600 })
        val days = water.map { it.date.toString().take(10) }.toSet()
        assertTrue(days.size in 300..365)
    }

    @Test
    fun sampleRecipes_areMultiIngredient() {
        val recipes = SampleDataGenerators.sampleRecipes(now = Instant.parse("2026-08-19T12:00:00Z"))
        assertEquals(4, recipes.size)
        assertTrue(recipes.all { it.ingredients.size >= 3 })
        assertTrue(recipes.all { it.totalCalories > 0 })
    }

    @Test
    fun sampleFavorites_areDistinctCopies() {
        val favs = SampleDataGenerators.sampleFavorites(now = Instant.parse("2026-08-19T12:00:00Z"))
        assertEquals(10, favs.size)
        assertEquals(favs.size, favs.map { it.favoriteKey }.toSet().size)
    }

    @Test
    fun sampleChat_alternatesRoles() {
        val chat = SampleDataGenerators.sampleChat(now = Instant.parse("2026-08-19T12:00:00Z"))
        assertEquals(16, chat.size)
        assertTrue(chat.size in 10..20)
        assertEquals(8, chat.count { it.role.name == "USER" })
        assertEquals(8, chat.count { it.role.name == "ASSISTANT" })
    }

    @Test
    fun legacyFoodEntries_unchangedShape() {
        val foods = SampleDataGenerators.foodEntries(totalDays = 30, today = today)
        assertTrue(foods.isNotEmpty())
        assertTrue(foods.all { it.source == FoodSource.TEXT_INPUT })
        assertTrue(foods.none { it.fiber != null })
    }

    @Test
    fun withFullMicros_setsEveryMicroField() {
        val row = SampleDataGenerators.withFullMicros(
            FoodEntry(
                name = "Micros",
                calories = 400,
                protein = 20.0,
                carbs = 40.0,
                fat = 12.0,
                source = FoodSource.MANUAL,
            ),
            seed = 0x51C0DE,
        )
        val micros = MicronutrientValues.from(row)
        val missing = MicronutrientField.entries.filter { micros[it] == null }
        assertEquals(emptyList<MicronutrientField>(), missing)
        // Deterministic: the same seed gives the same values.
        assertEquals(
            micros.sugar,
            MicronutrientValues.from(SampleDataGenerators.withFullMicros(row, 0x51C0DE)).sugar,
        )
    }

    @Test
    fun withServingUnits_selectsTheCupOption() {
        val row = SampleDataGenerators.withServingUnits(
            FoodEntry(
                name = "Granola",
                calories = 430,
                protein = 12.0,
                carbs = 66.0,
                fat = 14.0,
                source = FoodSource.BARCODE,
            )
        )
        assertEquals(320.0, row.servingSizeGrams!!, 0.001)
        assertEquals(2, row.servingUnitOptions.size)
        assertEquals("cup", row.selectedServingUnit)
        assertEquals(1.5, row.selectedServingQuantity!!, 0.001)
    }

    @Test
    fun withProductMetadata_carriesTheProductCard() {
        val row = SampleDataGenerators.withProductMetadata(
            FoodEntry(
                name = "Cereal",
                calories = 380,
                protein = 8.0,
                carbs = 70.0,
                fat = 6.0,
                source = FoodSource.BARCODE,
            )
        )
        val product = row.productMetadata!!
        assertEquals("4001234567890", product.barcode)
        assertEquals(4, product.novaGroup)
        assertTrue(product.hasDisplayDetails)
    }

    @Test
    fun compositeEntries_areOneRowPerMealWithReconciledConstituents() {
        val rows = SampleDataGenerators.compositeEntries(totalDays = 40, today = today)
        assertTrue(rows.isNotEmpty())
        assertTrue(rows.all { it.constituents.size == 3 })
        assertTrue(rows.all { it.recipeLogId == null })
        assertTrue(rows.all { it.source == FoodSource.MANUAL })
        rows.forEach { row ->
            assertEquals(row.servingSizeGrams!!, row.constituents.sumOf { it.servingSizeGrams }, 0.001)
            assertEquals(row.calories, row.constituents.sumOf { it.calories })
            assertEquals(row.protein, row.constituents.sumOf { it.protein }, 0.001)
            // Signature stamped for the mix that is actually stored → not stale.
            assertFalse(microsStaleFor(row.microsCompositionSignature, row.constituents))
            assertNotNull(microsCompositionSignature(row.constituents))
        }
        // Micros on 2 of 3 constituent rows only.
        assertEquals(2, rows.first().constituents.count { it.fiber != null })
        // Deterministic across calls (ids included).
        assertEquals(
            rows.map { it.id },
            SampleDataGenerators.compositeEntries(totalDays = 40, today = today).map { it.id },
        )
    }

    @Test
    fun sampleMealieRecipes_carryTheImportSignature() {
        val recipes = SampleDataGenerators.sampleMealieRecipes(now = Instant.parse("2026-08-19T12:00:00Z"))
        assertEquals(2, recipes.size)
        assertEquals(
            listOf("mealie:french-toast", "mealie:lentil-soup"),
            recipes.map { it.source },
        )
        recipes.forEach { recipe ->
            assertEquals(4.0, recipe.servingsPerRecipe!!, 0.001)
            assertTrue(recipe.nutritionCalories!! > 0)
            assertTrue(recipe.ingredients.isNotEmpty())
            assertTrue(
                recipe.ingredients.all {
                    it.amountText != null && it.unitText != null &&
                        it.baseCalories == 0 && it.baseProtein == 0.0
                }
            )
        }
    }

    @Test
    fun richFavorites_areDistinctAndCoverTheRichShapes() {
        val favorites = SampleDataGenerators.richFavorites(now = Instant.parse("2026-08-19T12:00:00Z"))
        assertEquals(3, favorites.size)
        assertEquals(favorites.size, favorites.map { it.favoriteKey }.toSet().size)
        assertTrue(favorites.any { it.productMetadata == null && MicronutrientValues.from(it).fiber != null })
        assertTrue(favorites.any { it.selectedServingUnit == "cup" && it.customNote != null })
        assertTrue(favorites.any { it.constituents.size == 3 })
        // No name collision with the plain favorites set (identity is the name).
        val plain = SampleDataGenerators.sampleFavorites(now = Instant.parse("2026-08-19T12:00:00Z"))
        assertTrue(favorites.none { rich -> plain.any { it.favoriteKey == rich.favoriteKey } })
    }

    @Test
    fun sampleChat_withAttachmentAddsOneImageBubble() {
        val plain = SampleDataGenerators.sampleChat(now = Instant.parse("2026-08-19T12:00:00Z"))
        assertEquals(16, plain.size)
        val withAttachment = SampleDataGenerators.sampleChat(
            now = Instant.parse("2026-08-19T12:00:00Z"),
            withAttachment = true,
        )
        assertEquals(17, withAttachment.size)
        assertEquals(1, withAttachment.count { it.attachmentImageBase64 != null })
    }

    @Test
    fun sampleNotes_useTheStablePerDayId() {
        val notes = SampleDataGenerators.sampleNotes(today = today)
        assertEquals(3, notes.size)
        assertEquals(listOf(today, today.minusDays(10), today.minusDays(25)), notes.map { it.date })
        notes.forEach { assertEquals(DailyNote.idFor(it.date), it.id) }
        assertEquals(notes.size, notes.map { it.text }.toSet().size)
    }

    @Test
    fun sampleMealCatalog_addsOneValidCustomSlot() {
        val catalog = SampleDataGenerators.sampleMealCatalog()
        assertTrue(catalog.isValid)
        val custom = catalog.def(SampleDataGenerators.SEED_CUSTOM_MEAL_ID)!!
        assertEquals("Seed Brunch", custom.label)
        assertTrue(custom.isCustom)
        // The builtin slots survive alongside it.
        assertNotNull(catalog.def(MealType.BREAKFAST.id))
    }

    @Test
    fun trackerPresets_areValidAndCarryOneCustomEach() {
        val (caffeine, nicotine) = SampleDataGenerators.trackerPresets()
        assertNull(caffeine.validate(HabitPresetDomain.CAFFEINE))
        assertNull(nicotine.validate(HabitPresetDomain.NICOTINE))
        assertEquals(1, caffeine.customCount)
        assertEquals(1, nicotine.customCount)
        assertEquals("Flat White", caffeine.def(CaffeineKind.COFFEE.storageKey)?.label)
        val latte = caffeine.def(SampleDataGenerators.SEED_CAFFEINE_PRESET_ID)!!
        assertEquals(MilkKind.OAT.storageKey, latte.milkKind)
        assertEquals(30, latte.milkMl)
        assertEquals(2, nicotine.def(SampleDataGenerators.SEED_NICOTINE_PRESET_ID)?.defaultCount)
    }

    @Test
    fun allSitesMeasurementSeries_coversAllEightSites() {
        val rows = SampleDataGenerators.allSitesMeasurementSeries(totalDays = 60, seed = 0x7A1, today = today)
        assertTrue(rows.isNotEmpty())
        assertTrue(rows.all { it.hasAnyValue })
        val covered = rows.flatMap { row ->
            BodyMeasurement.Site.entries.filter { row.value(it) != null }
        }.toSet()
        assertEquals(BodyMeasurement.Site.entries.toSet(), covered)
        // Deterministic: same seed, same first row.
        assertEquals(
            rows.first().waistCm,
            SampleDataGenerators.allSitesMeasurementSeries(totalDays = 60, seed = 0x7A1, today = today)
                .first().waistCm,
        )
    }
}

package app.chompass.services

import app.chompass.models.ChatMessage
import app.chompass.models.FoodEntry
import app.chompass.models.FoodSource
import app.chompass.models.MealType
import app.chompass.models.OptionalNutrientGoals
import app.chompass.models.WaterEntry
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class SeedBackupRoundTripTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun newFieldsSurviveEncodeDecode() {
        val water = listOf(WaterEntry(date = Instant.parse("2026-08-19T08:00:00Z"), milliliters = 300))
        val chat = SampleDataGenerators.sampleChat(now = Instant.parse("2026-08-19T12:00:00Z"))
        val favorites = listOf(
            FoodEntry(
                name = "Oats",
                calories = 150,
                protein = 5.0,
                carbs = 27.0,
                fat = 3.0,
                timestamp = Instant.parse("2026-08-18T08:00:00Z"),
                source = FoodSource.TEXT_INPUT,
                mealType = MealType.BREAKFAST,
            )
        )
        val recipes = SampleDataGenerators.sampleRecipes(now = Instant.parse("2026-08-19T12:00:00Z"))
        val backup = SeedBackup(
            entriesJson = "[]",
            weightsJson = "[]",
            profileJson = null,
            healthConnectEnabled = false,
            onboarded = true,
            waterJson = json.encodeToString(ListSerializer(WaterEntry.serializer()), water),
            recipesJson = json.encodeToString(ListSerializer(app.chompass.models.Recipe.serializer()), recipes),
            favoritesJson = json.encodeToString(ListSerializer(FoodEntry.serializer()), favorites),
            chatJson = json.encodeToString(ListSerializer(ChatMessage.serializer()), chat),
            measurementsJson = "[]",
            settings = SeedSettingsSnapshot(
                waterTrackingEnabled = true,
                waterDynamicEnabled = true,
                notificationsEnabled = true,
                progressMeasurementSites = setOf("waist", "hips"),
                optionalNutrientGoalsJson = json.encodeToString(
                    OptionalNutrientGoals.serializer(),
                    OptionalNutrientGoals.Default.copy(fiber = 30),
                ),
                homeShowSteps = true,
            ),
        )
        val decoded = json.decodeFromString(SeedBackup.serializer(), json.encodeToString(SeedBackup.serializer(), backup))
        assertEquals(true, decoded.settings?.waterTrackingEnabled)
        assertEquals(setOf("waist", "hips"), decoded.settings?.progressMeasurementSites)
        assertEquals(1, json.decodeFromString(ListSerializer(WaterEntry.serializer()), decoded.waterJson!!).size)
        assertEquals(4, json.decodeFromString(ListSerializer(app.chompass.models.Recipe.serializer()), decoded.recipesJson!!).size)
        assertEquals("oats", json.decodeFromString(ListSerializer(FoodEntry.serializer()), decoded.favoritesJson!!).single().favoriteKey)
        assertEquals(16, json.decodeFromString(ListSerializer(ChatMessage.serializer()), decoded.chatJson!!).size)
    }

    @Test
    fun olderBackupWithoutNewFieldsStillDecodes() {
        val raw = """
            {"entriesJson":"[]","weightsJson":"[]","profileJson":null,
             "healthConnectEnabled":true,"onboarded":false}
        """.trimIndent()
        val decoded = json.decodeFromString(SeedBackup.serializer(), raw)
        assertNull(decoded.waterJson)
        assertNull(decoded.recipesJson)
        assertNull(decoded.favoritesJson)
        assertNull(decoded.chatJson)
        assertNull(decoded.settings)
        assertEquals(true, decoded.healthConnectEnabled)
    }
}

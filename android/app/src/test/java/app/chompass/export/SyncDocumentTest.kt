package app.chompass.export

import app.chompass.models.DailyNote
import app.chompass.models.FoodConstituent
import app.chompass.models.FoodEntry
import app.chompass.models.FoodSource
import app.chompass.models.MealType
import app.chompass.models.NicotineEntry
import app.chompass.models.NicotineKind
import app.chompass.models.WaterEntry
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import app.chompass.parity.ParityFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class SyncDocumentTest {
    @Test
    fun paritySampleParses() {
        val json = ParityFixtures.readText("sync-sample.json")
        val result = SyncDocument.parse(json, ZoneOffset.UTC)
        assertTrue("expected Success but was $result", result is SyncDocument.ParseResult.Success)
        val parsed = (result as SyncDocument.ParseResult.Success).parsed
        assertEquals(2, parsed.foodEntries.count { it.entry != null })
        assertEquals("Chicken salad", parsed.foodEntries.first { it.entry != null }.entry?.name)
        val salad = parsed.foodEntries.first { it.entry?.name == "Chicken salad" }.entry!!
        assertEquals("🥗", salad.emoji)
        assertEquals("bowl", salad.selectedServingUnit)
        assertEquals(2, salad.constituents.size)
        assertEquals(90.0, salad.constituents[0].servingUnitOptions.single().gramsPerUnit, 0.0)
        // #86: the 1.3 fixture carries constituent micros; nulls stay null.
        val chicken = salad.constituents[0]
        assertEquals(0.0, chicken.sugar)
        assertEquals(6.2, chicken.monounsaturatedFat)
        assertEquals(145.0, chicken.cholesterol)
        assertEquals(220.0, chicken.sodium)
        assertEquals(640.0, chicken.potassium)
        assertEquals(0.5, chicken.vitaminB12)
        assertEquals(null, chicken.caffeine)
        val rice = salad.constituents[1]
        assertEquals(2.0, rice.sugar)
        assertEquals(100.0, rice.vitaminK)
        assertEquals(50.0, rice.folate)
        val coffee = parsed.foodEntries.first { it.entry?.name == "Black coffee" }.entry!!
        assertTrue(coffee.constituents.isEmpty())
        assertEquals(1, parsed.weights.count { it.entry != null })
        assertEquals(1, parsed.water.count { it.entry != null })
        assertTrue(parsed.profile != null)
    }

    @Test
    fun mergeRawDocumentsKeepsBothMeals() {
        val phone = """
            {"export":{"app":"Chompass","kind":"sync","format_version":"1.0"},
             "food_entries":[{"id":"b","updated_at":"2026-07-24T08:00:00Z","deleted_at":null,"name":"Oats","date":"2026-07-24","time":"08:00","meal_type":"breakfast","calories":300,"protein_g":10,"carbs_g":50,"fat_g":5}],
             "favorites":[],"weights":[],"body_fat":[],"measurements":[],"water":[],"recipes":[],"profile":null,"prefs":null}
        """.trimIndent()
        val desktop = """
            {"export":{"app":"Chompass","kind":"sync","format_version":"1.0"},
             "food_entries":[{"id":"l","updated_at":"2026-07-24T12:30:00Z","deleted_at":null,"name":"Salad","date":"2026-07-24","time":"12:30","meal_type":"lunch","calories":420,"protein_g":38,"carbs_g":12,"fat_g":22}],
             "favorites":[],"weights":[],"body_fat":[],"measurements":[],"water":[],"recipes":[],"profile":null,"prefs":null}
        """.trimIndent()
        val local = (SyncDocument.parse(phone, ZoneOffset.UTC) as SyncDocument.ParseResult.Success).parsed.raw
        val remote = (SyncDocument.parse(desktop, ZoneOffset.UTC) as SyncDocument.ParseResult.Success).parsed.raw
        val merged = SyncDocument.mergeRawDocuments(local, remote)
        assertEquals(2, merged["food_entries"]!!.jsonArray.size)
    }

    @Test
    fun mergeRawDocumentsDedupesWeightRows() {
        // #39: same date+value written under different ids (and different wire
        // styles: Android writes 80.0, the PWA writes 80) must collapse, while
        // distinct rows and tombstones pass through.
        val phone = """
            {"export":{"app":"Chompass","kind":"sync","format_version":"1.0"},
             "food_entries":[],"favorites":[],
             "weights":[
               {"id":"11111111-1111-4111-8111-111111111111","updated_at":"2026-07-20T08:00:00Z","deleted_at":null,"date":"2026-07-20T08:00:00Z","weight_kg":80.0},
               {"id":"33333333-3333-4333-8333-333333333333","updated_at":"2026-07-19T08:00:00Z","deleted_at":null,"date":"2026-07-19T08:00:00Z","weight_kg":78.9},
               {"id":"99999999-9999-4999-8999-999999999999","updated_at":"2026-07-20T08:00:00Z","deleted_at":"2026-07-20T08:00:00Z"}
             ],
             "body_fat":[],"measurements":[],"water":[],"recipes":[],"profile":null,"prefs":null}
        """.trimIndent()
        val desktop = """
            {"export":{"app":"Chompass","kind":"sync","format_version":"1.0"},
             "food_entries":[],"favorites":[],
             "weights":[
               {"id":"22222222-2222-4222-8222-222222222222","updated_at":"2026-07-21T08:00:00Z","deleted_at":null,"date":"2026-07-20T08:00:00Z","weight_kg":80}
             ],
             "body_fat":[],"measurements":[],"water":[],"recipes":[],"profile":null,"prefs":null}
        """.trimIndent()
        val local = (SyncDocument.parse(phone, ZoneOffset.UTC) as SyncDocument.ParseResult.Success).parsed.raw
        val remote = (SyncDocument.parse(desktop, ZoneOffset.UTC) as SyncDocument.ParseResult.Success).parsed.raw
        val merged = SyncDocument.mergeRawDocuments(local, remote)
        val weights = merged["weights"]!!.jsonArray
        assertEquals(3, weights.size)
        val ids = weights.map { it.jsonObject["id"]!!.jsonPrimitive.content }.toSet()
        // The 80.0/80 duplicate collapsed to the newest row (remote 2222...).
        assertTrue("22222222-2222-4222-8222-222222222222" in ids)
        assertTrue("11111111-1111-4111-8111-111111111111" !in ids)
        // Distinct row and tombstone pass through untouched.
        assertTrue("33333333-3333-4333-8333-333333333333" in ids)
        assertTrue("99999999-9999-4999-8999-999999999999" in ids)
        assertTrue(weights.any { it.jsonObject["deleted_at"] != null })
    }

    @Test
    fun buildRoundTripsDatesInZone() {
        // EMUI java.time defect: LocalDate/LocalTime.ofInstant are missing on some
        // Android 10 ROMs. buildJson must use atZone(...).toLocalDate()/toLocalTime()
        // so sync export works there; this pins the equivalent output.
        val zone = ZoneId.of("Europe/Berlin")
        val food = FoodEntry(
            name = "Oats",
            calories = 300,
            protein = 10.0,
            carbs = 50.0,
            fat = 5.0,
            timestamp = Instant.parse("2026-08-15T22:30:00Z"),
            source = FoodSource.MANUAL,
            mealType = MealType.BREAKFAST.id,
        )
        val water = WaterEntry(date = Instant.parse("2026-08-15T23:00:00Z"), milliliters = 250)
        val json = SyncDocument.buildJson(
            foodEntries = listOf(food),
            favorites = emptyList(),
            weights = emptyList(),
            bodyFats = emptyList(),
            measurements = emptyList(),
            water = listOf(water),
            recipes = emptyList(),
            zone = zone,
        )
        val result = SyncDocument.parse(json, zone)
        assertTrue("expected Success but was $result", result is SyncDocument.ParseResult.Success)
        // The wire date/time are exactly what the atZone(...) conversion emits.
        val root = Json.parseToJsonElement(json).jsonObject
        val foodWire = root["food_entries"]!!.jsonArray.single().jsonObject
        // 22:30Z in Europe/Berlin (UTC+2 in August) is 2026-08-16 00:30 local.
        assertEquals("2026-08-16", foodWire["date"]!!.jsonPrimitive.content)
        assertEquals("00:30", foodWire["time"]!!.jsonPrimitive.content)
        val waterWire = root["water"]!!.jsonArray.single().jsonObject
        assertEquals("2026-08-16", waterWire["date"]!!.jsonPrimitive.content)
    }

    @Test
    fun buildRoundTripsEmoji() {
        // Entry emoji must survive the sync wire (#34 family): photos are
        // intentionally excluded, emoji is not.
        val food = FoodEntry(
            name = "Oats",
            calories = 300,
            protein = 10.0,
            carbs = 50.0,
            fat = 5.0,
            timestamp = Instant.parse("2026-08-15T22:30:00Z"),
            source = FoodSource.MANUAL,
            mealType = MealType.BREAKFAST.id,
            emoji = "🥣",
        )
        val json = SyncDocument.buildJson(
            foodEntries = listOf(food),
            favorites = emptyList(),
            weights = emptyList(),
            bodyFats = emptyList(),
            measurements = emptyList(),
            water = emptyList(),
            recipes = emptyList(),
            zone = ZoneOffset.UTC,
        )
        val result = SyncDocument.parse(json, ZoneOffset.UTC)
        assertTrue("expected Success but was $result", result is SyncDocument.ParseResult.Success)
        val parsed = (result as SyncDocument.ParseResult.Success).parsed
        assertEquals("🥣", parsed.foodEntries.single().entry?.emoji)
    }

    @Test
    fun buildRoundTripsCaffeine() {
        // Caffeine rides the food wire as an optional mg field (caffeine plan 4.6).
        val food = FoodEntry(
            name = "Espresso",
            calories = 5,
            protein = 0.0,
            carbs = 1.0,
            fat = 0.0,
            timestamp = Instant.parse("2026-08-15T08:00:00Z"),
            source = FoodSource.MANUAL,
            mealType = MealType.BREAKFAST.id,
            caffeine = 95.0,
        )
        val json = SyncDocument.buildJson(
            foodEntries = listOf(food),
            favorites = emptyList(),
            weights = emptyList(),
            bodyFats = emptyList(),
            measurements = emptyList(),
            water = emptyList(),
            recipes = emptyList(),
            zone = ZoneOffset.UTC,
        )
        val result = SyncDocument.parse(json, ZoneOffset.UTC)
        assertTrue("expected Success but was $result", result is SyncDocument.ParseResult.Success)
        val parsed = (result as SyncDocument.ParseResult.Success).parsed
        assertEquals(95.0, parsed.foodEntries.single().entry?.caffeine)
    }

    @Test
    fun buildRoundTripsConstituentMicros() {
        // #86: constituent micros ride the sync wire (1.3); absent stays null.
        val food = FoodEntry(
            name = "Bowl",
            calories = 520,
            protein = 42.0,
            carbs = 40.0,
            fat = 18.0,
            timestamp = Instant.parse("2026-08-15T12:00:00Z"),
            source = FoodSource.MANUAL,
            mealType = MealType.LUNCH.id,
            constituents = listOf(
                FoodConstituent(
                    name = "Chicken",
                    calories = 280,
                    protein = 32.0,
                    carbs = 0.0,
                    fat = 12.0,
                    servingSizeGrams = 150.0,
                    saturatedFat = 4.5,
                    cholesterol = 145.0,
                    sodium = 320.0,
                    vitaminB12 = 0.5,
                ),
            ),
        )
        val json = SyncDocument.buildJson(
            foodEntries = listOf(food),
            favorites = emptyList(),
            weights = emptyList(),
            bodyFats = emptyList(),
            measurements = emptyList(),
            water = emptyList(),
            recipes = emptyList(),
            zone = ZoneOffset.UTC,
        )
        val root = Json.parseToJsonElement(json).jsonObject
        val row = root["food_entries"]!!.jsonArray.single().jsonObject["constituents"]!!
            .jsonArray.single().jsonObject
        assertEquals(4.5, row["saturated_fat_g"]!!.jsonPrimitive.content.toDouble(), 0.0)
        assertEquals(145.0, row["cholesterol_mg"]!!.jsonPrimitive.content.toDouble(), 0.0)
        assertEquals(320.0, row["sodium_mg"]!!.jsonPrimitive.content.toDouble(), 0.0)
        assertEquals(0.5, row["vitamin_b12_mcg"]!!.jsonPrimitive.content.toDouble(), 0.0)
        assertTrue(row["caffeine_mg"] is kotlinx.serialization.json.JsonNull)

        val result = SyncDocument.parse(json, ZoneOffset.UTC)
        assertTrue("expected Success but was $result", result is SyncDocument.ParseResult.Success)
        val got = (result as SyncDocument.ParseResult.Success).parsed
            .foodEntries.single().entry!!.constituents.single()
        assertEquals(4.5, got.saturatedFat)
        assertEquals(145.0, got.cholesterol)
        assertEquals(320.0, got.sodium)
        assertEquals(0.5, got.vitaminB12)
        assertEquals(null, got.caffeine)
        assertEquals(null, got.sugar)
    }

    @Test
    fun buildRoundTripsNicotineEntries() {
        // Optional nicotine tracker rides its own sync array + revisions kind.
        val nicotine = NicotineEntry(
            date = Instant.parse("2026-08-15T10:00:00Z"),
            kind = NicotineKind.POUCH,
            count = 2,
            mg = 6.5,
        )
        val json = SyncDocument.buildJson(
            foodEntries = emptyList(),
            favorites = emptyList(),
            weights = emptyList(),
            bodyFats = emptyList(),
            measurements = emptyList(),
            water = emptyList(),
            nicotine = listOf(nicotine),
            recipes = emptyList(),
            zone = ZoneOffset.UTC,
        )
        val result = SyncDocument.parse(json, ZoneOffset.UTC)
        assertTrue("expected Success but was $result", result is SyncDocument.ParseResult.Success)
        val parsed = (result as SyncDocument.ParseResult.Success).parsed
        val wire = parsed.nicotine.single()
        assertEquals(nicotine.id, wire.entry?.id)
        assertEquals(NicotineKind.POUCH, wire.entry?.kind)
        assertEquals(2, wire.entry?.count)
        assertEquals(6.5, wire.entry?.mg)
        // The wire key and kind are what the PWA mirrors.
        val root = Json.parseToJsonElement(json).jsonObject
        val wireArray = root["nicotine_entries"]!!.jsonArray
        assertEquals(1, wireArray.size)
        assertEquals("pouch", wireArray.single().jsonObject["kind"]!!.jsonPrimitive.content)
        assertEquals("2026-08-15", wireArray.single().jsonObject["date"]!!.jsonPrimitive.content)
    }

    @Test
    fun parseNicotineUnknownKindFallsBackToOther() {
        // Old/new client interop: an unknown wire kind must not break the parse.
        val json = SyncDocument.buildJson(
            foodEntries = emptyList(),
            favorites = emptyList(),
            weights = emptyList(),
            bodyFats = emptyList(),
            measurements = emptyList(),
            water = emptyList(),
            nicotine = emptyList(),
            recipes = emptyList(),
            zone = ZoneOffset.UTC,
        )
        val root = Json.parseToJsonElement(json).jsonObject
        val withUnknownKind = buildJsonObject {
            put("export", root["export"]!!)
            put("food_entries", buildJsonArray {})
            put("favorites", buildJsonArray {})
            put("weights", buildJsonArray {})
            put("body_fat", buildJsonArray {})
            put("measurements", buildJsonArray {})
            put("water", buildJsonArray {})
            put("nicotine_entries", buildJsonArray {
                add(buildJsonObject {
                    put("id", "eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee")
                    put("updated_at", "2026-08-15T10:00:00Z")
                    put("deleted_at", null)
                    put("date", "2026-08-15")
                    put("kind", "snus_future")
                    put("count", 3)
                })
            })
            put("recipes", buildJsonArray {})
        }
        val result = SyncDocument.parse(withUnknownKind.toString(), ZoneOffset.UTC)
        assertTrue("expected Success but was $result", result is SyncDocument.ParseResult.Success)
        val entry = (result as SyncDocument.ParseResult.Success).parsed.nicotine.single().entry
        assertEquals(NicotineKind.OTHER, entry?.kind)
        assertEquals(3, entry?.count)
    }

    @Test
    fun dailyNotesRoundTripAndTombstone() {
        val note = DailyNote(
            id = DailyNote.idFor(LocalDate.of(2026, 8, 3)),
            date = LocalDate.of(2026, 8, 3),
            text = "Solid day: energy held up.",
        )
        val json = SyncDocument.buildJson(
            foodEntries = emptyList(),
            favorites = emptyList(),
            weights = emptyList(),
            bodyFats = emptyList(),
            measurements = emptyList(),
            water = emptyList(),
            dailyNotes = listOf(note),
            recipes = emptyList(),
            revisions = mapOf(
                note.id.toString() to SyncDocument.Revision(
                    updatedAt = "2026-08-03T21:00:00Z",
                    kind = "daily_note",
                ),
                // A tombstoned note from another day must ride along.
                "00000000-0000-0000-0000-0000000050b2" to SyncDocument.Revision(
                    updatedAt = "2026-07-24T21:00:00Z",
                    deletedAt = "2026-07-24T22:00:00Z",
                    kind = "daily_note",
                ),
            ),
            zone = ZoneOffset.UTC,
        )
        val root = Json.parseToJsonElement(json).jsonObject
        val notesWire = root["daily_notes"]!!.jsonArray
        // Live note + tombstone, and the format stamped 1.3.
        assertEquals(2, notesWire.size)
        val export = root["export"]!!.jsonObject
        assertEquals("1.3", export["format_version"]!!.jsonPrimitive.content)

        val result = SyncDocument.parse(json, ZoneOffset.UTC)
        assertTrue("expected Success but was $result", result is SyncDocument.ParseResult.Success)
        val parsed = (result as SyncDocument.ParseResult.Success).parsed
        assertEquals(1, parsed.dailyNotes.count { it.entry != null })
        assertEquals(1, parsed.dailyNotes.count { it.deletedAt != null })
        val live = parsed.dailyNotes.first { it.entry != null }.entry!!
        assertEquals(LocalDate.of(2026, 8, 3), live.date)
        assertEquals(note.id, live.id)
        assertEquals("Solid day: energy held up.", live.text)
    }

    @Test
    fun dailyNotesMergeCollapsesSameDayLastWriteWins() {
        val phone = """
            {"export":{"app":"Chompass","kind":"sync","format_version":"1.2"},
             "food_entries":[],"favorites":[],"weights":[],"body_fat":[],"measurements":[],"water":[],
             "daily_notes":[
               {"id":"00000000-0000-0000-0000-0000000050b2","updated_at":"2026-07-24T18:00:00Z","deleted_at":null,"date":"2026-07-24","text":"phone draft"}
             ],
             "recipes":[],"profile":null,"prefs":null}
        """.trimIndent()
        val desktop = """
            {"export":{"app":"Chompass","kind":"sync","format_version":"1.2"},
             "food_entries":[],"favorites":[],"weights":[],"body_fat":[],"measurements":[],"water":[],
             "daily_notes":[
               {"id":"00000000-0000-0000-0000-0000000050b2","updated_at":"2026-07-24T19:00:00Z","deleted_at":null,"date":"2026-07-24","text":"desktop wins"}
             ],
             "recipes":[],"profile":null,"prefs":null}
        """.trimIndent()
        val local = (SyncDocument.parse(phone, ZoneOffset.UTC) as SyncDocument.ParseResult.Success).parsed.raw
        val remote = (SyncDocument.parse(desktop, ZoneOffset.UTC) as SyncDocument.ParseResult.Success).parsed.raw
        val merged = SyncDocument.mergeRawDocuments(local, remote)
        val notes = merged["daily_notes"]!!.jsonArray
        assertEquals(1, notes.size)
        assertEquals(
            "desktop wins",
            notes.single().jsonObject["text"]!!.jsonPrimitive.content,
        )
    }

    @Test
    fun importsStillAcceptSync11() {
        // Old documents (no daily_notes key) keep parsing under 1.2 rules.
        val legacy = """
            {"export":{"app":"Chompass","kind":"sync","format_version":"1.1"},
             "food_entries":[],"favorites":[],"weights":[],"body_fat":[],"measurements":[],"water":[],
             "recipes":[],"profile":null,"prefs":null}
        """.trimIndent()
        val result = SyncDocument.parse(legacy, ZoneOffset.UTC)
        assertTrue("expected Success but was $result", result is SyncDocument.ParseResult.Success)
        val parsed = (result as SyncDocument.ParseResult.Success).parsed
        assertTrue(parsed.dailyNotes.isEmpty())
        // The #60 goal journal rides the same optional-array rule.
        assertTrue(parsed.goalJournal.isEmpty())
    }

    @Test
    fun goalJournalRoundTripsWithDeterministicIds() {
        val day = LocalDate.of(2026, 7, 23)
        val entry = app.chompass.models.GoalJournalEntry(
            date = day.toString(),
            calories = 2100,
            proteinG = 150,
            carbsG = 160,
            fatG = 78,
            profileId = "r",
            profileName = "Rest day",
            updatedAtMillis = 1_784_300_700_000L,
            source = app.chompass.models.GoalJournalSource.MANUAL_SWITCH,
        )
        val json = SyncDocument.buildJson(
            foodEntries = emptyList(),
            favorites = emptyList(),
            weights = emptyList(),
            bodyFats = emptyList(),
            measurements = emptyList(),
            water = emptyList(),
            recipes = emptyList(),
            goalJournal = listOf(entry),
            zone = ZoneOffset.UTC,
        )
        val root = Json.parseToJsonElement(json).jsonObject
        val wire = root["goal_journal"]!!.jsonArray.single().jsonObject
        // Deterministic per-day id (daily_notes scheme) — merge-by-id == per-day LWW.
        assertEquals(app.chompass.models.GoalJournal.idFor(day).toString(), wire["id"]!!.jsonPrimitive.content)
        assertEquals("2026-07-23", wire["date"]!!.jsonPrimitive.content)
        assertEquals("manual_switch", wire["source"]!!.jsonPrimitive.content)
        assertEquals(2100, wire["calories"]!!.jsonPrimitive.content.toInt())

        val parsed = (SyncDocument.parse(json, ZoneOffset.UTC) as SyncDocument.ParseResult.Success).parsed
        val live = parsed.goalJournal.single().entry!!
        assertEquals(entry, live)
        assertEquals(1_784_300_700_000L, live.updatedAtMillis)
    }

    @Test
    fun goalJournalMergeCollapsesSameDayLastWriteWins() {
        val id = app.chompass.models.GoalJournal.idFor(LocalDate.of(2026, 7, 24)).toString()
        val phone = """
            {"export":{"app":"Chompass","kind":"sync","format_version":"1.2"},
             "food_entries":[],"favorites":[],"weights":[],"body_fat":[],"measurements":[],"water":[],
             "goal_journal":[
               {"id":"$id","updated_at":"2026-07-24T18:00:00Z","deleted_at":null,"date":"2026-07-24","calories":2800,"protein_g":170,"carbs_g":350,"fat_g":78,"profile_id":"t","profile_name":"Training day","source":"plan"}
             ],
             "recipes":[],"profile":null,"prefs":null}
        """.trimIndent()
        val desktop = """
            {"export":{"app":"Chompass","kind":"sync","format_version":"1.2"},
             "food_entries":[],"favorites":[],"weights":[],"body_fat":[],"measurements":[],"water":[],
             "goal_journal":[
               {"id":"$id","updated_at":"2026-07-24T19:00:00Z","deleted_at":null,"date":"2026-07-24","calories":2100,"protein_g":150,"carbs_g":160,"fat_g":78,"profile_id":"r","profile_name":"Rest day","source":"manual_switch"}
             ],
             "recipes":[],"profile":null,"prefs":null}
        """.trimIndent()
        val local = (SyncDocument.parse(phone, ZoneOffset.UTC) as SyncDocument.ParseResult.Success).parsed.raw
        val remote = (SyncDocument.parse(desktop, ZoneOffset.UTC) as SyncDocument.ParseResult.Success).parsed.raw
        val merged = SyncDocument.mergeRawDocuments(local, remote)
        val rows = merged["goal_journal"]!!.jsonArray
        assertEquals(1, rows.size)
        assertEquals(2100, rows.single().jsonObject["calories"]!!.jsonPrimitive.content.toInt())
        assertEquals("manual_switch", rows.single().jsonObject["source"]!!.jsonPrimitive.content)
    }
}

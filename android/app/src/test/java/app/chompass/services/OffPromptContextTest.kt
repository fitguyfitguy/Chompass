package app.chompass.services

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OffPromptContextTest {
    @Test
    fun format_emptyHits_returnsEmpty() {
        assertEquals("", OffPromptContext.format(emptyList()))
    }

    @Test
    fun format_singleHit_includesBarcodeMacrosAndInstructions() {
        val text = OffPromptContext.format(
            listOf(
                OffPromptContext.ProductHit(
                    barcode = "3017620422003",
                    name = "Ferrero Nutella",
                    servingGrams = 15.0,
                    calories = 80,
                    proteinG = 0.6,
                    carbsG = 10.5,
                    fatG = 4.3,
                    sugarG = 10.0,
                    fiberG = 0.5,
                    sodiumMg = 5.0,
                ),
            ),
        )
        assertTrue(text.contains("Open Food Facts match detected"))
        assertTrue(text.contains("barcode: 3017620422003"))
        assertTrue(text.contains("name: Ferrero Nutella"))
        assertTrue(text.contains("15g"))
        assertTrue(text.contains("80 kcal"))
        assertTrue(text.contains("P 0.6 g"))
        assertTrue(text.contains("also: sugar 10.0 g"))
        assertTrue(text.contains("per 100 g (derived from labeled serving):"))
        assertTrue(text.contains("authoritative package label data"))
        assertFalse(text.contains("matches detected"))
    }

    @Test
    fun format_multipleHits_usesPluralHeader() {
        val text = OffPromptContext.format(
            listOf(
                OffPromptContext.ProductHit(
                    barcode = "111",
                    name = "A",
                    servingGrams = 100.0,
                    calories = 100,
                    proteinG = 1.0,
                    carbsG = 2.0,
                    fatG = 3.0,
                ),
                OffPromptContext.ProductHit(
                    barcode = "222",
                    name = "B",
                    servingGrams = 50.0,
                    calories = 50,
                    proteinG = 0.0,
                    carbsG = 0.0,
                    fatG = 0.0,
                ),
            ),
        )
        assertTrue(text.contains("Open Food Facts matches detected"))
        assertTrue(text.contains("barcode: 111"))
        assertTrue(text.contains("barcode: 222"))
    }

    @Test
    fun formatFromAnalyses_usesGroundingSourceId() {
        val analysis = OpenFoodFactsService.analysis(
            org.json.JSONObject(
                """
                {
                  "product_name": "Greek Yogurt",
                  "brands": "Acme",
                  "serving_quantity": 150,
                  "nutriments": {
                    "energy-kcal_100g": 80,
                    "proteins_100g": 8,
                    "carbohydrates_100g": 6,
                    "fat_100g": 2
                  }
                }
                """.trimIndent(),
            ),
            "3017620422003",
        )
        val text = OffPromptContext.formatFromAnalyses(listOf(analysis))
        assertTrue(text.contains("barcode: 3017620422003"))
        assertTrue(text.contains("Acme Greek Yogurt"))
        assertTrue(text.contains("150g"))
    }

    @Test
    fun decodeOne_emptyBytes_returnsEmpty() = runBlocking {
        assertEquals(emptyList<String>(), BarcodeImageDecoder.decodeOne(ByteArray(0)))
    }

    @Test
    fun decodeOne_nonImageBytes_returnsEmpty() = runBlocking {
        assertEquals(emptyList<String>(), BarcodeImageDecoder.decodeOne("not-an-image".toByteArray()))
    }
}

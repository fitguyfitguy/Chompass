package app.chompass.services.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodPartialJsonAssemblerTest {
    @Test
    fun ignoresIncompleteString() {
        val assembler = FoodPartialJsonAssembler()
        assertNull(assembler.push("""{"name":"Piz"""))
        assertNull(assembler.current())
    }

    @Test
    fun emitsNameWhenStringCloses() {
        val assembler = FoodPartialJsonAssembler()
        assembler.push("""{"name":"Piz""")
        val partial = assembler.push("""za","calories":""")
        assertNotNull(partial)
        assertEquals("Pizza", partial!!.name)
        assertNull(partial.calories)
    }

    @Test
    fun emitsNumbersOnlyWhenComplete() {
        val assembler = FoodPartialJsonAssembler()
        assembler.push("""{"name":"Soup","calories":12""")
        assertNull(assembler.current()?.calories)
        val partial = assembler.push("0,")
        assertEquals(120, partial!!.calories)
    }

    @Test
    fun advancesMacrosAndServing() {
        val assembler = FoodPartialJsonAssembler()
        val json = """
            {"name":"Oatmeal","emoji":"🥣","calories":300,"protein":10.5,"carbs":45.0,"fat":6.0,"serving_size_grams":250.0,"fiber":4.0,"unit_options":[{"unit":"bowl","quantity":1,"grams_per_unit":250}]}
        """.trimIndent()
        // Feed in small chunks to simulate streaming.
        var last: PartialFoodAnalysis? = null
        for (chunk in json.chunked(17)) {
            last = assembler.push(chunk) ?: last
        }
        assertNotNull(last)
        assertEquals("Oatmeal", last!!.name)
        assertEquals("🥣", last.emoji)
        assertEquals(300, last.calories)
        assertEquals(10.5, last.protein!!, 0.001)
        assertEquals(45.0, last.carbs!!, 0.001)
        assertEquals(6.0, last.fat!!, 0.001)
        assertEquals(250.0, last.servingSizeGrams!!, 0.001)
        assertTrue(last.hasUnitOptions)
        assertTrue(last.micronutrientCount >= 1)
    }

    @Test
    fun doesNotTreatNestedKeysAsTopLevel() {
        val assembler = FoodPartialJsonAssembler()
        val partial = assembler.push(
            """{"name":"Salad","extras":{"calories":999},"calories":180}"""
        )
        assertEquals(180, partial!!.calories)
        assertEquals("Salad", partial.name)
    }

    @Test
    fun fromCompleteMarksStreamingFalse() {
        val analysis = FoodAnalysis(
            name = "Bread",
            calories = 100,
            protein = 4.0,
            carbs = 20.0,
            fat = 1.0,
            servingSizeGrams = 40.0,
            fiber = 2.0,
        )
        val partial = PartialFoodAnalysis.fromComplete(analysis, streaming = false)
        assertFalse(partial.streaming)
        assertEquals("Bread", partial.name)
        assertTrue(partial.micronutrientCount >= 1)
    }
}

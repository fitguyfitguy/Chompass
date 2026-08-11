package app.chompass.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EditFoodEntryIconTest {

    @Test
    fun foodEntryEmojis_nonBlankAndUnique() {
        assertEquals(FOOD_ENTRY_EMOJIS.size, FOOD_ENTRY_EMOJIS.toSet().size)
        assertTrue(FOOD_ENTRY_EMOJIS.none { it.isBlank() })
    }

    @Test
    fun foodEntryEmojis_atLeastOneRowOfSix() {
        assertTrue(FOOD_ENTRY_EMOJIS.size >= 6)
    }

    @Test
    fun foodEntryEmojis_singleCodepointEach() {
        FOOD_ENTRY_EMOJIS.forEach { emoji ->
            assertEquals(
                "Expected one codepoint in '$emoji' for uniform rendering",
                1,
                emoji.codePointCount(0, emoji.length),
            )
        }
    }
}

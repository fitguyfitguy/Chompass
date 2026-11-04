package app.chompass.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CoachQuickPromptsTest {
    private val defaults = listOf(
        CoachQuickPromptEntry(id = "predict_30_days"),
        CoachQuickPromptEntry(id = "lose_faster"),
    )

    @Test
    fun absentStorageIsNull() {
        assertNull(CoachQuickPrompts.fromStorage(null))
        assertNull(CoachQuickPrompts.fromStorage("  "))
    }

    @Test
    fun invalidJsonIsNull() {
        assertNull(CoachQuickPrompts.fromStorage("not json"))
    }

    @Test
    fun jsonRoundTrips() {
        val prompts = CoachQuickPrompts(
            listOf(
                CoachQuickPromptEntry(id = "predict_30_days"),
                CoachQuickPromptEntry(id = "c1", label = "Ask about salt", prompt = "How much salt should I eat?"),
            )
        )
        assertEquals(prompts, CoachQuickPrompts.fromStorage(prompts.toStorage()))
    }

    @Test
    fun emptyEntriesSurviveAsExplicitHidden() {
        val raw = CoachQuickPrompts(emptyList()).toStorage()
        assertEquals(emptyList<CoachQuickPromptEntry>(), CoachQuickPrompts.fromStorage(raw)!!.entries)
    }

    @Test
    fun unknownBuiltinIdsDropCustomsKept() {
        val stored = CoachQuickPrompts(
            listOf(
                CoachQuickPromptEntry(id = "predict_30_days"),
                CoachQuickPromptEntry(id = "no_such_chip"),
                CoachQuickPromptEntry(id = "c1", label = "Salt", prompt = "Salt?"),
            )
        )
        val resolved = stored.resolveFor(defaults)
        assertEquals(listOf("predict_30_days", "c1"), resolved.map { it.id })
    }
}

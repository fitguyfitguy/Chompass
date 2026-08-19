package app.chompass.services.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiSseTest {
    @Test
    fun jsonObjectIsNotSse() {
        assertFalse(AiSse.looksLikeSse("""{"choices":[]}"""))
        assertFalse(
            AiSse.looksLikeSse(
                """
                {
                  "choices": [],
                  "_streamed": true
                }
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun dataPrefixIsSse() {
        assertTrue(AiSse.looksLikeSse("data: {\"choices\":[]}\n\ndata: [DONE]\n"))
        assertTrue(AiSse.looksLikeSse("event: message\ndata: {}\n\n"))
        assertTrue(AiSse.looksLikeSse(": ping\n\ndata: {}\n\n"))
    }

    @Test
    fun payloadsSkipDoneAndEmpty() {
        val raw = """
            data: {"a":1}

            data: 

            data: [DONE]
            data: {"b":2}
        """.trimIndent()
        assertEquals(listOf("""{"a":1}""", """{"b":2}"""), AiSse.payloads(raw))
    }
}

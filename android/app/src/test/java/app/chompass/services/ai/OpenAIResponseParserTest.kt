package app.chompass.services.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAIResponseParserTest {
    private val greeting =
        "Greetings! I'm Coach, your nutrition and weight-change assistant. I'd love to help you with your goals. What can I do for you today?"

    @Test
    fun readsContentWithoutTreatingReasoningAsTheAnswer() {
        val response = OpenAIResponseParser.parse(
            """{"choices":[{"finish_reason":"stop","message":{"reasoning":"private analysis","content":"  {\"calories\":100}  "}}]}""",
        )

        assertEquals("{\"calories\":100}", response.text)
        assertTrue(response.hasReasoning)
        assertFalse(response.wasTruncated)
    }

    @Test
    fun requestsCompactRetryWhenReasoningUsesTheWholeResponse() {
        val response = OpenAIResponseParser.parse(
            """{"choices":[{"finish_reason":"stop","message":{"reasoning_details":[{"type":"reasoning.text","text":"working"}],"content":null}}]}""",
        )

        assertNull(response.text)
        assertTrue(response.needsCompactRetry)
    }

    @Test
    fun reportsLengthFinishAsTruncationEvenWithPartialContent() {
        val response = OpenAIResponseParser.parse(
            """{"choices":[{"finish_reason":"length","message":{"content":"{\"calories\":"}}]}""",
        )

        assertTrue(response.wasTruncated)
        assertTrue(response.needsCompactRetry)
    }

    @Test
    fun readsOmniRouteDashboardGreeting() {
        val body = javaClass.classLoader!!
            .getResourceAsStream("ai/omniroute-streamed-greeting.json")!!
            .bufferedReader()
            .readText()
        val response = OpenAIResponseParser.parse(body)

        assertEquals(greeting, response.text)
        assertFalse(response.hasReasoning)
        assertFalse(response.wasTruncated)
        assertEquals("stop", response.finishReason)
        assertNull(response.toolCalls)
        assertNotNull(response.messageJson)
    }

    @Test
    fun readsSseDeltaChunksAsGreeting() {
        val body = """
            data: {"choices":[{"index":0,"delta":{"role":"assistant","content":"Greetings"},"finish_reason":null}]}

            data: {"choices":[{"index":0,"delta":{"content":"! I'm Coach, your nutrition and weight-change assistant. I'd love to help you with your goals. What can I do for you today?"},"finish_reason":null}]}

            data: {"choices":[{"index":0,"delta":{},"finish_reason":"stop"}]}

            data: [DONE]
        """.trimIndent()

        val response = OpenAIResponseParser.parseBody(body)
        assertEquals(greeting, response.text)
        assertEquals("stop", response.finishReason)
        assertFalse(response.hasReasoning)
    }

    @Test
    fun readsSingleSseEventWrappingAssembledMessage() {
        val body = """
            data: {"choices":[{"message":{"role":"assistant","content":"$greeting"},"finish_reason":"stop"}],"_streamed":true}

            data: [DONE]
        """.trimIndent()

        val response = OpenAIResponseParser.parseBody(body)
        assertEquals(greeting, response.text)
        assertEquals("stop", response.finishReason)
        assertNotNull(response.messageJson)
    }

    @Test
    fun readsDeltaOnlyJsonWithoutMessage() {
        val body =
            """{"choices":[{"delta":{"role":"assistant","content":"$greeting"},"finish_reason":"stop"}],"_streamed":true}"""
        val response = OpenAIResponseParser.parseBody(body)
        assertEquals(greeting, response.text)
        assertEquals("stop", response.finishReason)
        assertNull(response.messageJson)
        assertNull(response.toolCalls)
    }

    @Test
    fun readsLegacyChoiceText() {
        val body = """{"choices":[{"text":"$greeting","finish_reason":"stop"}]}"""
        val response = OpenAIResponseParser.parseBody(body)
        assertEquals(greeting, response.text)
    }

    @Test
    fun readsContentPartsArray() {
        val body =
            """{"choices":[{"message":{"role":"assistant","content":[{"type":"text","text":"$greeting"}]},"finish_reason":"stop"}]}"""
        val response = OpenAIResponseParser.parseBody(body)
        assertEquals(greeting, response.text)
    }

    @Test
    fun fallsBackToChoiceTextWhenMessageContentIsNull() {
        val body =
            """{"choices":[{"message":{"role":"assistant","content":null},"text":"$greeting","finish_reason":"stop"}]}"""
        val response = OpenAIResponseParser.parseBody(body)
        assertEquals(greeting, response.text)
        assertNotNull(response.messageJson)
    }

    @Test
    fun keepsToolCallsOnMessageForCoachLoop() {
        val body = """
            {"choices":[{"message":{"role":"assistant","content":null,"tool_calls":[{"id":"call_1","type":"function","function":{"name":"get_data_summary","arguments":"{}"}}]},"finish_reason":"tool_calls"}]}
        """.trimIndent()
        val response = OpenAIResponseParser.parseBody(body)
        assertNull(response.text)
        assertNotNull(response.toolCalls)
        assertEquals(1, response.toolCalls!!.length())
        assertEquals("call_1", response.toolCalls!!.getJSONObject(0).getString("id"))
    }
}

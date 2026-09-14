package app.chompass.services.ai

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * #97: local reasoning models (Qwen-class via LM Studio / Ollama / Unsloth)
 * inline `<think>` blocks in `content`; the food-JSON parser cannot see past
 * them, so the assembled reply is stripped before parsing.
 */
class OpenAIThinkStripTest {
    @Test
    fun stripsClosedThinkBlockBeforeJson() {
        val text = "<think>The user wants macros. 200 kcal seems right.</think>{\"calories\":200}"
        assertEquals("{\"calories\":200}", OpenAICompatibleClient.stripThinking(text))
    }

    @Test
    fun stripsThinkingVariantAndMultilineBlocks() {
        val text = "<thinking>\nline one\nline two\n</thinking>\n{\"name\":\"toast\"}"
        assertEquals("{\"name\":\"toast\"}", OpenAICompatibleClient.stripThinking(text))
    }

    @Test
    fun cutsUnterminatedTrailingBlockToTheEnd() {
        val text = "{\"calories\":200}<think>half-finished reasoning that never"
        assertEquals("{\"calories\":200}", OpenAICompatibleClient.stripThinking(text))
    }

    @Test
    fun jsonAfterUnterminatedBlockIsNotKept() {
        // Everything after an unclosed tag is reasoning by contract; JSON the
        // model emitted inside it must not leak into the parser.
        val text = "<think>musings {\"calories\":9999} more"
        assertEquals("", OpenAICompatibleClient.stripThinking(text))
    }

    @Test
    fun leavesOrdinaryRepliesUntouched() {
        val text = "```json\n{\"calories\":200}\n```"
        assertEquals(text, OpenAICompatibleClient.stripThinking(text))
    }

    @Test
    fun multipleBlocksAllStripped() {
        val text = "<think>a</think>{\"a\":1}<think>b</think>"
        assertEquals("{\"a\":1}", OpenAICompatibleClient.stripThinking(text))
    }
}

package org.codeberg.fitguy.nofud.services.ai

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.ToolSet
import com.google.ai.edge.litertlm.tool
import java.io.Closeable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** [Message.contents] is a list of [Content] parts; concatenates only the text parts. */
fun Message.plainText(): String =
    contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text }

/**
 * Debug-only wrapper around Google AI Edge's LiteRT-LM engine, used by
 * [org.codeberg.fitguy.nofud.services.OnDeviceLlmSmokeTest]. Deliberately NOT
 * wired into [org.codeberg.fitguy.nofud.models.AIProvider] /
 * [FoodAnalysisService] / [ChatService] dispatch — this is a standalone
 * prototype to validate LiteRT-LM on real hardware before any of that
 * production plumbing is built. API surface (Engine/EngineConfig/Backend/
 * Conversation/ToolSet) is per developers.google.com/edge/litert-lm/android
 * as of this writing; verify against current docs if this fails to compile
 * against whatever `litertlm-android` version resolves.
 */
class OnDeviceLlmClient(private val modelPath: String) : Closeable {

    private var engine: Engine? = null

    /** Loads the model on first call; a no-op (returns 0) if already loaded. Returns load time in ms. */
    suspend fun ensureLoaded(): Long = withContext(Dispatchers.Default) {
        if (engine != null) return@withContext 0L
        val start = System.nanoTime()
        val loaded = Engine(EngineConfig(modelPath = modelPath, backend = Backend.CPU()))
        loaded.initialize()
        engine = loaded
        (System.nanoTime() - start) / 1_000_000
    }

    /** Single-shot prompt/response, no tool calling. Used for the Tier A (`analyzeText`) scenario. */
    suspend fun generate(systemPrompt: String, userPrompt: String): String = withContext(Dispatchers.Default) {
        val active = engine ?: error("Engine not initialized — call ensureLoaded() first")
        active.createConversation(
            ConversationConfig(systemInstruction = Contents.of(systemPrompt))
        ).use { conversation -> conversation.sendMessage(userPrompt).plainText() }
    }

    /**
     * Opens a tool-enabled conversation for the Tier C (Coach) scenarios. LiteRT-LM's
     * native function-calling drives the tool round-trip internally when [toolSet]'s
     * `@Tool`-annotated functions are invoked — callers just send messages and read
     * the final response; per-round detail is logged inside the tool functions themselves.
     */
    suspend fun createToolConversation(systemPrompt: String, toolSet: ToolSet): Conversation =
        withContext(Dispatchers.Default) {
            val active = engine ?: error("Engine not initialized — call ensureLoaded() first")
            active.createConversation(
                ConversationConfig(
                    systemInstruction = Contents.of(systemPrompt),
                    tools = listOf(tool(toolSet))
                )
            )
        }

    override fun close() {
        engine?.close()
        engine = null
    }
}

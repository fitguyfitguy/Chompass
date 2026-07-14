package org.codeberg.fitguy.nofud.services.ai

import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.ExperimentalFlags
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.ToolSet
import com.google.ai.edge.litertlm.tool
import java.io.Closeable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Shared log tag for the on-device LLM smoke test harness. */
const val ON_DEVICE_LLM_TAG = "FudOnDeviceLlm"

/** [Message.contents] is a list of [Content] parts; concatenates only the text parts. */
fun Message.plainText(): String =
    contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text }

/**
 * Debug-only wrapper around Google AI Edge's LiteRT-LM engine, used by
 * [org.codeberg.fitguy.nofud.services.OnDeviceLlmSmokeTest]. Deliberately NOT
 * wired into [org.codeberg.fitguy.nofud.models.AIProvider] /
 * [FoodAnalysisService] / [ChatService] dispatch.
 */
class OnDeviceLlmClient(
    private val modelPath: String,
    private val cacheDir: String,
    private val backend: Backend = Backend.GPU(),
    private val enableMtp: Boolean = false,
) : Closeable {

    val backendName: String get() = backend.name

    private var engine: Engine? = null

    /** Loads the model on first call; a no-op (returns 0) if already loaded. Returns load time in ms. */
    suspend fun ensureLoaded(): Long = withContext(Dispatchers.Default) {
        if (engine != null) return@withContext 0L
        val start = System.nanoTime()
        val config = EngineConfig(
            modelPath = modelPath,
            backend = backend,
            cacheDir = cacheDir,
        )
        Log.i(
            ON_DEVICE_LLM_TAG,
            "op=ondevice_llm phase=engineInit_begin backend=$backendName mtp=$enableMtp " +
                "note=gpu_cold_init_may_take_several_minutes"
        )
        try {
            if (backend is Backend.GPU && enableMtp) {
                enableGpuMtpIfAvailable()
            }
            coroutineScope {
                val heartbeat = launch {
                    var tick = 0
                    while (isActive) {
                        delay(15_000)
                        tick++
                        val elapsedMs = (System.nanoTime() - start) / 1_000_000
                        Log.i(
                            ON_DEVICE_LLM_TAG,
                            "op=ondevice_llm phase=engineInit_waiting backend=$backendName tick=$tick elapsedMs=$elapsedMs"
                        )
                    }
                }
                try {
                    val loaded = Engine(config)
                    loaded.initialize()
                    engine = loaded
                } finally {
                    heartbeat.cancel()
                }
            }
        } catch (e: Throwable) {
            throw IllegalStateException("LiteRT-LM engine init failed (backend=$backendName): ${e.message}", e)
        }
        val loadMs = (System.nanoTime() - start) / 1_000_000
        Log.i(ON_DEVICE_LLM_TAG, "op=ondevice_llm phase=engineInit backend=$backendName cacheDir=$cacheDir ms=$loadMs")
        loadMs
    }

    /** Single-shot prompt/response, no tool calling. Used for Tier A (`analyzeText`) scenarios. */
    suspend fun generate(systemPrompt: String, userPrompt: String): String = withContext(Dispatchers.Default) {
        val active = engine ?: error("Engine not initialized — call ensureLoaded() first")
        active.createConversation(
            ConversationConfig(systemInstruction = Contents.of(systemPrompt))
        ).use { conversation -> conversation.sendMessage(userPrompt).plainText() }
    }

    /**
     * Opens a tool-enabled conversation for Tier C (Coach) scenarios. LiteRT-LM's
     * native function-calling drives the tool round-trip internally.
     */
    suspend fun createToolConversation(systemPrompt: String, toolSet: ToolSet): Conversation =
        withContext(Dispatchers.Default) {
            val active = engine ?: error("Engine not initialized — call ensureLoaded() first")
            active.createConversation(
                ConversationConfig(
                    systemInstruction = Contents.of(systemPrompt),
                    tools = listOf(tool(toolSet)),
                )
            )
        }

    override fun close() {
        engine?.close()
        engine = null
    }

    companion object {
        @OptIn(ExperimentalApi::class)
        private fun enableGpuMtpIfAvailable() {
            runCatching {
                ExperimentalFlags.enableSpeculativeDecoding = true
                Log.i(ON_DEVICE_LLM_TAG, "op=ondevice_llm phase=engineConfig mtp=enabled")
                Log.w(
                    ON_DEVICE_LLM_TAG,
                    "op=ondevice_llm phase=engineConfig mtp=warning note=token_budget_bug_2816 " +
                        "draft+rejected tokens may count toward output limit; " +
                        "double maxOutputToken when API supports it (not in litertlm 0.14.0 ConversationConfig)"
                )
            }.onFailure {
                Log.w(ON_DEVICE_LLM_TAG, "op=ondevice_llm phase=engineConfig mtp=skipped err=${it.message}")
            }
        }

        fun backendFromIntentValue(value: String?): Backend = when (value?.lowercase()) {
            "cpu" -> Backend.CPU(numOfThreads = 4)
            else -> Backend.GPU()
        }

        fun backendLabel(backend: Backend): String = when (backend) {
            is Backend.CPU -> "cpu"
            is Backend.GPU -> "gpu"
            else -> backend.name.lowercase()
        }
    }
}

package app.chompass.services.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Response
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Reads Server-Sent Event bodies from AI providers. Invokes [onData] for each
 * `data:` payload (excluding the terminal `[DONE]` marker).
 */
object AiSse {
    /** True when [raw] is an SSE stream, not a single JSON object. */
    fun looksLikeSse(raw: String): Boolean {
        val trimmed = raw.trimStart()
        if (trimmed.startsWith("data:") || trimmed.startsWith("event:")) return true
        if (trimmed.startsWith(":") && trimmed.contains("\ndata:")) return true
        return trimmed.contains('\n') &&
            trimmed.lineSequence().any { it.startsWith("data:") }
    }

    /** `data:` payloads from an already-buffered SSE body, skipping `[DONE]`. */
    fun payloads(raw: String): List<String> =
        raw.lineSequence().mapNotNull { line ->
            val trimmed = line.trimEnd('\r')
            if (!trimmed.startsWith("data:")) return@mapNotNull null
            val payload = trimmed.removePrefix("data:").trim()
            payload.takeIf { it.isNotEmpty() && it != "[DONE]" }
        }.toList()

    suspend fun read(
        response: Response,
        onData: (String) -> Unit,
    ) = withContext(Dispatchers.IO) {
        response.use { resp ->
            val body = resp.body ?: throw AiError.InvalidResponse
            BufferedReader(InputStreamReader(body.byteStream(), Charsets.UTF_8)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val raw = line ?: continue
                    if (!raw.startsWith("data:")) continue
                    val payload = raw.removePrefix("data:").trim()
                    if (payload.isEmpty() || payload == "[DONE]") continue
                    onData(payload)
                }
            }
        }
    }
}

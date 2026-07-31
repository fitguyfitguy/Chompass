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

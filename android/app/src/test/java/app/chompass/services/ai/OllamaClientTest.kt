package app.chompass.services.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OllamaClientTest {
    @Test
    fun parseTags_readsIdAndDetails() {
        val body = """
            {
              "models": [
                {
                  "name": "llama3.2:latest",
                  "model": "llama3.2:latest",
                  "size": 2019393189,
                  "digest": "a80c4f17acd5",
                  "details": {
                    "parameter_size": "3.2B",
                    "quantization_level": "Q4_K_M",
                    "family": "llama"
                  }
                },
                {
                  "name": "mistral:7b",
                  "details": {
                    "parameter_size": "7B"
                  }
                }
              ]
            }
        """.trimIndent()
        val models = OllamaClient.parseTags(body)
        assertEquals(2, models.size)
        assertEquals("llama3.2:latest", models[0].id)
        assertEquals("3.2B", models[0].parameterSize)
        assertEquals("Q4_K_M", models[0].quantization)
        assertEquals("3.2B · Q4_K_M", models[0].subtitle)
        assertEquals("mistral:7b", models[1].id)
        assertEquals("7B", models[1].parameterSize)
        assertNull(models[1].quantization)
        assertEquals("7B", models[1].subtitle)
    }

    @Test
    fun parseTags_emptyOrInvalid_isEmpty() {
        assertTrue(OllamaClient.parseTags("{}").isEmpty())
        assertTrue(OllamaClient.parseTags("not-json").isEmpty())
        assertTrue(OllamaClient.parseTags("""{"models":[]}""").isEmpty())
    }

    @Test
    fun tagsUrl_stripsV1() {
        assertEquals("http://localhost:11434/api/tags", OllamaClient.tagsUrl("http://localhost:11434/v1"))
        assertEquals("http://localhost:11434/api/tags", OllamaClient.tagsUrl("http://localhost:11434/v1/"))
        assertEquals("http://192.168.1.10:11434/api/tags", OllamaClient.tagsUrl("http://192.168.1.10:11434"))
    }
}

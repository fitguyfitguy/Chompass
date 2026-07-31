package app.chompass.services.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class AiErrorTest {
    private val keyRejected = "Your API key was rejected. Open Settings → AI Provider and re-paste a valid key."

    @Test
    fun badApiKeyOn400ReturnsKeyRejectedGuidance() {
        assertEquals(
            keyRejected,
            friendlyMessage(400, "API key not valid. Please pass a valid API key.")
        )
    }

    @Test
    fun keyInvalidMarkersOn400AreCaseInsensitive() {
        assertEquals(keyRejected, friendlyMessage(400, "api KEY not VALID. Please pass a valid API key."))
        assertEquals(keyRejected, friendlyMessage(400, "reason: API_KEY_INVALID"))
        assertEquals(keyRejected, friendlyMessage(400, "API key expired"))
        assertEquals(keyRejected, friendlyMessage(400, "reason: API_KEY_EXPIRED"))
    }

    @Test
    fun unauthorizedAndForbiddenReturnKeyRejectedGuidance() {
        assertEquals(keyRejected, friendlyMessage(401, "Unauthorized"))
        assertEquals(keyRejected, friendlyMessage(403, "Forbidden"))
    }

    @Test
    fun rateLimitReturnsRateLimitMessage() {
        assertEquals(
            "Rate limit hit on your API key. Wait a minute, or switch to another provider in Settings → AI Provider.",
            friendlyMessage(429, "Too many requests")
        )
    }

    @Test
    fun overloadedStatusesReturnOverloadedMessage() {
        val overloaded = "The AI provider is overloaded right now. We retried a few times — please try again in a minute, or switch to a different provider/model in Settings → AI Provider."

        assertEquals(overloaded, friendlyMessage(503, "Service unavailable"))
        assertEquals(overloaded, friendlyMessage(529, "Overloaded"))
    }

    @Test
    fun nonKeyBadRequestReturnsRawMessage() {
        assertEquals(
            "Invalid JSON payload received.",
            friendlyMessage(400, "Invalid JSON payload received.")
        )
    }

    @Test
    fun locationUnsupportedOn400ReturnsGuidance() {
        assertEquals(
            locationUnsupported,
            friendlyMessage(400, "User location is not supported for the API use.")
        )
    }

    @Test
    fun locationUnsupportedFailedPreconditionReturnsGuidance() {
        assertEquals(
            locationUnsupported,
            friendlyMessage(
                400,
                "FAILED_PRECONDITION: User location is not supported for the API use."
            )
        )
    }

    @Test
    fun freeTierCountryRestrictionReturnsGuidance() {
        assertEquals(
            locationUnsupported,
            friendlyMessage(
                400,
                "Gemini API free tier is not available in your country. Please enable billing on your project in Google AI Studio."
            )
        )
    }

    @Test
    fun unmappedStatusReturnsRawMessage() {
        assertEquals("Internal error", friendlyMessage(500, "Internal error"))
    }

    private val locationUnsupported =
        "Gemini isn't available from this network location (country/IP). If you use a VPN, turn it off or switch to a residential exit — datacenter/non-residential VPN IPs are often blocked. Or enable billing on the Google AI Studio project, try another network, or switch provider in Settings → AI Provider."
}

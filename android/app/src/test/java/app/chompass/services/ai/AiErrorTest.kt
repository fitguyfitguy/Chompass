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
            "Rate limit hit on your API key. Wait a minute, or switch to another provider in Settings → AI Provider. On the free tier, the Flash-Lite model has the highest quota.",
            friendlyMessage(429, "Too many requests")
        )
    }

    @Test
    fun overloadedStatusesReturnOverloadedMessage() {
        val overloaded = "The AI provider is overloaded right now. We retried a few times. Please try again in a minute, or switch to a different provider/model in Settings → AI Provider."

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

    @Test
    fun modelNotFoundOn404ReturnsGuidance() {
        assertEquals(
            modelUnavailable,
            friendlyMessage(404, "404 not found: models/gemini-3.7-flash is not found for API version v1beta")
        )
    }

    @Test
    fun modelNotFoundOn400ReturnsGuidance() {
        assertEquals(
            modelUnavailable,
            friendlyMessage(400, "MODEL_NOT_FOUND: models/gemini-2.5-pro is not found")
        )
    }

    @Test
    fun modelNotSupportedMarkerReturnsGuidance() {
        assertEquals(
            modelUnavailable,
            friendlyMessage(400, "models/gemini-2.5-pro is not supported for generateContent")
        )
    }

    @Test
    fun notFoundOn404WithoutMarkerReturnsRaw() {
        assertEquals("404 page gone", friendlyMessage(404, "404 page gone"))
    }

    private val locationUnsupported =
        "Gemini isn't available from this network location (country/IP). If you use a VPN, turn it off or switch to a residential exit. Datacenter/non-residential VPN IPs are often blocked. Or enable billing on the Google AI Studio project, try another network, or switch provider in Settings → AI Provider."
    private val modelUnavailable =
        "Your provider couldn't find this model. It may be paid-only on the free tier, restricted in your region, or the endpoint may be wrong. Switch to the default Flash model in Settings → AI Provider, or enable billing on your AI Studio project."
}

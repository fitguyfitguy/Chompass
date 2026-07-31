package app.chompass.services.ai

sealed class AiError(message: String) : Exception(message) {
    object NoApiKey : AiError("No API key configured. Add your key in Settings → AI Provider.")
    object ImageConversionFailed : AiError("Failed to process the image.")
    class Network(cause: Throwable) : AiError("Network error: ${cause.localizedMessage}")
    object InvalidResponse : AiError("Could not understand the AI response. Please try again.")
    class Api(raw: String) : AiError(raw)
    class InvalidUrl(val url: String) : AiError("Invalid API URL. Check your provider settings.")
    object OnDeviceModelNotDownloaded : AiError("On-device model not downloaded yet. Open Settings → AI Provider → Model to download it.")
    object OnDeviceUnsupportedDevice : AiError("This device doesn't meet the requirements for on-device AI. Choose a cloud provider in Settings → AI Provider.")
    object OnDeviceLowMemory : AiError("Not enough free memory for on-device photo analysis right now. Try the smaller E2B model, close other apps, or switch providers in Settings → AI Provider.")
}

internal fun friendlyMessage(status: Int, raw: String): String {
    val keyRejected = "Your API key was rejected. Open Settings → AI Provider and re-paste a valid key."
    val locationUnsupported =
        "Gemini isn't available from this network location (country/IP). If you use a VPN, turn it off or switch to a residential exit. Datacenter/non-residential VPN IPs are often blocked. Or enable billing on the Google AI Studio project, try another network, or switch provider in Settings → AI Provider."
    val hasKeyInvalidMarker =
        raw.contains("api key not valid", ignoreCase = true) ||
            raw.contains("api_key_invalid", ignoreCase = true) ||
            raw.contains("api key expired", ignoreCase = true) ||
            raw.contains("api_key_expired", ignoreCase = true)
    val hasLocationUnsupportedMarker =
        raw.contains("location is not supported", ignoreCase = true) ||
            raw.contains("not available in your country", ignoreCase = true)

    return when (status) {
        503, 529 -> "The AI provider is overloaded right now. We retried a few times. Please try again in a minute, or switch to a different provider/model in Settings → AI Provider."
        429 -> "Rate limit hit on your API key. Wait a minute, or switch to another provider in Settings → AI Provider."
        400 -> when {
            hasKeyInvalidMarker -> keyRejected
            hasLocationUnsupportedMarker -> locationUnsupported
            else -> raw
        }
        401, 403 -> keyRejected
        else -> if (hasLocationUnsupportedMarker) locationUnsupported else raw
    }
}

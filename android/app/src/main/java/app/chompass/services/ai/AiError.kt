package app.chompass.services.ai

import android.content.Context
import androidx.annotation.StringRes
import app.chompass.R

/**
 * User-facing AI errors. Subclasses carry a [messageRes] so the UI layer can
 * show the user's language without threading a Context through the service
 * stack; [message] stays English (logs / raw fallback when [messageRes] is 0,
 * e.g. provider-supplied text in [Api]).
 */
sealed class AiError(
    message: String,
    @StringRes val messageRes: Int = 0,
    val formatArgs: Array<out Any> = emptyArray(),
) : Exception(message) {
    object NoApiKey : AiError("No API key configured. Add your key in Settings → AI Provider.", messageRes = R.string.ai_error_no_api_key)
    object ImageConversionFailed : AiError("Failed to process the image.", messageRes = R.string.ai_error_image_conversion_failed)
    class Network(cause: Throwable) : AiError(
        "Network error: ${cause.localizedMessage}",
        messageRes = R.string.ai_error_network_format,
        formatArgs = arrayOf(cause.localizedMessage.orEmpty()),
    )
    object InvalidResponse : AiError("Could not understand the AI response. Please try again.", messageRes = R.string.ai_error_invalid_response)
    class Api(raw: String, @StringRes messageRes: Int = 0) : AiError(raw, messageRes = messageRes)
    class InvalidUrl(val url: String) : AiError("Invalid API URL. Check your provider settings.", messageRes = R.string.ai_error_invalid_url)
    object OnDeviceModelNotDownloaded : AiError("On-device model not downloaded yet. Open Settings → AI Provider → Model to download it.", messageRes = R.string.ai_error_on_device_model_not_downloaded)
    object OnDeviceUnsupportedDevice : AiError("This device doesn't meet the requirements for on-device AI. Choose a cloud provider in Settings → AI Provider.", messageRes = R.string.ai_error_on_device_unsupported_device)
    object OnDeviceLowMemory : AiError("Not enough free memory for on-device photo analysis right now. Try the smaller E2B model, close other apps, or switch providers in Settings → AI Provider.", messageRes = R.string.ai_error_on_device_low_memory)
}

/**
 * Resolve an [AiError] to the user's language at the UI boundary. Falls back
 * to the raw message (provider text) when the type carries no resource.
 */
fun AiError.userMessage(context: Context): String =
    if (messageRes != 0) context.getString(messageRes, *formatArgs) else message.orEmpty()

internal fun friendlyMessage(status: Int, raw: String): String {
    val keyRejected = "Your API key was rejected. Open Settings → AI Provider and re-paste a valid key."
    val locationUnsupported =
        "Gemini isn't available from this network location (country/IP). If you use a VPN, turn it off or switch to a residential exit. Datacenter/non-residential VPN IPs are often blocked. Or enable billing on the Google AI Studio project, try another network, or switch provider in Settings → AI Provider."
    val modelUnavailable =
        "Your provider couldn't find this model. It may be paid-only on the free tier, restricted in your region, or the endpoint may be wrong. Switch to the default Flash model in Settings → AI Provider, or enable billing on your AI Studio project."
    val hasKeyInvalidMarker =
        raw.contains("api key not valid", ignoreCase = true) ||
            raw.contains("api_key_invalid", ignoreCase = true) ||
            raw.contains("api key expired", ignoreCase = true) ||
            raw.contains("api_key_expired", ignoreCase = true)
    val hasLocationUnsupportedMarker =
        raw.contains("location is not supported", ignoreCase = true) ||
            raw.contains("not available in your country", ignoreCase = true)
    val hasModelNotFoundMarker =
        raw.contains("not found", ignoreCase = true) ||
            raw.contains("model_not_found", ignoreCase = true) ||
            raw.contains("not supported for", ignoreCase = true)

    return when (status) {
        503, 529 -> "The AI provider is overloaded right now. We retried a few times. Please try again in a minute, or switch to a different provider/model in Settings → AI Provider."
        429 -> "Rate limit hit on your API key. Wait a minute, or switch to another provider in Settings → AI Provider. On the free tier, the Flash-Lite model has the highest quota."
        400 -> when {
            hasKeyInvalidMarker -> keyRejected
            hasLocationUnsupportedMarker -> locationUnsupported
            hasModelNotFoundMarker -> modelUnavailable
            else -> raw
        }
        404 -> if (hasModelNotFoundMarker) modelUnavailable else raw
        401, 403 -> keyRejected
        else -> when {
            hasLocationUnsupportedMarker -> locationUnsupported
            hasModelNotFoundMarker -> modelUnavailable
            else -> raw
        }
    }
}

/**
 * Localizable counterpart of [friendlyMessage]: maps the same status/marker
 * rules to a string resource (0 = keep the raw provider text). The English
 * [friendlyMessage] is kept for logs and for the raw-message fallback.
 */
internal fun friendlyMessageRes(status: Int, raw: String): Int = when (status) {
    503, 529 -> R.string.ai_error_provider_overloaded
    429 -> R.string.ai_error_rate_limit
    400 -> when {
        raw.contains("api key not valid", ignoreCase = true) ||
            raw.contains("api_key_invalid", ignoreCase = true) ||
            raw.contains("api key expired", ignoreCase = true) ||
            raw.contains("api_key_expired", ignoreCase = true) -> R.string.ai_error_key_rejected
        raw.contains("location is not supported", ignoreCase = true) ||
            raw.contains("not available in your country", ignoreCase = true) -> R.string.ai_error_location_unsupported
        modelNotFoundMarker(raw) -> R.string.ai_error_model_unavailable
        else -> 0
    }
    404 -> if (modelNotFoundMarker(raw)) R.string.ai_error_model_unavailable else 0
    401, 403 -> R.string.ai_error_key_rejected
    else -> when {
        raw.contains("location is not supported", ignoreCase = true) ||
            raw.contains("not available in your country", ignoreCase = true) -> R.string.ai_error_location_unsupported
        modelNotFoundMarker(raw) -> R.string.ai_error_model_unavailable
        else -> 0
    }
}

private fun modelNotFoundMarker(raw: String): Boolean =
    raw.contains("not found", ignoreCase = true) ||
        raw.contains("model_not_found", ignoreCase = true) ||
        raw.contains("not supported for", ignoreCase = true)

/**
 * Maps common connection failures on custom endpoints to actionable hints instead of raw
 * platform exceptions. Cleartext is blocked by the release network-security-config
 * ("CLEARTEXT communication … not permitted by network security policy", OkHttp
 * [java.net.UnknownServiceException]); untrusted self-signed certs surface as
 * [java.security.cert.CertPathValidatorException] wrapped in an SSLHandshakeException.
 */
internal fun connectionFailureMessage(cause: Throwable): String {
    var t: Throwable? = cause
    while (t != null) {
        val message = t.message.orEmpty()
        when {
            message.contains("CLEARTEXT communication", ignoreCase = true) ||
                message.contains("not permitted by network security policy", ignoreCase = true) ->
                return "Cleartext HTTP is blocked in the release build. Use https:// for custom endpoints — a certificate you install on this phone is trusted — or use the debug build for plain http."

            t is java.security.cert.CertPathValidatorException ||
                message.contains("Trust anchor", ignoreCase = true) ->
                return "The server's certificate isn't trusted. Install its CA certificate on this phone (Settings → Security → Install a certificate) and restart the app; custom endpoints trust your installed certificates."
        }
        t = t.cause
    }
    return "Network error: ${cause.localizedMessage}"
}

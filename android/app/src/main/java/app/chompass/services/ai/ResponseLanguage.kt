package app.chompass.services.ai

import java.util.Locale

/**
 * Language the model should answer in — the app's UI language. Per-app language
 * (Android 13+ settings) and the system locale both surface through
 * [Locale.getDefault], so no Context is needed. Returns the English display
 * name ("German", "Japanese") because the prompts themselves are English, or
 * null for English locales so English behavior is unchanged.
 */
internal fun nonEnglishResponseLanguage(): String? {
    val locale = Locale.getDefault()
    if (locale.language.isEmpty() || locale.language == "en") return null
    return locale.getDisplayLanguage(Locale.ENGLISH).takeIf { it.isNotEmpty() }
}

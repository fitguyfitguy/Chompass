package app.chompass.utils

import android.annotation.SuppressLint
import android.app.LocaleManager
import android.content.Context
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import androidx.core.os.LocaleListCompat
import java.util.Locale

/**
 * Applies per-app language setting.
 * - Android 13+ (API 33): Uses LocaleManager.setApplicationLocales() — persists across restarts
 * - Android 8-12 (API 26-32): Updates Configuration.locales + recreates activity (legacy approach)
 *
 * Empty string = follow system language (default behavior).
 */
object LocaleHelper {
    /**
     * Apply the selected app language.
     * @param context Application context
     * @param languageTag Empty string for system default, or BCP-47 tag like "de", "zh-CN"
     */
    @SuppressLint("DiscouragedApi", "NewApi")
    fun apply(context: Context, languageTag: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            applyModern(context, languageTag)
        } else {
            applyLegacy(context, languageTag)
        }
    }

    /** Android 13+ (API 33+): LocaleManager.setApplicationLocales() */
    @SuppressLint("NewApi")
    private fun applyModern(context: Context, languageTag: String) {
        val localeManager = context.getSystemService(LocaleManager::class.java)
        val localeListCompat = if (languageTag.isBlank()) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(languageTag)
        }
        // Convert LocaleListCompat to framework LocaleList
        localeManager.setApplicationLocales(localeListCompat.toFrameworkLocaleList())
    }

    /** Android 8-12 (API 26-32): update Configuration; caller recreates the activity. */
    private fun applyLegacy(context: Context, languageTag: String) {
        val configuration = context.resources.configuration
        configuration.setLocales(localeListForLanguageTag(languageTag))
        context.createConfigurationContext(configuration)
    }

    /**
     * Convert LocaleListCompat to framework LocaleList.
     */
    private fun LocaleListCompat.toFrameworkLocaleList(): LocaleList {
        val locales = mutableListOf<Locale>()
        for (i in 0 until size()) {
            get(i)?.let { locales.add(it) }
        }
        return LocaleList(*locales.toTypedArray())
    }
}

/**
 * Locale list to apply for a language tag. Blank = follow the system.
 * Never returns an empty list: `setLocales(empty)` makes `locales[0]` null
 * on API 26-32 and crashes Home (#43).
 */
internal fun localeListForLanguageTag(languageTag: String): LocaleList {
    if (languageTag.isBlank()) return systemLocaleList()
    val locales = mutableListOf<Locale>()
    val compat = LocaleListCompat.forLanguageTags(languageTag)
    for (i in 0 until compat.size()) {
        compat.get(i)?.let { locales.add(it) }
    }
    if (locales.isEmpty()) return systemLocaleList()
    return LocaleList(*locales.toTypedArray())
}

internal fun systemLocaleList(): LocaleList {
    val system = Resources.getSystem().configuration.locales
    if (system.size() > 0) {
        val first = system[0]
        if (first != null) return system
    }
    return LocaleList(Locale.getDefault() ?: Locale.US)
}

package app.chompass.utils

import android.annotation.SuppressLint
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.core.os.LocaleListCompat
import java.util.Locale

/**
 * Applies per-app language setting.
 * - Android 13+ (API 33): Uses LocaleManager.setApplicationLocales() — persists across restarts
 * - Android 8-12 (API 26-32): the OS cannot localize non-activity contexts, so the selected
 *   tag is cached in [legacyTag] and every context that resolves user-facing strings wraps
 *   itself via [wrapIfLegacy] (activities through attachBaseContext, notifications, widgets,
 *   receivers). The activity re-creates on switch; services/receivers wrap at call time.
 *
 * Empty string = follow system language (default behavior).
 */
object LocaleHelper {
    /** Cached legacy (API < 33) app language; null = follow system. Set at app start and on switch. */
    @Volatile
    var legacyTag: String? = null

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
            // API < 33 has no persistent per-app locale store: refresh the cache.
            // Activities re-create and re-attach with the new tag; services,
            // receivers, and widgets pick it up on their next wrap.
            legacyTag = languageTag.takeIf { it.isNotBlank() }
        }
    }

    /**
     * Wrap [context] with the cached legacy locale (Android 8-12). Identity on
     * API 33+ (LocaleManager already localizes every context) and when no tag
     * is cached (follow system).
     */
    fun wrapIfLegacy(context: Context): Context {
        val tag = legacyTag
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU || tag.isNullOrBlank()) {
            return context
        }
        val localeList = localeListForLanguageTag(tag)
        if (localeList.isEmpty) {
            // Unparseable tag: follow the system instead of setting an empty
            // list (`locales[0]` would be null — crash #43).
            return context
        }
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocales(localeList)
        return context.createConfigurationContext(configuration)
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
 * Locale list for a non-blank language tag. Callers guard blank/unparseable
 * tags ([wrapIfLegacy] falls back to identity), so an empty result here means
 * "do not touch locales" rather than a crash.
 */
internal fun localeListForLanguageTag(languageTag: String): LocaleList {
    val locales = mutableListOf<Locale>()
    val compat = LocaleListCompat.forLanguageTags(languageTag)
    for (i in 0 until compat.size()) {
        compat.get(i)?.let { locales.add(it) }
    }
    return LocaleList(*locales.toTypedArray())
}

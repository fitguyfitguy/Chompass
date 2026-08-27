package app.chompass.data

import app.chompass.models.HabitPresetCatalog
import app.chompass.models.HabitPresetDomain
import app.chompass.models.parseHabitPresetCatalog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString

/**
 * Tracker preset catalogs (custom / renamed caffeine + nicotine presets,
 * Codeberg #55 follow-up): device-local JSON prefs, deliberately not part of
 * the sync doc's prefs payload. Defaults = the builtin kinds with blank
 * labels, i.e. no behavioral change until the user edits anything.
 */
internal val PreferencesStore.caffeinePresetsImpl: Flow<HabitPresetCatalog>
    get() = stringPref(Keys.CAFFEINE_PRESETS)
        .map { parseHabitPresetCatalog(it, HabitPresetDomain.CAFFEINE, json) }

internal suspend fun PreferencesStore.setCaffeinePresetsImpl(catalog: HabitPresetCatalog) =
    setStringPref(
        Keys.CAFFEINE_PRESETS,
        json.encodeToString(catalog.validatedOrDefault(HabitPresetDomain.CAFFEINE)),
    )

internal val PreferencesStore.nicotinePresetsImpl: Flow<HabitPresetCatalog>
    get() = stringPref(Keys.NICOTINE_PRESETS)
        .map { parseHabitPresetCatalog(it, HabitPresetDomain.NICOTINE, json) }

internal suspend fun PreferencesStore.setNicotinePresetsImpl(catalog: HabitPresetCatalog) =
    setStringPref(
        Keys.NICOTINE_PRESETS,
        json.encodeToString(catalog.validatedOrDefault(HabitPresetDomain.NICOTINE)),
    )

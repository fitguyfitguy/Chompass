package app.chompass.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.chompass.models.HabitPreset
import app.chompass.models.HabitPresetDomain
import app.chompass.models.caffeineKindLabelRes
import app.chompass.models.nicotineKindLabelRes

/**
 * Resolved kind label for hub chips, sheets and history rows: a non-blank
 * preset override wins; blank builtins use the locale default; ids without a
 * catalog entry (deleted custom, manual restore) fall back to the localized
 * "Other" label.
 */
@Composable
internal fun trackerPresetLabel(presets: List<HabitPreset>, id: String, domain: HabitPresetDomain): String {
    presets.firstOrNull { it.id == id }
        ?.label?.trim()?.takeIf { it.isNotEmpty() }
        ?.let { return it }
    return stringResource(
        when (domain) {
            HabitPresetDomain.CAFFEINE -> caffeineKindLabelRes(id)
            HabitPresetDomain.NICOTINE -> nicotineKindLabelRes(id)
        }
    )
}

package app.chompass.data

import app.chompass.models.CoachQuickPrompts
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Coach quick-prompt strip (Codeberg #155): null storage = per-goal defaults. */
internal val PreferencesStore.coachQuickPromptsImpl: Flow<CoachQuickPrompts?>
    get() = stringPref(Keys.COACH_QUICK_PROMPTS)
        .map { CoachQuickPrompts.fromStorage(it) }

internal suspend fun PreferencesStore.setCoachQuickPromptsImpl(prompts: CoachQuickPrompts?) =
    setStringPrefOrRemove(Keys.COACH_QUICK_PROMPTS, prompts?.toStorage())

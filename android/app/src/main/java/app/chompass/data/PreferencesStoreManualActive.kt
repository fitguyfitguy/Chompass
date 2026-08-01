package app.chompass.data

import kotlinx.coroutines.flow.Flow
import app.chompass.models.ManualActiveEntry

internal val PreferencesStore.manualActiveEntriesImpl: Flow<List<ManualActiveEntry>>
    get() = listPref(Keys.MANUAL_ACTIVE_ENTRIES, ManualActiveEntry.serializer())

internal suspend fun PreferencesStore.setManualActiveEntriesImpl(entries: List<ManualActiveEntry>) =
    setListPref(Keys.MANUAL_ACTIVE_ENTRIES, ManualActiveEntry.serializer(), entries)

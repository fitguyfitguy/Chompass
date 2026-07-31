package app.chompass.data

import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import app.chompass.sync.normalizeWebDavUrl

@Serializable
data class SyncRevision(
    val updatedAt: String,
    val deletedAt: String? = null,
    val kind: String = "food",
)

internal val PreferencesStore.syncRevisionsImpl: Flow<Map<String, SyncRevision>>
    get() = dataStore.data.map { prefs ->
        prefs[Keys.SYNC_REVISIONS]?.let {
            runCatching {
                json.decodeFromString(MapSerializer(String.serializer(), SyncRevision.serializer()), it)
            }.getOrNull()
        } ?: emptyMap()
    }

internal suspend fun PreferencesStore.setSyncRevisionsImpl(revisions: Map<String, SyncRevision>) {
    dataStore.edit {
        it[Keys.SYNC_REVISIONS] =
            json.encodeToString(MapSerializer(String.serializer(), SyncRevision.serializer()), revisions)
    }
}

internal val PreferencesStore.webDavUrlImpl: Flow<String>
    get() = dataStore.data.map { it[Keys.WEBDAV_URL].orEmpty() }

internal suspend fun PreferencesStore.setWebDavUrlImpl(url: String) {
    dataStore.edit { it[Keys.WEBDAV_URL] = normalizeWebDavUrl(url) }
}

internal val PreferencesStore.webDavUsernameImpl: Flow<String>
    get() = dataStore.data.map { it[Keys.WEBDAV_USERNAME].orEmpty() }

internal suspend fun PreferencesStore.setWebDavUsernameImpl(username: String) {
    dataStore.edit { it[Keys.WEBDAV_USERNAME] = username.trim() }
}

internal val PreferencesStore.webDavEnabledImpl: Flow<Boolean>
    get() = dataStore.data.map { it[Keys.WEBDAV_ENABLED] ?: false }

internal suspend fun PreferencesStore.setWebDavEnabledImpl(enabled: Boolean) {
    dataStore.edit { it[Keys.WEBDAV_ENABLED] = enabled }
}

internal val PreferencesStore.lastSyncAtImpl: Flow<String?>
    get() = dataStore.data.map { it[Keys.LAST_SYNC_AT] }

internal suspend fun PreferencesStore.setLastSyncAtImpl(iso: String?) {
    dataStore.edit {
        if (iso.isNullOrBlank()) it.remove(Keys.LAST_SYNC_AT)
        else it[Keys.LAST_SYNC_AT] = iso
    }
}

internal val PreferencesStore.lastSyncEtagImpl: Flow<String?>
    get() = dataStore.data.map { it[Keys.LAST_SYNC_ETAG] }

internal suspend fun PreferencesStore.setLastSyncEtagImpl(etag: String?) {
    dataStore.edit {
        if (etag.isNullOrBlank()) it.remove(Keys.LAST_SYNC_ETAG)
        else it[Keys.LAST_SYNC_ETAG] = etag
    }
}

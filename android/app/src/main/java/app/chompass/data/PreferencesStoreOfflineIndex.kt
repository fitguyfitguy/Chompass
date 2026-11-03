package app.chompass.data

import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

// -- Offline barcode packs (downloadable USDA GTIN / OFF country slices) ---
//
// Tracks which packs are installed so Settings can render state and startup
// can validate the files, plus the Wi-Fi-only download default. One JSON map
// keyed by pack id, mirroring the barcode cache's single-blob pattern.

@Serializable
data class InstalledOfflinePack(
    val version: String,
    /** sha256 of the published `.sqlite.gz` asset (from the manifest). */
    val sha256: String,
    /** Byte size of the published `.sqlite.gz` asset. */
    val bytes: Long,
    /** sha256 of the installed (gunzipped) sqlite file; the startup validation gate. */
    val fileSha256: String,
)

private val offlinePackMapSerializer =
    MapSerializer(String.serializer(), InstalledOfflinePack.serializer())

internal val PreferencesStore.offlineIndexPacksImpl: Flow<Map<String, InstalledOfflinePack>> get() = dataStore.data.map { prefs ->
        prefs[Keys.OFFLINE_INDEX_PACKS]?.let {
            runCatching { json.decodeFromString(offlinePackMapSerializer, it) }.getOrNull()
        } ?: emptyMap()
    }

internal suspend fun PreferencesStore.setOfflineIndexPacksImpl(packs: Map<String, InstalledOfflinePack>) {
    dataStore.edit {
        it[Keys.OFFLINE_INDEX_PACKS] = json.encodeToString(offlinePackMapSerializer, packs)
    }
}

/** One-pack write: null removes the entry (removed by the user / dropped at startup). */
internal suspend fun PreferencesStore.updateOfflineIndexPackImpl(id: String, pack: InstalledOfflinePack?) {
    dataStore.edit { prefs ->
        val current = prefs[Keys.OFFLINE_INDEX_PACKS]?.let {
            runCatching { json.decodeFromString(offlinePackMapSerializer, it) }.getOrNull()
        } ?: emptyMap()
        val updated = current.toMutableMap()
        if (pack == null) updated.remove(id) else updated[id] = pack
        prefs[Keys.OFFLINE_INDEX_PACKS] = json.encodeToString(offlinePackMapSerializer, updated)
    }
}

internal val PreferencesStore.offlineIndexWifiOnlyImpl: Flow<Boolean>
    get() = dataStore.data.map { it[Keys.OFFLINE_INDEX_WIFI_ONLY] ?: true }

internal suspend fun PreferencesStore.setOfflineIndexWifiOnlyImpl(enabled: Boolean) {
    dataStore.edit { it[Keys.OFFLINE_INDEX_WIFI_ONLY] = enabled }
}

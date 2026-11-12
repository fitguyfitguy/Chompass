package app.chompass.data

import androidx.datastore.preferences.core.edit
import app.chompass.services.ai.FoodAnalysis
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

// -- Barcode lookup cache (Open Food Facts results) ---------------------
//
// Caches successful barcode -> FoodAnalysis lookups so repeat scans of the
// same product are instant and work offline, without a full local food
// database. Bounded LRU-by-timestamp so the single JSON blob stays small.

internal const val BARCODE_CACHE_MAX_ENTRIES = 500

/**
 * Bump when the FoodAnalysis barcode mapping changes meaning: entries written
 * below this version hold stale portion bases (pre-#168 entries served the
 * 100 g guess). Version 0 covers all entries written before the field existed.
 */
internal const val BARCODE_CACHE_SCHEMA = 2

@Serializable
data class CachedBarcodeProduct(
    val analysis: FoodAnalysis,
    val cachedAtEpochMs: Long,
    /** Absent in pre-schema entries; see [BARCODE_CACHE_SCHEMA]. */
    val schemaVersion: Int = 0,
)

internal val PreferencesStore.barcodeCacheImpl: Flow<Map<String, CachedBarcodeProduct>> get() = dataStore.data.map { prefs ->
        prefs[Keys.BARCODE_CACHE]?.let {
            runCatching {
                json.decodeFromString(MapSerializer(String.serializer(), CachedBarcodeProduct.serializer()), it)
            }.getOrNull()
        } ?: emptyMap()
    }

internal suspend fun PreferencesStore.setBarcodeCacheImpl(cache: Map<String, CachedBarcodeProduct>) {
    dataStore.edit {
        it[Keys.BARCODE_CACHE] = json.encodeToString(
            MapSerializer(String.serializer(), CachedBarcodeProduct.serializer()),
            cache
        )
    }
}

/** Inserts [barcode] -> [analysis], evicting the oldest entry if over [BARCODE_CACHE_MAX_ENTRIES]. */
internal suspend fun PreferencesStore.cacheBarcodeLookupImpl(barcode: String, analysis: FoodAnalysis) {
    dataStore.edit { prefs ->
        val existing = prefs[Keys.BARCODE_CACHE]?.let {
            runCatching {
                json.decodeFromString(MapSerializer(String.serializer(), CachedBarcodeProduct.serializer()), it)
            }.getOrNull()
        } ?: emptyMap()
        val updated = existing.toMutableMap()
        updated[barcode] = CachedBarcodeProduct(analysis, System.currentTimeMillis(), BARCODE_CACHE_SCHEMA)
        val bounded = if (updated.size > BARCODE_CACHE_MAX_ENTRIES) {
            updated.entries.sortedByDescending { it.value.cachedAtEpochMs }
                .take(BARCODE_CACHE_MAX_ENTRIES)
                .associate { it.key to it.value }
        } else updated
        prefs[Keys.BARCODE_CACHE] = json.encodeToString(
            MapSerializer(String.serializer(), CachedBarcodeProduct.serializer()),
            bounded
        )
    }
}

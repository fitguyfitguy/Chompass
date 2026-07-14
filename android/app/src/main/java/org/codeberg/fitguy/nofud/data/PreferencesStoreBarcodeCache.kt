package org.codeberg.fitguy.nofud.data

import androidx.datastore.preferences.core.edit
import org.codeberg.fitguy.nofud.services.ai.FoodAnalysis
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

@Serializable
data class CachedBarcodeProduct(
    val analysis: FoodAnalysis,
    val cachedAtEpochMs: Long,
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
        updated[barcode] = CachedBarcodeProduct(analysis, System.currentTimeMillis())
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

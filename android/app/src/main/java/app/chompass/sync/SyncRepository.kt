package app.chompass.sync

import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import app.chompass.data.KeyStore
import app.chompass.data.PreferencesStore
import app.chompass.data.SyncRevision
import app.chompass.export.SyncDocument
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/**
 * Builds / applies sync-1.0 documents and optionally exchanges them over user-hosted WebDAV.
 */
class SyncRepository(
    private val prefs: PreferencesStore,
    private val keyStore: KeyStore,
    private val webDav: WebDavClient = WebDavClient(),
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    sealed class SyncResult {
        data class Success(val message: String) : SyncResult()
        data class Failed(val message: String) : SyncResult()
    }

    suspend fun exportDocumentJson(zone: ZoneId = ZoneId.systemDefault()): String {
        val revisions = prefs.syncRevisions.first()
        return SyncDocument.buildJson(
            foodEntries = prefs.foodEntries.first(),
            favorites = prefs.favoriteFoodEntries.first(),
            weights = prefs.weightEntries.first(),
            bodyFats = prefs.bodyFatEntries.first(),
            measurements = prefs.bodyMeasurements.first(),
            water = prefs.waterEntries.first(),
            recipes = prefs.recipes.first(),
            revisions = revisions.mapValues { (_, rev) ->
                SyncDocument.Revision(rev.updatedAt, rev.deletedAt, rev.kind)
            },
            zone = zone,
        )
    }

    suspend fun importDocumentJson(jsonText: String, zone: ZoneId = ZoneId.systemDefault()): SyncResult {
        val localJson = exportDocumentJson(zone)
        val remoteParsed = SyncDocument.parse(jsonText, zone)
        if (remoteParsed !is SyncDocument.ParseResult.Success) {
            return SyncResult.Failed(
                when (remoteParsed) {
                    is SyncDocument.ParseResult.Malformed -> remoteParsed.reason
                    else -> "Unsupported sync file"
                },
            )
        }
        val localParsed = SyncDocument.parse(localJson, zone)
        val localRaw = (localParsed as? SyncDocument.ParseResult.Success)?.parsed?.raw
            ?: remoteParsed.parsed.raw
        val merged = SyncDocument.mergeRawDocuments(localRaw, remoteParsed.parsed.raw)
        return applyMergedDocument(json.encodeToString(JsonObject.serializer(), merged), zone)
    }

    suspend fun syncNow(zone: ZoneId = ZoneId.systemDefault()): SyncResult {
        val url = normalizeWebDavUrl(prefs.webDavUrl.first())
        val user = prefs.webDavUsername.first().trim()
        val password = keyStore.webDavPassword().orEmpty()
        if (url.isEmpty() || user.isEmpty() || password.isEmpty()) {
            return SyncResult.Failed("Configure WebDAV URL, username, and password first")
        }
        return try {
            val remote = webDav.get(url, user, password)
            val localJson = exportDocumentJson(zone)
            val mergedJson = if (remote.notFound || remote.body.isNullOrBlank()) {
                localJson
            } else {
                val localParsed = SyncDocument.parse(localJson, zone) as? SyncDocument.ParseResult.Success
                    ?: return SyncResult.Failed("Could not build local sync document")
                val remoteParsed = SyncDocument.parse(remote.body, zone) as? SyncDocument.ParseResult.Success
                    ?: return SyncResult.Failed("Remote sync document is invalid")
                json.encodeToString(
                    JsonObject.serializer(),
                    SyncDocument.mergeRawDocuments(localParsed.parsed.raw, remoteParsed.parsed.raw),
                )
            }
            val apply = applyMergedDocument(mergedJson, zone)
            if (apply is SyncResult.Failed) return apply

            var etag = remote.etag
            var put = webDav.put(url, user, password, mergedJson, ifMatch = etag)
            if (put.conflict) {
                val again = webDav.get(url, user, password)
                val localParsed = SyncDocument.parse(exportDocumentJson(zone), zone) as? SyncDocument.ParseResult.Success
                    ?: return SyncResult.Failed("Local sync rebuild failed after conflict")
                val remoteParsed = SyncDocument.parse(again.body.orEmpty(), zone) as? SyncDocument.ParseResult.Success
                    ?: return SyncResult.Failed("Remote sync document invalid after conflict")
                val retryJson = json.encodeToString(
                    JsonObject.serializer(),
                    SyncDocument.mergeRawDocuments(localParsed.parsed.raw, remoteParsed.parsed.raw),
                )
                val retryApply = applyMergedDocument(retryJson, zone)
                if (retryApply is SyncResult.Failed) return retryApply
                put = webDav.put(url, user, password, retryJson, ifMatch = again.etag)
                if (put.conflict) return SyncResult.Failed("WebDAV conflict persisted; try again")
                etag = put.etag ?: again.etag
            } else {
                etag = put.etag ?: etag
            }
            prefs.setLastSyncAt(Instant.now().toString())
            prefs.setLastSyncEtag(etag)
            SyncResult.Success("Synced with WebDAV")
        } catch (t: Throwable) {
            SyncResult.Failed(t.localizedMessage ?: "WebDAV sync failed")
        }
    }

    suspend fun touch(id: UUID, kind: String) {
        val now = Instant.now().toString()
        val current = prefs.syncRevisions.first().toMutableMap()
        current[id.toString()] = SyncRevision(updatedAt = now, deletedAt = null, kind = kind)
        prefs.setSyncRevisions(current)
    }

    suspend fun tombstone(id: UUID, kind: String) {
        val now = Instant.now().toString()
        val current = prefs.syncRevisions.first().toMutableMap()
        current[id.toString()] = SyncRevision(updatedAt = now, deletedAt = now, kind = kind)
        prefs.setSyncRevisions(current)
    }

    private suspend fun applyMergedDocument(jsonText: String, zone: ZoneId): SyncResult {
        val parsed = SyncDocument.parse(jsonText, zone)
        if (parsed !is SyncDocument.ParseResult.Success) {
            return SyncResult.Failed("Merged sync document invalid")
        }
        val doc = parsed.parsed
        val revisionMap = mutableMapOf<String, SyncRevision>()

        fun track(id: String, updatedAt: String, deletedAt: String?, kind: String) {
            revisionMap[id] = SyncRevision(updatedAt, deletedAt, kind)
        }

        val liveFood = doc.foodEntries.mapNotNull { wire ->
            track(wire.id, wire.updatedAt, wire.deletedAt, "food")
            wire.entry
        }
        prefs.replaceAllFoodEntries(liveFood)

        val liveFavorites = doc.favorites.mapNotNull { wire ->
            track(wire.id, wire.updatedAt, wire.deletedAt, "favorite")
            wire.entry
        }
        prefs.setFavoriteFoodEntries(liveFavorites)
        prefs.setFavoriteKeys(liveFavorites.map { it.favoriteKey }.toSet())

        val liveWeights = doc.weights.mapNotNull {
            track(it.id, it.updatedAt, it.deletedAt, "weight")
            it.entry
        }
        prefs.setWeightEntries(liveWeights)

        val liveBodyFat = doc.bodyFats.mapNotNull {
            track(it.id, it.updatedAt, it.deletedAt, "bodyfat")
            it.entry
        }
        prefs.setBodyFatEntries(liveBodyFat)

        val liveMeasures = doc.measurements.mapNotNull {
            track(it.id, it.updatedAt, it.deletedAt, "measure")
            it.entry
        }
        prefs.setBodyMeasurements(liveMeasures)

        val liveWater = doc.water.mapNotNull {
            track(it.id, it.updatedAt, it.deletedAt, "water")
            it.entry
        }
        prefs.setWaterEntries(liveWater)

        val liveRecipes = doc.recipes.mapNotNull {
            track(it.id, it.updatedAt, it.deletedAt, "recipe")
            it.entry
        }
        prefs.setRecipes(liveRecipes)

        prefs.setSyncRevisions(revisionMap)
        return SyncResult.Success("Sync document applied")
    }
}

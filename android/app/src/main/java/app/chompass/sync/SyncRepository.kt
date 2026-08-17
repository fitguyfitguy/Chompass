package app.chompass.sync

import android.content.Context
import app.chompass.R
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import app.chompass.data.KeyStore
import app.chompass.data.PreferencesStore
import app.chompass.data.SyncRevision
import app.chompass.export.SyncDocument
import app.chompass.models.FoodEntry
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/**
 * Builds / applies sync-1.0 documents and optionally exchanges them over user-hosted WebDAV.
 */
class SyncRepository(
    private val prefs: PreferencesStore,
    private val keyStore: KeyStore,
    private val webDav: WebDavClient = WebDavClient(),
    private val appContext: Context,
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
                    else -> appContext.getString(R.string.sync_error_unsupported_file)
                },
            )
        }
        val localParsed = SyncDocument.parse(localJson, zone)
        val localRaw = (localParsed as? SyncDocument.ParseResult.Success)?.parsed?.raw
            ?: remoteParsed.parsed.raw
        val merged = SyncDocument.mergeRawDocuments(localRaw, remoteParsed.parsed.raw)
        return applyMergedDocument(json.encodeToString(JsonObject.serializer(), merged), zone)
    }

    /**
     * Opt-in foreground auto-sync: at most once per local calendar day when WebDAV is configured.
     * Returns null when skipped; otherwise the result of [syncNow].
     */
    suspend fun maybeAutoSyncWebDav(zone: ZoneId = ZoneId.systemDefault()): SyncResult? {
        val url = normalizeWebDavUrl(prefs.webDavUrl.first())
        val user = prefs.webDavUsername.first().trim()
        val password = keyStore.webDavPassword().orEmpty()
        val today = LocalDate.now(zone)
        if (!shouldAutoSyncWebDav(
                enabled = prefs.webDavEnabled.first(),
                configured = url.isNotEmpty() && user.isNotEmpty() && password.isNotEmpty(),
                today = today,
                lastSyncAtIso = prefs.lastSyncAt.first(),
                lastAutoSyncDayIso = prefs.webDavAutoSyncDay.first(),
                zone = zone,
            )
        ) {
            return null
        }
        prefs.setWebDavAutoSyncDay(today.toString())
        return syncNow(zone)
    }

    suspend fun syncNow(zone: ZoneId = ZoneId.systemDefault()): SyncResult {
        val url = normalizeWebDavUrl(prefs.webDavUrl.first())
        val user = prefs.webDavUsername.first().trim()
        val password = keyStore.webDavPassword().orEmpty()
        if (url.isEmpty() || user.isEmpty() || password.isEmpty()) {
            return SyncResult.Failed(appContext.getString(R.string.sync_error_configure_webdav))
        }
        return try {
            backfillRevisionsIfNeeded()
            val remote = webDav.get(url, user, password)
            val localJson = exportDocumentJson(zone)
            val mergedJson = if (remote.notFound || remote.body.isNullOrBlank()) {
                localJson
            } else {
                val localParsed = SyncDocument.parse(localJson, zone) as? SyncDocument.ParseResult.Success
                    ?: return SyncResult.Failed(appContext.getString(R.string.sync_error_build_local_doc))
                val remoteParsed = SyncDocument.parse(remote.body, zone) as? SyncDocument.ParseResult.Success
                    ?: return SyncResult.Failed(appContext.getString(R.string.sync_error_remote_invalid))
                json.encodeToString(
                    JsonObject.serializer(),
                    SyncDocument.mergeRawDocuments(localParsed.parsed.raw, remoteParsed.parsed.raw),
                )
            }
            val apply = applyMergedDocument(mergedJson, zone)
            if (apply is SyncResult.Failed) return apply

            var etag = remote.etag
            var put = webDav.put(url, user, password, mergedJson, webDavPutMode(remote.etag, remote.notFound))
            if (put.conflict) {
                val again = webDav.get(url, user, password)
                val localParsed = SyncDocument.parse(exportDocumentJson(zone), zone) as? SyncDocument.ParseResult.Success
                    ?: return SyncResult.Failed(appContext.getString(R.string.sync_error_local_rebuild_failed))
                val remoteParsed = SyncDocument.parse(again.body.orEmpty(), zone) as? SyncDocument.ParseResult.Success
                    ?: return SyncResult.Failed(appContext.getString(R.string.sync_error_remote_invalid_conflict))
                val retryJson = json.encodeToString(
                    JsonObject.serializer(),
                    SyncDocument.mergeRawDocuments(localParsed.parsed.raw, remoteParsed.parsed.raw),
                )
                val retryApply = applyMergedDocument(retryJson, zone)
                if (retryApply is SyncResult.Failed) return retryApply
                put = webDav.put(url, user, password, retryJson, webDavPutMode(again.etag, again.notFound))
                // Broken/weak ETags (or CORS-hidden ones on some stacks) can 412 forever;
                // after a fresh merge, overwrite once without preconditions.
                if (put.conflict) {
                    put = webDav.put(url, user, password, retryJson, WebDavPutMode.Unconditional)
                }
                if (put.conflict) return SyncResult.Failed(appContext.getString(R.string.sync_error_conflict_persisted))
                etag = put.etag ?: again.etag
            } else {
                etag = put.etag ?: etag
            }
            prefs.setLastSyncAt(Instant.now().toString())
            prefs.setLastSyncEtag(etag)
            SyncResult.Success(appContext.getString(R.string.sync_success_synced))
        } catch (t: Throwable) {
            SyncResult.Failed(t.localizedMessage ?: appContext.getString(R.string.error_webdav_sync_failed))
        }
    }

    suspend fun touch(id: UUID, kind: String) {
        if (!syncConfigured()) return
        val now = Instant.now().toString()
        val current = prefs.syncRevisions.first().toMutableMap()
        current[id.toString()] = SyncRevision(updatedAt = now, deletedAt = null, kind = kind)
        prefs.setSyncRevisions(current)
    }

    /**
     * Batched [touch] for bulk saves (progressive-meal Log meal / Copy From
     * Day): one revisions-map write instead of one full-file DataStore edit
     * per entry.
     */
    suspend fun touchMany(updates: List<Pair<UUID, String>>) {
        if (updates.isEmpty() || !syncConfigured()) return
        val now = Instant.now().toString()
        val current = prefs.syncRevisions.first().toMutableMap()
        updates.forEach { (id, kind) ->
            current[id.toString()] = SyncRevision(updatedAt = now, deletedAt = null, kind = kind)
        }
        prefs.setSyncRevisions(current)
    }

    suspend fun tombstone(id: UUID, kind: String) {
        if (!syncConfigured()) return
        val now = Instant.now().toString()
        val current = prefs.syncRevisions.first().toMutableMap()
        current[id.toString()] = SyncRevision(updatedAt = now, deletedAt = now, kind = kind)
        prefs.setSyncRevisions(current)
    }

    /**
     * Whether revision tracking is meaningful: WebDAV is opted in and a URL is
     * set. When false, [touch]/[tombstone] are no-ops so logging never pays a
     * full-file DataStore write for a revision map nothing will consume.
     */
    private suspend fun syncConfigured(): Boolean {
        val enabled = prefs.webDavEnabled.first()
        val url = normalizeWebDavUrl(prefs.webDavUrl.first())
        return enabled && url.isNotEmpty()
    }

    /**
     * One-time repair for records created while WebDAV was disabled (or before
     * revision tracking existed): records a live revision for every current
     * row the revision map is missing, so a freshly-enabled sync uploads the
     * whole history instead of relying on wire-level timestamp fallbacks. Runs
     * only at sync time, never on the save path.
     */
    private suspend fun backfillRevisionsIfNeeded() {
        val revisions = prefs.syncRevisions.first().toMutableMap()
        var changed = false
        fun track(id: UUID, kind: String) {
            val key = id.toString()
            if (key !in revisions) {
                revisions[key] = SyncRevision(updatedAt = Instant.now().toString(), deletedAt = null, kind = kind)
                changed = true
            }
        }
        prefs.foodEntries.first().forEach { track(it.id, "food") }
        prefs.favoriteFoodEntries.first().forEach { track(it.id, "favorite") }
        prefs.weightEntries.first().forEach { track(it.id, "weight") }
        prefs.bodyFatEntries.first().forEach { track(it.id, "bodyfat") }
        prefs.bodyMeasurements.first().forEach { track(it.id, "measure") }
        prefs.waterEntries.first().forEach { track(it.id, "water") }
        prefs.recipes.first().forEach { track(it.id, "recipe") }
        if (changed) prefs.setSyncRevisions(revisions)
    }

    private suspend fun applyMergedDocument(jsonText: String, zone: ZoneId): SyncResult {
        val parsed = SyncDocument.parse(jsonText, zone)
        if (parsed !is SyncDocument.ParseResult.Success) {
            return SyncResult.Failed(appContext.getString(R.string.sync_error_merged_invalid))
        }
        val doc = parsed.parsed
        val revisionMap = mutableMapOf<String, SyncRevision>()

        fun track(id: String, updatedAt: String, deletedAt: String?, kind: String) {
            revisionMap[id] = SyncRevision(updatedAt, deletedAt, kind)
        }

        // Photos are excluded from the wire format on purpose (see SyncDocument),
        // so the re-parsed rows carry no imageFilename. Emoji only ride the wire
        // since this fix, so rows merged from older exports lack them too.
        // Re-attach the local visual fields before replacing, otherwise a
        // sync/import drops every food photo and emoji (#34).
        val localFood = prefs.foodEntries.first().associateBy { it.id }
        val localFavorites = prefs.favoriteFoodEntries.first().associateBy { it.id }

        val liveFood = doc.foodEntries.mapNotNull { wire ->
            track(wire.id, wire.updatedAt, wire.deletedAt, "food")
            wire.entry?.let { reattachLocalFields(it, localFood) }
        }
        prefs.replaceAllFoodEntries(liveFood)

        val liveFavorites = doc.favorites.mapNotNull { wire ->
            track(wire.id, wire.updatedAt, wire.deletedAt, "favorite")
            wire.entry?.let { reattachLocalFields(it, localFavorites) }
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
        return SyncResult.Success(appContext.getString(R.string.sync_success_applied))
    }
}

/**
 * The sync wire never carries photos, and only carries entry emoji since the
 * #34 fix, so a merged row is usually parsed without them. Keep the local
 * visual fields (the wire never overwrites one either; the emoji picker only
 * sets, never removes) so applying a merged document cannot drop food photos
 * or emoji (#34).
 */
internal fun reattachLocalFields(entry: FoodEntry, local: Map<UUID, FoodEntry>): FoodEntry {
    val localEntry = local[entry.id] ?: return entry
    return entry.copy(
        imageFilename = entry.imageFilename ?: localEntry.imageFilename,
        emoji = entry.emoji ?: localEntry.emoji,
    )
}

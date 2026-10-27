package app.chompass.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import app.chompass.R
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import app.chompass.data.KeyStore
import app.chompass.data.PreferencesStore
import app.chompass.data.SyncRevision
import app.chompass.export.SyncDocument
import app.chompass.export.SyncMerge
import app.chompass.models.BodyFatEntry
import app.chompass.models.BodyMeasurement
import app.chompass.models.FoodEntry
import app.chompass.models.GoalJournal
import app.chompass.models.WeightEntry
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
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
    /** Local photo bytes + hashing for the opt-in WebDAV photo phase (#122). */
    private val imageStore: app.chompass.services.FoodImageStore? = null,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    sealed class SyncResult {
        data class Success(val message: String) : SyncResult()
        data class Failed(val message: String) : SyncResult()
    }

    suspend fun exportDocumentJson(
        zone: ZoneId = ZoneId.systemDefault(),
        emitPhotos: Boolean = true,
    ): String {
        // Photo refs only ever leave the device when the user opted in (#122);
        // when gated off they drop out of the doc and the version falls back to 1.3.
        val withPhotos = emitPhotos && prefs.webDavPhotosEnabled.first()
        val food = prefs.foodEntries.first().let { list ->
            if (withPhotos) list else list.map { it.copy(photoFilenames = emptyList()) }
        }
        val favorites = prefs.favoriteFoodEntries.first().let { list ->
            if (withPhotos) list else list.map { it.copy(photoFilenames = emptyList()) }
        }
        val revisions = prefs.syncRevisions.first()
        return SyncDocument.buildJson(
            foodEntries = food,
            favorites = favorites,
            weights = prefs.weightEntries.first(),
            bodyFats = prefs.bodyFatEntries.first(),
            measurements = prefs.bodyMeasurements.first(),
            water = prefs.waterEntries.first(),
            dailyNotes = prefs.noteEntries.first(),
            nicotine = prefs.nicotineEntries.first(),
            caffeine = prefs.caffeineEntries.first(),
            recipes = prefs.recipes.first(),
            goalJournal = prefs.goalJournal.first(),
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
        return applyMergedDocument(
            json.encodeToString(JsonObject.serializer(), merged),
            zone,
            local = (localParsed as? SyncDocument.ParseResult.Success)?.parsed,
        )
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

    suspend fun syncNow(
        zone: ZoneId = ZoneId.systemDefault(),
        onStatus: ((String) -> Unit)? = null,
    ): SyncResult {
        val url = normalizeWebDavUrl(prefs.webDavUrl.first())
        val user = prefs.webDavUsername.first().trim()
        val password = keyStore.webDavPassword().orEmpty()
        if (url.isEmpty() || user.isEmpty() || password.isEmpty()) {
            return SyncResult.Failed(appContext.getString(R.string.sync_error_configure_webdav))
        }
        return try {
            backfillRevisionsIfNeeded()
            val remote = webDav.get(url, user, password)
            val remoteParsed = if (remote.notFound || remote.body.isNullOrBlank()) {
                null
            } else {
                SyncDocument.parse(remote.body, zone) as? SyncDocument.ParseResult.Success
                    ?: return SyncResult.Failed(appContext.getString(R.string.sync_error_remote_invalid))
            }
            val localJson = exportDocumentJson(zone)
            val localParsed = SyncDocument.parse(localJson, zone) as? SyncDocument.ParseResult.Success
            val mergedJson = if (remoteParsed == null) {
                localJson
            } else {
                if (localParsed == null) {
                    return SyncResult.Failed(appContext.getString(R.string.sync_error_build_local_doc))
                }
                json.encodeToString(
                    JsonObject.serializer(),
                    SyncDocument.mergeRawDocuments(localParsed.parsed.raw, remoteParsed.parsed.raw),
                )
            }
            val apply = applyMergedDocument(mergedJson, zone, local = localParsed?.parsed)
            if (apply is SyncResult.Failed) return apply

            // --- Photo phase (#122), before the doc PUT: downloads fill local
            // files for advertised refs, uploads PUT local bytes under
            // content-addressed refs and only then stamp refs + revisions, so
            // the doc never advertises a file the server does not have.
            val photosActive = photosActive(url)
            if (photosActive) {
                onStatus?.invoke(appContext.getString(R.string.sync_photos_syncing))
                if (syncPhotoFiles(url, user, password)) {
                    return SyncResult.Failed(appContext.getString(R.string.sync_photos_failed))
                }
            }

            // localRaw AFTER the photo phase so fresh refs + touched revisions ride the PUT.
            val localJsonForPut = exportDocumentJson(zone, emitPhotos = photosActive)
            val localParsedForPut = SyncDocument.parse(localJsonForPut, zone) as? SyncDocument.ParseResult.Success
                ?: return SyncResult.Failed(appContext.getString(R.string.sync_error_local_rebuild_failed))
            val mergedForPut = if (remoteParsed == null) {
                localJsonForPut
            } else {
                json.encodeToString(
                    JsonObject.serializer(),
                    SyncDocument.mergeRawDocuments(localParsedForPut.parsed.raw, remoteParsed.parsed.raw),
                )
            }

            var etag = remote.etag
            var put = webDav.put(url, user, password, mergedForPut, webDavPutMode(remote.etag, remote.notFound))
            if (put.conflict) {
                val again = webDav.get(url, user, password)
                val localParsed = SyncDocument.parse(exportDocumentJson(zone, emitPhotos = photosActive), zone) as? SyncDocument.ParseResult.Success
                    ?: return SyncResult.Failed(appContext.getString(R.string.sync_error_local_rebuild_failed))
                val remoteParsed = SyncDocument.parse(again.body.orEmpty(), zone) as? SyncDocument.ParseResult.Success
                    ?: return SyncResult.Failed(appContext.getString(R.string.sync_error_remote_invalid_conflict))
                val retryJson = json.encodeToString(
                    JsonObject.serializer(),
                    SyncDocument.mergeRawDocuments(localParsed.parsed.raw, remoteParsed.parsed.raw),
                )
                val retryApply = applyMergedDocument(retryJson, zone, local = localParsed.parsed)
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

    /** The photo phase runs only when opted in, on an allowed network, with a parseable URL (#122). */
    private suspend fun photosActive(url: String): Boolean {
        if (imageStore == null) return false
        if (!prefs.webDavPhotosEnabled.first()) return false
        if (prefs.webDavPhotosWifiOnly.first() && photosBlockedByNetwork()) return false
        return url.contains('/')
    }

    /** True when the active network is missing, unvalidated, or metered — photo transfer not allowed. */
    private fun photosBlockedByNetwork(): Boolean {
        val cm = appContext.getSystemService(ConnectivityManager::class.java) ?: return true
        val network = cm.activeNetwork ?: return true
        val caps = cm.getNetworkCapabilities(network) ?: return true
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) return true
        return !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    /**
     * Opt-in photo transfer (#122). Downloads: advertised refs without a local
     * file fetch their bytes (404 keeps the ref — other devices still advertise
     * it; retried next sync; no revision touch, `imageFilename` is
     * wire-invisible). Uploads: each local photo is PUT under its
     * content-addressed ref; `photoFilenames` + a revision touch are written
     * only after a successful PUT, so the doc never advertises a missing file.
     * Returns true on a hard upload failure (caller aborts before the doc PUT).
     */
    private suspend fun syncPhotoFiles(url: String, user: String, password: String): Boolean {
        val store = imageStore ?: return false
        val photoBase = url.substringBeforeLast('/') + "/photos/"

        // The uploaded-ref cache belongs to one WebDAV URL; a switch resets it.
        var cached = prefs.webDavSyncedPhotos.first()
        if (prefs.webDavSyncedPhotosUrl.first() != url) {
            cached = emptySet()
            prefs.setWebDavSyncedPhotos(emptySet(), url)
        }

        // Downloads: refs advertised for entries without local bytes.
        val foodNow = prefs.foodEntries.first()
        val downloadedFood = mutableListOf<FoodEntry>()
        for (entry in foodNow) {
            val ref = entry.photoFilenames.firstOrNull() ?: continue
            if (entry.imageFilename != null) continue
            val bytes = try {
                webDav.getBinary(photoBase + ref.removePrefix("photos/"), user, password)
            } catch (t: Throwable) {
                continue // transient GET failure: ref stays, retried next sync
            } ?: continue // 404: keep the ref, never drop it
            val stored = store.storeBytes(bytes, entry.id) ?: continue
            downloadedFood += entry.copy(imageFilename = stored)
            cached += ref // the bytes came from the server — no re-upload needed
        }
        if (downloadedFood.isNotEmpty()) {
            val byId = downloadedFood.associateBy { it.id }
            prefs.replaceAllFoodEntries(foodNow.map { byId[it.id] ?: it })
        }

        // Same for favorites (own rows, own photos).
        val favoritesNow = prefs.favoriteFoodEntries.first()
        val downloadedFavorites = mutableListOf<FoodEntry>()
        for (entry in favoritesNow) {
            val ref = entry.photoFilenames.firstOrNull() ?: continue
            if (entry.imageFilename != null) continue
            val bytes = try {
                webDav.getBinary(photoBase + ref.removePrefix("photos/"), user, password)
            } catch (t: Throwable) {
                continue
            } ?: continue
            val stored = store.storeBytes(bytes, entry.id) ?: continue
            downloadedFavorites += entry.copy(imageFilename = stored)
            cached += ref
        }
        if (downloadedFavorites.isNotEmpty()) {
            val byId = downloadedFavorites.associateBy { it.id }
            prefs.setFavoritesAtomic(favoritesNow.map { byId[it.id] ?: it })
        }

        // Uploads: local bytes not known to be on the server yet.
        val refTouchUpdates = mutableListOf<Pair<UUID, String>>()
        val refsUpdatedFood = mutableListOf<FoodEntry>()
        val refsUpdatedFavorites = mutableListOf<FoodEntry>()

        suspend fun uploadFor(entries: List<FoodEntry>, kind: String, collect: (FoodEntry) -> Unit): Boolean {
            for (entry in entries) {
                val filename = entry.imageFilename ?: continue
                val file = store.file(filename)
                if (!file.canRead() || file.length() == 0L) continue
                val ref = app.chompass.services.FoodImageStore.contentRef(file) ?: continue
                if (ref !in cached) {
                    val first = try {
                        webDav.putBytes(photoBase + ref.removePrefix("photos/"), user, password, file.readBytes())
                    } catch (t: Throwable) {
                        return true
                    }
                    val put = if (first.conflict) {
                        try {
                            webDav.mkcol(photoBase, user, password)
                        } catch (t: Throwable) {
                            return true
                        }
                        try {
                            webDav.putBytes(photoBase + ref.removePrefix("photos/"), user, password, file.readBytes())
                        } catch (t: Throwable) {
                            return true
                        }
                    } else first
                    if (put.conflict) return true
                    cached += ref
                    prefs.setWebDavSyncedPhotos(cached, url)
                }
                // Stamp the ref only after a successful PUT; a changed photo
                // (re-pick overwrites the file) moves to the fresh ref.
                if (entry.photoFilenames.firstOrNull() != ref) {
                    collect(entry.copy(photoFilenames = listOf(ref)))
                    refTouchUpdates += entry.id to kind
                }
            }
            return false
        }

        if (uploadFor(prefs.foodEntries.first(), "food") { refsUpdatedFood += it }) return true
        if (refsUpdatedFood.isNotEmpty()) {
            val byId = refsUpdatedFood.associateBy { it.id }
            prefs.replaceAllFoodEntries(prefs.foodEntries.first().map { byId[it.id] ?: it })
        }
        if (uploadFor(prefs.favoriteFoodEntries.first(), "favorite") { refsUpdatedFavorites += it }) return true
        if (refsUpdatedFavorites.isNotEmpty()) {
            val byId = refsUpdatedFavorites.associateBy { it.id }
            prefs.setFavoritesAtomic(prefs.favoriteFoodEntries.first().map { byId[it.id] ?: it })
        }
        if (refTouchUpdates.isNotEmpty()) touchMany(refTouchUpdates)
        return false
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
     * Whether revision tracking is meaningful: a WebDAV URL is set. Auto-sync
     * is a separate toggle; Sync Now still runs without it, so deletes must
     * tombstone or the next pull re-injects the row (#39). When false,
     * [touch]/[tombstone] are no-ops so logging never pays a full-file
     * DataStore write for a revision map nothing will consume.
     */
    private suspend fun syncConfigured(): Boolean =
        shouldTrackSyncRevisions(prefs.webDavUrl.first())

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
        prefs.noteEntries.first().forEach { track(it.id, "daily_note") }
        prefs.nicotineEntries.first().forEach { track(it.id, "nicotine") }
        prefs.caffeineEntries.first().forEach { track(it.id, "caffeine") }
        prefs.recipes.first().forEach { track(it.id, "recipe") }
        if (changed) prefs.setSyncRevisions(revisions)
    }

    /** Tombstones every live sync row so a later sync of the same file cannot restore a wipe. */
    suspend fun tombstonesForWipe(now: Instant = Instant.now()): Map<String, SyncRevision> {
        val rows = mutableListOf<Pair<UUID, String>>()
        prefs.foodEntries.first().forEach { rows += it.id to "food" }
        prefs.favoriteFoodEntries.first().forEach { rows += it.id to "favorite" }
        prefs.weightEntries.first().forEach { rows += it.id to "weight" }
        prefs.bodyFatEntries.first().forEach { rows += it.id to "bodyfat" }
        prefs.bodyMeasurements.first().forEach { rows += it.id to "measure" }
        prefs.waterEntries.first().forEach { rows += it.id to "water" }
        prefs.noteEntries.first().forEach { rows += it.id to "daily_note" }
        prefs.nicotineEntries.first().forEach { rows += it.id to "nicotine" }
        prefs.caffeineEntries.first().forEach { rows += it.id to "caffeine" }
        prefs.recipes.first().forEach { rows += it.id to "recipe" }
        prefs.goalJournal.first().forEach { entry ->
            val date = GoalJournal.parseDateOrNull(entry.date) ?: return@forEach
            rows += GoalJournal.idFor(date) to "goal_journal"
        }
        return wipeTombstones(rows, now.toString())
    }

    private suspend fun applyMergedDocument(
        jsonText: String,
        zone: ZoneId,
        local: SyncDocument.Parsed? = null,
    ): SyncResult {
        val parsed = SyncDocument.parse(jsonText, zone)
        if (parsed !is SyncDocument.ParseResult.Success) {
            return SyncResult.Failed(appContext.getString(R.string.sync_error_merged_invalid))
        }
        val doc = parsed.parsed
        val revisionMap = mutableMapOf<String, SyncRevision>()

        fun track(id: String, updatedAt: String, deletedAt: String?, kind: String) {
            revisionMap[id] = SyncRevision(updatedAt, deletedAt, kind)
        }

        // `imageFilename` never rides the wire (local bytes only); emoji only
        // ride the wire since the #34 fix, and photo refs only since sync-1.4
        // (#122) — rows merged from older docs lack both. Re-attach the local
        // visual fields before replacing, otherwise a sync/import drops every
        // food photo and emoji (#34).
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
        prefs.setFavoritesAtomic(liveFavorites)

        val liveWeights = doc.weights.mapNotNull {
            track(it.id, it.updatedAt, it.deletedAt, "weight")
            it.entry
        }
        applyMergedWeightBuckets(liveWeights, local?.weights?.map { it.id }.orEmpty())

        val liveBodyFat = doc.bodyFats.mapNotNull {
            track(it.id, it.updatedAt, it.deletedAt, "bodyfat")
            it.entry
        }
        applyMergedBodyFatBuckets(liveBodyFat, local?.bodyFats?.map { it.id }.orEmpty())

        val liveMeasures = doc.measurements.mapNotNull {
            track(it.id, it.updatedAt, it.deletedAt, "measure")
            it.entry
        }
        applyMergedMeasurementBuckets(liveMeasures, local?.measurements?.map { it.id }.orEmpty())

        val liveWater = doc.water.mapNotNull {
            track(it.id, it.updatedAt, it.deletedAt, "water")
            it.entry
        }
        prefs.setWaterEntries(liveWater)

        val liveNotes = doc.dailyNotes.mapNotNull {
            track(it.id, it.updatedAt, it.deletedAt, "daily_note")
            it.entry
        }
        prefs.setNoteEntries(liveNotes)

        val liveNicotine = doc.nicotine.mapNotNull {
            track(it.id, it.updatedAt, it.deletedAt, "nicotine")
            it.entry
        }
        prefs.setNicotineEntries(liveNicotine)

        val liveCaffeine = doc.caffeine.mapNotNull {
            track(it.id, it.updatedAt, it.deletedAt, "caffeine")
            it.entry
        }
        prefs.setCaffeineEntries(liveCaffeine)

        val liveRecipes = doc.recipes.mapNotNull {
            track(it.id, it.updatedAt, it.deletedAt, "recipe")
            it.entry
        }
        prefs.setRecipes(liveRecipes)

        // Goal journal (#60): per-day LWW by updatedAtMillis (GoalJournal.merge
        // against the local list — the wire merge already collapsed ids), then
        // the 400-day prune. Rows absent from an older remote parse to [] and
        // merge to a no-op, so pre-#60 docs round-trip untouched.
        doc.goalJournal.forEach {
            track(it.id, it.updatedAt, it.deletedAt, "goal_journal")
        }
        val localJournal = prefs.goalJournal.first()
        val mergedJournal = GoalJournal.prune(
            GoalJournal.merge(localJournal, doc.goalJournal.mapNotNull { it.entry }),
            LocalDate.now(),
        )
        prefs.setGoalJournal(mergedJournal)

        prefs.setSyncRevisions(revisionMap)
        return SyncResult.Success(appContext.getString(R.string.sync_success_applied))
    }

    /**
     * Apply merged body-metric rows without [PreferencesStore.setWeightEntries]
     * replaceAll. Upsert the merged live set and drop only ids that were in the
     * local *export snapshot* and did not survive the merge. A weigh-in logged
     * after that snapshot (#63) is not in [localIds], so it stays.
     */
    private suspend fun applyMergedWeightBuckets(live: List<WeightEntry>, localIds: Collection<String>) {
        val drop = uuidDropSet(localIds, live.map { it.id.toString() })
        val current = prefs.weightEntries.first()
        prefs.applyWeightBucketChanges(
            upsertsByMonth = live.groupBy { it.date.monthBucket() },
            removalIdsByMonth = removalsByMonth(
                current, drop, idOf = { it.id }, monthOf = { it.date.monthBucket() },
            ),
        )
    }

    private suspend fun applyMergedBodyFatBuckets(live: List<BodyFatEntry>, localIds: Collection<String>) {
        val drop = uuidDropSet(localIds, live.map { it.id.toString() })
        val current = prefs.bodyFatEntries.first()
        prefs.applyBodyFatBucketChanges(
            upsertsByMonth = live.groupBy { it.date.monthBucket() },
            removalIdsByMonth = removalsByMonth(
                current, drop, idOf = { it.id }, monthOf = { it.date.monthBucket() },
            ),
        )
    }

    private suspend fun applyMergedMeasurementBuckets(live: List<BodyMeasurement>, localIds: Collection<String>) {
        val drop = uuidDropSet(localIds, live.map { it.id.toString() })
        val current = prefs.bodyMeasurements.first()
        prefs.applyMeasurementBucketChanges(
            upsertsByMonth = live.groupBy { it.date.monthBucket() },
            removalIdsByMonth = removalsByMonth(
                current, drop, idOf = { it.id }, monthOf = { it.date.monthBucket() },
            ),
        )
    }
}

private fun Instant.monthBucket(): YearMonth = YearMonth.from(atZone(ZoneId.systemDefault()))

private fun uuidDropSet(localIds: Collection<String>, mergedLiveIds: Collection<String>): Set<UUID> =
    SyncMerge.dropIdsNotInMerged(localIds, mergedLiveIds).mapNotNull {
        runCatching { UUID.fromString(it) }.getOrNull()
    }.toSet()

private fun <T> removalsByMonth(
    current: List<T>,
    dropIds: Set<UUID>,
    idOf: (T) -> UUID,
    monthOf: (T) -> YearMonth,
): Map<YearMonth, Set<UUID>> {
    if (dropIds.isEmpty()) return emptyMap()
    return current.filter { idOf(it) in dropIds }
        .groupBy(monthOf)
        .mapValues { (_, rows) -> rows.map(idOf).toSet() }
}

/**
 * The sync wire never carries `imageFilename` (local bytes only), and only
 * carries emoji since the #34 fix and photo refs since sync-1.4 (#122), so a
 * merged row is usually parsed without them. Keep the local visual fields (the
 * wire never overwrites one either; the emoji picker only sets, never removes)
 * so applying a merged document cannot drop food photos or emoji (#34). A wire
 * row carrying fresh photo refs wins over local ones (photo re-pick moved the
 * ref); a row without refs keeps the local refs (photo phase gated off).
 */
internal fun reattachLocalFields(entry: FoodEntry, local: Map<UUID, FoodEntry>): FoodEntry {
    val localEntry = local[entry.id] ?: return entry
    return entry.copy(
        imageFilename = entry.imageFilename ?: localEntry.imageFilename,
        emoji = entry.emoji ?: localEntry.emoji,
        photoFilenames = entry.photoFilenames.ifEmpty { localEntry.photoFilenames },
    )
}

/** Last write wins on duplicate ids. */
internal fun wipeTombstones(rows: List<Pair<UUID, String>>, now: String): Map<String, SyncRevision> {
    val out = LinkedHashMap<String, SyncRevision>()
    for ((id, kind) in rows) {
        out[id.toString()] = SyncRevision(updatedAt = now, deletedAt = now, kind = kind)
    }
    return out
}

package app.chompass.services.offline

import android.content.Context
import android.os.StatFs
import app.chompass.data.InstalledOfflinePack
import app.chompass.data.PreferencesStore
import app.chompass.services.ai.FoodAnalysisService
import app.chompass.services.grounding.BarcodePackIndex
import app.chompass.services.grounding.PackResolver
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/** Identifies one downloadable offline barcode pack. */
data class PackDescriptor(val id: String)

/** The shipped pack catalog (naming contract: `<pack-id>-<version>.sqlite.gz`). */
object OfflineIndexPacks {
    val USDA_GTINS = PackDescriptor("usda-gtins")
    val OFF_DE = PackDescriptor("off-de")
    val ALL = listOf(USDA_GTINS, OFF_DE)

    fun byId(id: String): PackDescriptor? = ALL.firstOrNull { it.id == id }
}

/** Per-pack lifecycle surfaced to Settings. */
sealed interface OfflineIndexState {
    data object NotDownloaded : OfflineIndexState
    data class Downloading(val progress: Float) : OfflineIndexState
    data class Installed(val version: String, val sha256: String, val bytes: Long) : OfflineIndexState
    data class Failed(val reason: String?) : OfflineIndexState
}

/**
 * Manifest published next to each pack and downloaded first; `sha256`/`bytes`
 * describe the `.sqlite.gz` asset and [url] is its absolute download URL.
 */
@Serializable
internal data class PackManifest(
    val id: String,
    val version: String,
    val sha256: String,
    val bytes: Long,
    val url: String,
    val rows: Long? = null,
    val license: String? = null,
    val source: String? = null,
    val generatedAt: String? = null,
)

/**
 * Owns `filesDir/offline_index/` for the downloadable offline barcode packs:
 * per-pack state, the manifest-resolve → stream-to-.part → sha-gate → gunzip
 * → atomic-rename install (the ModelDownloadWorker mechanics as plain,
 * JVM-testable functions — packs are tens of MB, so no resume/foreground),
 * remove, and open read-only [BarcodePackIndex] handles for the lookup chain.
 * Installed entries are validated against the stored file digest at startup;
 * a mismatch drops the entry and the file.
 *
 * Publishing (maintainer): create a Codeberg release tagged
 * `offline-index-<yyyymmdd>` on fitguy/Chompass and attach the pack assets;
 * [resolveManifest] picks the newest such release by the naming contract.
 */
class OfflineIndexManager(
    context: Context,
    private val prefs: PreferencesStore,
    private val client: OkHttpClient = FoodAnalysisService.defaultClient,
    private val releasesApiUrl: String =
        "https://codeberg.org/api/v1/repos/fitguy/chompass/releases?limit=50",
    private val diskFreeBytes: (File) -> Long = { dir -> StatFs(dir.path).availableBytes },
) {
    private val appContext = context.applicationContext
    private val dir = File(appContext.filesDir, "offline_index")
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _states = MutableStateFlow<Map<String, OfflineIndexState>>(emptyMap())
    val states: StateFlow<Map<String, OfflineIndexState>> = _states.asStateFlow()

    private val opened = HashMap<String, BarcodePackIndex>()

    init {
        scope.launch { validateInstalled() }
    }

    /** Enqueues the WorkManager download (Wi-Fi-only honored via the constraint). */
    fun requestDownload(packId: String) {
        val pack = OfflineIndexPacks.byId(packId) ?: return
        scope.launch {
            val wifiOnly = runCatching { prefs.offlineIndexWifiOnly.first() }.getOrDefault(true)
            OfflineIndexDownloadWorker.enqueue(appContext, pack.id, wifiOnly)
        }
    }

    /**
     * Resolves the newest matching release manifest, then streams, verifies,
     * unpacks and records [pack]. Throws on failure (the worker maps that to a
     * failed result); the per-pack state reflects every transition.
     */
    suspend fun download(pack: PackDescriptor) {
        setState(pack.id, OfflineIndexState.Downloading(0f))
        try {
            val manifest = resolveManifest(pack)
            dir.mkdirs()
            val free = runCatching { diskFreeBytes(dir) }.getOrDefault(0L)
            if (free < 2 * manifest.bytes) {
                throw IOException("Not enough free storage for the ${pack.id} pack")
            }
            install(pack, manifest)
            _states.update {
                it + (pack.id to OfflineIndexState.Installed(manifest.version, manifest.sha256, manifest.bytes))
            }
        } catch (e: CancellationException) {
            setState(pack.id, OfflineIndexState.NotDownloaded)
            throw e
        } catch (e: Exception) {
            cleanup(pack.id)
            setState(pack.id, OfflineIndexState.Failed(e.message))
            throw e
        }
    }

    /** Deletes the installed file and its pref entry; a later download starts fresh. */
    fun remove(packId: String) {
        closeIndex(packId)
        packFile(packId).delete()
        scope.launch { runCatching { prefs.updateOfflineIndexPack(packId, null) } }
        setState(packId, OfflineIndexState.NotDownloaded)
    }

    /** Open read-only handle for an installed pack, or null when absent. */
    fun openPack(packId: String): BarcodePackIndex? {
        val file = packFile(packId)
        if (!file.isFile) {
            closeIndex(packId)
            return null
        }
        synchronized(opened) {
            opened[packId]?.let { return it }
            return BarcodePackIndex(file).apply { open() }.also { opened[packId] = it }
        }
    }

    /** Streams the asset through the sha gate, gunzips, atomically installs, records the pref. */
    private suspend fun install(pack: PackDescriptor, manifest: PackManifest) =
        withContext(Dispatchers.IO) {
            val gzPart = File(dir, "${pack.id}.sqlite.gz.part")
            val gzFile = File(dir, "${pack.id}.sqlite.gz")
            val sqlitePart = File(dir, "${pack.id}.sqlite.part")
            val sqliteFile = packFile(pack.id)
            try {
                val digest = MessageDigest.getInstance("SHA-256")
                var written = 0L
                val request = Request.Builder().url(manifest.url).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("HTTP ${response.code} downloading the ${pack.id} pack")
                    }
                    val body = response.body ?: throw IOException("Empty body downloading the ${pack.id} pack")
                    gzPart.outputStream().use { out ->
                        body.byteStream().use { input ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                val read = input.read(buffer)
                                if (read == -1) break
                                out.write(buffer, 0, read)
                                digest.update(buffer, 0, read)
                                written += read
                                if (manifest.bytes > 0) {
                                    val progress = ((written * 100f) / manifest.bytes).coerceIn(0f, 99f) / 100f
                                    setState(pack.id, OfflineIndexState.Downloading(progress))
                                }
                            }
                        }
                    }
                }
                val actual = digest.digest().joinToString("") { "%02x".format(it) }
                if (!actual.equals(manifest.sha256, ignoreCase = true)) {
                    throw IOException("Checksum mismatch on the ${pack.id} pack")
                }
                if (!gzPart.renameTo(gzFile)) {
                    gzPart.copyTo(gzFile, overwrite = true)
                    gzPart.delete()
                }
                GZIPInputStream(gzFile.inputStream().buffered()).use { input ->
                    sqlitePart.outputStream().buffered().use { out -> input.copyTo(out) }
                }
                if (!sqlitePart.renameTo(sqliteFile)) {
                    sqliteFile.delete()
                    if (!sqlitePart.renameTo(sqliteFile)) {
                        throw IOException("Could not finalize the ${pack.id} pack")
                    }
                }
                prefs.updateOfflineIndexPack(
                    pack.id,
                    InstalledOfflinePack(
                        version = manifest.version,
                        sha256 = manifest.sha256,
                        bytes = manifest.bytes,
                        fileSha256 = sha256Hex(sqliteFile),
                    ),
                )
            } finally {
                gzPart.delete()
                gzFile.delete()
                sqlitePart.delete()
            }
        }

    /**
     * Picks the newest release whose tag starts with `offline-index-` and
     * carries this pack's manifest asset (`<pack-id>-<version>.json`, version
     * from the tag).
     */
    internal suspend fun resolveManifest(pack: PackDescriptor): PackManifest {
        val body = fetchText(releasesApiUrl)
            ?: throw IOException("Could not reach the offline index release list")
        val releases = runCatching { org.json.JSONArray(body) }.getOrNull()
            ?: throw IOException("Offline index release list was not valid JSON")
        for (i in 0 until releases.length()) {
            val release = releases.optJSONObject(i) ?: continue
            val tag = release.optString("tag_name")
            if (!tag.startsWith(RELEASE_TAG_PREFIX)) continue
            val version = tag.removePrefix(RELEASE_TAG_PREFIX)
            val assets = release.optJSONArray("assets") ?: continue
            val manifestAsset = (0 until assets.length())
                .mapNotNull { assets.optJSONObject(it) }
                .firstOrNull { it.optString("name") == "${pack.id}-$version.json" }
                ?: continue
            val manifestUrl = manifestAsset.optString("browser_download_url")
            if (manifestUrl.isBlank()) continue
            val manifestBody = fetchText(manifestUrl)
                ?: throw IOException("Could not fetch the ${pack.id} manifest")
            val manifest = runCatching { json.decodeFromString<PackManifest>(manifestBody) }.getOrNull()
                ?: throw IOException("The ${pack.id} manifest was not valid JSON")
            if (manifest.id != pack.id) throw IOException("The ${pack.id} manifest identity mismatched")
            return manifest
        }
        throw IOException("No offline index release found for ${pack.id}")
    }

    private suspend fun fetchText(url: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) null else response.body?.string()
            }
        }.getOrNull()
    }

    /** Startup gate: drop entries whose installed file is missing or fails its digest. */
    private suspend fun validateInstalled() {
        val installed = runCatching { prefs.offlineIndexPacks.first() }.getOrNull().orEmpty()
        if (installed.isEmpty()) return
        val kept = HashMap<String, InstalledOfflinePack>()
        for ((id, entry) in installed) {
            val file = packFile(id)
            if (file.isFile && entry.fileSha256 == sha256Hex(file)) {
                kept[id] = entry
            } else {
                closeIndex(id)
                file.delete()
                runCatching { prefs.updateOfflineIndexPack(id, null) }
            }
        }
        _states.update { current ->
            val next = current.toMutableMap()
            for (id in installed.keys) {
                val entry = kept[id]
                next[id] = if (entry != null) {
                    OfflineIndexState.Installed(entry.version, entry.sha256, entry.bytes)
                } else {
                    OfflineIndexState.NotDownloaded
                }
            }
            next
        }
    }

    private fun cleanup(packId: String) {
        closeIndex(packId)
        packFile(packId).delete()
    }

    private fun closeIndex(packId: String) {
        synchronized(opened) { opened.remove(packId)?.close() }
    }

    private fun setState(packId: String, state: OfflineIndexState) {
        _states.update { it + (packId to state) }
    }

    private fun packFile(packId: String) = File(dir, "$packId.sqlite")

    private fun sha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val RELEASE_TAG_PREFIX = "offline-index-"
    }
}

/** AppContainer's [PackResolver]: the two shipped packs by id. */
class InstalledPackResolver(private val manager: OfflineIndexManager) : PackResolver {
    override fun gtins(): BarcodePackIndex? = manager.openPack(OfflineIndexPacks.USDA_GTINS.id)
    override fun regional(): BarcodePackIndex? = manager.openPack(OfflineIndexPacks.OFF_DE.id)
}

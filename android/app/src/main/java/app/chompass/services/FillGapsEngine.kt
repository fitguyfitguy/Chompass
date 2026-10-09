package app.chompass.services

import android.content.Context
import app.chompass.R
import app.chompass.data.FoodRepository
import app.chompass.data.PreferencesStore
import app.chompass.models.FoodEntry
import app.chompass.models.GapMacroField
import app.chompass.models.MicronutrientField
import app.chompass.models.fillMissingFrom
import app.chompass.models.gapReport
import app.chompass.services.ai.AiError
import app.chompass.services.ai.FoodAnalysis
import app.chompass.services.ai.FoodAnalysisService
import app.chompass.services.ai.userMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import java.security.MessageDigest
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID

/**
 * One gapped row in the fill-missing scan: a favorite template or a diary
 * entry of the current month whose [FoodEntry.gapReport] has gaps.
 */
data class GappedItem(val favorite: Boolean, val entry: FoodEntry)

/**
 * Snapshot of everything the AI fill-missing flow may act on: favorites plus
 * current-month diary rows that are missing macros / micros / constituent
 * values. The [signature] fingerprints the item set so the Home banner and a
 * running sheet session can recognize the same scan (dismissal + session
 * continuity) without holding the entries.
 */
data class GapScan(val items: List<GappedItem>) {
    val count: Int get() = items.size

    /** Stable SHA-256 hex over the (kind, id) pairs; changes when the set changes. */
    val signature: String by lazy {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.digest(
            items.joinToString("|") { (if (it.favorite) "f" else "d") + ":" + it.entry.id }.toByteArray(),
        ).joinToString("") { "%02x".format(it) }
    }
}

/** Home banner payload: gapped-row count + the scan signature it came from. */
data class FillGapsBanner(val count: Int, val signature: String)

/** Lifecycle of one queue row inside the gaps sheet. */
enum class FillStatus { PENDING, RUNNING, FILLED, FAILED, APPLIED }

data class FillQueueItem(
    val target: GappedItem,
    val status: FillStatus = FillStatus.PENDING,
    val analysis: FoodAnalysis? = null,
    val merged: FoodEntry? = null,
    /** Res ids of fields that were missing before the fill and are present after. */
    val filledLabels: List<Int> = emptyList(),
    val errorText: String? = null,
)

/**
 * Pre-apply snapshot for undo: the original diary rows (direct fills + cascade
 * copies) and the original favorite templates, restored verbatim by [FillGapsEngine.undo].
 */
data class UndoSnapshot(val diary: List<FoodEntry>, val favorites: List<FoodEntry>) {
    val count: Int get() = diary.size + favorites.size
}

/**
 * Scan favorites + the current month's diary for gapped rows, gate a
 * once-per-day Home banner, and run the sequential fill session behind the
 * gaps sheet: per-row or run-all AI fills (only-missing merges, nothing is
 * written until Apply), optional today-cascade for favorite templates, apply
 * with undo. The sheet drives runs from its own coroutine scope; cancel is
 * coroutine cancel and nothing is persisted until [applyFilled].
 */
class FillGapsEngine(
    private val foodRepository: FoodRepository,
    private val prefs: PreferencesStore,
    private val foodAnalysis: FoodAnalysisService,
    private val appContext: Context,
) {
    companion object {
        /**
         * Pure scan shaping: favorites first (name-sorted), then current-month
         * diary rows (newest first), gated on [EntryGapReport.hasMacroGaps] —
         * the mass surfaces never list micro-only gaps.
         */
        internal fun computeScan(favorites: List<FoodEntry>, monthEntries: List<FoodEntry>): GapScan =
            GapScan(
                favorites.filter { it.gapReport().hasMacroGaps }
                    .sortedBy { it.name.lowercase() }
                    .map { GappedItem(favorite = true, entry = it) } +
                    monthEntries.filter { it.gapReport().hasMacroGaps }
                        .sortedByDescending { it.timestamp }
                        .map { GappedItem(favorite = false, entry = it) },
            )

    /**
     * Pure banner truth table: visible only when AI features are on, the scan
     * is non-empty, and this exact scan was never dismissed. The once-per-day
     * rule is NOT here: a reactive shown-today check would collapse the chip
     * the moment its own shown-write re-emits, before anyone can tap it. It
     * lives in HomeViewModel instead (startup pref gate + session latch).
     */
    internal fun bannerState(
        scan: GapScan,
        aiEnabled: Boolean,
        dismissed: Set<String>,
    ): FillGapsBanner? {
        if (!aiEnabled || scan.items.isEmpty()) return null
        if (scan.signature in dismissed) return null
        return FillGapsBanner(count = scan.count, signature = scan.signature)
    }
    } // companion

    /**
     * Reactive scan over favorites + the current month. YearMonth.now() is
     * pinned at construction — accepted midnight edge; the next store write or
     * app restart refreshes it.
     */
    val scan: Flow<GapScan> = combine(
        foodRepository.favorites,
        prefs.foodEntriesForMonth(YearMonth.now()),
        ::computeScan,
    )

    /** Banner gate combining the scan with the master switch + sticky dismissals. */
    val banner: Flow<FillGapsBanner?> = combine(
        scan,
        prefs.aiFeaturesEnabled,
        prefs.fillMissingDismissedSignatures,
    ) { scan, aiEnabled, dismissed ->
        bannerState(scan, aiEnabled, dismissed)
    }

    suspend fun markBannerShown() {
        prefs.setFillMissingBannerShown(LocalDate.now().toEpochDay())
    }

    suspend fun dismissBanner(signature: String) {
        prefs.setFillMissingSignatureDismissed(signature)
    }

    /** Null = no session. The sheet owns the lifecycle (load / reset). */
    private val _queue = MutableStateFlow<List<FillQueueItem>?>(null)
    val queue: StateFlow<List<FillQueueItem>?> = _queue.asStateFlow()

    private var loadedSignature: String? = null

    /** Build PENDING items; an existing session with the same signature is kept (no clobber mid-run). */
    fun loadQueue(scan: GapScan) {
        val current = _queue.value
        if (current != null && loadedSignature == scan.signature) return
        _queue.value = scan.items.map { FillQueueItem(target = it) }
        loadedSignature = scan.signature
    }

    /** Sheet closed: discard in-memory analyses; applied writes are untouched. */
    fun resetQueue() {
        _queue.value = null
        loadedSignature = null
    }

    /** Published right before the first apply write; consumed by the Home undo snackbar. */
    private val _lastUndo = MutableStateFlow<UndoSnapshot?>(null)
    val lastUndo: StateFlow<UndoSnapshot?> = _lastUndo.asStateFlow()

    fun clearUndo() {
        _lastUndo.value = null
    }

    /**
     * Fill one row: RUNNING → [FoodAnalysisService.fillEntry] → only-missing
     * merge. `merged == entry` means nothing was fillable (Already complete).
     * PENDING and FAILED rows (re)enter RUNNING — the sheet offers retry on
     * FAILED.
     */
    suspend fun runOne(item: FillQueueItem) {
        val target = item.target
        _queue.update { list ->
            list?.map {
                if (it.target.entry.id == target.entry.id &&
                    (it.status == FillStatus.PENDING || it.status == FillStatus.FAILED)
                ) {
                    it.copy(status = FillStatus.RUNNING, errorText = null)
                } else {
                    it
                }
            }
        }
        try {
            val analysis = foodAnalysis.fillEntry(target.entry)
            val merged = target.entry.fillMissingFrom(analysis)
            val before = target.entry.gapReport()
            val after = merged.gapReport()
            val filledLabels = (before.missingMacros - after.missingMacros).map(GapMacroField::labelRes) +
                (before.missingMicros - after.missingMicros).map(MicronutrientField::labelRes)
            _queue.update { list ->
                list?.map {
                    if (it.target.entry.id == target.entry.id && it.status == FillStatus.RUNNING) {
                        it.copy(
                            status = FillStatus.FILLED,
                            analysis = analysis,
                            merged = merged,
                            filledLabels = filledLabels,
                        )
                    } else {
                        it
                    }
                }
            }
        } catch (e: Exception) {
            // Same mapping as the edit sheet's fill mode; cancellation rethrows below.
            if (e is kotlinx.coroutines.CancellationException) throw e
            val text = (e as? AiError)?.userMessage(appContext)
                ?: appContext.getString(R.string.edit_reprocessing_failed)
            _queue.update { list ->
                list?.map {
                    if (it.target.entry.id == target.entry.id && it.status == FillStatus.RUNNING) {
                        it.copy(status = FillStatus.FAILED, errorText = text)
                    } else {
                        it
                    }
                }
            }
        }
    }

    /** Sequential run over every PENDING row (the sheet's Fill all). */
    suspend fun runAll() {
        val pending = _queue.value?.filter { it.status == FillStatus.PENDING } ?: return
        for (item in pending) runOne(item)
    }

    /** True while any row is mid-fill (Apply waits it out instead of dropping the tap). */
    fun isRunning(): Boolean = _queue.value?.any { it.status == FillStatus.RUNNING } == true

    /**
     * Today's diary copies of a favorite template (same normalized name, other
     * id). Count only here; the copies are filled locally at apply — no extra
     * AI requests.
     */
    suspend fun cascadeCandidates(template: FoodEntry): List<FoodEntry> {
        val today = LocalDate.now()
        return prefs.foodEntriesForMonth(YearMonth.now()).first()
            .filter {
                it.timestamp.atZone(ZoneId.systemDefault()).toLocalDate() == today &&
                    it.favoriteKey == template.favoriteKey &&
                    it.id != template.id
            }
    }

    /**
     * Persist every FILLED row: diary rows (plus locally-filled cascade copies
     * of checked favorites' today-loggings) in one batched write, favorites via
     * [FoodRepository.updateFavorite]. Snapshots originals for undo first.
     */
    suspend fun applyFilled(cascade: Set<UUID>) {
        val session = _queue.value ?: return
        val filled = session.filter { it.status == FillStatus.FILLED && it.merged != null }
        if (filled.isEmpty()) return

        val diaryTargets = filled.filter { !it.target.favorite }
        val cascadeCopies = mutableListOf<Pair<FoodEntry, FoodEntry>>() // original → filled copy
        for (item in filled) {
            if (item.target.favorite && item.target.entry.id in cascade && item.analysis != null) {
                for (copy in cascadeCandidates(item.target.entry)) {
                    // Copies keep their own values; only missing fields take the
                    // template's analysis — same only-missing semantics, no AI call.
                    cascadeCopies += copy to copy.fillMissingFrom(item.analysis)
                }
            }
        }

        val snapshot = UndoSnapshot(
            diary = diaryTargets.map { it.target.entry } + cascadeCopies.map { it.first },
            favorites = filled.filter { it.target.favorite }.map { it.target.entry },
        )
        _lastUndo.value = snapshot

        foodRepository.updateEntries(diaryTargets.map { it.merged!! } + cascadeCopies.map { it.second })
        for (item in filled.filter { it.target.favorite }) {
            foodRepository.updateFavorite(item.target.entry, item.merged!!)
        }

        val appliedIds = filled.map { it.target.entry.id }.toSet()
        _queue.update { list ->
            list?.map { if (it.target.entry.id in appliedIds) it.copy(status = FillStatus.APPLIED) else it }
        }
    }

    /** Restore the last apply verbatim (diary rows, cascade copies, favorites). */
    suspend fun undo() {
        val snapshot = _lastUndo.value ?: return
        foodRepository.updateEntries(snapshot.diary)
        val currentFavorites = prefs.favoriteFoodEntries.first()
        for (original in snapshot.favorites) {
            val current = currentFavorites.find { it.id == original.id } ?: continue
            foodRepository.updateFavorite(current, original)
        }
        _lastUndo.value = null
    }
}

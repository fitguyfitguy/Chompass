package app.chompass.models

import app.chompass.R
import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID

/**
 * Caffeine source kinds for the optional caffeine tracker. Mg-based habit
 * logging (coffee, tea, energy drinks); [defaultMg] is what the +1 quick chip
 * logs for that kind (FDA-style ballpark: 95 mg coffee, 28 mg tea, 80 mg
 * energy drink).
 *
 * Since custom presets (#55 follow-up) the enum is only the *builtin*
 * vocabulary: label resources and builtin quick-chip defaults. Entries store
 * raw kind ids ([CaffeineEntry.kind] is a String) so custom presets never
 * grow this enum; unknown ids display as the localized "Other" fallback.
 */
enum class CaffeineKind(val storageKey: String, val labelRes: Int, val defaultMg: Double?) {
    COFFEE("coffee", R.string.caffeine_kind_coffee, 95.0),
    TEA("tea", R.string.caffeine_kind_tea, 28.0),
    ENERGY("energy", R.string.caffeine_kind_energy, 80.0),
    OTHER("other", R.string.caffeine_kind_other, null);

    companion object {
        fun fromStorage(raw: String?): CaffeineKind =
            entries.firstOrNull { it.storageKey == raw } ?: OTHER

        /** Default quick-log chips on the Add Food hub (mirrors NicotineKind). */
        val DefaultQuickKinds = listOf(COFFEE, TEA, ENERGY)
    }
}

/**
 * One logged caffeine dose (a coffee, a tea, an energy drink). Mg-first: the
 * daily total is the sum of [mg]; [kind] drives the label and the quick-chip
 * default amount. [kind] is a raw preset id: builtin storageKeys ("coffee"…)
 * or custom `t_…` ids (see [HabitPresetCatalog]); the wire format and month
 * buckets accept any string, old clients degrade unknown ids to "other".
 */
@Serializable
data class CaffeineEntry(
    @Serializable(with = UuidSerializer::class)
    val id: UUID = UUID.randomUUID(),
    @Serializable(with = InstantSerializer::class)
    val date: Instant = Instant.now(),
    @Serializable(with = TrackerKindIdSerializer::class)
    val kind: String = CaffeineKind.COFFEE.storageKey,
    val mg: Double,
    /** Milk sidecar food row; null on legacy buckets and caffeine-only logs. */
    @Serializable(with = UuidSerializer::class)
    val linkedFoodEntryId: UUID? = null,
) {
    companion object {
        /**
         * Quick factory for a log about now. [mg] falls back to the *builtin*
         * default for known kinds; custom presets pass their preset default
         * explicitly (the hub/VM resolves it from the catalog).
         */
        fun forNow(kind: String, mg: Double? = null): CaffeineEntry =
            CaffeineEntry(
                kind = normalizeKindId(kind),
                mg = (mg ?: builtinCaffeineDefaultMg(kind) ?: 0.0).coerceAtLeast(0.0),
            )

        /** Day caffeine micro: food-entry caffeine plus tracker logs. */
        fun dayCaffeineMg(food: List<FoodEntry>, tracker: List<CaffeineEntry>): Double =
            food.sumOf { it.caffeine ?: 0.0 } + tracker.sumOf { it.mg }
    }
}

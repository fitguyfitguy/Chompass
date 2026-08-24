package app.chompass.models

import app.chompass.R
import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID

/**
 * Caffeine source kinds for the optional caffeine tracker. Mg-based habit
 * logging (coffee, tea, energy drinks); [defaultMg] is what the +1 quick chip
 * logs for that kind (FDA-style ballpark: 95 mg coffee, 28 mg tea, 80 mg
 * energy drink). `OTHER` is the parse fallback for unknown wire values.
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

        /** User-configurable quick chips, validated like NicotineKind. */
        fun quickKindsFromStorage(raw: String?): List<CaffeineKind> {
            if (raw.isNullOrBlank()) return DefaultQuickKinds
            val parsed = raw.split(',').mapNotNull { fromStorage(it.trim()) }
            return parsed.distinct().ifEmpty { DefaultQuickKinds }
        }

        fun quickKindsToStorage(kinds: List<CaffeineKind>): String =
            kinds.distinct().joinToString(",") { it.storageKey }
    }
}

/**
 * One logged caffeine dose (a coffee, a tea, an energy drink). Mg-first: the
 * daily total is the sum of [mg]; [kind] drives the label and the quick-chip
 * default amount.
 */
@Serializable
data class CaffeineEntry(
    @Serializable(with = UuidSerializer::class)
    val id: UUID = UUID.randomUUID(),
    @Serializable(with = InstantSerializer::class)
    val date: Instant = Instant.now(),
    val kind: CaffeineKind = CaffeineKind.COFFEE,
    val mg: Double,
) {
    companion object {
        fun forNow(kind: CaffeineKind, mg: Double? = null): CaffeineEntry =
            CaffeineEntry(
                kind = kind,
                mg = (mg ?: kind.defaultMg ?: 0.0).coerceAtLeast(0.0),
            )
    }
}

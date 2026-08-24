package app.chompass.models

import app.chompass.R
import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID

/**
 * Nicotine source kinds for the optional nicotine tracker. Count-based habit
 * logging (cigarettes, vapes, pouches, gum, patches); `OTHER` is the parse
 * fallback for unknown wire values so old/new clients stay interoperable.
 */
enum class NicotineKind(val storageKey: String, val labelRes: Int) {
    CIGARETTE("cigarette", R.string.nicotine_kind_cigarette),
    VAPE("vape", R.string.nicotine_kind_vape),
    POUCH("pouch", R.string.nicotine_kind_pouch),
    GUM("gum", R.string.nicotine_kind_gum),
    PATCH("patch", R.string.nicotine_kind_patch),
    OTHER("other", R.string.nicotine_kind_other);

    companion object {
        fun fromStorage(raw: String?): NicotineKind =
            entries.firstOrNull { it.storageKey == raw } ?: OTHER

        /** Default quick-log chips on the Add Food hub (mirrors WaterQuickPresets). */
        val DefaultQuickKinds = listOf(CIGARETTE, VAPE, POUCH)

        /** User-configurable quick chips, validated like WaterQuickPresets. */
        fun quickKindsFromStorage(raw: String?): List<NicotineKind> {
            if (raw.isNullOrBlank()) return DefaultQuickKinds
            val parsed = raw.split(',').mapNotNull { fromStorage(it.trim()) }
            return parsed.distinct().ifEmpty { DefaultQuickKinds }
        }

        fun quickKindsToStorage(kinds: List<NicotineKind>): String =
            kinds.distinct().joinToString(",") { it.storageKey }
    }
}

/**
 * One logged nicotine dose (a cigarette, a vape session, a pouch, ...).
 * Count-first: the daily total is the sum of [count]; [mg] is an optional
 * per-dose detail (pouches, vape liquid) shown in history when present.
 */
@Serializable
data class NicotineEntry(
    @Serializable(with = UuidSerializer::class)
    val id: UUID = UUID.randomUUID(),
    @Serializable(with = InstantSerializer::class)
    val date: Instant = Instant.now(),
    val kind: NicotineKind = NicotineKind.CIGARETTE,
    val count: Int = 1,
    val mg: Double? = null,
) {
    companion object {
        fun forNow(kind: NicotineKind, count: Int = 1, mg: Double? = null): NicotineEntry =
            NicotineEntry(
                kind = kind,
                count = count.coerceAtLeast(1),
                mg = mg?.takeIf { it > 0 },
            )
    }
}

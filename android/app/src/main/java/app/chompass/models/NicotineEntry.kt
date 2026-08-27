package app.chompass.models

import app.chompass.R
import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID

/**
 * Nicotine source kinds for the optional nicotine tracker. Count-based habit
 * logging (cigarettes, vapes, pouches, gum, patches).
 *
 * Since custom presets (#55 follow-up) the enum is only the *builtin*
 * vocabulary: label resources. Entries store raw kind ids
 * ([NicotineEntry.kind] is a String) so custom presets never grow this enum;
 * unknown ids display as the localized "Other" fallback.
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
    }
}

/**
 * One logged nicotine dose (a cigarette, a vape session, a pouch, ...).
 * Count-first: the daily total is the sum of [count]; [mg] is an optional
 * per-dose detail (pouches, vape liquid) shown in history when present.
 * [kind] is a raw preset id: builtin storageKeys ("cigarette"…) or custom
 * `t_…` ids (see [HabitPresetCatalog]).
 */
@Serializable
data class NicotineEntry(
    @Serializable(with = UuidSerializer::class)
    val id: UUID = UUID.randomUUID(),
    @Serializable(with = InstantSerializer::class)
    val date: Instant = Instant.now(),
    @Serializable(with = TrackerKindIdSerializer::class)
    val kind: String = NicotineKind.CIGARETTE.storageKey,
    val count: Int = 1,
    val mg: Double? = null,
) {
    companion object {
        fun forNow(kind: String, count: Int = 1, mg: Double? = null): NicotineEntry =
            NicotineEntry(
                kind = normalizeKindId(kind),
                count = count.coerceAtLeast(1),
                mg = mg?.takeIf { it > 0 },
            )
    }
}

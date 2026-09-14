package app.chompass.models

import app.chompass.R
import java.util.Locale
import kotlin.random.Random
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json

/**
 * Custom / renamed tracker presets (Codeberg #55 follow-up). Identity and
 * display are split: [HabitPreset.id] is what entries store and sync (builtin
 * ids equal the legacy kind enum storageKeys, customs get a `t_` hex id),
 * [HabitPreset.label] is a display override where blank means "locale default
 * for builtins" (the MealDef pattern from MealCatalog, #61).
 *
 * One shape serves both trackers; the per-tracker constraints live on
 * [HabitPresetDomain] (builtin id set, defaults, wheel caps).
 */
@Serializable
data class HabitPreset(
    val id: String,
    val label: String = "",
    /** Caffeine: what the +1 chip logs and the wheel prefills. Null = none. */
    val defaultMg: Double? = null,
    /** Nicotine: count the +1 chip logs (wheel range 1..20). */
    val defaultCount: Int = 1,
    /** Nicotine: optional per-dose mg prefill (wheel range 0..30). */
    val defaultDoseMg: Double? = null,
    /** Caffeine: [MilkKind.storageKey] when [milkMl] > 0; else null. */
    val milkKind: String? = null,
    /** Caffeine: sidecar millilitres. 0 = no milk food. */
    val milkMl: Int = 0,
) {
    val isCustom: Boolean get() = id.startsWith(HabitPresetCatalog.CUSTOM_PREFIX)
}

@Serializable
data class HabitPresetCatalog(
    /** User order = display order (hub chips, sheets, settings list). */
    val presets: List<HabitPreset> = emptyList(),
    val version: Int = 1,
) {
    val customCount: Int get() = presets.count { it.isCustom }

    fun validate(domain: HabitPresetDomain): String? {
        if (presets.isEmpty()) return "empty"
        if (customCount > HabitPresetCatalog.MAX_CUSTOM) return "custom_cap"
        val ids = presets.map { it.id }
        if (ids.toSet().size != ids.size) return "duplicate_id"
        val builtin = domain.builtinIds
        for (preset in presets) {
            if (preset.id.isBlank()) return "blank_id"
            if (preset.isCustom && !CUSTOM_ID_REGEX.matches(preset.id)) return "bad_custom_id"
            if (!preset.isCustom && preset.id !in builtin) return "bad_id"
            if (preset.label.length > MAX_LABEL) return "label"
            if (preset.defaultMg != null && preset.defaultMg !in 0.0..MAX_DEFAULT_MG) return "mg"
            if (preset.defaultCount !in 1..MAX_DEFAULT_COUNT) return "count"
            if (preset.defaultDoseMg != null && preset.defaultDoseMg !in 0.0..MAX_DOSE_MG) return "dose"
            if (domain == HabitPresetDomain.NICOTINE) {
                if (preset.milkMl != 0 || preset.milkKind != null) return "milk"
            } else {
                if (preset.milkMl !in 0..MilkKind.MAX_ML) return "milk_ml"
                if (preset.milkMl > 0 && MilkKind.fromStorage(preset.milkKind) == null) return "milk_kind"
            }
        }
        return null
    }

    /** Corrupt/oversized stored JSON resets to the builtin defaults. */
    fun validatedOrDefault(domain: HabitPresetDomain): HabitPresetCatalog =
        if (validate(domain) == null) this else domain.defaultCatalog

    fun def(id: String): HabitPreset? = presets.firstOrNull { it.id == id }

    fun ids(): List<String> = presets.map { it.id }

    fun withLabel(id: String, label: String): HabitPresetCatalog =
        copy(presets = presets.map {
            if (it.id == id) it.copy(label = label.trim().take(MAX_LABEL)) else it
        })

    /** Replaces one preset's defaults (label trimmed like withLabel). */
    fun withPreset(preset: HabitPreset): HabitPresetCatalog =
        copy(presets = presets.map {
            if (it.id == preset.id) preset.copy(label = preset.label.trim().take(MAX_LABEL)).clampedMilk() else it
        })

    fun without(id: String): HabitPresetCatalog = copy(presets = presets.filterNot { it.id == id })

    /** Custom ids present in this catalog but not in [previous] (add path only). */
    fun addedCustomIds(previous: HabitPresetCatalog): List<String> {
        val before = previous.presets.mapTo(mutableSetOf()) { it.id }
        return presets.map { it.id }.filter { it.startsWith(CUSTOM_PREFIX) && it !in before }
    }

    fun reordered(ids: List<String>): HabitPresetCatalog {
        val byId = presets.associateBy { it.id }
        val next = ids.mapNotNull { byId[it] } + presets.filter { it.id !in ids.toSet() }
        return copy(presets = next)
    }

    fun addCustom(
        domain: HabitPresetDomain,
        label: String,
        defaultMg: Double? = null,
        defaultCount: Int = 1,
        defaultDoseMg: Double? = null,
        milkKind: String? = null,
        milkMl: Int = 0,
    ): HabitPresetCatalog {
        if (customCount >= MAX_CUSTOM) return this
        val preset = HabitPreset(
            id = newCustomPresetId(presets.mapTo(mutableSetOf()) { it.id }),
            label = label.trim().take(MAX_LABEL),
            defaultMg = if (domain.mgBased) defaultMg?.coerceIn(0.0, MAX_DEFAULT_MG) else null,
            defaultCount = if (domain.mgBased) 1 else defaultCount.coerceIn(1, MAX_DEFAULT_COUNT),
            defaultDoseMg = if (domain.mgBased) null else defaultDoseMg?.coerceIn(0.0, MAX_DOSE_MG),
            milkKind = if (domain.mgBased) milkKind else null,
            milkMl = if (domain.mgBased) milkMl else 0,
        ).clampedMilk()
        return copy(presets = presets + preset)
    }

    private fun HabitPreset.clampedMilk(): HabitPreset {
        val ml = milkMl.coerceIn(0, MilkKind.MAX_ML)
        val kind = if (ml > 0) MilkKind.fromStorage(milkKind)?.storageKey else null
        return copy(milkMl = if (kind == null) 0 else ml, milkKind = kind)
    }

    companion object {
        /** Cap on custom presets per tracker (builtins are not capped). */
        const val MAX_CUSTOM = 8
        /** Same label cap as meal labels (MealCatalog.MAX_LABEL). */
        const val MAX_LABEL = 24
        /** Caffeine wheel max (mg) — also the cap on stored preset defaults. */
        const val MAX_DEFAULT_MG = 500.0
        /** Nicotine count wheel max. */
        const val MAX_DEFAULT_COUNT = 20
        /** Nicotine per-dose wheel max (mg). */
        const val MAX_DOSE_MG = 30.0
        const val CUSTOM_PREFIX = "t_"
        val CUSTOM_ID_REGEX = Regex("^t_[0-9a-f]{4,16}$")

        fun newCustomPresetId(existing: Set<String> = emptySet()): String {
            repeat(16) {
                val hex = Random.nextBytes(4).joinToString("") { b ->
                    val v = b.toInt() and 0xff
                    "%02x".format(v)
                }
                val id = CUSTOM_PREFIX + hex
                if (id !in existing) return id
            }
            return CUSTOM_PREFIX + System.nanoTime().toString(16).takeLast(8)
        }
    }
}

/**
 * Per-tracker preset constraints and defaults. The builtin id set mirrors the
 * legacy kind enums (single source: storageKeys), so a default catalog equals
 * today's behavior until the user edits anything.
 */
enum class HabitPresetDomain(val mgBased: Boolean) {
    CAFFEINE(true),
    NICOTINE(false);

    val builtinIds: Set<String>
        get() = when (this) {
            CAFFEINE -> CaffeineKind.entries.mapTo(LinkedHashSet()) { it.storageKey }
            NICOTINE -> NicotineKind.entries.mapTo(LinkedHashSet()) { it.storageKey }
        }

    /** Builtins in enum order with blank labels (= locale defaults). */
    val defaultCatalog: HabitPresetCatalog
        get() = when (this) {
            CAFFEINE -> HabitPresetCatalog(CaffeineKind.entries.map { HabitPreset(it.storageKey, defaultMg = it.defaultMg) })
            NICOTINE -> HabitPresetCatalog(NicotineKind.entries.map { HabitPreset(it.storageKey) })
        }

    /** Default hub chips (the legacy enum DefaultQuickKinds as ids). */
    val defaultQuickKindIds: List<String>
        get() = when (this) {
            CAFFEINE -> CaffeineKind.DefaultQuickKinds.map { it.storageKey }
            NICOTINE -> NicotineKind.DefaultQuickKinds.map { it.storageKey }
        }

    /** Label shown for an entry whose kind id is not in the catalog. */
    val otherLabelRes: Int
        get() = when (this) {
            CAFFEINE -> R.string.caffeine_kind_other
            NICOTINE -> R.string.nicotine_kind_other
        }

    /**
     * Hub chip selection: builtin ids plus `t_` custom ids are retained
     * (unknown garbage drops as before); blank/empty falls back to the
     * defaults. A custom id whose preset was deleted simply stops rendering.
     */
    fun quickKindIdsFromStorage(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return defaultQuickKindIds
        val parsed = raw.split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .filter { it in builtinIds || it.startsWith(HabitPresetCatalog.CUSTOM_PREFIX) }
        return parsed.distinct().ifEmpty { defaultQuickKindIds }
    }

    fun quickKindIdsToStorage(ids: List<String>): String =
        ids.map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString(",")

    /**
     * Hub +1 chips: catalog presets in catalog (user) order, restricted to
     * the quick-kind selection; an empty selection falls back to the
     * defaults, ids without a preset (deleted custom) drop out.
     */
    fun hubPresets(quickKindIds: List<String>, catalog: HabitPresetCatalog): List<HabitPreset> {
        val selected = quickKindIds.ifEmpty { defaultQuickKindIds }.toHashSet()
        return catalog.presets.filter { it.id in selected }
    }
}

private val BUILTIN_KIND_IDS: Set<String> =
    (CaffeineKind.entries.map { it.storageKey } + NicotineKind.entries.map { it.storageKey }).toSet()

/**
 * Legacy month buckets encoded the kind enum's constant name ("TEA"); the
 * sync wire and new buckets encode storage keys ("tea"); custom presets use
 * opaque `t_` ids. Normalizes builtin spellings case-insensitively to their
 * storage key and passes everything else through verbatim.
 */
fun normalizeKindId(raw: String): String {
    val lower = raw.lowercase(Locale.ROOT)
    return if (lower in BUILTIN_KIND_IDS) lower else raw
}

/**
 * Field serializer for entry `kind` ids: writes the id verbatim, reads with
 * legacy enum-name normalization so enum-era bucket files keep decoding.
 */
object TrackerKindIdSerializer : KSerializer<String> {
    override val descriptor = PrimitiveSerialDescriptor("TrackerKindId", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: String) = encoder.encodeString(value)
    override fun deserialize(decoder: Decoder): String = normalizeKindId(decoder.decodeString())
}

/** Label resource for a builtin caffeine kind id, else the "Other" fallback. */
fun caffeineKindLabelRes(id: String): Int =
    CaffeineKind.entries.firstOrNull { it.storageKey == id }?.labelRes ?: R.string.caffeine_kind_other

/** Label resource for a builtin nicotine kind id, else the "Other" fallback. */
fun nicotineKindLabelRes(id: String): Int =
    NicotineKind.entries.firstOrNull { it.storageKey == id }?.labelRes ?: R.string.nicotine_kind_other

/** Builtin default mg for a caffeine kind id (enum-era quick-chip defaults). */
fun builtinCaffeineDefaultMg(kind: String): Double? =
    CaffeineKind.entries.firstOrNull { it.storageKey == kind }?.defaultMg

/** Decodes a stored catalog JSON string; corrupt/blank resets to defaults. */
fun parseHabitPresetCatalog(raw: String?, domain: HabitPresetDomain, json: Json): HabitPresetCatalog {
    if (!raw.isNullOrBlank()) {
        val parsed = runCatching { json.decodeFromString(HabitPresetCatalog.serializer(), raw) }.getOrNull()
        if (parsed != null) return parsed.validatedOrDefault(domain)
    }
    return domain.defaultCatalog
}

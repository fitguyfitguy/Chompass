package app.chompass.models

import java.time.LocalDate
import kotlinx.serialization.Serializable

/**
 * Macro day-type plan (Codeberg #60, docs/local/MACRO_PROFILES_DESIGN.md).
 *
 * Named profiles ("Training day" / "Rest day") with explicit absolute targets,
 * mapped onto calendar days by manual toggle, weekday map, or repeating cycle.
 * Stored as a nullable field on [UserProfile] so it rides the existing
 * sync-1.2 profile payload (decoders use ignoreUnknownKeys; old APKs skip it).
 *
 * Resolution lives in [MacroPlanResolver] (MACRO-CYCLE-A); this file is pure
 * data + fingerprints.
 */
@Serializable
enum class MacroPlanMode { MANUAL, WEEKDAYS, CYCLE }

/** One named day type with explicit absolute targets. */
@Serializable
data class MacroDayProfile(
    val id: String,
    val name: String,
    val calories: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
) {
    fun signature(): String = listOf(id, name, calories, proteinG, carbsG, fatG).joinToString(",")
}

@Serializable
data class MacroPlan(
    val enabled: Boolean = false,
    /** 2..7 profiles while enabled (editor enforces). */
    val profiles: List<MacroDayProfile> = emptyList(),
    val mode: MacroPlanMode = MacroPlanMode.MANUAL,
    /** MANUAL: the profile for any day without an override. */
    val defaultProfileId: String? = null,
    /** WEEKDAYS: DayOfWeek.name ("MONDAY".."SUNDAY") -> profile id; missing = default. */
    val weekdayProfileIds: Map<String, String> = emptyMap(),
    /** CYCLE: ordered profile ids walked from the anchor day (2..7 entries). */
    val cyclePattern: List<String> = emptyList(),
    /** CYCLE anchor: ISO local date "2026-09-01" — day granularity, string-sync-safe, JS-mirrorable. */
    val cycleAnchorDay: String? = null,
    /** ISO date -> profile id: manual per-day override for ANY mode (wins over the pattern). */
    val dayAssignments: Map<String, String> = emptyMap(),
) {
    fun profileById(id: String?): MacroDayProfile? =
        id?.let { wanted -> profiles.firstOrNull { it.id == wanted } }

    /**
     * Drops assignments outside today ± [keepDays]. Called on every plan write so
     * the map stays bounded (assignments pruned like the journal).
     */
    fun prunedAssignments(today: LocalDate, keepDays: Long = ASSIGNMENT_KEEP_DAYS): Map<String, String> {
        val from = today.minusDays(keepDays).toString()
        val to = today.plusDays(keepDays).toString()
        return dayAssignments.filterKeys { it in from..to }
    }

    /** Stable fingerprint feeding [UserProfile.goalInputSignature] (staleness nudge). */
    fun signature(): String = listOf(
        enabled,
        profiles.joinToString(";") { it.signature() },
        mode,
        defaultProfileId,
        weekdayProfileIds.entries.sortedWith(compareBy({ it.key }, { it.value }))
            .joinToString(",") { "${it.key}=${it.value}" },
        cyclePattern.joinToString(","),
        cycleAnchorDay,
        dayAssignments.entries.sortedWith(compareBy({ it.key }, { it.value }))
            .joinToString(",") { "${it.key}=${it.value}" },
    ).joinToString("|")

    companion object {
        const val ASSIGNMENT_KEEP_DAYS = 366L
    }
}

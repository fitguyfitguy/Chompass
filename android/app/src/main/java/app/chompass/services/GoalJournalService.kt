package app.chompass.services

import app.chompass.data.PreferencesStore
import app.chompass.data.ProfileRepository
import app.chompass.models.GoalJournal
import app.chompass.models.GoalJournalEntry
import app.chompass.models.GoalJournalSource
import app.chompass.models.MacroPlanResolver
import app.chompass.models.UserProfile
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Per-day goal journal write triggers (Codeberg #60, docs/local/
 * MACRO_PROFILES_DESIGN.md § Per-day goal journal). All three triggers land in
 * one implementation, [refresh]:
 *
 *  1. plan enable / edit / day-type switch — the profile flow re-emits
 *     ([observe], launched app-scope in ChompassApp);
 *  2. day rollover — the silent midnight widget alarm calls [refresh]
 *     (ReminderReceiver CHANNEL_WIDGET_MIDNIGHT), so a CYCLE/WEEKDAYS switch
 *     lands at midnight even without a food edit;
 *  3. app start — the profile flow's first emission re-runs [refresh], which
 *     gap-fills days missed while the app was closed ([GoalJournal.gapFill]).
 *
 * Journaling runs only while the macro day plan is enabled; a journaled day
 * from a since-disabled plan stays frozen (that is the point). Reads stay pure
 * in [GoalJournal] / [MacroPlanResolver] — this service owns persistence only.
 */
class GoalJournalService(
    private val prefs: PreferencesStore,
    private val profileRepository: ProfileRepository,
) {
    private val mutex = Mutex()

    /**
     * Profile-driven trigger (plan edits + app start). Gap-fills and re-records
     * today on every emission; a no-op while the plan is off or nothing
     * changed. Launched app-scope in ChompassApp.
     */
    fun observe(): Flow<UserProfile?> = profileRepository.profile
        .onEach { refresh() }

    /**
     * Record today + gap-fill with explicit provenance. PLAN for schedule /
     * rollover / app-start writes, MANUAL_SWITCH for the quick day-type switch
     * (Home chip sheet) so history can tell a manual toggle from the schedule.
     */
    suspend fun refresh(source: GoalJournalSource = GoalJournalSource.PLAN) {
        mutex.withLock {
            val updated = updatedJournal(
                profile = profileRepository.current(),
                current = prefs.goalJournal.first(),
                today = LocalDate.now(),
                nowMillis = System.currentTimeMillis(),
                source = source,
            )
            if (updated != null) prefs.setGoalJournal(updated)
        }
    }

    suspend fun recordManualSwitch() = refresh(GoalJournalSource.MANUAL_SWITCH)

    internal companion object {
        /**
         * Pure core: the new journal after gap-fill + recording [today], or
         * null when nothing changes (plan off, or today's entry already holds
         * the resolved values) so unrelated profile writes never bump
         * `updatedAtMillis` and provenance survives.
         */
        fun updatedJournal(
            profile: UserProfile?,
            current: List<GoalJournalEntry>,
            today: LocalDate,
            nowMillis: Long,
            source: GoalJournalSource,
        ): List<GoalJournalEntry>? {
            if (profile?.macroPlan?.enabled != true) return null
            val gapped = GoalJournal.gapFill(current, today, nowMillis) { day ->
                MacroPlanResolver.targetsFor(profile, day)
            }
            val resolved = MacroPlanResolver.targetsFor(profile, today)
            val todayEntry = GoalJournalEntry(
                date = today.toString(),
                calories = resolved.targets.calories,
                proteinG = resolved.targets.proteinG,
                carbsG = resolved.targets.carbsG,
                fatG = resolved.targets.fatG,
                profileId = resolved.profileId,
                profileName = resolved.profileName,
                updatedAtMillis = nowMillis,
                source = source,
            )
            val existing = gapped.firstOrNull { it.date == todayEntry.date }
            if (existing != null && existing.hasSameValues(todayEntry)) {
                return gapped.takeIf { it != current }
            }
            return GoalJournal.upsertRespectingFreeze(gapped, todayEntry, today)
                .takeIf { it != current }
        }

        /** Value comparison ignoring `updatedAtMillis` and [GoalJournalEntry.source]. */
        private fun GoalJournalEntry.hasSameValues(other: GoalJournalEntry): Boolean =
            date == other.date && calories == other.calories && proteinG == other.proteinG &&
                carbsG == other.carbsG && fatG == other.fatG &&
                profileId == other.profileId && profileName == other.profileName
    }
}

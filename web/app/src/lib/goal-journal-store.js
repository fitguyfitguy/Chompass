// @ts-check
/**
 * Goal-journal persistence + write triggers (Codeberg #60, PWA mirror of
 * Android `GoalJournalService`). One `refresh` core behind all three triggers:
 *
 *  1. plan enable / edit / day-type switch — callers save the profile, then
 *     call refresh (the Settings editor and the Home switch sheet);
 *  2. day rollover — the diary's minute tick calls refresh, so a CYCLE /
 *     WEEKDAYS switch lands at midnight while the app is open (a PWA has no
 *     background alarms);
 *  3. app start — the first render's refresh gap-fills days missed while the
 *     app was closed (source GAP_FILL via the core).
 *
 * Journaling runs only while the macro day plan is enabled; a journaled day
 * from a since-disabled plan stays frozen (that is the point).
 */
import { profile as profileStore, goalJournal } from "./db.js";
import { dailyTargets } from "./chompass-core/formulas.js";
import { updatedJournalEntries } from "./chompass-core/macro-plan-edit.js";
import { goalJournalIdFor } from "./chompass-core/macro-plan.js";

/** Local calendar day yyyy-MM-dd. */
function localCalendarDay(d = new Date()) {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${y}-${m}-${day}`;
}

/**
 * Record today + gap-fill with explicit provenance. PLAN for schedule /
 * rollover / app-start writes, MANUAL_SWITCH for the quick day-type switch
 * (Home chip sheet) so history can tell a manual toggle from the schedule.
 * No-op when the plan is off or nothing changed.
 * @param {"PLAN"|"MANUAL_SWITCH"|"OVERRIDE"|"GAP_FILL"} [source]
 */
export async function refreshGoalJournal(source = "PLAN") {
  const prof = await profileStore.load();
  if (!prof) return;
  const current = await goalJournal.all();
  const today = localCalendarDay();
  const base = dailyTargets(prof);
  const updated = updatedJournalEntries(prof, current, base, today, Date.now(), source);
  if (!updated) return;
  await goalJournal.clear();
  for (const entry of updated) await goalJournal.put({ ...entry, id: goalJournalIdFor(entry.date) });
}

/** Home quick-switch trigger (manual day-type toggle writes today's entry). */
export async function recordManualSwitchGoalJournal() {
  await refreshGoalJournal("MANUAL_SWITCH");
}

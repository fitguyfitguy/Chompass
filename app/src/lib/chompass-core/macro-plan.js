// @ts-check

/**
 * Macro day-plan resolution + per-day goal journal (Codeberg #60).
 *
 * Pure mirror of the Android `app.chompass.models.MacroPlanResolver` /
 * `GoalJournal` helpers. Semantics:
 *  - MACRO-CYCLE-A `resolveDay`: day assignment -> mode (MANUAL / WEEKDAYS /
 *    CYCLE) -> default -> base targets. Cycle math is epoch-day integers +
 *    floorMod only, so both platforms agree exactly.
 *  - MACRO-CYCLE-B `averageForward`: unweighted mean over a forward window
 *    (pattern length for CYCLE, else 7 days) — planning number for Adaptive
 *    baseline / Coach "this week" / forecast.
 *  - MACRO-CYCLE-D `journalAverage`: mean over journaled days in a past range;
 *    gaps are skipped, never filled.
 *
 * Golden vectors: `testdata/parity/macro-plan-expected.json` (both platforms
 * assert the same table). Design: docs/local/MACRO_PROFILES_DESIGN.md.
 */

const MS_PER_DAY = 86_400_000;
const WEEKDAY_NAMES = ["SUNDAY", "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY"];

/** Journal retention (days back from today); mirrors GoalJournal.KEEP_DAYS. */
export const JOURNAL_KEEP_DAYS = 400;

/**
 * Stable per-day sync record id for a journal entry (daily_notes precedent):
 * epoch day in the low 48 bits as 12 lowercase hex digits, zeros elsewhere.
 * Mirrors Android GoalJournal.idFor — same date → same id on every platform,
 * so sync's merge-by-id is per-day last-write-wins.
 * @param {string} isoDate
 * @returns {string} "00000000-0000-0000-0000-<12 hex>"
 */
export function goalJournalIdFor(isoDate) {
  const days = isoToEpochDay(isoDate);
  const safe = Number.isFinite(days) ? Math.max(0, Math.min(days, 0x0000ffffffffffff)) : 0;
  return `00000000-0000-0000-0000-${safe.toString(16).padStart(12, "0")}`;
}

/**
 * @param {string} iso "2026-09-01"
 * @returns {number} epoch day; NaN for malformed input
 */
export function isoToEpochDay(iso) {
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(iso ?? "");
  if (!m) return Number.NaN;
  const [y, mo, d] = [Number(m[1]), Number(m[2]), Number(m[3])];
  const utc = Date.UTC(y, mo - 1, d);
  // Reject rollover dates like "2026-02-31" (UTC parse normalizes them).
  if (new Date(utc).getUTCMonth() !== mo - 1) return Number.NaN;
  return Math.round(utc / MS_PER_DAY);
}

/** @param {number} epochDay */
export function epochDayToIso(epochDay) {
  return new Date(epochDay * MS_PER_DAY).toISOString().slice(0, 10);
}

/** @param {string} iso @returns {"SUNDAY"|"MONDAY"|"TUESDAY"|"WEDNESDAY"|"THURSDAY"|"FRIDAY"|"SATURDAY"|undefined} */
export function isoWeekday(iso) {
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(iso ?? "");
  if (!m) return undefined;
  const [y, mo, d] = [Number(m[1]), Number(m[2]), Number(m[3])];
  const name = WEEKDAY_NAMES[new Date(Date.UTC(y, mo - 1, d)).getUTCDay()];
  return /** @type {"SUNDAY"|"MONDAY"|"TUESDAY"|"WEDNESDAY"|"THURSDAY"|"FRIDAY"|"SATURDAY"|undefined} */ (name);
}

const floorMod = (n, m) => ((n % m) + m) % m;

/**
 * @typedef {Object} DayTargets
 * @property {number} calories
 * @property {number} proteinG
 * @property {number} carbsG
 * @property {number} fatG
 */

/**
 * @typedef {Object} MacroDayProfile
 * @property {string} id
 * @property {string} name
 * @property {number} calories
 * @property {number} proteinG
 * @property {number} carbsG
 * @property {number} fatG
 */

/**
 * @typedef {Object} MacroPlan
 * @property {boolean} enabled
 * @property {MacroDayProfile[]} [profiles]
 * @property {"MANUAL"|"WEEKDAYS"|"CYCLE"} [mode]
 * @property {string|null} [defaultProfileId]
 * @property {Record<string,string>} [weekdayProfileIds] DayOfWeek name -> profile id
 * @property {string[]} [cyclePattern]
 * @property {string|null} [cycleAnchorDay] ISO date
 * @property {Record<string,string>} [dayAssignments] ISO date -> profile id
 */

/**
 * @typedef {Object} GoalJournalEntry
 * @property {string} date ISO date — the key
 * @property {number} calories
 * @property {number} proteinG
 * @property {number} carbsG
 * @property {number} fatG
 * @property {string|null} [profileId]
 * @property {string|null} [profileName]
 * @property {number} [updatedAtMillis]
 * @property {"PLAN"|"MANUAL_SWITCH"|"OVERRIDE"|"GAP_FILL"} [source]
 */

/**
 * MACRO-CYCLE-A: resolve one day's targets.
 * @param {MacroPlan|null} plan
 * @param {DayTargets} base
 * @param {string} isoDate
 */
export function resolveDay(plan, base, isoDate) {
  if (!plan || !plan.enabled || !plan.profiles || plan.profiles.length === 0) {
    return { targets: { ...base }, profileId: null, profileName: null };
  }
  const byId = (id) => (id ? plan.profiles.find((p) => p.id === id) : undefined);
  const modeId = plan.mode === "WEEKDAYS"
    ? plan.weekdayProfileIds?.[isoWeekday(isoDate)] ?? plan.defaultProfileId ?? null
    : plan.mode === "CYCLE"
      ? cycleProfileId(plan, isoDate)
      : plan.defaultProfileId ?? null;
  const profile =
    byId(plan.dayAssignments?.[isoDate]) ?? byId(modeId) ?? byId(plan.defaultProfileId ?? null);
  if (!profile) return { targets: { ...base }, profileId: null, profileName: null };
  return {
    targets: {
      calories: profile.calories,
      proteinG: profile.proteinG,
      carbsG: profile.carbsG,
      fatG: profile.fatG,
    },
    profileId: profile.id,
    profileName: profile.name,
  };
}

/** @param {MacroPlan} plan @param {string} isoDate */
function cycleProfileId(plan, isoDate) {
  if (!plan.cyclePattern || plan.cyclePattern.length === 0) return plan.defaultProfileId ?? null;
  if (!plan.cycleAnchorDay) return plan.defaultProfileId ?? null;
  const offset = isoToEpochDay(isoDate) - isoToEpochDay(plan.cycleAnchorDay);
  if (!Number.isFinite(offset)) return plan.defaultProfileId ?? null;
  return plan.cyclePattern[floorMod(offset, plan.cyclePattern.length)];
}

/**
 * Journal-first read rule (#60 phase 3): past + today consult the goal journal
 * before the resolver — frozen actuals win even when the plan has since been
 * edited or disabled; gaps and future days resolve live from the plan.
 * Mirrors Android MacroPlanResolver.targetsForJournaled.
 * @param {GoalJournalEntry[]} journal
 * @param {MacroPlan|null} plan
 * @param {DayTargets} base
 * @param {string} isoDate
 * @param {string} isoToday
 */
export function resolveDayJournaled(journal, plan, base, isoDate, isoToday) {
  const today = isoToEpochDay(isoToday);
  const d = isoToEpochDay(isoDate);
  if (Number.isFinite(today) && Number.isFinite(d) && d <= today) {
    const entry = journal.find((e) => e.date === isoDate);
    if (entry) {
      return {
        targets: { calories: entry.calories, proteinG: entry.proteinG, carbsG: entry.carbsG, fatG: entry.fatG },
        profileId: entry.profileId ?? null,
        profileName: entry.profileName ?? null,
      };
    }
  }
  return resolveDay(plan, base, isoDate);
}

/**
 * MACRO-CYCLE-B: forward-window average starting today (planned overrides count).
 * @param {MacroPlan|null} plan
 * @param {DayTargets} base
 * @param {string} isoToday
 * @param {number|null} [windowDays] override; default = CYCLE pattern length, else 7
 * @returns {DayTargets}
 */
export function averageForward(plan, base, isoToday, windowDays = null) {
  if (!plan || !plan.enabled || !plan.profiles || plan.profiles.length === 0) return { ...base };
  const window = windowDays ??
    (plan.mode === "CYCLE" && plan.cyclePattern?.length ? plan.cyclePattern.length : 7);
  const start = isoToEpochDay(isoToday);
  const days = [];
  for (let i = 0; i < window; i++) {
    days.push(resolveDay(plan, base, epochDayToIso(start + i)).targets);
  }
  return mean(days);
}

/**
 * MACRO-CYCLE-D: mean over journaled days in [from, to]; gaps skipped, never filled.
 * @param {GoalJournalEntry[]} entries
 * @param {string} isoFrom
 * @param {string} isoTo
 * @returns {DayTargets|null}
 */
export function journalAverage(entries, isoFrom, isoTo) {
  const from = isoToEpochDay(isoFrom);
  const to = isoToEpochDay(isoTo);
  const inRange = entries
    .filter((e) => {
      const d = isoToEpochDay(e.date);
      return Number.isFinite(d) && d >= from && d <= to;
    })
    .map((e) => ({ calories: e.calories, proteinG: e.proteinG, carbsG: e.carbsG, fatG: e.fatG }));
  if (inRange.length === 0) return null;
  return mean(inRange);
}

/** @param {DayTargets[]} days */
function mean(days) {
  return {
    calories: Math.round(days.reduce((s, d) => s + d.calories, 0) / days.length),
    proteinG: Math.round(days.reduce((s, d) => s + d.proteinG, 0) / days.length),
    carbsG: Math.round(days.reduce((s, d) => s + d.carbsG, 0) / days.length),
    fatG: Math.round(days.reduce((s, d) => s + d.fatG, 0) / days.length),
  };
}

// -- Goal journal helpers (mirror of Android GoalJournal) -----------------------

/**
 * Freeze rule: past entries are immutable; today is live. Always prunes.
 * @param {GoalJournalEntry[]} entries
 * @param {GoalJournalEntry} entry
 * @param {string} isoToday
 */
export function upsertJournalEntry(entries, entry, isoToday) {
  const today = isoToEpochDay(isoToday);
  const date = isoToEpochDay(entry.date);
  if (entries.some((e) => e.date === entry.date) && Number.isFinite(date) && date < today) {
    return entries;
  }
  return pruneJournal(entries.filter((e) => e.date !== entry.date).concat([entry]), isoToday);
}

/** Per-day last-write-wins merge (sync import); deterministic in argument order. */
export function mergeJournal(local, remote) {
  const byDate = new Map();
  for (const e of [...local, ...remote]) {
    const cur = byDate.get(e.date);
    if (!cur || compareEntries(e, cur) > 0) byDate.set(e.date, e);
  }
  return sortEntries([...byDate.values()]);
}

/** @param {GoalJournalEntry} a @param {GoalJournalEntry} b */
function compareEntries(a, b) {
  const au = a.updatedAtMillis ?? 0;
  const bu = b.updatedAtMillis ?? 0;
  if (au !== bu) return au - bu;
  const as = entrySignature(a);
  const bs = entrySignature(b);
  return as < bs ? -1 : as > bs ? 1 : 0;
}

/** @param {GoalJournalEntry} e */
function entrySignature(e) {
  return [
    e.date, e.calories, e.proteinG, e.carbsG, e.fatG,
    e.profileId ?? "", e.profileName ?? "", e.source ?? "",
  ].join(",");
}

/** @param {GoalJournalEntry[]} entries @param {string} isoToday @param {number} [keepDays] */
export function pruneJournal(entries, isoToday, keepDays = JOURNAL_KEEP_DAYS) {
  const cutoff = isoToEpochDay(isoToday) - (keepDays - 1);
  return sortEntries(
    entries.filter((e) => {
      const d = isoToEpochDay(e.date);
      return Number.isFinite(d) && d >= cutoff;
    }),
  );
}

/** @param {GoalJournalEntry[]} entries */
function sortEntries(entries) {
  return entries.slice().sort((a, b) => (a.date < b.date ? -1 : a.date > b.date ? 1 : 0));
}

/**
 * Fills missing days between the first journaled day and yesterday from the
 * current plan (best effort, source GAP_FILL). No entries -> nothing to fill.
 * @param {GoalJournalEntry[]} entries
 * @param {string} isoToday
 * @param {number} nowMillis
 * @param {(isoDate: string) => {targets: DayTargets, profileId: string|null, profileName: string|null}} resolve
 */
export function gapFillJournal(entries, isoToday, nowMillis, resolve) {
  const known = new Set(entries.map((e) => isoToEpochDay(e.date)).filter((d) => Number.isFinite(d)));
  if (known.size === 0) return entries;
  const first = Math.min(...known);
  const today = isoToEpochDay(isoToday);
  const start = Math.max(first, today - (JOURNAL_KEEP_DAYS - 1));
  const filled = [];
  for (let d = start; d < today; d++) {
    if (!known.has(d)) {
      const iso = epochDayToIso(d);
      const r = resolve(iso);
      /** @type {GoalJournalEntry} */
      const filledEntry = {
        date: iso,
        calories: r.targets.calories,
        proteinG: r.targets.proteinG,
        carbsG: r.targets.carbsG,
        fatG: r.targets.fatG,
        profileId: r.profileId,
        profileName: r.profileName,
        updatedAtMillis: nowMillis,
        source: "GAP_FILL",
      };
      filled.push(filledEntry);
    }
  }
  return pruneJournal(entries.concat(filled), isoToday);
}

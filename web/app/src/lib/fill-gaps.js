// @ts-check
/**
 * Fill-missing scan over favorites + the current month's diary, plus the
 * Home banner gate (once per day + sticky per-scan dismissal). Web twin of
 * android .../services/FillGapsEngine.kt computeScan / bannerState — pure
 * logic, no DOM: the banner element lives in components/fill-gaps-view.js.
 */

import { todayIso } from "./date.js";
import { gapReport } from "./chompass-core/entry-gaps.js";

/**
 * Product gate for AI fill-missing (Home banner, review sheet, entry-form
 * pill, diary ⋮ item). WIP — disabled for release: the scan/merge logic stays
 * compiled and tested, this flag only hides the entry points. Flip to true
 * when the fill-missing device pass is green (mirrors Android
 * FillGapsFeature.ENABLED).
 */
export const FILL_GAPS_ENABLED = false;

const DISMISSED_KEY = "chompass-fill-gaps-banner-dismissed";
const LAST_SHOWN_KEY = "chompass-fill-gaps-banner-last-shown";
const DISABLED_KEY = "chompass-fill-gaps-banner-disabled";

/**
 * Session latch: a banner shown this session stays visible across the
 * minute-tick re-renders — the once-per-day stamp must not collapse it the
 * moment it is written (Android HomeViewModel startup-gate + session-latch
 * parity). Reset by a page reload = new session.
 */
let bannerShownThisSession = false;

/**
 * Pure scan shaping (FillGapsEngine.computeScan parity): favorites first
 * (name-sorted), then current-month diary rows (newest first), gated on
 * hasMacroGaps — the mass surfaces never list micro-only gaps.
 * @param {Object} args
 * @param {import('./chompass-core/models.js').FoodEntry[]} args.favorites
 * @param {import('./chompass-core/models.js').FoodEntry[]} args.monthEntries
 * @returns {{ items: {favorite: boolean, entry: import('./chompass-core/models.js').FoodEntry}[], count: number, signature: string }}
 */
export function scanGaps({ favorites, monthEntries }) {
  const items = [
    ...favorites
      .filter((e) => gapReport(e).hasMacroGaps)
      .slice()
      .sort((a, b) => a.name.toLowerCase().localeCompare(b.name.toLowerCase()))
      .map((entry) => ({ favorite: /** @type {const} */ (true), entry })),
    ...monthEntries
      .filter((e) => gapReport(e).hasMacroGaps)
      .slice()
      .sort((a, b) => `${b.date}T${b.time || "00:00"}`.localeCompare(`${a.date}T${a.time || "00:00"}`))
      .map((entry) => ({ favorite: /** @type {const} */ (false), entry })),
  ];
  return { items, count: items.length, signature: scanSignature(items) };
}

/**
 * Stable (kind, id) fingerprint of the item set (FillGapsEngine.kt:49 shape)
 * — changes when the set changes, so a dismissal only sticks for the scan it
 * dismissed.
 * @param {{favorite: boolean, entry: import('./chompass-core/models.js').FoodEntry}[]} items
 */
export function scanSignature(items) {
  return items.map((it) => `${it.favorite ? "f" : "d"}:${it.entry.id}`).join("|");
}

/**
 * Banner truth table: non-empty scan, never dismissed for THIS scan, and the
 * once-per-day gate only before the first show of the session.
 * @param {{count: number, signature: string}} scan
 */
export function bannerVisible(scan) {
  if (scan.count === 0) return false;
  try {
    if (localStorage.getItem(DISABLED_KEY) === "1") return false;
    if (localStorage.getItem(DISMISSED_KEY) === scan.signature) return false;
    if (!bannerShownThisSession && localStorage.getItem(LAST_SHOWN_KEY) === todayIso()) return false;
  } catch {
    // localStorage unavailable (privacy mode): show the banner for the session.
  }
  return true;
}

/** Stamp the once-per-day key and open the session latch. */
export function markBannerShown() {
  bannerShownThisSession = true;
  try {
    const today = todayIso();
    if (localStorage.getItem(LAST_SHOWN_KEY) !== today) {
      localStorage.setItem(LAST_SHOWN_KEY, today);
    }
  } catch {
    // Ignore: latch still holds for the session.
  }
}

/**
 * Sticky dismissal for one scan result: a changed scan re-shows the banner
 * in-session (per-signature rule, Android dismissBanner parity).
 * @param {{signature: string}} scan
 */
export function dismissBanner(scan) {
  try {
    localStorage.setItem(DISMISSED_KEY, scan.signature);
  } catch {
    // Ignore: banner is removed for the session regardless.
  }
}

/**
 * Forever opt-out (Android fillMissingBannerDisabled parity): hides the
 * banner across sessions; per-scan dismissals keep their own key.
 */
export function disableBannerForever() {
  try {
    localStorage.setItem(DISABLED_KEY, "1");
  } catch {
    // Ignore: banner is removed for the session regardless.
  }
}

/** Re-enable reminders (Settings toggle): clears the forever opt-out. */
export function enableBannerReminders() {
  try {
    localStorage.removeItem(DISABLED_KEY);
  } catch {
    // Ignore.
  }
}

// @ts-check
import { foodEntries, profile as profileStore, water, dailyNotes, nicotine, caffeine, prefs, goalJournal } from "../lib/db.js";
import { dailyTargets, estimatedDailyActiveCalories } from "../lib/chompass-core/formulas.js";
import { dailyNoteIdFor } from "../lib/chompass-core/models.js";
import { displayUnit, entryServingEcho, formatQuantity } from "../lib/chompass-core/serving-units.js";
import { computeFastingState, nextFastStartMillis, FastingPhase } from "../lib/chompass-core/fasting-state.js";
import { microOrNull } from "../lib/chompass-core/constituents.js";
import { openSheet } from "../lib/ui/sheet.js";
import { openConfirm, openInfo, openInput } from "../lib/ui/dialog.js";
import {
  historyTemplates,
  frequentFoodGroups,
  listFavorites,
  quickRelogRows,
  filterHistoryTemplates,
  sortHistoryTemplates,
  toggleFavorite,
  isFavorite,
  duplicatedForLogging,
  toPrefill,
} from "../lib/saved-meals.js";
import { weekDates as weekDatesForPrefs, guessMealTypeFromPrefs } from "../lib/meal-schedule.js";
import { listRecipes, logRecipe } from "../lib/recipes.js";
import { mealShareText } from "../lib/meal-share.js";
import {
  normalizeHomeTopNutrients,
  normalizeFoodLogChips,
  sumNutrient,
  nutrientGoal,
  nutrientDef,
  nutritionGoalPercent,
  nutritionGoalText,
  tubeStatus,
  formatMacroChipLine,
  formatFoodPills,
  sumMealChipValues,
  NUTRITION_DETAIL_MICROS,
  mergeOptionalGoals,
} from "../lib/home-nutrients.js";
import { createSpeechCapture } from "../lib/speech.js";
import { t, formatNumber } from "../lib/i18n/index.js";
import {
  consumeResumeProgressiveCapture,
  consumeShowProgressiveMealSheet,
  discardProgressiveMeal,
  draftTotals,
  getProgressiveMeal,
  hasProgressiveMealItems,
  progressiveMealItemCount,
  progressiveMealToFoodEntries,
  removeProgressiveMealItem,
  setShowProgressiveMealSheet,
  updateProgressiveMealMeta,
} from "../lib/progressive-meal.js";
import {
  addActiveGaugeTarget,
  addManualActiveEntry,
  deleteManualActiveEntry,
  makeManualActiveEntry,
  manualActiveKcalForDate,
  loadManualActiveEntries,
  resolveWebActiveBurn,
  updateManualActiveEntry,
} from "../lib/manual-active.js";
import {
  computeDayTypeActiveStats,
  resolveActiveTypical,
  sumManualByDay,
} from "../lib/chompass-core/day-type-active.js";
import { setDayAssignment } from "../lib/chompass-core/macro-plan-edit.js";
import { resolveDay, resolveDayJournaled } from "../lib/chompass-core/macro-plan.js";
import { refreshGoalJournal, recordManualSwitchGoalJournal } from "../lib/goal-journal-store.js";
import { escapeHtml, escapeAttr } from "../lib/ui/html.js";
import { shiftDate, todayIso } from "../lib/date.js";
import { chevronLeft, chevronRight } from "../lib/icons.js";
import { showToast, showUndoToast } from "../lib/ui/toast.js";

/** Localized meal label; unknown ids fall back to the raw value. */
function mealLabel(mealType) {
  return MEAL_ORDER.includes(mealType) ? t(`meal.${mealType}`) : mealType;
}

// nutritionGoalPercent / nutritionGoalText live in lib/home-nutrients.js,
// shared with entry-form constituent rows.
/** Saved-meals sheet tab labels (catalog key names). */
const SEGMENT_LABELS = { RECENTS: "add_food.hero_recents", FREQUENT: "add_food.frequent", FAVORITES: "add_food.favorites", RECIPES: "diary.tab_recipes" };
const MEAL_ORDER = ["breakfast", "lunch", "dinner", "snack", "other"];

/** @param {Array<{mealType?: string}>} entries */
function mealOrderFor(entries) {
  const present = [...new Set(entries.map((e) => e.mealType).filter(Boolean))];
  const known = MEAL_ORDER.filter((m) => present.includes(m));
  const extra = present.filter((m) => !MEAL_ORDER.includes(m)).sort();
  return known.concat(extra);
}
const WATER_PRESETS = [250, 500, 750];
/** PWA quick chips mirror Android NicotineKind.DefaultQuickKinds. */
const NICOTINE_QUICK_KINDS = ["cigarette", "vape", "pouch"];
const CAFFEINE_QUICK_KINDS = ["coffee", "tea", "energy"];
/** Default mg per quick chip, mirroring Android CaffeineKind.defaultMg. */
const CAFFEINE_KIND_MG = { coffee: 95, tea: 28, energy: 80, other: 0 };
const HOME_DATE_KEY = "chompass-home-date";

/** Local-only fasting timer card (docs/local/PLAN_FASTING_TRACKER.md mirror).
 *  State derivation lives in chompass-core/fasting-state.js — an exact mirror
 *  of Android's refreshFastingTick + FastingViews; this function only renders. */
function fastingCard(p) {
  const now = Date.now();
  const s = computeFastingState(p, now);
  const { phase, goal, autoEffective, anchor, elapsed, eatElapsed, goalMillis, reached, autoStarted } = s;
  // Clock-time labels only in an effective auto cycle (Android: autoMode &&
  // nextFastStartMillis != null); manual mode stays relative.
  const clock = autoEffective && anchor != null;
  const timeStr = (ms) =>
    new Date(ms).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
  let status;
  if (phase === FastingPhase.FASTING) {
    status = reached
      ? t("diary.fasting_goal_reached")
      : goal > 0
        ? t("diary.fasting_goal_hint", { elapsed: fmtFastDuration(elapsed), goal })
        : fmtFastDuration(elapsed);
  } else if (phase === FastingPhase.EATING) {
    const remaining = fmtFastDuration(Math.max(0, (anchor ?? now) - now));
    status = clock
      ? t("diary.fasting_fast_starts_at", { time: timeStr(anchor), remaining })
      : t("diary.fasting_fast_starts_in", { remaining });
  } else {
    status = clock
      ? t("diary.fasting_next_fast_at", { time: timeStr(anchor) })
      : t("diary.fasting_idle");
  }
  const showBar = (phase === FastingPhase.FASTING && goal > 0) || phase === FastingPhase.EATING;
  const pct = showBar
    ? Math.min(100, ((phase === FastingPhase.FASTING ? elapsed / goalMillis : eatElapsed / Math.max(1, (anchor ?? now) - now + eatElapsed)) * 100))
    : 0;
  const showButtons = s.showStart || s.showStop;
  let hint = "";
  if (phase === FastingPhase.FASTING && goal > 0 && !reached) {
    hint = `<div class="water-row__hint">${t("diary.fasting_window_opens_in", { remaining: fmtFastDuration(Math.max(0, goalMillis - elapsed)) })}</div>`;
  } else if (phase === FastingPhase.EATING) {
    const remaining = fmtFastDuration(Math.max(0, (anchor ?? now) - now));
    hint = `<div class="water-row__hint">${
      clock
        ? t("diary.fasting_fast_starts_at", { time: timeStr(anchor), remaining })
        : t("diary.fasting_fast_starts_in", { remaining })
    }</div>`;
  }
  return `<div class="card card--glass water-row fasting-row">
      <div class="water-row__top">
        <div class="water-row__meta"><strong>${t("diary.fasting")}${autoStarted && phase === FastingPhase.FASTING ? ` <span class="fasting-auto-tag">${t("diary.fasting_auto")}</span>` : ""}</strong><br/><span class="water-row__meta-sub">${status}</span></div>
        <div class="water-presets">
          ${
            showButtons
              ? phase === FastingPhase.FASTING
                ? `<button type="button" class="chip" data-fasting-stop>${t("diary.fasting_stop")}</button>`
                : `<button type="button" class="chip" data-fasting-start>${t("diary.fasting_start")}</button>`
              : ""
          }
        </div>
      </div>
      ${
        showBar
          ? `<div class="water-bar" role="progressbar" aria-valuemin="0" aria-valuemax="${phase === FastingPhase.FASTING ? goal : s.eat}" aria-valuenow="${(phase === FastingPhase.FASTING ? elapsed / 3_600_000 : eatElapsed / 3_600_000).toFixed(1)}" aria-label="${t("diary.fasting")}">
              <span data-width="${pct.toFixed(1)}%"></span>
            </div>${hint}`
          : ""
      }
    </div>`;
}

/** "14h 20m" / "45m" / "2h" — elapsed label shared by the fasting card. */
function fmtFastDuration(millis) {
  const totalMinutes = Math.max(0, Math.floor(millis / 60_000));
  const hours = Math.floor(totalMinutes / 60);
  const minutes = totalMinutes % 60;
  if (hours > 0 && minutes > 0) return `${hours}h ${minutes}m`;
  if (hours > 0) return `${hours}h`;
  return `${minutes}m`;
}

/** Lazy-load the photo AI flow (camera + AI stack) only when used — the demo
 *  hero never opens the real camera, so the whole photo-ai-flow →
 *  camera-capture → food-analyze chain stays out of its bundle. */
function openPhotoAiFlow(date) {
  return import("../lib/ui/photo-ai-flow.js").then(({ startPhotoAiFlow }) =>
    startPhotoAiFlow({ date }),
  );
}

/** Demo hero mode (web/app/demo.html): mock plate camera instead of capture. */
const DEMO = typeof window !== "undefined" && Boolean(/** @type {any} */ (window).CHOMPASS_DEMO);

const ICONS = {
  photo: `<svg viewBox="0 0 24 24" aria-hidden="true"><path fill="currentColor" d="M12 12.5a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5zM4 5h3.2l1.4-1.8c.2-.3.5-.4.8-.4h5.2c.3 0 .6.1.8.4L16.8 5H20c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V7c0-1.1.9-2 2-2zm8 13c2.8 0 5-2.2 5-5s-2.2-5-5-5-5 2.2-5 5 2.2 5 5 5z"/></svg>`,
  note: `<svg viewBox="0 0 24 24" aria-hidden="true"><path fill="currentColor" d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04a1 1 0 0 0 0-1.41l-2.34-2.34a1 1 0 0 0-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z"/></svg>`,
  manual: `<svg viewBox="0 0 24 24" aria-hidden="true"><path fill="currentColor" d="M14 2H6c-1.1 0-2 .9-2 2v16c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V8l-6-6zm2 16H8v-2h8v2zm0-4H8v-2h8v2zm-3-5V3.5L18.5 9H13z"/></svg>`,
  barcode: `<svg viewBox="0 0 24 24" aria-hidden="true"><path fill="currentColor" d="M3 5h2v14H3V5zm3 0h1v14H6V5zm2 0h3v14H8V5zm4 0h1v14h-1V5zm2 0h3v14h-3V5zm4 0h1v14h-1V5zm2 0h2v14h-2V5z"/></svg>`,
  recents: `<svg viewBox="0 0 24 24" aria-hidden="true"><path fill="currentColor" d="M13 3a9 9 0 1 0 8.94 10h-2.02A7 7 0 1 1 13 5v5.59l3.3 3.3 1.4-1.42L15 10.17V3h-2z"/></svg>`,
  frequent: `<svg viewBox="0 0 24 24" aria-hidden="true"><path fill="currentColor" d="M7 7h10v2H7V7zm0 4h10v2H7v-2zm0 4h7v2H7v-2zM5 3h14c1.1 0 2 .9 2 2v14l-4-2H5c-1.1 0-2-.9-2-2V5c0-1.1.9-2 2-2z"/></svg>`,
  favorites: `<svg viewBox="0 0 24 24" aria-hidden="true"><path fill="currentColor" d="M17 3H7c-1.1 0-2 .9-2 2v16l7-3 7 3V5c0-1.1-.9-2-2-2z"/></svg>`,
  copy: `<svg viewBox="0 0 24 24" aria-hidden="true"><path fill="currentColor" d="M16 1H4c-1.1 0-2 .9-2 2v14h2V3h12V1zm3 4H8c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h11c1.1 0 2-.9 2-2V7c0-1.1-.9-2-2-2zm0 16H8V7h11v14z"/></svg>`,
  recipe: `<svg viewBox="0 0 24 24" aria-hidden="true"><path fill="currentColor" d="M8 4h8v2H8V4zm0 4h8v2H8V8zm0 4h5v2H8v-2zm-4 8h16v2H4v-2zM6 2v20h2V2H6zm10 0v20h2V2h-2z"/></svg>`,
  voice: `<svg viewBox="0 0 24 24" aria-hidden="true"><path fill="currentColor" d="M12 14c1.66 0 3-1.34 3-3V5c0-1.66-1.34-3-3-3S9 3.34 9 5v6c0 1.66 1.34 3 3 3zm5-3c0 2.76-2.24 5-5 5s-5-2.24-5-5H5c0 3.53 2.61 6.43 6 6.92V21h2v-3.08c3.39-.49 6-3.39 6-6.92h-2z"/></svg>`,
  active: `<svg viewBox="0 0 24 24" aria-hidden="true"><path fill="currentColor" d="M13.49 5.48c1.1 0 2-.9 2-2s-.9-2-2-2-2 .9-2 2 .9 2 2 2zm-3.6 13.9 1-4.4 2.1 2v6h2v-7.5l-2.1-2 .6-3c1.3 1.5 3.3 2.5 5.5 2.5v-2c-1.9 0-3.5-1-4.3-2.4l-1-1.6c-.4-.6-1-1-1.7-1-.3 0-.5.1-.8.1l-5.2 2.2v4.7h2v-3.4l1.8-.7-1.6 8.1-4.9-.9-.4 2 7 1.4z"/></svg>`,
  breakfast: `<svg viewBox="0 0 24 24" aria-hidden="true"><path fill="currentColor" d="M18 2H6v6h12V2zm0 8H6c-1.1 0-2 .9-2 2v8c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2v-8c0-1.1-.9-2-2-2zM8 16H6v-2h2v2zm4 0h-2v-2h2v2zm4 0h-2v-2h2v2z"/></svg>`,
  lunch: `<svg viewBox="0 0 24 24" aria-hidden="true"><path fill="currentColor" d="M11 9H9V2H7v7H5V2H3v7c0 2.12 1.66 3.84 3.75 3.97V22h2.5v-9.03C11.34 12.84 13 11.12 13 9V2h-2v7zm5-3v8h2.5v8H21V2c-2.76 0-5 2.24-5 4z"/></svg>`,
  dinner: `<svg viewBox="0 0 24 24" aria-hidden="true"><path fill="currentColor" d="M8.1 13.34 3.91 9.16a4.008 4.008 0 0 1 0-5.66l7.05 7.05-2.86 2.79zm6.78-.02c1.58.92 3.68.55 5.05-.81s1.74-3.46.81-5.05l-3.14 3.14L14.2 8.2l3.14-3.14c-1.58-.92-3.68-.55-5.05.81s-1.74 3.46-.81 5.05l-7.06 7.05 1.41 1.41 5.05-5.05L15 18.95l1.41-1.41-1.53-4.22z"/></svg>`,
  snack: `<svg viewBox="0 0 24 24" aria-hidden="true"><path fill="currentColor" d="M12 6c1.11 0 2-.9 2-2 0-.38-.1-.73-.29-1.03L12 0l-1.71 2.97c-.19.3-.29.65-.29 1.03 0 1.1.9 2 2 2zm4.6 9.99c-.84-.33-1.4-.99-1.58-1.82-.03-.15-.05-.3-.05-.46 0-.84.41-1.58 1.04-2.04C16.66 11.2 17 10.39 17 9.5c0-1.52-.98-2.81-2.34-3.28C14.21 5.91 13.14 5.75 12 5.75s-2.21.16-2.66.47C7.98 6.69 7 7.98 7 9.5c0 .89.34 1.7.99 2.17.63.46 1.04 1.2 1.04 2.04 0 .16-.02.31-.05.46-.18.83-.74 1.49-1.58 1.82C5.85 16.66 5 17.95 5 19.5V21h14v-1.5c0-1.55-.85-2.84-2.4-3.51z"/></svg>`,
};

/**
 * @param {string} selectedIso
 * @param {boolean|string} [weekStart] true/"monday", false/"sunday", or "saturday"
 */
function weekDates(selectedIso, weekStart = true) {
  return weekDatesForPrefs(selectedIso, weekStart);
}

function clampDate(iso) {
  const today = todayIso();
  return iso > today ? today : iso;
}

function loadHomeDate() {
  try {
    const saved = sessionStorage.getItem(HOME_DATE_KEY);
    if (saved && /^\d{4}-\d{2}-\d{2}$/.test(saved)) return clampDate(saved);
  } catch {
    /* private mode */
  }
  return todayIso();
}

/** @param {string} iso */
function saveHomeDate(iso) {
  try {
    sessionStorage.setItem(HOME_DATE_KEY, iso);
  } catch {
    /* ignore */
  }
}

/** Android clockTimePattern: locale-aware time, lowercased (e.g. "2:00 pm"). */
function formatEntryTime(time) {
  if (!time) return "";
  const m = /^(\d{1,2}):(\d{2})/.exec(String(time));
  if (!m) return escapeHtml(String(time));
  const d = new Date(2000, 0, 1, Number(m[1]), Number(m[2]));
  return escapeHtml(d.toLocaleTimeString(undefined, { hour: "numeric", minute: "2-digit" }).toLowerCase());
}

/** Android serving grams: "24g" or "24.5g". */
function formatGrams(g) {
  const n = Number(g);
  if (!Number.isFinite(n)) return "";
  return Number.isInteger(n) ? `${n}g` : `${n.toFixed(1)}g`;
}

/** Localized cup / tbsp / tsp labels for displayUnit (same set as entry-form). */
function culinaryUnitLabels() {
  return {
    cup: [t("unit.cup"), t("unit.cup_plural")],
    tbsp: [t("unit.tbsp"), t("unit.tbsp")],
    tsp: [t("unit.tsp"), t("unit.tsp")],
  };
}

/**
 * Diary-card serving echo (Codeberg #65): the logged choice ("2 oz", "1 bowl")
 * when it still matches a non-gram option, else null (callers keep today's
 * grams line).
 * @param {import('../lib/chompass-core/models.js').FoodEntry} entry
 */
function servingEchoText(entry) {
  const echo = entryServingEcho(entry);
  if (!echo) return null;
  return `${formatQuantity(echo.quantity)} ${displayUnit(
    echo.option,
    echo.quantity,
    t("unit.serving"),
    t("unit.serving_plural"),
    t("unit.package"),
    t("unit.package_plural"),
    culinaryUnitLabels()
  )}`;
}

/** Android BurnShadeCaption: "380 of 560 active" (live of typical), else "560 active". */
function burnCaptionText(zoneActive, burn) {
  if (!burn) return t("diary.burn_active", { amount: zoneActive });
  if (burn.live > 0 && burn.typical > 0) {
    if (burn.typicalIsDayType && burn.typicalDayTypeName) {
      return t("diary.burn_active_of_named", { live: burn.live, typical: burn.typical, name: burn.typicalDayTypeName });
    }
    return t("diary.burn_active_of", { live: burn.live, typical: burn.typical });
  }
  return t("diary.burn_active", { amount: Math.max(burn.typical, zoneActive) });
}

const GAUGE_INFO_ICON = `<svg viewBox="0 0 24 24" aria-hidden="true"><path fill="currentColor" d="M11 7h2v2h-2V7zm0 4h2v6h-2v-6zm1-9C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 18c-4.41 0-8-3.59-8-8s3.59-8 8-8 8 3.59 8 8-3.59 8-8 8z"/></svg>`;

/** Semicircle (~180°) calorie gauge — Android HomeCalorieHero shape (mobile). */
function ringSvg(eaten, target, baseGoal = null, burn = null) {
  const width = 260;
  const stroke = 16;
  const r = (width - stroke) / 2;
  const topPad = 3;
  const baseFrac = baseGoal && baseGoal > 0 && target > 0 ? Math.min(1, baseGoal / target) : 1;
  const showActive = baseFrac < 1;
  const active = baseGoal && baseGoal < target ? Math.round(target - baseGoal) : 0;
  const baseHeight = Math.ceil(r + stroke + topPad);
  const burnLineH = showActive ? 22 : 0;
  const height = baseHeight + burnLineH;
  const cx = width / 2;
  const cy = baseHeight - stroke / 2;
  const halfC = Math.PI * r;
  const pct = target > 0 ? Math.min(1, eaten / target) : 0;
  const leftLabel = t("diary.calories_left", { amount: Math.max(0, Math.round(target - eaten)) });
  const x1 = (cx - r).toFixed(1);
  const x2 = (cx + r).toFixed(1);
  const y = cy.toFixed(1);
  const arc = `M ${x1} ${y} A ${r.toFixed(1)} ${r.toFixed(1)} 0 0 1 ${x2} ${y}`;

  // Activity-earned zone [baseGoal → target]: a fixed-tint segment on the same
  // budget axis. The progress fill sweeps over it, so eaten past the boundary
  // notch = dipping into the calories you burned. No thermometer, no dot.
  let tailMarkup = "";
  if (showActive) {
    const gap = baseFrac * halfC;
    const tailLen = (1 - baseFrac) * halfC;
    const theta = (Math.PI * (180 + 180 * baseFrac)) / 180;
    const rIn = r - stroke / 2 + 2;
    const rOut = r + stroke / 2 - 2;
    const nxIn = (cx + rIn * Math.cos(theta)).toFixed(1);
    const nyIn = (cy + rIn * Math.sin(theta)).toFixed(1);
    const nxOut = (cx + rOut * Math.cos(theta)).toFixed(1);
    const nyOut = (cy + rOut * Math.sin(theta)).toFixed(1);
    const boundary =
      `<line class="calorie-ring__boundary" x1="${nxIn}" y1="${nyIn}" x2="${nxOut}" y2="${nyOut}" />`;
    tailMarkup = `
      <path class="calorie-ring__tail" d="${arc}" fill="none"
        stroke="var(--protein)" stroke-width="${stroke}" stroke-linecap="round"
        stroke-dasharray="${gap.toFixed(1)} 0"
        data-dash="${gap.toFixed(1)} ${tailLen.toFixed(1)}" />${boundary}`;
  }

  let burnMarkup = "";
  let ariaBurn = "";
  if (showActive) {
    const burnCaption = burnCaptionText(active, burn);
    burnMarkup = `
      <text x="50%" y="148" text-anchor="middle" class="calorie-ring__burn-label">🔥 ${burnCaption}</text>`;
    ariaBurn = `, ${t("diary.aria_active_burn", { caption: burnCaption })}`;
  }

  return `
    <svg class="calorie-ring calorie-ring--semi" viewBox="0 0 ${width} ${height}" role="img"
      aria-label="${t("diary.ring_aria", { eaten: Math.round(eaten), target: Math.round(target), left: leftLabel })}${ariaBurn}">
      <path d="${arc}" fill="none" stroke="var(--surface)" stroke-width="${stroke}" stroke-linecap="round" />
      ${tailMarkup}
      <path class="calorie-ring__progress" d="${arc}" fill="none"
        stroke="var(--teal)" stroke-width="${stroke}" stroke-linecap="round"
        stroke-dasharray="0 ${halfC.toFixed(1)}"
        data-dash="${(pct * halfC).toFixed(1)} ${halfC.toFixed(1)}" />
      <text x="50%" y="58" text-anchor="middle" class="calorie-ring__caption">${t("diary.calories")}</text>
      <text x="50%" y="88" text-anchor="middle" class="calorie-ring__label">${Math.round(eaten)}</text>
      <text x="50%" y="108" text-anchor="middle" class="calorie-ring__sub">${t("diary.calories_of", { amount: Math.round(target) })}</text>
      <text x="50%" y="128" text-anchor="middle" class="calorie-ring__left">🔥 ${leftLabel}</text>
      ${burnMarkup}
    </svg>`;
}

/** Horizontal calorie progress bar (desktop). */
function calorieBar(eaten, target, baseGoal = null, burn = null) {
  const pct = target > 0 ? Math.min(100, (eaten / target) * 100) : 0;
  const leftLabel = t("diary.calories_left", { amount: Math.max(0, Math.round(target - eaten)) });
  const baseFrac = baseGoal && baseGoal > 0 && target > 0 ? Math.min(1, baseGoal / target) : 1;
  const showActive = baseFrac < 1;
  const active = baseGoal && baseGoal < target ? Math.round(target - baseGoal) : 0;
  const burnMarkup = showActive
    ? `<span class="calorie-hero__burn">🔥 ${burnCaptionText(active, burn)}</span>`
    : "";
  let tailMarkup = "";
  if (showActive) {
    const tailLeft = (baseFrac * 100).toFixed(1);
    tailMarkup = `
      <span class="calorie-bar__tail" style="left:${tailLeft}%"
        data-width="${((1 - baseFrac) * 100).toFixed(1)}%"></span>
      <span class="calorie-bar__boundary" style="left:${tailLeft}%"></span>`;
  }
  return `
    <div class="calorie-hero calorie-hero--bar" role="img"
      aria-label="${t("diary.ring_aria", { eaten: Math.round(eaten), target: Math.round(target), left: leftLabel })}">
      <div class="calorie-hero__top">
        <div class="calorie-hero__nums">
          <span class="calorie-hero__caption">${t("diary.calories")}</span>
          <span class="calorie-hero__value">${Math.round(eaten)}</span>
          <span class="calorie-hero__sub">${t("diary.calories_of", { amount: Math.round(target) })}</span>
        </div>
        <span class="calorie-hero__left">🔥 ${leftLabel}</span>
      </div>
      <div class="calorie-bar" role="progressbar"
        aria-valuemin="0" aria-valuemax="${Math.round(target)}" aria-valuenow="${Math.round(eaten)}">
        <span data-width="${pct.toFixed(1)}%"></span>
        ${tailMarkup}
      </div>
      ${burnMarkup}
    </div>`;
}

/** Vertical macro tube — Android MacroCard (mobile). */
function macroTube(key, label, value, target, unit = "g") {
  const pct = target > 0 ? Math.min(100, (value / target) * 100) : 0;
  const status = tubeStatus(value, target, unit);
  const over = target > 0 && value > target;
  return `
    <div class="macro-tube macro-tube--${key}${over ? " is-over" : ""}">
      <span class="macro-tube__value">${Math.round(value)}</span>
      <div class="macro-tube__track" aria-hidden="true">
        <span class="macro-tube__fill" data-height="${pct.toFixed(1)}%"></span>
      </div>
      <span class="macro-tube__label">${label}</span>
      <span class="macro-tube__status">${status}</span>
    </div>`;
}

/** Horizontal macro progress row (desktop). */
function macroRow(key, label, value, target, unit = "g") {
  const pct = target > 0 ? Math.min(100, (value / target) * 100) : 0;
  const status = tubeStatus(value, target, unit);
  const over = target > 0 && value > target;
  return `
    <div class="macro-row macro-row--${key}${over ? " is-over" : ""}">
      <div class="macro-row__meta">
        <span class="macro-row__label">${label}</span>
        <span class="macro-row__value">${Math.round(value)}</span>
        <span class="macro-row__status">${status}</span>
      </div>
      <div class="macro-row__track" aria-hidden="true">
        <span class="macro-row__fill" data-width="${pct.toFixed(1)}%"></span>
      </div>
    </div>`;
}

/**
 * @param {string[]} tubeKeys
 * @param {import('../lib/chompass-core/models.js').FoodEntry[]} entries
 * @param {ReturnType<typeof dailyTargets>|null} targets
 * @param {import('../lib/db.js').OptionalNutrientGoals} optionalGoals
 * @param {"tube" | "row"} style
 */
function renderMacros(tubeKeys, entries, targets, optionalGoals, style) {
  return tubeKeys
    .map((key) => {
      const def = nutrientDef(key);
      if (!def) return "";
      const value = sumNutrient(entries, key);
      const goal = nutrientGoal(key, targets, optionalGoals);
      return style === "row"
        ? macroRow(def.tubeCss, def.label, value, goal, def.unit)
        : macroTube(def.tubeCss, def.label, value, goal, def.unit);
    })
    .join("");
}

function tile(action, label, sub, icon, hero = false) {
  return `
    <button type="button" class="add-food-tile${hero ? " add-food-tile--hero" : ""}" data-add="${action}">
      <span class="add-food-tile__icon">${icon}</span>
      <span class="add-food-tile__label">${label}</span>
      ${sub ? `<span class="add-food-tile__sub">${sub}</span>` : ""}
    </button>`;
}

/**
 * @param {import('../lib/chompass-core/models.js').FoodEntry[]} mealEntries
 * @param {string} mealType
 * @param {string[]} chipKeys
 */
function mealCard(mealType, mealEntries, chipKeys) {
  const totals = sumMealChipValues(mealEntries, chipKeys);
  const icon = ICONS[mealType] || ICONS.snack;
  return `
    <section class="meal-card card card--glass">
      <button type="button" class="meal-card__header" data-meal-nutrition="${escapeAttr(mealType)}" aria-label="${escapeAttr(`${mealLabel(mealType)} · ${t("diary.nutrition_detail")}`)}">
        <span class="meal-card__icon meal-card__icon--${escapeAttr(mealType)}">${icon}</span>
        <div class="meal-card__titles">
          <h2 class="meal-card__title">${mealLabel(mealType)}</h2>
          <p class="meal-card__summary">
            <span class="meal-card__kcal">${Math.round(totals.calories)} kcal</span>
            <span class="meal-card__summary-sep"> · </span>${formatMacroChipLine(totals, chipKeys)}
          </p>
        </div>
      </button>
      <div class="meal-card__list">
        ${mealEntries
          .map((e) => {
            // Serving echoes the logged unit when resolvable (#65), else grams.
            const serving = servingEchoText(e) ?? (e.quantityG != null ? escapeHtml(formatGrams(e.quantityG)) : null);
            return `
          <div class="food-swipe" data-entry-id="${e.id}">
            <div class="food-swipe__behind food-swipe__behind--fav" aria-hidden="true">${t("diary.favorite")}</div>
            <div class="food-swipe__behind food-swipe__behind--del" aria-hidden="true">${t("action.delete")}</div>
            <div class="food-item">
              <button type="button" class="food-item__main" data-edit>
                <span class="food-item__text">
                  <span class="food-item__top">
                    <span class="food-item__name">${escapeHtml(e.name)}</span>
                    ${e.time ? `<span class="food-item__meta-time">${formatEntryTime(e.time)}</span>` : ""}
                  </span>
                  <span class="food-item__kcalrow">
                    <span class="food-item__cals">${Math.round(e.calories)} kcal</span>
                    ${serving != null ? `<span class="food-item__meta-sep"> · </span><span class="food-item__serving">${serving}</span>` : ""}
                  </span>
                  <span class="food-item__pills">${formatFoodPills(e, chipKeys)}</span>
                </span>
              </button>
              <button type="button" class="food-item__menu" data-menu aria-label="${escapeAttr(t("diary.more_actions", { name: e.name }))}">⋮</button>
            </div>
          </div>`;
          })
          .join("")}
      </div>
    </section>`;
}

export class DiaryView extends HTMLElement {
  constructor() {
    super();
    this.date = loadHomeDate();
    this.fabOpen = false;
    /** @type {import('../lib/chompass-core/models.js').FoodEntry|null} */
    this._undoEntry = null;
    /** @type {ReturnType<typeof openSheet> | null} */
    this._sheet = null;
    /** @type {string|null} */
    this._renderedDay = null;
  }

  connectedCallback() {
    this.render();
    // Day-rollover journal trigger (#60): a CYCLE/WEEKDAYS switch lands at
    // midnight while the app is open (a PWA has no background alarms); the
    // refresh core is a no-op when nothing changed.
    this._dayRolloverTick = setInterval(async () => {
      const day = todayIso();
      if (day === this._renderedDay) return;
      await refreshGoalJournal().catch(() => null);
      this.render();
    }, 60_000);
    // Local-only fasting timer (mirrors the Android tracker): roll the elapsed
    // label over each minute while a fast is running (re-render is cheap), and
    // drive auto-cycle transitions (PWA has no background alarms).
    this._fastingTick = setInterval(async () => {
      const p = this._appPrefs;
      const active = p?.showFasting === true && (p?.fastingStartedAt != null || p?.fastingEatHours > 0 || p?.fastingAutoWindows === true);
      if (!active) return;
      await this.fastingHeal();
      this.render();
    }, 60_000);
  }

  disconnectedCallback() {
    clearInterval(this._fastingTick);
    clearInterval(this._dayRolloverTick);
    this._sheet?.close();
    this._sheet = null;
  }

  /** @param {string} iso */
  setDate(iso) {
    this.date = clampDate(iso);
    saveHomeDate(this.date);
  }

  async render() {
    // The 60s fasting/day-rollover ticks re-render every minute; a full render
    // rebuilds the day-note textarea from the stored note and would wipe
    // unsaved typing. Skip while the user is in the note: the tick's store
    // healing still ran, and the next render after blur or any interaction
    // catches the UI up.
    const activeNoteInput = this.querySelector("[data-note-input]");
    if (activeNoteInput && document.activeElement === activeNoteInput) return;
    const [entries, prof, waterLogs, appPrefs, manualKcal, noteLogs, journal, allManual] = await Promise.all([
      foodEntries.byDate(this.date),
      profileStore.load(),
      water.byDate(this.date),
      prefs.load(),
      manualActiveKcalForDate(this.date),
      dailyNotes.byDate(this.date),
      goalJournal.all(),
      loadManualActiveEntries(),
    ]);
    this._renderedDay = todayIso();
    const note = noteLogs[0] ?? null;
    const nicotineLogs = await nicotine.byDate(this.date);
    const caffeineLogs = await caffeine.byDate(this.date);

    const totals = entries.reduce(
      (acc, e) => {
        acc.calories += e.calories;
        acc.proteinG += e.proteinG;
        acc.carbsG += e.carbsG;
        acc.fatG += e.fatG;
        return acc;
      },
      { calories: 0, proteinG: 0, carbsG: 0, fatG: 0 }
    );

    // Macro day types (#60): today's (or the viewed past day's) targets resolve
    // journal-first (frozen actuals) with live plan resolution as fallback.
    const baseTargets = prof ? dailyTargets(prof) : null;
    const dayResolved = baseTargets
      ? resolveDayJournaled(journal, prof?.macroPlan ?? null, baseTargets, this.date, todayIso())
      : null;
    const targets = dayResolved ? dayResolved.targets : null;
    const dayTypeChip =
      dayResolved && (prof?.macroPlan?.enabled || dayResolved.profileName)
        ? `<button type="button" class="chip day-type-chip" data-day-type>${escapeHtml(
            dayResolved.profileName ?? t("day_types.chip_none"),
          )}</button>`
        : "";
    let calorieTarget = targets?.calories ?? 0;
    /** @type {{ goal: number, active: number, live: number, typical: number, typicalIsDayType?: boolean, typicalDayTypeName?: string|null, source: string, awaiting: false } | { goal: number, awaiting: true } | null} */
    let gaugeInfo = null;
    if (prof && targets && appPrefs.calorieGaugeMode === "add_active") {
      const { sedentaryBudget, estimatedDailyActive } = estimatedDailyActiveCalories(prof, targets.calories);
      const stats = computeDayTypeActiveStats(journal, sumManualByDay(allManual), this.date);
      const typicalRes = resolveActiveTypical(dayResolved?.profileId, stats, 0, estimatedDailyActive);
      const typical = typicalRes.kcal;
      const burn = resolveWebActiveBurn(typical, manualKcal);
      calorieTarget = addActiveGaugeTarget(targets.calories, sedentaryBudget, burn);
      gaugeInfo = burn
        ? {
            goal: sedentaryBudget,
            active: burn.calories,
            live: Math.round(manualKcal),
            typical,
            typicalIsDayType: typicalRes.typicalIsDayType,
            typicalDayTypeName: dayResolved?.profileName ?? null,
            source: burn.source,
            awaiting: false,
          }
        : { goal: sedentaryBudget, awaiting: true };
    }
    // Manual burns are deliberate "eat back" logs: they raise that day's
    // target even in static mode, which only excludes automatic burn
    // (Health Connect / activity estimate).
    else if (targets && manualKcal > 0) {
      calorieTarget = targets.calories + Math.round(manualKcal);
    }

    const waterMl = waterLogs.reduce((s, w) => s + w.amountMl, 0);
    const waterGoal = appPrefs.waterGoalMl ?? 2000;
    const waterPct = waterGoal > 0 ? Math.min(100, (waterMl / waterGoal) * 100) : 0;
    const showWater = appPrefs.showWater === true;
    const nicotineCount = nicotineLogs.reduce((s, n) => s + (n.count ?? 1), 0);
    const nicotineLimit = appPrefs.nicotineDailyLimit ?? 0;
    const nicotinePct = nicotineLimit > 0 ? Math.min(100, (nicotineCount / nicotineLimit) * 100) : 0;
    const showNicotine = appPrefs.showNicotine === true;
    const showFasting = appPrefs.showFasting === true;
    // Caffeine hero total mirrors Android: tracker logs + food-entry caffeine.
    const caffeineMg =
      caffeineLogs.reduce((s, c) => s + (Number(c.mg) || 0), 0) +
      entries.reduce((s, e) => s + (e.caffeineMg ?? 0), 0);
    const caffeineLimit = appPrefs.optionalNutrientGoals?.caffeineMg ?? 400;
    const caffeinePct = caffeineLimit > 0 ? Math.min(100, (caffeineMg / caffeineLimit) * 100) : 0;
    const showCaffeine = appPrefs.showCaffeine === true;
    const showNotes = appPrefs.showNotes === true;
    // #38 (Android parity): in ADD_ACTIVE the ring target can sit above the
    // stored base (manual kcal stacked on the estimate); scale the macro
    // goals to the ring so cards and gauge never disagree. Typical days
    // (estimate only) scale to exactly 1; keto targets stay fixed.
    let macroTargets = targets;
    if (targets && !prof?.ketoMode && calorieTarget > targets.calories) {
      const s = calorieTarget / targets.calories;
      macroTargets = {
        ...targets,
        proteinG: Math.round(targets.proteinG * s),
        carbsG: Math.round(targets.carbsG * s),
        fatG: Math.round(targets.fatG * s),
      };
    }
    const tubeKeys = normalizeHomeTopNutrients(appPrefs.homeTopNutrients, appPrefs.homeNutrientCardCount);
    const chipKeys = normalizeFoodLogChips(appPrefs.foodLogMacroChips);
    const optionalGoals = mergeOptionalGoals(appPrefs.optionalNutrientGoals);
    const weekStart =
      appPrefs.weekStartDay || (appPrefs.weekStartsOnMonday === false ? "sunday" : "monday");
    const today = todayIso();
    // Week pager window: ±2 weeks around the viewed day (five 7-day pages).
    // The old 53-page strip rendered 371 buttons on every render, flooded
    // the a11y tree, and made keyboard users tab through all of them before
    // the food rows; a centered window keeps scroll context at 35 tabs.
    const WEEK_PAGES_EACH_SIDE = 2;
    const selectedWeekStart = weekDates(this.date, weekStart)[0];
    const selectedWeekIndex = WEEK_PAGES_EACH_SIDE;

    /** @type {string[][]} */
    const weekPages = [];
    for (let i = -WEEK_PAGES_EACH_SIDE; i <= WEEK_PAGES_EACH_SIDE; i++) {
      const start = shiftDate(selectedWeekStart, i * 7);
      weekPages.push(
        Array.from({ length: 7 }, (_, d) => shiftDate(start, d))
      );
    }

    const nextDisabled = this.date >= today ? "disabled" : "";
    const macrosMobile = targets
      ? `<div class="macro-tubes macro-tubes--${tubeKeys.length}">
          ${renderMacros(tubeKeys, entries, macroTargets, optionalGoals, "tube")}
        </div>
        <button type="button" class="home-hero__more" data-nutrition-detail>${t("diary.view_more")} ›</button>`
      : "";
    const macrosDesktop = targets
      ? `<div class="macro-rows macro-rows--${tubeKeys.length}">
          ${renderMacros(tubeKeys, entries, macroTargets, optionalGoals, "row")}
        </div>`
      : "";
    const gaugeInfoLabel = t("diary.calorie_budget_info");
    // Android keeps the info affordance in every gauge mode — it explains the budget.
    const gaugeInfoBtnMobile = `<button type="button" class="gauge-info-btn" data-gauge-info aria-label="${gaugeInfoLabel}">${GAUGE_INFO_ICON}</button>`;
    const gaugeInfoBtnDesktop = `<button type="button" class="gauge-info-btn gauge-info-btn--inline" data-gauge-info aria-label="${gaugeInfoLabel}">${GAUGE_INFO_ICON}</button>`;
    const gaugeBaseGoal = gaugeInfo && "active" in gaugeInfo ? gaugeInfo.goal : calorieTarget;
    const gaugeBurn =
      gaugeInfo && "active" in gaugeInfo
        ? { live: gaugeInfo.live, typical: gaugeInfo.typical }
        : null;
    const gaugeMobile = targets
      ? ringSvg(totals.calories, calorieTarget, gaugeBaseGoal, gaugeBurn)
      : `<p class="empty-state">${t("diary.empty_no_profile")}</p>`;
    const gaugeDesktop = targets
      ? calorieBar(totals.calories, calorieTarget, gaugeBaseGoal, gaugeBurn)
      : `<p class="empty-state">${t("diary.empty_no_profile")}</p>`;

    const progressiveCount = progressiveMealItemCount();
    const progressiveChip =
      progressiveCount > 0
        ? `<button type="button" class="chip progressive-meal-chip" data-progressive-meal>
             ${escapeHtml(t("progressive_meal.continue", { count: String(progressiveCount) }))}
           </button>`
        : "";

    this.innerHTML = `
      <div class="week-nav">
        <button type="button" class="day-nav-btn day-nav-btn--week" data-day-delta="-1" aria-label="${t("diary.prev_day")}">${chevronLeft}</button>
        <div class="week-pager" data-week-pager aria-label="${t("diary.week_calendar")}">
          ${weekPages
            .map(
              (days, pageIdx) => `
            <div class="week-page" data-week-page="${pageIdx}">
              <div class="week-strip" role="group" aria-label="${t("diary.week_of", { date: new Date(`${days[0]}T00:00:00`).toLocaleDateString(undefined, { month: "short", day: "numeric" }) })}">
                ${days
                  .map((iso) => {
                    const d = new Date(`${iso}T00:00:00`);
                    const selected = iso === this.date ? " is-selected" : "";
                    const isToday = iso === today ? " is-today" : "";
                    const future = iso > today;
                    return `
                      <button type="button" class="week-day${selected}${isToday}" data-date="${iso}"
                        aria-pressed="${iso === this.date}" ${future ? "disabled" : ""}>
                        <span class="week-day__dow">${d.toLocaleDateString(undefined, { weekday: "narrow" })}</span>
                        <span class="week-day__num">${d.getDate()}</span>
                      </button>`;
                  })
                  .join("")}
              </div>
            </div>`
            )
            .join("")}
        </div>
        <button type="button" class="day-nav-btn day-nav-btn--week" data-day-delta="1" aria-label="${t("diary.next_day")}" ${nextDisabled}>${chevronRight}</button>
      </div>

      <div class="home-hero" data-day-swipe>
        <div class="home-hero--mobile">
          <div class="home-hero__day-nav">
            <button type="button" class="day-nav-btn" data-day-delta="-1" aria-label="${t("diary.prev_day")}">${chevronLeft}</button>
            <div class="home-hero__gauge-wrap">
              <button type="button" class="calorie-hero calorie-hero--tap calorie-hero--tap-arc" data-nutrition-detail aria-label="${t("diary.open_nutrition_detail")}">
                ${gaugeMobile}
              </button>
              ${gaugeInfoBtnMobile}
            </div>
            <button type="button" class="day-nav-btn" data-day-delta="1" aria-label="${t("diary.next_day")}" ${nextDisabled}>${chevronRight}</button>
          </div>
          <div class="day-type-chip-row">${dayTypeChip}</div>
          ${macrosMobile}
        </div>
        <div class="home-hero--desktop">
          <div class="home-hero__bar-wrap">
            <!-- div (not button): the calorie bar contains block content; a
                 nested button would break HTML parsing and escape this
                 container (visible on mobile). Mobile hero uses a real button
                 since it only wraps SVG. -->
            <div class="calorie-hero--tap calorie-hero--tap-bar" data-nutrition-detail role="button" tabindex="0" aria-label="${t("diary.open_nutrition_detail")}">
              ${gaugeDesktop}
            </div>
            ${gaugeInfoBtnDesktop}
          </div>
          <div class="day-type-chip-row">${dayTypeChip}</div>
          ${macrosDesktop}
        </div>
      </div>

      ${
        showWater
          ? `<div class="card card--glass water-row">
              <div class="water-row__top">
                <div class="water-row__meta">${t("diary.water_intake_line", { count: `<strong>${waterMl} ml</strong>`, goal: waterGoal })}</div>
                <div class="water-presets">
                  ${WATER_PRESETS.map((ml) => `<button type="button" class="chip" data-water="${ml}">+${ml}</button>`).join("")}
                  <button type="button" class="chip" data-water-custom>${t("diary.custom")}</button>
                  ${waterLogs.length ? `<button type="button" class="chip chip--ghost" data-water-undo title="${t("diary.remove_last")}">${t("diary.undo")}</button>` : ""}
                </div>
              </div>
              <div class="water-bar" role="progressbar" aria-valuemin="0" aria-valuemax="${waterGoal}" aria-valuenow="${waterMl}" aria-label="${t("diary.water_intake_aria")}">
                <span data-width="${waterPct.toFixed(1)}%"></span>
              </div>
            </div>`
          : ""
      }

      ${
        showNicotine
          ? `<div class="card card--glass water-row nicotine-row">
              <div class="water-row__top">
                <div class="water-row__meta">${
                  nicotineLimit > 0
                    ? t("diary.nicotine_limit_line", { count: `<strong>${nicotineCount}</strong>`, limit: nicotineLimit })
                    : t("diary.nicotine_logged_line", { count: `<strong>${nicotineCount}</strong>` })
                }</div>
                <div class="water-presets">
                  ${NICOTINE_QUICK_KINDS.map((kind) => `<button type="button" class="chip" data-nicotine="${kind}">${t(`diary.chip_${kind}`)}</button>`).join("")}
                  <button type="button" class="chip" data-nicotine-custom>${t("diary.custom")}</button>
                  ${nicotineLogs.length ? `<button type="button" class="chip chip--ghost" data-nicotine-undo title="${t("diary.remove_last")}">${t("diary.undo")}</button>` : ""}
                </div>
              </div>
              ${
                nicotineLimit > 0
                  ? `<div class="water-bar" role="progressbar" aria-valuemin="0" aria-valuemax="${nicotineLimit}" aria-valuenow="${nicotineCount}" aria-label="${t("diary.nicotine_intake_aria")}">
                      <span data-width="${nicotinePct.toFixed(1)}%"></span>
                    </div>`
                  : ""
              }
            </div>`
          : ""
      }

      ${
        showCaffeine
          ? `<div class="card card--glass water-row caffeine-row">
              <div class="water-row__top">
                <div class="water-row__meta">${
                  caffeineLimit > 0
                    ? t("diary.caffeine_limit_line", { count: `<strong>${caffeineMg}</strong>`, limit: caffeineLimit })
                    : t("diary.caffeine_logged_line", { count: `<strong>${caffeineMg}</strong>` })
                }</div>
                <div class="water-presets">
                  ${CAFFEINE_QUICK_KINDS.map((kind) => `<button type="button" class="chip" data-caffeine="${kind}">${t(`diary.chip_${kind}`)}</button>`).join("")}
                  <button type="button" class="chip" data-caffeine-custom>${t("diary.custom")}</button>
                  ${caffeineLogs.length ? `<button type="button" class="chip chip--ghost" data-caffeine-undo title="${t("diary.remove_last")}">${t("diary.undo")}</button>` : ""}
                </div>
              </div>
              ${
                caffeineLimit > 0
                  ? `<div class="water-bar" role="progressbar" aria-valuemin="0" aria-valuemax="${caffeineLimit}" aria-valuenow="${caffeineMg}" aria-label="${t("diary.caffeine_intake_aria")}">
                      <span data-width="${caffeinePct.toFixed(1)}%"></span>
                    </div>`
                  : ""
              }
            </div>`
          : ""
      }

      ${showFasting ? fastingCard(appPrefs) : ""}

      ${progressiveChip ? `<div class="progressive-meal-bar">${progressiveChip}</div>` : ""}

      ${
        showNotes
          ? this._noteEditing || note
            ? `<div class="card card--glass note-row">
              <div class="note-row__top">
                <strong>${t("diary.note_title")}</strong>
              </div>
              <textarea class="note-row__input" data-note-input maxlength="1000" rows="3" placeholder="${t("diary.note_empty")}">${note ? escapeHtml(note.text) : ""}</textarea>
              <div class="note-row__actions">
                ${note ? `<button type="button" class="chip chip--ghost" data-note-clear>${t("diary.note_clear")}</button>` : ""}
                <button type="button" class="chip" data-note-save>${t("diary.note_save")}</button>
              </div>
            </div>`
            : `<button type="button" class="note-row__empty" data-note-empty>✎ ${t("diary.note_empty")}</button>`
          : ""
      }

      ${
        entries.length === 0
          ? `<p class="empty-state">${t("diary.empty")}</p>`
          : mealOrderFor(entries)
              .map((mealType) => mealCard(mealType, entries.filter((e) => e.mealType === mealType), chipKeys))
              .join("")
      }

      <button class="fab" aria-label="${t("a11y.add_food")}" aria-expanded="${this.fabOpen}" data-action="fab">+</button>
    `;

    this._gaugeInfo = gaugeInfo;
    this._gaugeGoal = calorieTarget;
    this._appPrefs = appPrefs;
    this.bindInteractions(entries, appPrefs, macroTargets, optionalGoals, waterLogs, nicotineLogs, caffeineLogs);
    this.animateFills();
    requestAnimationFrame(() => this.scrollWeekPagerTo(selectedWeekIndex));
    this.afterHomeRender(appPrefs);
  }

  /** @param {Awaited<ReturnType<typeof prefs.load>>} appPrefs */
  afterHomeRender(appPrefs) {
    if (consumeResumeProgressiveCapture()) {
      // Codeberg #78: Add next ingredient opens the full Add Food sheet.
      void this.openAddFoodSheet(appPrefs);
      return;
    }
    if (consumeShowProgressiveMealSheet()) {
      this.openProgressiveMealSheet(appPrefs);
    }
  }

  /** @param {number} pageIndex */
  scrollWeekPagerTo(pageIndex) {
    const pager = /** @type {HTMLElement | null} */ (this.querySelector("[data-week-pager]"));
    const page = /** @type {HTMLElement | null} */ (this.querySelector(`[data-week-page="${pageIndex}"]`));
    if (!pager || !page) return;
    pager.scrollLeft = page.offsetLeft;
  }

  /**
   * @param {import('../lib/chompass-core/models.js').FoodEntry[]} entries
   * @param {Awaited<ReturnType<typeof prefs.load>>} appPrefs
   * @param {ReturnType<typeof dailyTargets>|null} targets
   * @param {import('../lib/db.js').OptionalNutrientGoals} optionalGoals
   * @param {{id: string, date: string, amountMl: number}[]} waterLogs
   * @param {{id: string, date: string, kind: string, mg: number}[]} nicotineLogs
   * @param {{id: string, date: string, kind: string, mg: number}[]} caffeineLogs
   */
  bindInteractions(entries, appPrefs, targets, optionalGoals, waterLogs, nicotineLogs, caffeineLogs) {
    this.querySelectorAll("[data-date]").forEach((el) => {
      el.addEventListener("click", () => {
        const iso = el.getAttribute("data-date") || this.date;
        if (iso > todayIso()) return;
        this.setDate(iso);
        this.render();
      });
    });
    this.querySelectorAll("[data-water]").forEach((el) => {
      el.addEventListener("click", () => this.addWater(Number(el.getAttribute("data-water"))));
    });
    this.querySelector("[data-day-type]")?.addEventListener("click", () => this.openDayTypeSheet());
    this.querySelector("[data-water-custom]")?.addEventListener("click", () => this.customWater());
    this.querySelector("[data-water-undo]")?.addEventListener("click", () => this.undoLastWater(waterLogs));
    this.querySelector("[data-note-empty]")?.addEventListener("click", () => {
      this._noteEditing = true;
      this.render();
    });
    this.querySelector("[data-note-save]")?.addEventListener("click", () => this.saveDailyNote());
    this.querySelector("[data-note-clear]")?.addEventListener("click", () => this.clearDailyNote());
    this.querySelectorAll("[data-nicotine]").forEach((el) => {
      el.addEventListener("click", () => this.addNicotine(String(el.getAttribute("data-nicotine"))));
    });
    this.querySelector("[data-nicotine-custom]")?.addEventListener("click", () => this.customNicotine());
    this.querySelector("[data-nicotine-undo]")?.addEventListener("click", () => this.undoLastNicotine(nicotineLogs));
    this.querySelectorAll("[data-caffeine]").forEach((el) => {
      el.addEventListener("click", () => this.addCaffeine(String(el.getAttribute("data-caffeine"))));
    });
    this.querySelector("[data-caffeine-custom]")?.addEventListener("click", () => this.customCaffeine());
    this.querySelector("[data-caffeine-undo]")?.addEventListener("click", () => this.undoLastCaffeine(caffeineLogs));
    this.querySelector("[data-fasting-start]")?.addEventListener("click", () => this.fastingStart());
    this.querySelector("[data-fasting-stop]")?.addEventListener("click", () => this.fastingStop());
    this.querySelectorAll("[data-nutrition-detail]").forEach((el) => {
      el.addEventListener("click", () => {
        this.openNutritionDetail(entries, targets, optionalGoals);
      });
    });
    this.querySelectorAll("[data-meal-nutrition]").forEach((el) => {
      el.addEventListener("click", () => {
        const mealType = el.getAttribute("data-meal-nutrition") || "";
        const mealEntries = entries.filter((e) => e.mealType === mealType);
        this.openNutritionDetail(mealEntries, targets, optionalGoals, mealLabel(mealType));
      });
    });
    // The desktop hero is a div (block content), not a <button>: give the
    // keyboard the same Enter/Space activation a real button would have.
    this.querySelectorAll('[data-nutrition-detail][role="button"]').forEach((el) => {
      el.addEventListener("keydown", (/** @type {KeyboardEvent} */ ev) => {
        if (ev.key !== "Enter" && ev.key !== " ") return;
        ev.preventDefault();
        if (el instanceof HTMLElement) el.click();
      });
    });
    this.querySelectorAll("[data-gauge-info]").forEach((el) => {
      el.addEventListener("click", (ev) => {
        ev.stopPropagation();
        this.openGaugeInfo();
      });
    });
    this.querySelector('[data-action="fab"]')?.addEventListener("click", () => this.openAddFoodSheet(appPrefs));
    this.querySelector("[data-progressive-meal]")?.addEventListener("click", () => {
      setShowProgressiveMealSheet(true);
      this.openProgressiveMealSheet(appPrefs);
    });

    this.querySelectorAll("[data-day-delta]").forEach((el) => {
      el.addEventListener("pointerdown", (ev) => ev.stopPropagation());
      el.addEventListener("click", () => {
        const delta = Number(el.getAttribute("data-day-delta"));
        if (!Number.isFinite(delta) || delta === 0) return;
        this.setDate(shiftDate(this.date, delta));
        this.render();
      });
    });

    this.querySelectorAll(".food-swipe").forEach((row) => {
      const id = row.getAttribute("data-entry-id");
      const entry = entries.find((e) => e.id === id);
      if (!entry) return;
      const item = row.querySelector(".food-item");
      row.querySelector("[data-edit]")?.addEventListener("click", () => this.openEntryForm(entry));
      row.querySelector("[data-menu]")?.addEventListener("click", (ev) => {
        ev.stopPropagation();
        this.openFoodMenu(entry);
      });
      if (item instanceof HTMLElement) this.bindFoodSwipe(row, item, entry);
    });

    this.bindDaySwipe(this.querySelector("[data-day-swipe]"));
  }

  animateFills() {
    requestAnimationFrame(() => {
      this.querySelectorAll("[data-width]").forEach((el) => {
        if (el instanceof HTMLElement) el.style.width = el.getAttribute("data-width") || "0%";
      });
      this.querySelectorAll("[data-height]").forEach((el) => {
        if (el instanceof HTMLElement) el.style.height = el.getAttribute("data-height") || "0%";
      });
      this.querySelectorAll(".calorie-ring__progress").forEach((el) => {
        if (el instanceof SVGElement) {
          const dash = el.getAttribute("data-dash");
          if (dash) el.setAttribute("stroke-dasharray", dash);
        }
      });
      this.querySelectorAll(".calorie-ring__tail").forEach((el) => {
        if (el instanceof SVGElement) {
          const dash = el.getAttribute("data-dash");
          if (dash) el.setAttribute("stroke-dasharray", dash);
        }
      });
    });
  }

  openGaugeInfo() {
    const info = this._gaugeInfo;
    if (!info) {
      // Static mode: plain daily goal breakdown.
      openInfo({
        title: t("diary.calorie_budget_title"),
        message: t("diary.calorie_budget_goal", { goal: String(this._gaugeGoal ?? 0) }),
        doneLabel: t("action.done"),
      });
      return;
    }
    if ("active" in info) {
      const sourceLine =
        info.source === "estimated"
          ? t("diary.calorie_budget_source_estimated")
          : t("diary.calorie_budget_source_manual");
      openInfo({
        title: t("diary.calorie_budget_title"),
        bodyHtml: `
          <p class="dialog__message dialog__message--strong">${escapeHtml(
            t("diary.calorie_budget_goal_plus_active", {
              goal: String(info.goal),
              active: String(info.active),
            }),
          )}</p>
          <p class="dialog__message">${escapeHtml(sourceLine)}</p>`,
        doneLabel: t("action.done"),
      });
      return;
    }
    openInfo({
      title: t("diary.calorie_budget_title"),
      message: t("diary.calorie_budget_awaiting"),
      doneLabel: t("action.done"),
    });
  }

  /** @param {Element | null} region */
  bindDaySwipe(region) {
    if (!(region instanceof HTMLElement)) return;
    let startX = 0;
    let startY = 0;
    let tracking = false;
    /** @type {number | null} */
    let pointerId = null;

    region.addEventListener("pointerdown", (ev) => {
      const pev = /** @type {PointerEvent} */ (ev);
      if (pev.pointerType === "mouse" && pev.button !== 0) return;
      startX = pev.clientX;
      startY = pev.clientY;
      tracking = true;
      pointerId = pev.pointerId;
    });

    region.addEventListener("pointerup", (ev) => {
      const pev = /** @type {PointerEvent} */ (ev);
      if (!tracking || pev.pointerId !== pointerId) return;
      tracking = false;
      pointerId = null;
      const dx = pev.clientX - startX;
      const dy = pev.clientY - startY;
      if (Math.abs(dx) < 56 || Math.abs(dx) < Math.abs(dy) * 1.4) return;
      this.setDate(shiftDate(this.date, dx < 0 ? 1 : -1));
      this.render();
    });

    region.addEventListener("pointercancel", () => {
      tracking = false;
      pointerId = null;
    });
  }

  /**
   * Swipe-left → delete; swipe-right → favorite (Android parity). Duplicate stays in overflow.
   * @param {Element} row
   * @param {HTMLElement} item
   * @param {import('../lib/chompass-core/models.js').FoodEntry} entry
   */
  bindFoodSwipe(row, item, entry) {
    let startX = 0;
    let startY = 0;
    let dx = 0;
    let active = false;
    let horizontal = false;
    /** @type {number | null} */
    let pointerId = null;

    const reset = () => {
      item.style.transform = "";
      dx = 0;
      active = false;
      horizontal = false;
      pointerId = null;
      row.classList.remove("is-swiping-left", "is-swiping-right");
    };

    row.addEventListener("pointerdown", (ev) => {
      const pev = /** @type {PointerEvent} */ (ev);
      if (pev.pointerType === "mouse" && pev.button !== 0) return;
      if (/** @type {Element} */ (pev.target).closest("[data-menu]")) return;
      startX = pev.clientX;
      startY = pev.clientY;
      active = true;
      horizontal = false;
      dx = 0;
      pointerId = pev.pointerId;
      try {
        row.setPointerCapture(pev.pointerId);
      } catch {
        /* ignore */
      }
    });

    row.addEventListener("pointermove", (ev) => {
      const pev = /** @type {PointerEvent} */ (ev);
      if (!active || pev.pointerId !== pointerId) return;
      const moveX = pev.clientX - startX;
      const moveY = pev.clientY - startY;
      if (!horizontal) {
        if (Math.abs(moveX) < 10 && Math.abs(moveY) < 10) return;
        if (Math.abs(moveY) > Math.abs(moveX)) {
          active = false;
          return;
        }
        horizontal = true;
      }
      dx = moveX;
      item.style.transition = "none";
      item.style.transform = `translateX(${dx}px)`;
      row.classList.toggle("is-swiping-left", dx < 0);
      row.classList.toggle("is-swiping-right", dx > 0);
    });

    const end = async (ev) => {
      const pev = /** @type {PointerEvent} */ (ev);
      if (pev.pointerId !== pointerId && pointerId != null) return;
      if (!active && !horizontal) {
        reset();
        return;
      }
      item.style.transition = "";
      if (dx < -88) this.deleteEntry(entry);
      else if (dx > 88) {
        const nowFav = await toggleFavorite(entry);
        showToast(nowFav ? t("diary.added_to_favorites") : t("diary.removed_from_favorites"));
        reset();
      } else reset();
    };

    row.addEventListener("pointerup", end);
    row.addEventListener("pointercancel", () => reset());
  }

  /** Macro day types (#60): quick-switch sheet from the hero chip. */
  async openDayTypeSheet() {
    const prof = await profileStore.load();
    if (!prof) return;
    const plan = prof.macroPlan ?? null;
    const today = todayIso();
    const base = dailyTargets(prof);
    const profiles = plan?.profiles ?? [];
    const active = resolveDay(plan, base, today);
    const tomorrow = resolveDay(plan, base, shiftDate(today, 1));
    const tomorrowLabel = tomorrow.profileName
      ? t("day_types.tomorrow_format", { name: tomorrow.profileName, kcal: String(tomorrow.targets.calories) })
      : t("day_types.tomorrow_base", { kcal: String(base.calories) });
    const hasOverride = plan?.dayAssignments?.[today] != null;
    const allManual = await loadManualActiveEntries();
    const journal = await goalJournal.all();
    const stats = computeDayTypeActiveStats(journal, sumManualByDay(allManual), today);
    const sheet = openSheet({
      title: t("day_types.sheet_title"),
      body: `
        <div class="sheet-actions" role="group" aria-label="${escapeAttr(t("day_types.sheet_title"))}">
          ${profiles
            .map((p) => {
              const typ = stats.byProfileId[p.id];
              const typLine =
                typ && typ.sampleCount >= 3
                  ? `<span style="display:block;font-size:0.78rem;color:var(--muted);">${escapeHtml(
                      t("day_types.active_typical", { kcal: String(typ.averageKcal) }),
                    )}</span>`
                  : "";
              return `
            <button type="button" data-type-id="${escapeAttr(p.id)}" aria-pressed="${active.profileId === p.id}">
              ${escapeHtml(p.name)} · ${p.calories} kcal
              <span style="display:block;font-size:0.8rem;color:var(--muted);">${p.proteinG}P / ${p.carbsG}C / ${p.fatG}F</span>
              ${typLine}
            </button>`;
            })
            .join("")}
          ${hasOverride ? `<button type="button" data-type-clear>${escapeHtml(t("day_types.follow_schedule"))}</button>` : ""}
        </div>
        <p style="margin:0.6rem 0 0;font-size:0.82rem;color:var(--muted);">${escapeHtml(tomorrowLabel)}</p>
        <div class="btn-row" style="margin-top:0.6rem;">
          <button type="button" class="btn btn--ghost" data-type-edit>${escapeHtml(t("day_types.edit"))}</button>
        </div>`,
    });
    /** @param {string|null} profileId */
    const apply = async (profileId) => {
      if (!plan || !plan.enabled) return;
      if (profileId != null && profileId === active.profileId) return; // already active: no stray override
      const next = setDayAssignment(plan, today, profileId);
      await profileStore.save({ ...prof, macroPlan: next });
      await recordManualSwitchGoalJournal();
      sheet.close();
      this.render();
    };
    sheet.body.querySelectorAll("[data-type-id]").forEach((btn) => {
      btn.addEventListener("click", () => apply(btn.getAttribute("data-type-id")));
    });
    sheet.body.querySelector("[data-type-clear]")?.addEventListener("click", () => apply(null));
    sheet.body.querySelector("[data-type-edit]")?.addEventListener("click", () => {
      sheet.close();
      location.hash = "#/settings?section=daytypes";
    });
  }

  /** @param {import('../lib/chompass-core/models.js').FoodEntry} entry */
  openFoodMenu(entry) {
    const sheet = openSheet({
      title: entry.name,
      body: `
        <div class="sheet-actions" role="menu">
          <button type="button" role="menuitem" data-act="edit">${t("action.edit")}</button>
          <button type="button" role="menuitem" data-act="meal">${t("diary.menu_change_meal")}</button>
          <button type="button" role="menuitem" data-act="fav">${t("diary.favorite")}</button>
          <button type="button" role="menuitem" data-act="share">${t("diary.share")}</button>
          <button type="button" role="menuitem" data-act="dup">${t("diary.duplicate")}</button>
          <button type="button" role="menuitem" data-act="del" class="is-danger">${t("action.delete")}</button>
        </div>`,
    });

    sheet.body.querySelector('[data-act="edit"]')?.addEventListener("click", () => {
      sheet.close();
      this.openEntryForm(entry);
    });
    sheet.body.querySelector('[data-act="fav"]')?.addEventListener("click", async () => {
      sheet.close();
      const nowFav = await toggleFavorite(entry);
      showToast(nowFav ? t("diary.added_to_favorites") : t("diary.removed_from_favorites"));
    });
    sheet.body.querySelector('[data-act="share"]')?.addEventListener("click", async () => {
      sheet.close();
      await this.shareEntries([entry]);
    });
    sheet.body.querySelector('[data-act="dup"]')?.addEventListener("click", () => {
      sheet.close();
      this.duplicateEntry(entry);
    });
    sheet.body.querySelector('[data-act="del"]')?.addEventListener("click", async () => {
      sheet.close();
      const ok = await openConfirm({
        title: t("diary.delete_entry_title"),
        message: t("diary.delete_entry_confirm", { name: entry.name }),
        confirmLabel: t("action.delete"),
        danger: true,
      });
      if (ok) this.deleteEntry(entry);
    });
    sheet.body.querySelector('[data-act="meal"]')?.addEventListener("click", () => {
      sheet.close();
      this.openChangeMealSheet(entry);
    });
    // Refresh favorite label
    isFavorite(entry).then((fav) => {
      const btn = sheet.body.querySelector('[data-act="fav"]');
      if (btn) btn.textContent = fav ? t("diary.unfavorite") : t("diary.favorite");
    });
  }

  /** @param {import('../lib/chompass-core/models.js').FoodEntry} entry */
  openChangeMealSheet(entry) {
    const sheet = openSheet({
      title: t("diary.menu_change_meal"),
      body: `
        <div class="sheet-actions" role="group" aria-label="${t("diary.meal_type")}">
          ${MEAL_ORDER.map(
            (m) =>
              `<button type="button" data-meal="${m}" aria-pressed="${entry.mealType === m}">
                ${mealLabel(m)}${entry.mealType === m ? ` · ${t("diary.current")}` : ""}
              </button>`
          ).join("")}
        </div>`,
    });
    sheet.body.querySelectorAll("[data-meal]").forEach((btn) => {
      btn.addEventListener("click", async () => {
        const meal = /** @type {"breakfast"|"lunch"|"dinner"|"snack"} */ (btn.getAttribute("data-meal"));
        sheet.close();
        if (!meal || meal === entry.mealType) return;
        await foodEntries.put({ ...entry, mealType: meal });
        this.render();
      });
    });
  }

  /** @param {Awaited<ReturnType<typeof prefs.load>>} appPrefs */
  async openAddFoodSheet(appPrefs) {
    if (this._sheet) {
      this._sheet.close();
      this._sheet = null;
      this.fabOpen = false;
      this.querySelector(".fab")?.setAttribute("aria-expanded", "false");
      return;
    }

    this.fabOpen = true;
    this.querySelector(".fab")?.setAttribute("aria-expanded", "true");

    const showWater = appPrefs.showWater === true;
    const speech = createSpeechCapture();
    const relogRows = await quickRelogRows(10);
    const relogChip = (e, key) => `
               <button type="button" class="add-food-relog-chip" data-relog="${key}" role="listitem">
                 <span class="add-food-relog-chip__emoji" aria-hidden="true">${e.emoji ? escapeHtml(String(e.emoji)) : "🍽"}</span>
                 <span class="add-food-relog-chip__text">
                   <strong>${escapeHtml(e.name)}</strong>
                   <span class="add-food-relog-chip__kcal">${Math.round(e.calories)} kcal</span>
                 </span>
                 <span class="add-food-relog-chip__add" aria-hidden="true">+</span>
               </button>`;
    const relogRow = (entries, prefix) =>
      entries.length === 0
        ? ""
        : `<div class="add-food-relog" role="list">
             ${entries.map((e, i) => relogChip(e, `${prefix}-${i}`)).join("")}
           </div>`;
    const hasRelog = relogRows.recents.length > 0 || relogRows.frequents.length > 0;
    const quickRelogBlock = `
      <button type="button" class="add-food-section add-food-section--action" data-add="log-again" aria-label="${t("add_food.open_logged_foods")}">
        <span class="add-food-section__text">
          <strong>${t("diary.saved_meals")}</strong>
          <span class="add-food-section__sub">${t("add_food.saved_meals_sub")}</span>
        </span>
        ${chevronRight}
      </button>
      ${
        hasRelog
          ? `<div class="add-food-relog-stack">
             ${relogRow(relogRows.recents, "r")}
             ${relogRow(relogRows.frequents, "f")}
           </div>`
          : `<p class="add-food-hint add-food-hint--empty">${t("add_food.quick_relog_empty")}</p>`
      }`;
    const body = `
      <div class="add-food-heroes">
        ${tile("photo", t("add_food.hero_photo"), t("add_food.hero_photo_sub"), ICONS.photo, true)}
        ${tile("note", t("add_food.hero_note"), t("add_food.hero_note_sub"), ICONS.note, true)}
      </div>
      ${quickRelogBlock}

      <p class="add-food-section">${t("add_food.more_section")}</p>
      <div class="add-food-grid">
        ${speech.supported ? tile("voice", t("add_food.voice"), "", ICONS.voice) : `<span class="add-food-tile add-food-tile--spacer" aria-hidden="true"></span>`}
        ${tile("scan", t("add_food.barcode"), "", ICONS.barcode)}
        ${tile("manual", t("add_food.manual"), "", ICONS.manual)}
        ${tile("copy", t("add_food.copy"), "", ICONS.copy)}
        ${tile("frequent", t("add_food.frequent"), "", ICONS.frequent)}
        ${tile("favorites", t("add_food.favorites"), "", ICONS.favorites)}
        ${tile("active", t("manual_active.title_short"), "", ICONS.active)}
        <span class="add-food-tile add-food-tile--spacer" aria-hidden="true"></span>
      </div>
      ${
        showWater
          ? `<p class="add-food-section">${t("diary.water")}</p>
             <div class="add-food-water">
               ${WATER_PRESETS.map((ml) => `<button type="button" class="chip" data-sheet-water="${ml}">+${ml} ml</button>`).join("")}
               <label class="add-food-water-slider">
                 <span>${t("diary.custom")}</span>
                 <input type="range" min="50" max="1000" step="50" value="200" data-water-range aria-label="${t("diary.water_amount")}" />
                 <span data-water-range-val>200 ml</span>
                 <button type="button" class="chip" data-sheet-water-range>${t("diary.add")}</button>
               </label>
             </div>`
          : ""
      }
    `;

    const sheet = openSheet({
      title: t("diary.add_food"),
      body,
      onClose: () => {
        this._sheet = null;
        this.fabOpen = false;
        this.querySelector(".fab")?.setAttribute("aria-expanded", "false");
      },
    });
    this._sheet = sheet;

    const go = (hash) => {
      sheet.close();
      location.hash = hash;
    };

    const openSaved = (segment) => {
      this.openSavedMealsSheet(sheet, appPrefs, segment);
    };

    sheet.body.querySelector('[data-add="photo"]')?.addEventListener("click", () => {
      sheet.close();
      if (DEMO) {
        // The hero demo has no real camera — the mock plate beat routes here.
        location.hash = `#/analyze?date=${this.date}&mode=photo`;
        return;
      }
      openPhotoAiFlow(this.date);
    });
    sheet.body.querySelector('[data-add="note"]')?.addEventListener("click", () => go(`#/analyze?date=${this.date}&mode=note`));
    sheet.body.querySelector('[data-add="recents"]')?.addEventListener("click", () => openSaved("RECENTS"));
    sheet.body.querySelector('[data-add="log-again"]')?.addEventListener("click", () => openSaved("RECENTS"));
    sheet.body.querySelector('[data-add="frequent"]')?.addEventListener("click", () => openSaved("FREQUENT"));
    sheet.body.querySelector('[data-add="favorites"]')?.addEventListener("click", () => openSaved("FAVORITES"));
    sheet.body.querySelector('[data-add="manual"]')?.addEventListener("click", () => go(`#/entry/new?date=${this.date}`));
    sheet.body.querySelector('[data-add="active"]')?.addEventListener("click", () => {
      sheet.close();
      this.openManualActiveSheet();
    });
    sheet.body.querySelector('[data-add="scan"]')?.addEventListener("click", () => go(`#/scan?date=${this.date}`));
    sheet.body.querySelector('[data-add="copy"]')?.addEventListener("click", () => {
      this.openCopyFromDaySheet(sheet);
    });
    sheet.body.querySelector('[data-add="voice"]')?.addEventListener("click", () => {
      sheet.close();
      this.startVoiceNote();
    });

    sheet.body.querySelectorAll("[data-relog]").forEach((btn) => {
      btn.addEventListener("click", async () => {
        const key = btn.getAttribute("data-relog") || "";
        const dash = key.indexOf("-");
        const prefix = dash >= 0 ? key.slice(0, dash) : "";
        const idx = Number(dash >= 0 ? key.slice(dash + 1) : key);
        const list = prefix === "f" ? relogRows.frequents : relogRows.recents;
        const entry = list[idx];
        if (!entry) return;
        sheet.close();
        const mealType = guessMealTypeFromPrefs(appPrefs);
        const now = new Date();
        const time = `${String(now.getHours()).padStart(2, "0")}:${String(now.getMinutes()).padStart(2, "0")}`;
        await foodEntries.put(duplicatedForLogging(entry, this.date, time, mealType));
        this.render();
      });
    });

    sheet.body.querySelectorAll("[data-sheet-water]").forEach((el) => {
      el.addEventListener("click", async () => {
        await this.addWater(Number(el.getAttribute("data-sheet-water")));
        sheet.close();
      });
    });

    const range = /** @type {HTMLInputElement | null} */ (sheet.body.querySelector("[data-water-range]"));
    const rangeVal = sheet.body.querySelector("[data-water-range-val]");
    range?.addEventListener("input", () => {
      if (rangeVal) rangeVal.textContent = `${range.value} ml`;
    });
    sheet.body.querySelector("[data-sheet-water-range]")?.addEventListener("click", async () => {
      const ml = Number(range?.value || 0);
      if (ml > 0) {
        await this.addWater(ml);
        sheet.close();
      }
    });
  }

  async openManualActiveSheet() {
    const all = await loadManualActiveEntries();
    const today = all.filter((e) => e.date === this.date);
    const rows = today
      .map(
        (e) => `
      <div class="manual-active-row" data-id="${escapeHtml(e.id)}">
        <button type="button" class="manual-active-row__edit" data-edit="${escapeHtml(e.id)}">
          ${escapeHtml(e.name)} · ${e.calories} kcal
        </button>
        <button type="button" class="manual-active-row__del" data-del="${escapeHtml(e.id)}" aria-label="${escapeHtml(t("manual_active.delete"))}">×</button>
      </div>`
      )
      .join("");
    const sheet = openSheet({
      title: t("manual_active.title"),
      body: `
        <p class="add-food-hint">${escapeHtml(t("manual_active.subtitle"))}</p>
        <form class="entry-form" id="manual-active-form">
          <div class="field">
            <label for="active-name">${escapeHtml(t("manual_active.name_hint"))}</label>
            <input id="active-name" name="name" type="text" autocomplete="off" />
          </div>
          <div class="field">
            <label for="active-kcal">${escapeHtml(t("manual_active.kcal_hint"))}</label>
            <input id="active-kcal" name="calories" type="number" min="1" max="99999" inputmode="numeric" required />
          </div>
          <div class="btn-row">
            <button type="submit" class="btn btn--primary">${escapeHtml(t("manual_active.save"))}</button>
          </div>
        </form>
        ${today.length ? `<div class="manual-active-list">${rows}</div>` : ""}
      `,
    });
    let editingId = null;
    sheet.body.querySelector("#manual-active-form")?.addEventListener("submit", async (ev) => {
      ev.preventDefault();
      const fd = new FormData(/** @type {HTMLFormElement} */ (ev.target));
      const kcal = Number(fd.get("calories"));
      if (!(kcal > 0)) return;
      const name = String(fd.get("name") || "");
      if (editingId) await updateManualActiveEntry(editingId, name, kcal);
      else await addManualActiveEntry(makeManualActiveEntry(this.date, name, kcal));
      sheet.close();
      this.render();
    });
    sheet.body.querySelectorAll("[data-del]").forEach((btn) => {
      btn.addEventListener("click", async () => {
        await deleteManualActiveEntry(btn.getAttribute("data-del"));
        sheet.close();
        this.render();
        this.openManualActiveSheet();
      });
    });
    sheet.body.querySelectorAll("[data-edit]").forEach((btn) => {
      btn.addEventListener("click", () => {
        const id = btn.getAttribute("data-edit");
        const entry = today.find((e) => e.id === id);
        if (!entry) return;
        editingId = id;
        const nameEl = sheet.body.querySelector("#active-name");
        const kcalEl = sheet.body.querySelector("#active-kcal");
        if (nameEl instanceof HTMLInputElement) nameEl.value = entry.name;
        if (kcalEl instanceof HTMLInputElement) kcalEl.value = String(entry.calories);
      });
    });
  }

  /** @param {Awaited<ReturnType<typeof prefs.load>>} [appPrefs] */
  openProgressiveMealSheet(appPrefs) {
    const draft = getProgressiveMeal();
    if (!draft || draft.items.length === 0) return;
    const totals = draftTotals(draft);
    const mealOptions = MEAL_ORDER.map(
      (m) =>
        `<option value="${m}" ${draft.mealType === m ? "selected" : ""}>${escapeHtml(t(`meal.${m}`))}</option>`
    ).join("");
    const rows = draft.items
      .map((item) => {
        // Same echo rule as saved rows (Codeberg #65): pending items keep
        // their analyzed unit when resolvable, else today's grams text.
        const serving = servingEchoText(item.analysis);
        return `
        <div class="progressive-meal-row" data-item-id="${escapeAttr(item.id)}">
          <div class="progressive-meal-row__text">
            <strong>${escapeHtml(item.analysis.name)}</strong>
            <span>${Math.round(item.analysis.calories)} kcal${
          serving != null
            ? ` · ${escapeHtml(serving)}`
            : item.analysis.quantityG != null
              ? ` · ${Math.round(item.analysis.quantityG)} g`
              : ""
        }</span>
          </div>
          <button type="button" class="btn btn--ghost btn--sm" data-remove-item aria-label="${escapeAttr(t("progressive_meal.remove"))}">×</button>
        </div>`;
      })
      .join("");

    let openAddFoodOnClose = false;
    const sheet = openSheet({
      title: t("progressive_meal.title"),
      body: `
        <form class="entry-form" id="progressive-meal-form">
          <div class="field">
            <label for="pm-name">${escapeHtml(t("progressive_meal.name_placeholder"))}</label>
            <input id="pm-name" name="name" type="text" value="${escapeAttr(draft.name)}" placeholder="${escapeAttr(t("progressive_meal.name_placeholder"))}" />
          </div>
          <p class="add-food-hint">${escapeHtml(t("progressive_meal.ingredient_count", { count: String(draft.items.length) }))}</p>
          <div class="field">
            <label for="pm-meal">${escapeHtml(t("progressive_meal.meal_label"))}</label>
            <select id="pm-meal" name="mealType">${mealOptions}</select>
          </div>
          <div class="progressive-meal-list">${rows}</div>
          <div class="progressive-meal-totals">
            <p class="add-food-hint">${escapeHtml(t("progressive_meal.totals"))}</p>
            <p><strong>${Math.round(totals.calories)} kcal</strong></p>
            <p>${Math.round(totals.proteinG)}P · ${Math.round(totals.carbsG)}C · ${Math.round(totals.fatG)}F</p>
          </div>
          <div class="btn-row">
            <button type="button" class="btn btn--primary" data-pm-log>${escapeHtml(t("progressive_meal.log"))}</button>
            <button type="button" class="btn btn--ghost" data-pm-add>${escapeHtml(t("progressive_meal.add_another"))}</button>
          </div>
          <button type="button" class="btn btn--danger" data-pm-discard>${escapeHtml(t("progressive_meal.discard"))}</button>
        </form>
      `,
      onClose: () => {
        if (openAddFoodOnClose) {
          openAddFoodOnClose = false;
          void this.openAddFoodSheet(appPrefs);
        }
      },
    });

    const syncMeta = () => {
      const nameInput = /** @type {HTMLInputElement|null} */ (sheet.body.querySelector("#pm-name"));
      const mealSel = /** @type {HTMLSelectElement|null} */ (sheet.body.querySelector("#pm-meal"));
      updateProgressiveMealMeta(nameInput?.value ?? "", mealSel?.value || draft.mealType);
    };

    sheet.body.querySelector("#pm-name")?.addEventListener("change", syncMeta);
    sheet.body.querySelector("#pm-meal")?.addEventListener("change", syncMeta);

    sheet.body.querySelectorAll("[data-remove-item]").forEach((btn) => {
      btn.addEventListener("click", () => {
        const row = btn.closest("[data-item-id]");
        const id = row?.getAttribute("data-item-id");
        if (!id) return;
        removeProgressiveMealItem(id);
        sheet.close();
        if (hasProgressiveMealItems()) {
          setShowProgressiveMealSheet(true);
          this.openProgressiveMealSheet(appPrefs);
        } else {
          this.render();
        }
      });
    });

    sheet.body.querySelector("[data-pm-log]")?.addEventListener("click", async () => {
      syncMeta();
      const current = getProgressiveMeal();
      if (!current || current.items.length === 0) return;
      const now = new Date();
      const time = `${String(now.getHours()).padStart(2, "0")}:${String(now.getMinutes()).padStart(2, "0")}`;
      const built = progressiveMealToFoodEntries(current, { date: this.date, time });
      for (const entry of built) {
        await foodEntries.put(entry);
      }
      discardProgressiveMeal();
      sheet.close();
      this.render();
    });

    sheet.body.querySelector("[data-pm-add]")?.addEventListener("click", () => {
      syncMeta();
      openAddFoodOnClose = true;
      sheet.close();
    });

    sheet.body.querySelector("[data-pm-discard]")?.addEventListener("click", () => {
      discardProgressiveMeal();
      sheet.close();
      this.render();
    });
  }

  async startVoiceNote() {
    const { openVoiceCaptureSheet } = await import("../lib/ui/voice-capture.js");
    const sheet = await openVoiceCaptureSheet({
      onResult: (text) => {
        location.hash = `#/analyze?date=${this.date}&mode=note&prefill=${encodeURIComponent(text)}`;
      },
      onCancel: () => {},
    });
    if (!sheet) {
      showToast(t("diary.voice_unsupported"));
    }
  }

  /**
   * @param {ReturnType<typeof openSheet>} parentSheet
   * @param {Awaited<ReturnType<typeof prefs.load>>} appPrefs
   * @param {"RECENTS"|"FREQUENT"|"FAVORITES"|"RECIPES"} [initialSegment]
   */
  async openSavedMealsSheet(parentSheet, appPrefs, initialSegment) {
    let segment = initialSegment || appPrefs.lastSavedMealsSegment || "RECENTS";
    const sortPref = appPrefs.lastSavedMealsSort;
    /** @type {"recent"|"name"|"size"} */
    let recentsSort = sortPref === "name" || sortPref === "size" ? sortPref : "recent";
    let recentsQuery = "";
    /** @type {import('../lib/chompass-core/models.js').FoodEntry[] | null} */
    let recentsCache = null;
    const sheet = openSheet({
      title: t("diary.saved_meals"),
      body: `<div class="saved-meals" data-saved-root><p class="empty-state">${t("diary.loading")}</p></div>`,
    });

    const tabsHtml = () => `
        <div class="saved-tabs" role="tablist">
          ${["RECENTS", "FREQUENT", "FAVORITES", "RECIPES"]
            .map(
              (s) =>
                `<button type="button" role="tab" data-seg="${s}" aria-selected="${segment === s}">${t(SEGMENT_LABELS[s] ?? s)}</button>`,
            )
            .join("")}
        </div>`;

    const recentsMapped = (list) =>
      sortHistoryTemplates(filterHistoryTemplates(list, recentsQuery), recentsSort).map((e) => ({
        label: e.name,
        meta: `${formatNumber(Math.round(e.calories))} kcal · ${Math.round(e.proteinG)}P / ${Math.round(e.carbsG)}C / ${Math.round(e.fatG)}F`,
        entry: e,
      }));

    const foodRowsHtml = (rows, listAttr = "") =>
      rows.length
        ? `<div class="recents-list sheet-recents"${listAttr}>
                ${rows
                  .map(
                    (r) => `
                  <div class="saved-row">
                    <button type="button" class="saved-row__main" data-prefill='${escapeAttr(JSON.stringify(toPrefill(r.entry)))}'>
                      <strong>${escapeHtml(r.label)}</strong><br/>
                      <span class="recents-meta">${escapeHtml(r.meta)}</span>
                    </button>
                    ${
                      r.favEditId
                        ? `<button type="button" class="saved-row__edit" data-edit-favorite="${escapeAttr(r.favEditId)}" aria-label="${t("diary.edit_saved_food")}" title="${t("diary.edit_saved_food")}">✎</button>`
                        : ""
                    }
                  </div>`,
                  )
                  .join("")}
              </div>`
        : `<p class="empty-state" style="padding:1rem 0;"${listAttr}>${t("diary.nothing")}</p>`;

    const bindPrefills = (root) => {
      root.querySelectorAll("[data-prefill]").forEach((btn) => {
        btn.addEventListener("click", () => {
          const raw = btn.getAttribute("data-prefill");
          if (!raw) return;
          const prefill = JSON.parse(raw);
          sheet.close();
          parentSheet.close();
          location.hash = `#/entry/new?date=${this.date}&fromSaved=1&prefill=${encodeURIComponent(JSON.stringify(prefill))}`;
        });
      });
    };

    const paintRecentsList = (root) => {
      const html = foodRowsHtml(recentsMapped(recentsCache ?? []), ` data-saved-list`);
      const existing = root.querySelector("[data-saved-list]");
      if (existing) existing.outerHTML = html;
      bindPrefills(root);
    };

    const renderTab = async () => {
      const root = sheet.body.querySelector("[data-saved-root]");
      if (!root) return;
      /** @type {Array<{label: string, meta: string, entry: import('../lib/chompass-core/models.js').FoodEntry, count?: number, favEditId?: string}>} */
      let rows = [];
      if (segment === "RECENTS") {
        if (!recentsCache) recentsCache = await historyTemplates();
        rows = recentsMapped(recentsCache);
      } else if (segment === "FREQUENT") {
        rows = (await frequentFoodGroups(90)).map((g) => ({
          label: g.template.name,
          meta: `${g.count}× · ${formatNumber(Math.round(g.template.calories))} kcal`,
          entry: g.template,
          count: g.count,
        }));
      } else if (segment === "FAVORITES") {
        rows = (await listFavorites()).map((e) => ({
          label: e.name,
          meta: `${formatNumber(Math.round(e.calories))} kcal · ${Math.round(e.proteinG)}P / ${Math.round(e.carbsG)}C / ${Math.round(e.fatG)}F`,
          entry: e,
          favEditId: e.id,
        }));
      } else {
        const recipeList = await listRecipes();
        root.innerHTML = `
          ${tabsHtml()}
          ${
            recipeList.length
              ? `<div class="recents-list sheet-recents">
                  ${recipeList
                    .map(
                      (r) => `
                    <button type="button" data-recipe-id="${r.id}">
                      <strong>${escapeHtml(r.name)}</strong><br/>
                      <span class="recents-meta">${t("diary.recipe_ingredients_kcal", { count: r.ingredients.length, kcal: r.ingredients.reduce((s, i) => s + Math.round(i.baseCalories * (i.quantityScale ?? 1)), 0) })}</span>
                    </button>`,
                    )
                    .join("")}
                </div>`
              : `<p class="empty-state" style="padding:1rem 0;">${t("diary.no_recipes")}</p>`
          }`;
        root.querySelectorAll("[data-seg]").forEach((btn) => {
          btn.addEventListener("click", async () => {
            segment = /** @type {any} */ (btn.getAttribute("data-seg"));
            await prefs.save({ lastSavedMealsSegment: segment });
            renderTab();
          });
        });
        root.querySelectorAll("[data-recipe-id]").forEach((btn) => {
          btn.addEventListener("click", async () => {
            const id = btn.getAttribute("data-recipe-id");
            const all = await listRecipes();
            const recipe = all.find((r) => r.id === id);
            if (!recipe) return;
            sheet.close();
            parentSheet.close();
            await logRecipe(recipe, this.date, appPrefs);
            this.render();
          });
        });
        return;
      }

      const recentsChrome =
        segment === "RECENTS"
          ? `<input type="search" class="saved-meals-search" data-saved-search placeholder="${t("saved_meals.search_placeholder")}" value="${escapeAttr(recentsQuery)}" />
        <div class="saved-sort" role="group">
          ${["recent", "name", "size"]
            .map(
              (s) =>
                `<button type="button" data-sort="${s}" aria-pressed="${recentsSort === s}">${t(`saved_meals.sort_${s}`)}</button>`,
            )
            .join("")}
        </div>`
          : "";

      root.innerHTML = `
        ${tabsHtml()}
        ${recentsChrome}
        ${foodRowsHtml(rows, segment === "RECENTS" ? ` data-saved-list` : "")}`;

      root.querySelectorAll("[data-seg]").forEach((btn) => {
        btn.addEventListener("click", async () => {
          segment = /** @type {any} */ (btn.getAttribute("data-seg"));
          await prefs.save({ lastSavedMealsSegment: segment });
          renderTab();
        });
      });
      bindPrefills(root);
      root.querySelector("[data-saved-search]")?.addEventListener("input", (ev) => {
        recentsQuery = /** @type {HTMLInputElement} */ (ev.target).value;
        paintRecentsList(root);
      });
      root.querySelectorAll("[data-sort]").forEach((btn) => {
        btn.addEventListener("click", async () => {
          const next = btn.getAttribute("data-sort");
          if (next !== "recent" && next !== "name" && next !== "size") return;
          recentsSort = next;
          await prefs.save({ lastSavedMealsSort: recentsSort });
          root.querySelectorAll("[data-sort]").forEach((el) => {
            el.setAttribute("aria-pressed", el.getAttribute("data-sort") === recentsSort ? "true" : "false");
          });
          paintRecentsList(root);
        });
      });
      // Codeberg #66: edit the saved food itself (library semantics) instead
      // of only prefilling a new diary row.
      root.querySelectorAll("[data-edit-favorite]").forEach((btn) => {
        btn.addEventListener("click", () => {
          const id = btn.getAttribute("data-edit-favorite");
          if (!id) return;
          sheet.close();
          parentSheet.close();
          location.hash = `#/entry/favorite/${id}`;
        });
      });
    };

    await renderTab();
  }

  /** @param {ReturnType<typeof openSheet>} parentSheet */
  async openCopyFromDaySheet(parentSheet) {
    const all = await foodEntries.all();
    const dates = [...new Set(all.map((e) => e.date))].filter((d) => d !== this.date).sort().reverse().slice(0, 60);
    const sheet = openSheet({
      title: t("add_food.copy"),
      body: dates.length
        ? `<div class="recents-list sheet-recents">
            ${dates
              .map(
                (d) =>
                  `<button type="button" data-copy-date="${d}"><strong>${escapeHtml(d)}</strong><br/><span class="recents-meta">${t("diary.tap_to_choose")}</span></button>`
              )
              .join("")}
          </div>`
        : `<p class="empty-state" style="padding:1rem 0;">${t("diary.no_other_days")}</p>`,
    });
    sheet.body.querySelectorAll("[data-copy-date]").forEach((btn) => {
      btn.addEventListener("click", async () => {
        const date = btn.getAttribute("data-copy-date");
        if (!date) return;
        const dayEntries = await foodEntries.byDate(date);
        sheet.close();
        this.openCopySelectSheet(parentSheet, dayEntries);
      });
    });
  }

  /**
   * @param {ReturnType<typeof openSheet>} parentSheet
   * @param {import('../lib/chompass-core/models.js').FoodEntry[]} dayEntries
   */
  openCopySelectSheet(parentSheet, dayEntries) {
    const sheet = openSheet({
      title: t("diary.select_foods"),
      body: `
        <div class="copy-select">
          ${dayEntries
            .map(
              (e) => `
            <label class="copy-select__row">
              <input type="checkbox" data-copy-id="${e.id}" checked />
              <span><strong>${escapeHtml(e.name)}</strong><br/><span class="recents-meta">${Math.round(e.calories)} kcal · ${mealLabel(e.mealType)}</span></span>
            </label>`
            )
            .join("")}
        </div>
        <button type="button" class="btn btn--primary" data-copy-confirm>${t("diary.copy_to_today")}</button>`,
    });
    sheet.body.querySelector("[data-copy-confirm]")?.addEventListener("click", async () => {
      const ids = [...sheet.body.querySelectorAll("[data-copy-id]:checked")].map((el) => el.getAttribute("data-copy-id"));
      const meal = guessMealTypeFromPrefs(await prefs.load());
      for (const e of dayEntries.filter((x) => ids.includes(x.id))) {
        await foodEntries.put(duplicatedForLogging(e, this.date, undefined, meal));
      }
      sheet.close();
      parentSheet.close();
      this.render();
    });
  }

  /** @param {import('../lib/chompass-core/models.js').FoodEntry[]} entries */
  async shareEntries(entries) {
    const text = mealShareText(entries);
    try {
      if (navigator.share) {
        await navigator.share({ text, title: t("diary.share_title") });
        return;
      }
    } catch {
      /* fall through */
    }
    try {
      await navigator.clipboard.writeText(text);
      showToast(t("diary.share_copied"));
    } catch {
      showToast(t("diary.share_failed"));
    }
  }

  async customWater() {
    const raw = await openInput({
      title: t("diary.add_water"),
      label: t("diary.amount"),
      value: "200",
      unit: "ml",
      inputMode: "numeric",
      type: "number",
      confirmLabel: t("diary.add"),
    });
    if (raw == null) return;
    const ml = Number(raw);
    if (ml > 0) await this.addWater(ml);
  }

  /**
   * @param {{id: string, date: string, amountMl: number}[]} waterLogs
   */
  async undoLastWater(waterLogs) {
    if (!waterLogs.length) return;
    const last = waterLogs[waterLogs.length - 1];
    await water.delete(last.id);
    showToast(t("diary.removed_ml", { amount: last.amountMl }));
    this.render();
  }

  /**
   * Day note (Codeberg #58a): save the textarea for the selected day. A blank
   * note clears the day (mirrors Android NotesRepository.setNote). The id is
   * deterministic from the date so merges collapse to last-write-wins.
   */
  async saveDailyNote() {
    const input = /** @type {HTMLTextAreaElement|null} */ (this.querySelector("[data-note-input]"));
    const text = (input?.value ?? "").trim();
    const id = dailyNoteIdFor(this.date);
    if (!text) {
      await dailyNotes.delete(id);
    } else {
      await dailyNotes.put({ id, date: this.date, text: text.slice(0, 1000) });
    }
    this._noteEditing = false;
    this.render();
  }

  /** Removes the selected day's note. */
  async clearDailyNote() {
    await dailyNotes.delete(dailyNoteIdFor(this.date));
    this._noteEditing = false;
    this.render();
  }

  /**
   * Day or meal-slot nutrition detail — Android NutritionDetailSheet parity.
   * @param {import('../lib/chompass-core/models.js').FoodEntry[]} entries
   * @param {ReturnType<typeof dailyTargets>|null} targets
   * @param {import('../lib/db.js').OptionalNutrientGoals} optionalGoals
   * @param {string} [title]
   */
  openNutritionDetail(entries, targets, optionalGoals, title) {
    const fmt = (v) => (v === 0 ? "—" : v.toFixed(1));
    const cal = entries.reduce((s, e) => s + e.calories, 0);
    const macroRows = [
      [t("diary.calories"), cal, targets?.calories ?? 0, "kcal"],
      [t("onboarding.plan.protein"), sumNutrient(entries, "proteinG"), targets?.proteinG ?? 0, "g"],
      [t("onboarding.plan.carbs"), sumNutrient(entries, "carbsG"), targets?.carbsG ?? 0, "g"],
      [t("onboarding.plan.fat"), sumNutrient(entries, "fatG"), targets?.fatG ?? 0, "g"],
    ];
    const microRows = NUTRITION_DETAIL_MICROS.map((def) => {
      const value = sumNutrient(entries, def.key);
      const goal = def.displayOnly ? null : nutrientGoal(def.key, targets, optionalGoals);
      return { def, value, goal };
    });
    /** Constituent rows across the scoped entries (Codeberg #86). */
    const constituents = entries.flatMap((e) => e.constituents ?? []);
    /** Same expandable micros+% block as the entry-form constituent rows. */
    const constituentMicrosHtml = (c) => {
      // #86 Android parity: normalize (string/negative/oversized) at render and
      // always print a present value, including 0.0 — the meal-level em-dash
      // for zero applies to meal totals only, not ingredient rows.
      const present = NUTRITION_DETAIL_MICROS
        .map((def) => ({ def, value: microOrNull(/** @type {Record<string, unknown>} */ (c)[def.key]) }))
        .filter((m) => m.value != null);
      if (!present.length) return "";
      const items = present
        .map(({ def, value }) => {
          const goal = nutrientGoal(def.key, targets, optionalGoals);
          const percent = nutritionGoalPercent(/** @type {number} */ (value), goal);
          const pct = percent != null ? ` (${percent}%)` : "";
          return `
            <li class="nutrition-detail__row">
              <span class="nutrition-detail__label">${def.label}</span>
              <span class="nutrition-detail__value">${/** @type {number} */ (value).toFixed(1)} ${def.unit}</span>
              <span class="nutrition-detail__goal">${pct}</span>
            </li>`;
        })
        .join("");
      return `
        <details class="nutrition-detail__micros">
          <summary>${t("diary.detailed_nutrition")}</summary>
          <ul class="nutrition-detail__list">${items}</ul>
        </details>`;
    };

    const sheet = openSheet({
      title: title || t("diary.nutrition_detail"),
      body: `
        <section class="nutrition-detail">
          <h3 class="nutrition-detail__heading">${t("diary.macros")}</h3>
          <ul class="nutrition-detail__list">
            ${macroRows
              .map(([label, value, goal, unit]) => {
                const percent = nutritionGoalPercent(value, goal);
                return `
              <li class="nutrition-detail__row">
                <span class="nutrition-detail__label">${label}</span>
                <span class="nutrition-detail__value">${Math.round(/** @type {number} */ (value))} ${unit}</span>
                <span class="nutrition-detail__goal">${nutritionGoalText(goal, percent)}</span>
              </li>`;
              })
              .join("")}
          </ul>
          <h3 class="nutrition-detail__heading">${t("diary.detailed_nutrition")}</h3>
          <ul class="nutrition-detail__list">
            ${microRows
              .map(({ def, value, goal }) => {
                const percent = goal != null && goal > 0 ? nutritionGoalPercent(value, goal) : null;
                const goalText = goal != null && goal > 0 ? nutritionGoalText(goal, percent) : "";
                return `
              <li class="nutrition-detail__row">
                <span class="nutrition-detail__label">${def.label}</span>
                <span class="nutrition-detail__value">${fmt(value)} ${def.unit}</span>
                <span class="nutrition-detail__goal">${goalText}</span>
              </li>`;
              })
              .join("")}
          </ul>
          ${
            constituents.length
              ? `
          <h3 class="nutrition-detail__heading">${t("entry.constituents.title")}</h3>
          <p class="nutrition-detail__note">${t("entry.constituents.estimates_note")}</p>
          <ul class="nutrition-detail__list">
            ${constituents
              .map(
                (c) => `
              <li class="nutrition-detail__row nutrition-detail__row--stacked">
                <span class="nutrition-detail__label">${escapeHtml(c.name)}</span>
                <span class="nutrition-detail__value">${escapeHtml(
                  t("entry.constituents.macros", {
                    calories: Math.round(c.calories),
                    protein: formatQuantity(c.proteinG),
                    carbs: formatQuantity(c.carbsG),
                    fat: formatQuantity(c.fatG),
                  }),
                )}</span>
                ${constituentMicrosHtml(c)}
              </li>`,
              )
              .join("")}
          </ul>`
              : ""
          }
        </section>`,
    });
    void sheet;
  }

  async addWater(amountMl) {
    await water.put({ id: crypto.randomUUID(), date: this.date, amountMl });
    this.render();
  }

  // -- Intermittent fasting timer (local-only, mirrors the Android tracker) --

  /** Starts a new fast (no-op when one is already running). */
  async fastingStart() {
    const p = await prefs.load();
    if (p.fastingStartedAt != null) return;
    await prefs.save({ fastingStartedAt: Date.now(), fastingGoalNotified: false, fastingAutoStarted: false });
    this.render();
  }

  /** Ends the running fast, recording it as the last completed fast. */
  async fastingStop() {
    const p = await prefs.load();
    if (p.fastingStartedAt == null) return;
    await prefs.save({
      fastingStartedAt: null,
      fastingLastEndedAt: Date.now(),
      fastingLastFastStartedAt: p.fastingStartedAt,
      fastingGoalNotified: false,
      fastingAutoStarted: false,
    });
    this.render();
  }

  /**
   * Auto-cycle catch-up (PWA has no background alarms, so the minute ticker
   * drives the transitions while the page is open — same heal as Android's
   * FastingAutoPlanner). Returns true when a transition ran.
   */
  async fastingHeal() {
    const p = await prefs.load();
    if (p.showFasting !== true || p.fastingAutoWindows !== true) return false;
    const goal = p.fastingGoalHours ?? 0;
    // The cycle needs a goal length: without it an auto fast could never end.
    if (goal <= 0) return false;
    const now = Date.now();
    if (p.fastingStartedAt != null) {
      if (goal > 0 && now - p.fastingStartedAt >= goal * 3_600_000) {
        await prefs.save({
          fastingStartedAt: null,
          fastingLastEndedAt: p.fastingStartedAt + goal * 3_600_000,
          fastingLastFastStartedAt: p.fastingStartedAt,
          fastingGoalNotified: false,
          fastingAutoStarted: false,
        });
        return true;
      }
      return false;
    }
    // Schedule catch-up (mirrors Android heal): the auto-start was missed
    // when the last stop happened before the most recent start time and no
    // fast is running; start at the scheduled instant to stay on the clock.
    // A user who stopped eating *after* the start time is still in their
    // eating phase and waits for the next one; a fresh user (no history) is
    // opted into the self-driving cycle by enabling Auto fast windows, so the
    // fast picks up from the scheduled start too.
    const hour = p.fastingStartHour ?? 20;
    const minute = p.fastingStartMinute ?? 0;
    const todayT = nextFastStartMillis(hour, minute, now);
    const scheduled = now >= todayT ? todayT : todayT - 24 * 60 * 60_000;
    const lastEnded = p.fastingLastEndedAt;
    if ((lastEnded == null || lastEnded < scheduled) && now >= scheduled) {
      await prefs.save({ fastingStartedAt: scheduled, fastingGoalNotified: false, fastingAutoStarted: true });
      return true;
    }
    return false;
  }

  /** @param {string} kind */
  async addNicotine(kind) {
    await nicotine.put({ id: crypto.randomUUID(), date: this.date, kind, count: 1, mg: null });
    this.render();
  }

  async customNicotine() {
    const raw = await openInput({
      title: t("diary.add_nicotine"),
      label: t("diary.count"),
      value: "1",
      unit: "",
      inputMode: "numeric",
      type: "number",
      confirmLabel: t("diary.add"),
    });
    if (raw == null) return;
    const count = Number(raw);
    if (count > 0) {
      await nicotine.put({ id: crypto.randomUUID(), date: this.date, kind: "other", count, mg: null });
      this.render();
    }
  }

  /**
   * @param {{id: string, date: string, count?: number}[]} nicotineLogs
   */
  async undoLastNicotine(nicotineLogs) {
    if (!nicotineLogs.length) return;
    const last = nicotineLogs[nicotineLogs.length - 1];
    await nicotine.delete(last.id);
    showToast(t("diary.removed_count", { count: last.count ?? 1 }));
    this.render();
  }

  /** @param {string} kind */
  async addCaffeine(kind) {
    const mg = CAFFEINE_KIND_MG[kind] ?? 0;
    if (mg <= 0) return;
    await caffeine.put({ id: crypto.randomUUID(), date: this.date, kind, mg });
    this.render();
  }

  async customCaffeine() {
    const raw = await openInput({
      title: t("diary.add_caffeine"),
      label: t("diary.caffeine_mg"),
      value: "95",
      unit: "mg",
      inputMode: "numeric",
      type: "number",
      confirmLabel: t("diary.add"),
    });
    if (raw == null) return;
    const mg = Number(raw);
    if (mg > 0) {
      await caffeine.put({ id: crypto.randomUUID(), date: this.date, kind: "other", mg });
      this.render();
    }
  }

  /**
   * @param {{id: string, date: string, mg?: number}[]} caffeineLogs
   */
  async undoLastCaffeine(caffeineLogs) {
    if (!caffeineLogs.length) return;
    const last = caffeineLogs[caffeineLogs.length - 1];
    await caffeine.delete(last.id);
    showToast(t("diary.removed_mg", { amount: last.mg ?? 0 }));
    this.render();
  }

  /** @param {import('../lib/chompass-core/models.js').FoodEntry} entry */
  async duplicateEntry(entry) {
    await foodEntries.put(duplicatedForLogging(entry, this.date));
    this.render();
  }

  async deleteEntry(entry) {
    this._undoEntry = entry;
    await foodEntries.delete(entry.id);
    this.render();
    showUndoToast(t("toast.deleted"), async () => {
      if (this._undoEntry) await foodEntries.put(this._undoEntry);
      this._undoEntry = null;
      this.render();
    });
  }

  openEntryForm(entry) {
    location.hash = entry ? `#/entry/${entry.id}?date=${this.date}` : `#/entry/new?date=${this.date}`;
  }
}

customElements.define("diary-view", DiaryView);

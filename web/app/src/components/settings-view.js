// @ts-check
import {
  profile as profileStore,
  foodEntries,
  weights,
  bodyFat,
  measurements,
  dailyNotes,
  prefs,
  goalJournal,
  clearAllUserData,
} from "../lib/db.js";
import { dailyTargets, bmr, tdee, safetyFloorKcal, CALORIE_PARSER_CEILING_KCAL } from "../lib/chompass-core/formulas.js";
import { computeWeightForecast } from "../lib/chompass-core/forecast.js";
import { exportDiary, importDiary } from "../lib/chompass-core/diary-format.js";
import { exportBodyMetrics, importBodyMetrics } from "../lib/chompass-core/body-metrics-format.js";
import {
  exportDiaryMarkdown,
  exportDiaryCsv,
  exportBodyMetricsCsv,
  filterDiaryRange,
} from "../lib/chompass-core/export-text.js";
import { PROVIDERS, modelSelectOptionsHtml, resolveProviderModel, visionModelOptionsHtml } from "../lib/ai/providers.js";
import { saveProviderKey, deleteProviderKey, listConfiguredProviders, loadProviderKey } from "../lib/ai/key-storage.js";
import { validateGeminiApiKey } from "../lib/ai/validate-key.js";
import {
  calculateGoalsWithAi,
  applyingFormulaGoals,
  applyingAiGoals,
  locksFromFilledCustoms,
  resolveGoalsAiClient,
} from "../lib/ai/calculate-goals.js";
import { openConfirm } from "../lib/ui/dialog.js";
import { subpageBar, bindSubpageBack } from "../lib/ui/subpage.js";
import { downloadJson, downloadText } from "../lib/download.js";
import {
  buildLocalSyncDocument,
  importAndMergeSyncDocument,
  loadWebDavSettings,
  saveWebDavSettings,
  syncWebDavNow,
} from "../lib/sync.js";
import { minutesToTimeInput, timeInputToMinutes } from "../lib/meal-schedule.js";
import {
  HOME_TOP_NUTRIENTS,
  FOOD_LOG_CHIP_KEYS,
  DEFAULT_OPTIONAL_NUTRIENT_GOALS,
  DEFAULT_NUTRIENT_CARD_COUNT,
  normalizeHomeTopNutrients,
  normalizeFoodLogChips,
  nutrientDef,
  mergeOptionalGoals,
  MAX_CUSTOM_GOAL_BY_KEY,
} from "../lib/home-nutrients.js";
import { LOCALES, t, formatNumber } from "../lib/i18n/index.js";
import {
  setPlanEnabled,
  upsertProfile,
  deleteProfile,
  reorderProfiles,
  setPlanMode,
  setDefaultProfile,
  setWeekdayProfile,
  setCyclePattern,
  restartCycle,
  setDayAssignment,
  pausedForKeto,
  MIN_PROFILES,
  MAX_PROFILES,
} from "../lib/chompass-core/macro-plan-edit.js";
import { resolveDay, resolveDayJournaled } from "../lib/chompass-core/macro-plan.js";
import { applyingAiGoalsToPlan } from "../lib/chompass-core/macro-plan-edit.js";
import { refreshGoalJournal } from "../lib/goal-journal-store.js";

function escapeHtmlSettings(s) {
  return String(s ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]);
}

/** Local calendar day yyyy-MM-dd. */
function localTodayIso() {
  const d = new Date();
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${y}-${m}-${day}`;
}

function shiftIso(iso, days) {
  const d = new Date(`${iso}T00:00:00`);
  d.setDate(d.getDate() + days);
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${y}-${m}-${day}`;
}

const WEEKDAY_ORDER = ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"];

/** @param {string} name DayOfWeek name */
function weekdayLabel(name) {
  return name.charAt(0) + name.slice(1).toLowerCase();
}

/** @param {import('../lib/chompass-core/macro-plan.js').MacroPlan|null} plan */
function dayTypesSummary(plan) {
  if (!plan || !plan.enabled) return t("day_types.summary_off");
  const mode =
    plan.mode === "WEEKDAYS"
      ? t("day_types.mode_weekdays")
      : plan.mode === "CYCLE"
        ? t("day_types.mode_cycle")
        : t("day_types.mode_manual");
  return t("day_types.summary_format", { count: String(plan.profiles?.length ?? 0), mode });
}

const ACTIVITY_LEVELS = [
  { id: "sedentary", label: "Sedentary" },
  { id: "light", label: "Light" },
  { id: "moderate", label: "Moderate" },
  { id: "active", label: "Active" },
  { id: "very_active", label: "Very active" },
  { id: "extra_active", label: "Extra active" },
];
const ACCENTS = ["system", "teal", "blue", "green", "purple", "pink", "orange", "indigo", "neutral"];

const SPEECH_LANGS = [
  { id: "", labelKey: "settings.speech.browser_default" },
  { id: "en-US", label: "English (US)" },
  { id: "en-GB", label: "English (UK)" },
  { id: "de-DE", label: "Deutsch" },
  { id: "fr-FR", label: "Français" },
  { id: "es-ES", label: "Español" },
  { id: "it-IT", label: "Italiano" },
  { id: "nl-NL", label: "Nederlands" },
  { id: "pt-BR", label: "Português (BR)" },
  { id: "hi-IN", label: "हिन्दी" },
  { id: "ja-JP", label: "日本語" },
  { id: "zh-CN", label: "简体中文" },
  { id: "ko-KR", label: "한국어" },
  { id: "sv-SE", label: "Svenska" },
];
/** @typedef {{ id: string, label?: string, labelKey?: string }} SpeechLangOption */

const OPTIONAL_GOAL_FIELDS = Object.keys(DEFAULT_OPTIONAL_NUTRIENT_GOALS).map((key) => {
  const def = nutrientDef(key);
  return /** @type {[string, string]} */ ([key, def ? `${def.label} (${def.unit})` : key]);
});

/** Parent hash for nested settings pages (mirrors Android hub groups). */
/** "HH:MM" (or "HH:MM:SS") from an <input type=time> → pref patch, or {} when empty. */
function parseFastStartTime(raw) {
  const m = /^(\d{1,2}):(\d{2})/.exec(String(raw ?? ""));
  if (!m) return {};
  return {
    fastingStartHour: Math.min(23, Math.max(0, Number(m[1]))),
    fastingStartMinute: Math.min(59, Math.max(0, Number(m[2]))),
  };
}

const SETTINGS_PARENT = {
  personal: "#/settings",
  profile: "#/settings",
  goals: "#/settings",
  nutrients: "#/settings?section=goals",
  daytypes: "#/settings?section=goals",
  app: "#/settings",
  units: "#/settings?section=app",
  home: "#/settings?section=app",
  language: "#/settings?section=app",
  install: "#/settings?section=app",
  ai: "#/settings",
  speech: "#/settings?section=ai",
  data: "#/settings",
  sync: "#/settings?section=data",
  about: "#/settings",
};

/** Material icons for the hub rows (Android Icons.Outlined set). */
const ICONS = {
  person: `<svg viewBox="0 0 24 24" aria-hidden="true"><path fill="currentColor" d="M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z"/></svg>`,
  equalizer: `<svg viewBox="0 0 24 24" aria-hidden="true"><path fill="currentColor" d="M10 20h4V4h-4v16zm-6 0h4v-8H4v8zM16 9v11h4V9h-4z"/></svg>`,
  settings: `<svg viewBox="0 0 24 24" aria-hidden="true"><path fill="currentColor" d="M19.14 12.94c.04-.3.06-.61.06-.94 0-.32-.02-.64-.07-.94l2.03-1.58c.18-.14.23-.41.12-.61l-1.92-3.32c-.12-.22-.37-.29-.59-.22l-2.39.96c-.5-.38-1.03-.7-1.62-.94l-.36-2.54c-.04-.24-.24-.41-.48-.41h-3.84c-.24 0-.43.17-.47.41l-.36 2.54c-.59.24-1.13.57-1.62.94l-2.39-.96c-.22-.08-.47 0-.59.22L2.74 8.87c-.12.21-.08.47.12.61l2.03 1.58c-.05.3-.09.63-.09.94s.02.64.07.94l-2.03 1.58c-.18.14-.23.41-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17.47-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32c.12-.22.07-.47-.12-.61l-2.01-1.58zM12 15.6c-1.98 0-3.6-1.62-3.6-3.6s1.62-3.6 3.6-3.6 3.6 1.62 3.6 3.6-1.62 3.6-3.6 3.6z"/></svg>`,
  smartToy: `<svg viewBox="0 0 24 24" aria-hidden="true"><path fill="currentColor" d="M20 9V7c0-1.1-.9-2-2-2h-3c0-1.66-1.34-3-3-3S9 3.34 9 5H6c-1.1 0-2 .9-2 2v2c-1.66 0-3 1.34-3 3s1.34 3 3 3v4c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2v-4c1.66 0 3-1.34 3-3s-1.34-3-3-3zm-2 10H6V7h12v12zm-9-6c-.83 0-1.5-.67-1.5-1.5S8.17 10 9 10s1.5.67 1.5 1.5S9.83 13 9 13zm7.5-1.5c0 .83-.67 1.5-1.5 1.5s-1.5-.67-1.5-1.5.67-1.5 1.5-1.5 1.5.67 1.5 1.5zM8 15h8v2H8z"/></svg>`,
  folderOpen: `<svg viewBox="0 0 24 24" aria-hidden="true"><path fill="currentColor" d="M20 6h-8l-2-2H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2zm0 12H4V8h16v10z"/></svg>`,
  info: `<svg viewBox="0 0 24 24" aria-hidden="true"><path fill="currentColor" d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-6h2v6zm0-8h-2V7h2v2z"/></svg>`,
  chevron: `<svg viewBox="0 0 24 24" aria-hidden="true"><path fill="currentColor" d="M10 6 8.59 7.41 13.17 12l-4.58 4.59L10 18l6-6z"/></svg>`,
};

export class SettingsView extends HTMLElement {
  connectedCallback() {
    const params = new URLSearchParams(location.hash.split("?")[1] ?? "");
    this.section = params.get("section") || "hub";
    // Legacy alias: profile → personal (Android “Personal Info” group).
    if (this.section === "profile") this.section = "personal";
    this.render();
  }

  /** Android SettingsHubRow: icon bubble + label/summary + chevron. */
  hubRow(section, icon) {
    const key = `settings.hub.${section}`;
    const hintKey = `settings.hub.${section}_hint`;
    return `
      <a class="settings-hub__row" href="#/settings?section=${section}">
        <span class="settings-hub__icon">${icon}</span>
        <span class="settings-hub__text">
          <strong>${t(key)}</strong>
          <span>${t(hintKey)}</span>
        </span>
        <span class="settings-hub__chevron">${ICONS.chevron}</span>
      </a>`;
  }

  async render() {
    if (this.section === "hub") {
      this.innerHTML = `
        <h1 class="screen-title">${t("settings.title")}</h1>
        <div class="settings-hub">
          ${this.hubRow("personal", ICONS.person)}
          ${this.hubRow("goals", ICONS.equalizer)}
          ${this.hubRow("app", ICONS.settings)}
          ${this.hubRow("ai", ICONS.smartToy)}
          ${this.hubRow("data", ICONS.folderOpen)}
        </div>
        <div class="settings-hub">
          ${this.hubRow("about", ICONS.info)}
        </div>
        <p class="settings-android-note">Health Connect, notifications, widgets, and on-device LLM are Android-only.</p>`;
      return;
    }

    if (this.section === "personal") {
      await this.renderProfile();
      return;
    }
    if (this.section === "goals") {
      await this.renderGoals();
      return;
    }
    if (this.section === "nutrients") {
      await this.renderNutrients();
      return;
    }
    if (this.section === "daytypes") {
      await this.renderDayTypes();
      return;
    }
    if (this.section === "app") {
      this.renderApp();
      return;
    }
    if (this.section === "units") {
      await this.renderUnits();
      return;
    }
    if (this.section === "home") {
      await this.renderHome();
      return;
    }
    if (this.section === "language") {
      await this.renderLanguage();
      return;
    }
    if (this.section === "speech") {
      await this.renderSpeech();
      return;
    }
    if (this.section === "data") {
      await this.renderData();
      return;
    }
    if (this.section === "sync") {
      await this.renderSync();
      return;
    }
    if (this.section === "ai") {
      await this.renderAi();
      return;
    }
    if (this.section === "install") {
      await this.renderInstall();
      return;
    }
    if (this.section === "about") {
      await this.renderAbout();
      return;
    }
    this.section = "hub";
    this.render();
  }

  renderApp() {
    this.innerHTML = `
      ${subpageBar(t("settings.app.title"), { backHref: SETTINGS_PARENT.app })}
      <nav class="settings-nav" aria-label="${t("settings.app.title")}">
        <a href="#/settings?section=language">${t("settings.app.language")} <span>${t("settings.app.language_hint")}</span></a>
        <a href="#/settings?section=units">${t("settings.app.units")} <span>${t("settings.app.units_hint")}</span></a>
        <a href="#/settings?section=home">${t("settings.app.home")} <span>${t("settings.app.home_hint")}</span></a>
        <a href="#/settings?section=install">${t("settings.app.install")} <span>${t("settings.app.install_hint")}</span></a>
      </nav>`;
    bindSubpageBack(this, SETTINGS_PARENT.app);
  }

  async renderLanguage() {
    const p = await prefs.load();
    const current = p.uiLang || "";
    const options = [
      `<option value="" ${current === "" ? "selected" : ""}>${t("settings.language.auto")}</option>`,
      ...LOCALES.map(
        (l) =>
          `<option value="${l.id}" ${l.id === current ? "selected" : ""}>${l.nativeName} (${l.name})</option>`,
      ),
    ];
    this.innerHTML = `
      ${subpageBar(t("settings.language.title"), { backHref: SETTINGS_PARENT.language })}
      <form class="entry-form card" id="language-form">
        <div class="field">
          <label for="uiLang">${t("settings.language.label")}</label>
          <select id="uiLang" name="uiLang">${options.join("")}</select>
          <p class="field-hint">${t("settings.language.auto_hint")}</p>
        </div>
        <button type="submit" class="btn btn--primary">${t("action.save")}</button>
      </form>`;
    bindSubpageBack(this, SETTINGS_PARENT.language);
    this.querySelector("#language-form")?.addEventListener("submit", async (ev) => {
      ev.preventDefault();
      const fd = new FormData(/** @type {HTMLFormElement} */ (ev.target));
      await prefs.save({ uiLang: String(fd.get("uiLang") || "") });
      window.dispatchEvent(new Event("chompass-prefs-changed"));
    });
  }

  async loadProfile() {
    return (
      (await profileStore.load()) ?? {
        sex: "other",
        age: 30,
        heightCm: 170,
        weightKg: 70,
        bodyFatPercentage: null,
        activityLevel: "moderate",
        goal: "maintain",
        weeklyChangeKg: null,
        ketoMode: false,
        goalWeightKg: null,
        customCalories: null,
      }
    );
  }

  async renderProfile() {
    const p = await this.loadProfile();
    const appPrefs = await prefs.load();
    const heightLabel = appPrefs.heightUnit === "in" ? "Height in" : "Height cm";
    const weightLabel = appPrefs.weightUnit === "lb" ? "Weight lb" : "Weight kg";
    const heightVal = appPrefs.heightUnit === "in" ? (p.heightCm / 2.54).toFixed(1) : p.heightCm;
    const weightVal = appPrefs.weightUnit === "lb" ? (p.weightKg * 2.20462).toFixed(1) : p.weightKg;

    this.innerHTML = `
      ${subpageBar("Personal Info", { backHref: SETTINGS_PARENT.personal })}
      <form class="entry-form card" id="profile-form">
        <div class="field-row field-row--2">
          <div class="field">
            <label for="sex">Sex</label>
            <select id="sex" name="sex">
              ${["male", "female", "other"].map((s) => `<option value="${s}" ${p.sex === s ? "selected" : ""}>${s}</option>`).join("")}
            </select>
          </div>
          <div class="field">
            <label for="age">Age</label>
            <input id="age" name="age" type="number" min="1" value="${p.age}" />
          </div>
        </div>
        <div class="field-row">
          <div class="field">
            <label for="height">${heightLabel}</label>
            <input id="height" name="height" type="number" step="0.1" min="1" value="${heightVal}" />
          </div>
          <div class="field">
            <label for="weight">${weightLabel}</label>
            <input id="weight" name="weight" type="number" step="0.1" min="1" value="${weightVal}" />
          </div>
          <div class="field">
            <label for="bodyFatPercentage">Body fat %</label>
            <input id="bodyFatPercentage" name="bodyFatPercentage" type="number" step="0.1" min="0" max="100"
              value="${p.bodyFatPercentage != null ? p.bodyFatPercentage * 100 : ""}" />
          </div>
        </div>
        <label class="field" style="display:flex;gap:0.5rem;align-items:center;">
          <input type="checkbox" name="useBodyFatInBMR" ${p.useBodyFatInBMR !== false ? "checked" : ""} />
          Use body fat % in BMR (Katch-McArdle)
        </label>
        <div class="field">
          <label for="activityLevel">Activity</label>
          <select id="activityLevel" name="activityLevel">
            ${ACTIVITY_LEVELS.map((a) => `<option value="${a.id}" ${p.activityLevel === a.id ? "selected" : ""}>${a.label}</option>`).join("")}
          </select>
        </div>
        <button type="submit" class="btn btn--primary">Save</button>
      </form>
      <nav class="settings-nav" aria-label="Related">
        <a href="#/measurements">Body measurements <span>Tape / Navy / RFM</span></a>
      </nav>`;
    this.querySelector("#profile-form")?.addEventListener("submit", async (ev) => {
      ev.preventDefault();
      const fd = new FormData(/** @type {HTMLFormElement} */ (ev.target));
      const height = Number(fd.get("height"));
      const weight = Number(fd.get("weight"));
      const bfRaw = fd.get("bodyFatPercentage");
      await profileStore.save({
        ...p,
        sex: /** @type {any} */ (fd.get("sex")),
        age: Number(fd.get("age")),
        heightCm: appPrefs.heightUnit === "in" ? height * 2.54 : height,
        weightKg: appPrefs.weightUnit === "lb" ? weight / 2.20462 : weight,
        bodyFatPercentage: bfRaw ? Number(bfRaw) / 100 : null,
        useBodyFatInBMR: fd.get("useBodyFatInBMR") === "on",
        activityLevel: /** @type {any} */ (fd.get("activityLevel")),
      });
      location.hash = SETTINGS_PARENT.personal;
    });
    bindSubpageBack(this, SETTINGS_PARENT.personal);
  }

  async renderGoals() {
    const p = await this.loadProfile();
    const targets = dailyTargets(p);
    this.innerHTML = `
      ${subpageBar("Goals & Nutrition", { backHref: SETTINGS_PARENT.goals })}
      <form class="entry-form card" id="goals-form">
        <div class="field-row">
          <div class="field">
            <label for="goal">Goal</label>
            <select id="goal" name="goal">
              ${["lose", "maintain", "gain"].map((g) => `<option value="${g}" ${p.goal === g ? "selected" : ""}>${g}</option>`).join("")}
            </select>
          </div>
          <div class="field">
            <label for="weeklyChangeKg">Pace kg/wk</label>
            <input id="weeklyChangeKg" name="weeklyChangeKg" type="number" step="0.05" min="0" value="${p.weeklyChangeKg ?? ""}" placeholder="0.5" />
          </div>
          <div class="field">
            <label for="goalWeightKg">Goal weight kg</label>
            <input id="goalWeightKg" name="goalWeightKg" type="number" step="0.1" min="0" value="${p.goalWeightKg ?? ""}" />
          </div>
        </div>
        <div class="field-row">
          <div class="field">
            <label for="ketoMode">Diet mode</label>
            <select id="ketoMode" name="ketoMode">
              <option value="false" ${!p.ketoMode ? "selected" : ""}>Standard</option>
              <option value="true" ${p.ketoMode ? "selected" : ""}>Keto</option>
            </select>
          </div>
          <div class="field">
            <label for="customCalories">Custom calories</label>
            <input id="customCalories" name="customCalories" type="number" min="0" max="${CALORIE_PARSER_CEILING_KCAL}" value="${p.customCalories ?? ""}" placeholder="${targets.calories}" />
          </div>
        </div>
        <p style="color:var(--muted);font-size:0.82rem;margin:0 0 0.5rem;">Formula targets: ${formatNumber(targets.calories)} kcal · ${Math.round(targets.proteinG)}P / ${Math.round(targets.carbsG)}C / ${Math.round(targets.fatG)}F. Leave blank to use formula.</p>
        <div class="field-row">
          <div class="field">
            <label for="proteinTargetMode">Protein target</label>
            <select id="proteinTargetMode" name="proteinTargetMode">
              <option value="gramsPerDay" ${(p.proteinTargetMode || "gramsPerDay") === "gramsPerDay" ? "selected" : ""}>Grams per day</option>
              <option value="gPerKgTotal" ${p.proteinTargetMode === "gPerKgTotal" ? "selected" : ""}>g/kg body weight</option>
              <option value="gPerKgLbm" ${p.proteinTargetMode === "gPerKgLbm" ? "selected" : ""}>g/kg lean mass</option>
            </select>
          </div>
          <div class="field">
            <label for="proteinGramsPerKg">Protein g/kg</label>
            <input id="proteinGramsPerKg" name="proteinGramsPerKg" type="number" min="0" step="0.1" value="${p.proteinGramsPerKg ?? ""}" placeholder="e.g. 2.0" />
          </div>
          <div class="field">
            <label for="customProtein">Custom protein g</label>
            <input id="customProtein" name="customProtein" type="number" min="0" value="${p.customProtein ?? ""}" placeholder="${Math.round(targets.proteinG)}" />
          </div>
        </div>
        <p style="color:var(--muted);font-size:0.82rem;margin:0 0 0.5rem;">In g/kg mode, the rate updates daily grams when weight or body fat changes. Leave g/day blank when using a rate.</p>
        <div class="field-row">
          <div class="field">
            <label for="customCarbs">Custom carbs g</label>
            <input id="customCarbs" name="customCarbs" type="number" min="0" value="${p.customCarbs ?? ""}" placeholder="${Math.round(targets.carbsG)}" />
          </div>
          <div class="field">
            <label for="customFat">Custom fat g</label>
            <input id="customFat" name="customFat" type="number" min="0" value="${p.customFat ?? ""}" placeholder="${Math.round(targets.fatG)}" />
          </div>
        </div>
        <button type="submit" class="btn btn--primary">Save goals</button>
        <button type="button" class="btn btn--ghost" id="clear-custom">Clear custom targets</button>
      </form>
      <div class="card">
        <h2 class="chart-title">Calculated targets</h2>
        <p style="color:var(--muted);margin:0 0 0.6rem;font-size:0.85rem;">
          BMR ${formatNumber(Math.round(bmr(p)))} · TDEE ${formatNumber(Math.round(tdee(p)))} kcal
        </p>
        <div class="stat-badges">
          <div class="stat-badge"><strong>${targets.calories}</strong>Calories</div>
          <div class="stat-badge" style="color:var(--protein)"><strong>${Math.round(targets.proteinG)} g</strong>Protein</div>
          <div class="stat-badge" style="color:var(--carbs)"><strong>${Math.round(targets.carbsG)} g</strong>Carbs</div>
          <div class="stat-badge" style="color:var(--fat)"><strong>${Math.round(targets.fatG)} g</strong>Fat</div>
        </div>
        <div class="btn-row" style="margin-top:0.9rem;">
          <button type="button" class="btn btn--primary" id="recalculate-goals">Recalculate Goals</button>
        </div>
        <p id="recalc-status" style="color:var(--muted);font-size:0.85rem;margin:0.55rem 0 0;" hidden></p>
      </div>
      <nav class="settings-nav" aria-label="Related">
        <a href="#/settings?section=nutrients">Optional nutrients <span>Fiber, sodium…</span></a>
        ${!p.ketoMode ? `<a href="#/settings?section=daytypes">${escapeHtmlSettings(t("day_types.title"))} <span>${escapeHtmlSettings(dayTypesSummary(p.macroPlan))}</span></a>` : ""}
      </nav>`;
    this.querySelector("#goals-form")?.addEventListener("submit", async (ev) => {
      ev.preventDefault();
      const fd = new FormData(/** @type {HTMLFormElement} */ (ev.target));
      const paceRaw = fd.get("weeklyChangeKg");
      const goalW = fd.get("goalWeightKg");
      const custom = fd.get("customCalories");
      const floor = safetyFloorKcal(p);
      if (custom && Number(custom) < floor) {
        const ok = await openConfirm({
          title: "Below the safety floor",
          message: "This is below your estimated resting burn or 1,200 kcal. Only continue if a clinician prescribed it.",
          confirmLabel: "Continue anyway",
        });
        if (!ok) return;
      }
      const customProteinRaw = fd.get("customProtein");
      const customCarbsRaw = fd.get("customCarbs");
      const customFatRaw = fd.get("customFat");
      const proteinTargetModeRaw = String(fd.get("proteinTargetMode") || "gramsPerDay");
      const proteinTargetMode =
        proteinTargetModeRaw === "gPerKgTotal" || proteinTargetModeRaw === "gPerKgLbm"
          ? proteinTargetModeRaw
          : "gramsPerDay";
      const proteinGramsPerKgRaw = fd.get("proteinGramsPerKg");
      const proteinGramsPerKg =
        proteinGramsPerKgRaw !== null && String(proteinGramsPerKgRaw).trim() !== ""
          ? Number(proteinGramsPerKgRaw)
          : null;
      const customCalories = custom ? Number(custom) : null;
      const customProtein =
        proteinTargetMode === "gramsPerDay" && customProteinRaw ? Number(customProteinRaw) : null;
      const customCarbs = customCarbsRaw ? Number(customCarbsRaw) : null;
      const customFat = customFatRaw ? Number(customFatRaw) : null;
      const locks = locksFromFilledCustoms({
        customCalories,
        customProtein,
        customCarbs,
        customFat,
      });
      await profileStore.save({
        ...p,
        goal: /** @type {any} */ (fd.get("goal")),
        weeklyChangeKg: paceRaw ? Number(paceRaw) : null,
        goalWeightKg: goalW ? Number(goalW) : null,
        ketoMode: fd.get("ketoMode") === "true",
        // Keto transition (#60 Q4): a day-type plan pauses (data kept).
        macroPlan:
          fd.get("ketoMode") === "true" && p.macroPlan
            ? pausedForKeto(p.macroPlan)
            : p.macroPlan,
        customCalories,
        customProtein,
        customCarbs,
        customFat,
        proteinTargetMode,
        proteinGramsPerKg:
          proteinTargetMode === "gramsPerDay" ? null : proteinGramsPerKg,
        caloriesLocked: locks.caloriesLocked,
        lockedMacros: locks.lockedMacros,
      });
      location.hash = SETTINGS_PARENT.goals;
    });
    this.querySelector("#clear-custom")?.addEventListener("click", async () => {
      await profileStore.save({
        ...p,
        customCalories: null,
        customProtein: null,
        customCarbs: null,
        customFat: null,
        proteinTargetMode: "gramsPerDay",
        proteinGramsPerKg: null,
        caloriesLocked: false,
        lockedMacros: [],
      });
      this.render();
    });
    this.querySelector("#recalculate-goals")?.addEventListener("click", () => this.onRecalculateGoals(p));
    bindSubpageBack(this, SETTINGS_PARENT.goals);
  }

  /**
   * Mirrors Android Settings Recalculate Goals: AI when a key is configured,
   * otherwise clear custom calories so formula targets apply.
   * @param {import('../lib/chompass-core/models.js').UserProfile} profile
   */
  async onRecalculateGoals(profile) {
    const aiClient = await resolveGoalsAiClient();
    const ok = await openConfirm({
      title: "Recalculate goals?",
      message: aiClient
        ? "Uses your AI provider with your profile and recent food/weight logs to refresh calorie and macro targets. Locked values stay put."
        : "No AI key configured. Unlocked calories and macros go back to formula defaults. Locked values stay. Add an AI key in Settings for the same AI recalculation as Android.",
      confirmLabel: "Recalculate",
    });
    if (!ok) return;

    const btn = /** @type {HTMLButtonElement | null} */ (this.querySelector("#recalculate-goals"));
    const status = /** @type {HTMLElement | null} */ (this.querySelector("#recalc-status"));
    if (btn) btn.disabled = true;
    if (status) {
      status.hidden = false;
      status.textContent = aiClient ? "Recalculating with AI…" : "Resetting to formula…";
    }

    try {
      if (!aiClient) {
        await profileStore.save(applyingFormulaGoals(profile));
        this.render();
        const after = /** @type {HTMLElement | null} */ (this.querySelector("#recalc-status"));
        if (after) {
          after.hidden = false;
          after.textContent = "Goals reset to formula defaults.";
        }
        return;
      }

      const appPrefs = await prefs.load();
      const [foods, weightEntries] = await Promise.all([foodEntries.all(), weights.all()]);
      const forecast = computeWeightForecast({ weights: weightEntries, foods, profile });
      const result = await calculateGoalsWithAi({
        providerId: aiClient.providerId,
        config: aiClient.config,
        profile,
        forecast,
        heightMetric: appPrefs.heightUnit !== "in",
        weightMetric: appPrefs.weightUnit !== "lb",
      });
      await profileStore.save(applyingAiGoalsToPlan(profile, result, applyingAiGoals));
      await refreshGoalJournal().catch(() => null);
      const reason = result.reason ? ` ${result.reason}` : "";
      this.render();
      const after = /** @type {HTMLElement | null} */ (this.querySelector("#recalc-status"));
      if (after) {
        after.hidden = false;
        after.textContent = `Updated to ${formatNumber(result.calories)} kcal.${reason}`;
      }
    } catch (err) {
      const msg = err instanceof Error ? err.message : String(err);
      if (status) {
        status.hidden = false;
        status.textContent = `Couldn't recalculate. Goals unchanged. Check your AI key in Settings. (${msg})`;
      }
      if (btn) btn.disabled = false;
    }
  }

  /**
   * Day types editor (#60, PWA mirror of Android DayTypesSettingsScreen):
   * profiles + schedule + overrides, every mutation saved immediately through
   * the pure write path (macro-plan-edit) and followed by a journal refresh.
   */
  async renderDayTypes() {
    const p = await this.loadProfile();
    const plan = p.macroPlan ?? null;
    const profiles = plan?.profiles ?? [];
    const today = localTodayIso();
    const base = dailyTargets(p);
    const floor = safetyFloorKcal(p);
    const enabled = plan?.enabled === true;
    const countOk = profiles.length >= MIN_PROFILES && profiles.length <= MAX_PROFILES;
    const editingId = this._editingProfileId ?? null; // "new" | profile id | null
    const editing = editingId === "new" ? null : profiles.find((x) => x.id === editingId) ?? null;

    /** Persist a mutated plan + refresh the goal journal (write trigger 1). */
    const savePlan = async (nextPlan) => {
      const safe = p.ketoMode ? pausedForKeto(nextPlan) : nextPlan;
      await profileStore.save({ ...p, macroPlan: safe });
      await refreshGoalJournal().catch(() => null);
      this.render();
    };

    const profileOptions = (selected, includeEmpty, emptyLabel) =>
      `${includeEmpty ? `<option value="" ${!selected ? "selected" : ""}>${escapeHtmlSettings(emptyLabel)}</option>` : ""}${profiles
        .map((x) => `<option value="${escapeHtmlSettings(x.id)}" ${selected === x.id ? "selected" : ""}>${escapeHtmlSettings(x.name)}</option>`)
        .join("")}`;

    const pattern = plan?.cyclePattern ?? [];
    const assignments = Object.entries(plan?.dayAssignments ?? {}).sort(([a], [b]) => a.localeCompare(b));
    const preview = Array.from({ length: 7 }, (_, i) => {
      const iso = shiftIso(today, i);
      const r = resolveDay(plan, base, iso);
      return { iso, r };
    });

    this.innerHTML = `
      ${subpageBar(t("day_types.title"), { backHref: SETTINGS_PARENT.daytypes })}
      ${p.ketoMode ? `<div class="card"><p style="margin:0;color:var(--muted);font-size:0.88rem;">${escapeHtmlSettings(t("day_types.keto_paused"))}</p></div>` : ""}
      <div class="card">
        <div class="field" style="display:flex;align-items:center;gap:0.6rem;">
          <label for="dt-enabled" style="margin:0;">${escapeHtmlSettings(t("day_types.master_toggle"))}</label>
          <select id="dt-enabled" style="max-width:110px;" ${countOk ? "" : "disabled"}>
            <option value="false" ${!enabled ? "selected" : ""}>Off</option>
            <option value="true" ${enabled ? "selected" : ""}>On</option>
          </select>
        </div>
        ${countOk ? "" : `<p class="field-hint">${escapeHtmlSettings(t("day_types.need_profiles", { min: String(MIN_PROFILES), max: String(MAX_PROFILES) }))}</p>`}
      </div>

      <div class="card">
        <h2 class="chart-title">${escapeHtmlSettings(t("day_types.profiles"))}</h2>
        ${profiles.length === 0 ? `<p style="color:var(--muted);font-size:0.85rem;margin:0;">${escapeHtmlSettings(t("day_types.no_profiles"))}</p>` : ""}
        ${profiles
          .map(
            (x, i) => `
          <div class="day-type-row">
            <div class="day-type-row__text">
              <strong>${escapeHtmlSettings(x.name)}</strong>
              <span class="day-type-row__sub">${x.calories} kcal · ${x.proteinG}P / ${x.carbsG}C / ${x.fatG}F${x.calories <= floor ? ` · ${escapeHtmlSettings(t("day_types.at_floor"))}` : ""}</span>
            </div>
            <div class="day-type-row__actions">
              <button type="button" class="chip" data-dt-up="${x.id}" ${i === 0 ? "disabled" : ""} aria-label="Move up">↑</button>
              <button type="button" class="chip" data-dt-down="${x.id}" ${i === profiles.length - 1 ? "disabled" : ""} aria-label="Move down">↓</button>
              <button type="button" class="chip" data-dt-edit="${x.id}">${escapeHtmlSettings(t("day_types.edit"))}</button>
              <button type="button" class="chip" data-dt-del="${x.id}">${escapeHtmlSettings(t("day_types.delete"))}</button>
            </div>
          </div>`,
          )
          .join("")}
        ${profiles.length < MAX_PROFILES ? `<button type="button" class="btn btn--ghost" data-dt-add>${escapeHtmlSettings(t("day_types.add_profile"))}</button>` : ""}
      </div>

      ${editingId ? `
      <form class="entry-form card" id="dt-profile-form">
        <h2 class="chart-title">${escapeHtmlSettings(editingId === "new" ? t("day_types.add_profile") : t("day_types.edit_profile"))}</h2>
        <div class="field">
          <label for="dt-name">${escapeHtmlSettings(t("day_types.name"))}</label>
          <input id="dt-name" name="name" type="text" maxlength="40" value="${escapeHtmlSettings(editing?.name ?? "")}" required />
        </div>
        <div class="field-row field-row--2">
          <div class="field"><label for="dt-kcal">${escapeHtmlSettings(t("day_types.calories"))}</label><input id="dt-kcal" name="calories" type="number" min="1200" max="6000" step="10" value="${editing?.calories ?? base.calories}" required /></div>
          <div class="field"><label for="dt-protein">${escapeHtmlSettings(t("day_types.protein_g"))}</label><input id="dt-protein" name="proteinG" type="number" min="0" max="500" value="${editing?.proteinG ?? base.proteinG}" required /></div>
          <div class="field"><label for="dt-carbs">${escapeHtmlSettings(t("day_types.carbs_g"))}</label><input id="dt-carbs" name="carbsG" type="number" min="0" max="1200" value="${editing?.carbsG ?? base.carbsG}" required /></div>
          <div class="field"><label for="dt-fat">${escapeHtmlSettings(t("day_types.fat_g"))}</label><input id="dt-fat" name="fatG" type="number" min="0" max="400" value="${editing?.fatG ?? base.fatG}" required /></div>
        </div>
        <p class="field-hint">${escapeHtmlSettings(t("day_types.floor_hint", { floor: String(floor) }))}</p>
        <div class="btn-row">
          <button type="submit" class="btn btn--primary">${escapeHtmlSettings(t("day_types.save_profile"))}</button>
          <button type="button" class="btn btn--ghost" id="dt-copy-current">${escapeHtmlSettings(t("day_types.copy_current"))}</button>
          <button type="button" class="btn btn--ghost" id="dt-cancel">${escapeHtmlSettings(t("action.cancel"))}</button>
        </div>
      </form>` : ""}

      ${profiles.length >= MIN_PROFILES ? `
      <div class="card">
        <h2 class="chart-title">${escapeHtmlSettings(t("day_types.schedule"))}</h2>
        <div class="field">
          <label for="dt-mode">${escapeHtmlSettings(t("day_types.mode"))}</label>
          <select id="dt-mode">
            <option value="MANUAL" ${plan?.mode !== "WEEKDAYS" && plan?.mode !== "CYCLE" ? "selected" : ""}>${escapeHtmlSettings(t("day_types.mode_manual"))}</option>
            <option value="WEEKDAYS" ${plan?.mode === "WEEKDAYS" ? "selected" : ""}>${escapeHtmlSettings(t("day_types.mode_weekdays"))}</option>
            <option value="CYCLE" ${plan?.mode === "CYCLE" ? "selected" : ""}>${escapeHtmlSettings(t("day_types.mode_cycle"))}</option>
          </select>
        </div>
        <div class="field">
          <label for="dt-default">${escapeHtmlSettings(t("day_types.default"))}</label>
          <select id="dt-default">${profileOptions(plan?.defaultProfileId ?? null, false, "")}</select>
        </div>
        ${plan?.mode === "WEEKDAYS"
          ? WEEKDAY_ORDER.map(
              (day) => `
          <div class="field">
            <label for="dt-wd-${day}">${weekdayLabel(day)}</label>
            <select id="dt-wd-${day}" data-dt-weekday="${day}">${profileOptions(plan?.weekdayProfileIds?.[day] ?? null, true, t("day_types.follow_default"))}</select>
          </div>`,
            ).join("")
          : ""}
        ${plan?.mode === "CYCLE"
          ? `
          <div class="field">
            <label for="dt-pattern-add">${escapeHtmlSettings(t("day_types.pattern"))}</label>
            <div class="btn-row" style="align-items:center;">
              <select id="dt-pattern-add">${profileOptions(null, false, "")}</select>
              <button type="button" class="btn btn--ghost" id="dt-pattern-append">${escapeHtmlSettings(t("day_types.pattern_add"))}</button>
              <button type="button" class="btn btn--ghost" id="dt-restart">${escapeHtmlSettings(t("day_types.restart"))}</button>
            </div>
            <div class="day-type-pattern">
              ${pattern.map((id, i) => {
                const x = profiles.find((y) => y.id === id);
                return `<button type="button" class="chip" data-dt-pattern-remove="${i}" title="${escapeHtmlSettings(t("day_types.remove"))}">${i + 1}. ${escapeHtmlSettings(x?.name ?? "?")} ×</button>`;
              }).join("")}
            </div>
            ${plan.cycleAnchorDay ? `<p class="field-hint">${escapeHtmlSettings(t("day_types.anchor_hint", { date: plan.cycleAnchorDay }))}</p>` : ""}
          </div>`
          : ""}
      </div>

      <div class="card">
        <h2 class="chart-title">${escapeHtmlSettings(t("day_types.overrides"))}</h2>
        <div class="field-row field-row--2" style="align-items:flex-end;">
          <div class="field"><label for="dt-ov-date">${escapeHtmlSettings(t("day_types.date"))}</label><input id="dt-ov-date" type="date" value="${today}" /></div>
          <div class="field"><label for="dt-ov-profile">${escapeHtmlSettings(t("day_types.day_type"))}</label><select id="dt-ov-profile">${profileOptions(null, false, "")}</select></div>
        </div>
        <button type="button" class="btn btn--ghost" id="dt-ov-add">${escapeHtmlSettings(t("day_types.override_add"))}</button>
        ${assignments.length ? `
          <div style="margin-top:0.6rem;">
          ${assignments
            .map(
              ([date, id]) => {
                const x = profiles.find((y) => y.id === id);
                return `<div class="day-type-row"><div class="day-type-row__text"><strong>${escapeHtmlSettings(date)}</strong><span class="day-type-row__sub">${escapeHtmlSettings(x?.name ?? "?")}</span></div><div class="day-type-row__actions"><button type="button" class="chip" data-dt-ov-remove="${date}">${escapeHtmlSettings(t("day_types.remove"))}</button></div></div>`;
              },
            )
            .join("")}
          </div>` : ""}
      </div>

      <div class="card">
        <h2 class="chart-title">${escapeHtmlSettings(t("day_types.preview"))}</h2>
        ${preview
          .map(
            ({ iso, r }) => `
          <div class="day-type-row">
            <div class="day-type-row__text"><strong>${escapeHtmlSettings(iso)}${iso === today ? ` · ${escapeHtmlSettings(t("day_types.today"))}` : ""}</strong></div>
            <div class="day-type-row__actions"><span class="day-type-row__sub">${escapeHtmlSettings(r.profileName ?? t("day_types.base"))} · ${r.targets.calories} kcal</span></div>
          </div>`,
          )
          .join("")}
      </div>` : ""}
    `;

    // -- bindings --------------------------------------------------------------
    this.querySelector("#dt-enabled")?.addEventListener("change", async (ev) => {
      const on = /** @type {HTMLSelectElement} */ (ev.target).value === "true";
      await savePlan(setPlanEnabled(plan, on, today));
    });
    this.querySelector("[data-dt-add]")?.addEventListener("click", () => {
      this._editingProfileId = "new";
      this.render();
    });
    this.querySelectorAll("[data-dt-edit]").forEach((btn) => {
      btn.addEventListener("click", () => {
        this._editingProfileId = btn.getAttribute("data-dt-edit");
        this.render();
      });
    });
    this.querySelector("#dt-cancel")?.addEventListener("click", () => {
      this._editingProfileId = null;
      this.render();
    });
    this.querySelector("#dt-copy-current")?.addEventListener("click", () => {
      const set = (id, v) => {
        const el = /** @type {HTMLInputElement|null} */ (this.querySelector(id));
        if (el) el.value = String(Math.round(v));
      };
      set("#dt-kcal", base.calories);
      set("#dt-protein", base.proteinG);
      set("#dt-carbs", base.carbsG);
      set("#dt-fat", base.fatG);
    });
    this.querySelector("#dt-profile-form")?.addEventListener("submit", async (ev) => {
      ev.preventDefault();
      const fd = new FormData(/** @type {HTMLFormElement} */ (ev.target));
      const name = String(fd.get("name") ?? "").trim();
      if (!name) return;
      const clash = profiles.some((x) => x.name.toLowerCase() === name.toLowerCase() && x.id !== editing?.id);
      if (clash) {
        await openConfirm({ title: t("day_types.duplicate_name"), message: t("day_types.duplicate_name_hint"), confirmLabel: "OK" });
        return;
      }
      const id = editing?.id ?? crypto.randomUUID();
      const next = upsertProfile(
        plan,
        {
          id,
          name,
          calories: Number(fd.get("calories")),
          proteinG: Number(fd.get("proteinG")),
          carbsG: Number(fd.get("carbsG")),
          fatG: Number(fd.get("fatG")),
        },
        p,
        today,
      );
      this._editingProfileId = null;
      await savePlan(next);
    });
    this.querySelectorAll("[data-dt-del]").forEach((btn) => {
      btn.addEventListener("click", async () => {
        const id = btn.getAttribute("data-dt-del");
        const x = profiles.find((y) => y.id === id);
        const ok = await openConfirm({
          title: t("day_types.delete"),
          message: t("day_types.delete_hint", { name: x?.name ?? "" }),
          confirmLabel: t("day_types.delete"),
          danger: true,
        });
        if (!ok) return;
        await savePlan(deleteProfile(plan, id, null, today));
      });
    });
    this.querySelectorAll("[data-dt-up], [data-dt-down]").forEach((btn) => {
      btn.addEventListener("click", async () => {
        const id = btn.getAttribute("data-dt-up") ?? btn.getAttribute("data-dt-down");
        const ids = profiles.map((x) => x.id);
        const i = ids.indexOf(id);
        const j = btn.hasAttribute("data-dt-up") ? i - 1 : i + 1;
        if (i < 0 || j < 0 || j >= ids.length) return;
        [ids[i], ids[j]] = [ids[j], ids[i]];
        await savePlan(reorderProfiles(plan, ids));
      });
    });
    this.querySelector("#dt-mode")?.addEventListener("change", async (ev) => {
      const mode = /** @type {"MANUAL"|"WEEKDAYS"|"CYCLE"} */ (/** @type {HTMLSelectElement} */ (ev.target).value);
      await savePlan(setPlanMode(plan, mode, today));
    });
    this.querySelector("#dt-default")?.addEventListener("change", async (ev) => {
      const v = /** @type {HTMLSelectElement} */ (ev.target).value;
      await savePlan(setDefaultProfile(plan, v || null));
    });
    this.querySelectorAll("[data-dt-weekday]").forEach((sel) => {
      sel.addEventListener("change", async () => {
        await savePlan(
          setWeekdayProfile(plan, sel.getAttribute("data-dt-weekday"), /** @type {HTMLSelectElement} */ (sel).value || null),
        );
      });
    });
    this.querySelector("#dt-pattern-append")?.addEventListener("click", async () => {
      const v = /** @type {HTMLSelectElement|null} */ (this.querySelector("#dt-pattern-add"))?.value;
      if (!v || pattern.length >= MAX_PROFILES) return;
      await savePlan(setCyclePattern(plan, [...pattern, v]));
    });
    this.querySelectorAll("[data-dt-pattern-remove]").forEach((btn) => {
      btn.addEventListener("click", async () => {
        const i = Number(btn.getAttribute("data-dt-pattern-remove"));
        await savePlan(setCyclePattern(plan, pattern.filter((_, idx) => idx !== i)));
      });
    });
    this.querySelector("#dt-restart")?.addEventListener("click", async () => {
      await savePlan(restartCycle(plan, today));
    });
    this.querySelector("#dt-ov-add")?.addEventListener("click", async () => {
      const date = /** @type {HTMLInputElement|null} */ (this.querySelector("#dt-ov-date"))?.value;
      const id = /** @type {HTMLSelectElement|null} */ (this.querySelector("#dt-ov-profile"))?.value;
      if (!date || !id) return;
      await savePlan(setDayAssignment(plan, date, id));
    });
    this.querySelectorAll("[data-dt-ov-remove]").forEach((btn) => {
      btn.addEventListener("click", async () => {
        await savePlan(setDayAssignment(plan, btn.getAttribute("data-dt-ov-remove"), null));
      });
    });
    bindSubpageBack(this, SETTINGS_PARENT.daytypes);
  }

  async renderUnits() {
    const p = await prefs.load();
    this.innerHTML = `
      ${subpageBar("Units & schedule", { backHref: SETTINGS_PARENT.units })}
      <form class="entry-form card" id="units-form">
        <div class="field-row field-row--2">
          <div class="field">
            <label for="weightUnit">Weight</label>
            <select id="weightUnit" name="weightUnit">
              <option value="kg" ${p.weightUnit === "kg" ? "selected" : ""}>kg</option>
              <option value="lb" ${p.weightUnit === "lb" ? "selected" : ""}>lb</option>
            </select>
          </div>
          <div class="field">
            <label for="heightUnit">Height</label>
            <select id="heightUnit" name="heightUnit">
              <option value="cm" ${p.heightUnit === "cm" ? "selected" : ""}>cm</option>
              <option value="in" ${p.heightUnit === "in" ? "selected" : ""}>in</option>
            </select>
          </div>
        </div>
        <div class="field">
          <label for="theme">Theme</label>
          <select id="theme" name="theme">
            ${["system", "light", "dark"].map((t) => `<option value="${t}" ${p.theme === t ? "selected" : ""}>${t}</option>`).join("")}
          </select>
        </div>
        <div class="field">
          <label for="accent">Accent</label>
          <select id="accent" name="accent">
            ${ACCENTS.map((a) => `<option value="${a}" ${p.accent === a ? "selected" : ""}>${a}</option>`).join("")}
          </select>
        </div>
        <div class="field">
          <label for="weekStartDay">Week starts</label>
          <select id="weekStartDay" name="weekStartDay">
            ${(() => {
              const day =
                p.weekStartDay || (p.weekStartsOnMonday === false ? "sunday" : "monday");
              return [
                ["monday", "Monday"],
                ["sunday", "Sunday"],
                ["saturday", "Saturday"],
              ]
                .map(
                  ([id, label]) =>
                    `<option value="${id}" ${day === id ? "selected" : ""}>${label}</option>`
                )
                .join("");
            })()}
          </select>
        </div>
        <div class="field">
          <label for="progressDefaultRangeId">${t("settings.progress_default_range")}</label>
          <select id="progressDefaultRangeId" name="progressDefaultRangeId">
            ${[
              ["1W", "progress.range_1w"],
              ["1M", "progress.range_1m"],
              ["3M", "progress.range_3m"],
              ["6M", "progress.range_6m"],
              ["1Y", "progress.range_1y"],
              ["All", "progress.range_all"],
            ]
              .map(
                ([id, key]) =>
                  `<option value="${id}" ${(p.progressDefaultRangeId || "1W") === id ? "selected" : ""}>${t(key)}</option>`
              )
              .join("")}
          </select>
        </div>
        <p class="section-label">Meal times</p>
        <div class="field-row field-row--2">
          <div class="field"><label for="mealBreakfastStart">Breakfast</label><input id="mealBreakfastStart" name="mealBreakfastStart" type="time" value="${minutesToTimeInput(p.mealBreakfastStart ?? 300)}" /></div>
          <div class="field"><label for="mealLunchStart">Lunch</label><input id="mealLunchStart" name="mealLunchStart" type="time" value="${minutesToTimeInput(p.mealLunchStart ?? 660)}" /></div>
        </div>
        <div class="field-row field-row--2">
          <div class="field"><label for="mealDinnerStart">Dinner</label><input id="mealDinnerStart" name="mealDinnerStart" type="time" value="${minutesToTimeInput(p.mealDinnerStart ?? 900)}" /></div>
          <div class="field"><label for="mealSnackStart">Snack</label><input id="mealSnackStart" name="mealSnackStart" type="time" value="${minutesToTimeInput(p.mealSnackStart ?? 1260)}" /></div>
        </div>
        <button type="submit" class="btn btn--primary">Save</button>
      </form>`;
    this.querySelector("#units-form")?.addEventListener("submit", async (ev) => {
      ev.preventDefault();
      const fd = new FormData(/** @type {HTMLFormElement} */ (ev.target));
      await prefs.save({
        weightUnit: /** @type {any} */ (fd.get("weightUnit")),
        heightUnit: /** @type {any} */ (fd.get("heightUnit")),
        theme: /** @type {any} */ (fd.get("theme")),
        accent: String(fd.get("accent") || "system"),
        weekStartDay: /** @type {"monday"|"sunday"|"saturday"} */ (String(fd.get("weekStartDay") || "monday")),
        weekStartsOnMonday: String(fd.get("weekStartDay") || "monday") === "monday",
        progressDefaultRangeId: String(fd.get("progressDefaultRangeId") || "1W"),
        mealBreakfastStart: timeInputToMinutes(String(fd.get("mealBreakfastStart"))),
        mealLunchStart: timeInputToMinutes(String(fd.get("mealLunchStart"))),
        mealDinnerStart: timeInputToMinutes(String(fd.get("mealDinnerStart"))),
        mealSnackStart: timeInputToMinutes(String(fd.get("mealSnackStart"))),
      });
      window.dispatchEvent(new Event("chompass-prefs-changed"));
      location.hash = SETTINGS_PARENT.units;
    });
    bindSubpageBack(this, SETTINGS_PARENT.units);
  }

  async renderSpeech() {
    const p = await prefs.load();
    const current = p.speechLang || "";
    this.innerHTML = `
      ${subpageBar(t("settings.speech.language"), { backHref: SETTINGS_PARENT.speech })}
      <form class="entry-form card" id="speech-form">
        <p style="color:var(--muted);margin:0 0 0.75rem;font-size:0.88rem;">
          Voice dictation uses the browser’s on-device speech recognition (Chrome/Edge best). Cloud speech engines are Android-only.
        </p>
        <div class="field">
          <label for="speechLang">${t("settings.speech.language")}</label>
          <select id="speechLang" name="speechLang">
            ${SPEECH_LANGS.map((/** @type {SpeechLangOption} */ l) => {
              const label = l.labelKey ? t(l.labelKey) : (l.label || l.id);
              return `<option value="${l.id}" ${l.id === current ? "selected" : ""}>${label}</option>`;
            }).join("")}
          </select>
        </div>
        <button type="submit" class="btn btn--primary">${t("action.save")}</button>
      </form>`;
    this.querySelector("#speech-form")?.addEventListener("submit", async (ev) => {
      ev.preventDefault();
      const fd = new FormData(/** @type {HTMLFormElement} */ (ev.target));
      await prefs.save({ speechLang: String(fd.get("speechLang") || "") });
      location.hash = SETTINGS_PARENT.speech;
    });
    bindSubpageBack(this, SETTINGS_PARENT.speech);
  }

  async renderHome() {
    const p = await prefs.load();
    const selectedTubes = new Set(
      normalizeHomeTopNutrients(p.homeTopNutrients, p.homeNutrientCardCount ?? DEFAULT_NUTRIENT_CARD_COUNT)
    );
    const selectedChips = new Set(normalizeFoodLogChips(p.foodLogMacroChips));
    const chipDefs = HOME_TOP_NUTRIENTS.filter((n) => FOOD_LOG_CHIP_KEYS.includes(n.key));
    this.innerHTML = `
      ${subpageBar("Home display", { backHref: SETTINGS_PARENT.home })}
      <form class="entry-form card" id="home-form">
        <div class="field">
          <label for="showWater">Water tracking</label>
          <select id="showWater" name="showWater">
            <option value="false" ${p.showWater !== true ? "selected" : ""}>Off</option>
            <option value="true" ${p.showWater === true ? "selected" : ""}>On</option>
          </select>
        </div>
        <div class="field">
          <label for="waterGoalMl">Water goal (ml)</label>
          <input id="waterGoalMl" name="waterGoalMl" type="number" min="0" value="${p.waterGoalMl ?? 2000}" />
        </div>
        <div class="field">
          <label for="showNicotine">Nicotine tracking</label>
          <select id="showNicotine" name="showNicotine">
            <option value="false" ${p.showNicotine !== true ? "selected" : ""}>Off</option>
            <option value="true" ${p.showNicotine === true ? "selected" : ""}>On</option>
          </select>
          <p class="nutrient-picker__hint">Log cigarettes, vapes and pouches from the diary. Stays on your device and in your WebDAV sync; never sent to an AI provider.</p>
        </div>
        <div class="field">
          <label for="nicotineDailyLimit">Nicotine daily limit (0 = none)</label>
          <input id="nicotineDailyLimit" name="nicotineDailyLimit" type="number" min="0" value="${p.nicotineDailyLimit ?? 0}" />
        </div>
        <div class="field">
          <label for="showCaffeine">Caffeine tracking</label>
          <select id="showCaffeine" name="showCaffeine">
            <option value="false" ${p.showCaffeine !== true ? "selected" : ""}>Off</option>
            <option value="true" ${p.showCaffeine === true ? "selected" : ""}>On</option>
          </select>
          <p class="nutrient-picker__hint">Log coffee, tea and energy drinks from the diary. The daily total includes caffeine from food entries. Stays on your device and in your WebDAV sync; never sent to an AI provider.</p>
        </div>
        <div class="field">
          <label for="caffeineDailyLimitMg">Caffeine daily limit (mg, 0 = none)</label>
          <input id="caffeineDailyLimitMg" name="caffeineDailyLimitMg" type="number" min="0" max="1000" value="${p.optionalNutrientGoals?.caffeineMg ?? 400}" />
        </div>
        <div class="field">
          <label for="showNotes">Daily notes</label>
          <select id="showNotes" name="showNotes">
            <option value="false" ${p.showNotes !== true ? "selected" : ""}>Off</option>
            <option value="true" ${p.showNotes === true ? "selected" : ""}>On</option>
          </select>
          <p class="nutrient-picker__hint">Show the per-day note card on the diary. Notes sync and export with your diary and are never sent to an AI provider.</p>
        </div>
        <div class="field">
          <label for="showFasting">Fasting timer</label>
          <select id="showFasting" name="showFasting">
            <option value="false" ${p.showFasting !== true ? "selected" : ""}>Off</option>
            <option value="true" ${p.showFasting === true ? "selected" : ""}>On</option>
          </select>
          <p class="nutrient-picker__hint">Start, stop or cancel a local-only intermittent-fasting timer from the diary. Stays on this device; never synced, exported or sent to an AI provider.</p>
        </div>
        <div class="field">
          <label for="fastingGoalHours">Fasting goal (hours, 0 = none)</label>
          <input id="fastingGoalHours" name="fastingGoalHours" type="number" min="0" max="48" value="${p.fastingGoalHours ?? 0}" />
        </div>
        <div class="field">
          <label for="fastingEatHours">Eating window (hours, 0 = off)</label>
          <input id="fastingEatHours" name="fastingEatHours" type="number" min="0" max="24" value="${p.fastingEatHours ?? 0}" />
          <p class="nutrient-picker__hint">Quick picks (fast : eat):</p>
          <div class="fasting-presets">
            ${[12, 14, 16, 18, 20, 23]
              .map(
                (h) =>
                  `<button type="button" class="chip${p.fastingGoalHours === h && (p.fastingEatHours ?? 0) === 24 - h ? " chip--active" : ""}" data-fasting-preset="${h}">${h === 23 ? "23:1" : `${h}:${24 - h}`}</button>`,
              )
              .join("")}
          </div>
        </div>
        <div class="field">
          <label for="fastingStartHour">Fast start time</label>
          <input id="fastingStartTime" name="fastingStartTime" type="time" value="${String(p.fastingStartHour ?? 20).padStart(2, "0")}:${String(p.fastingStartMinute ?? 0).padStart(2, "0")}" />
          <p class="nutrient-picker__hint">Auto fast windows start your fast at this time each day.</p>
        </div>
        <div class="field">
          <label for="fastingAutoWindows">Auto fast windows</label>
          <select id="fastingAutoWindows" name="fastingAutoWindows">
            <option value="false" ${p.fastingAutoWindows !== true ? "selected" : ""}>Off</option>
            <option value="true" ${p.fastingAutoWindows === true ? "selected" : ""}>On</option>
          </select>
          <p class="nutrient-picker__hint">When on, your fast starts automatically at the fast start time and ends at the goal, with no buttons on Home. Works while this page is open.</p>
        </div>
        <div class="field">
          <label for="calorieGaugeMode">Calorie gauge</label>
          <select id="calorieGaugeMode" name="calorieGaugeMode">
            <option value="static" ${p.calorieGaugeMode !== "add_active" ? "selected" : ""}>Static (full target)</option>
            <option value="add_active" ${p.calorieGaugeMode === "add_active" ? "selected" : ""}>Add active burn to budget</option>
          </select>
          <p class="nutrient-picker__hint">Add active: set Activity Level to everyday baseline (not peak training). Uses your activity-level estimate (TDEE − BMR) plus any manual active burn you log from Add food. Measured Health Connect burn is Android-only.</p>
        </div>
        <div class="field">
          <label for="adaptiveGoals">Adaptive goals (Progress)</label>
          <select id="adaptiveGoals" name="adaptiveGoals">
            <option value="false" ${!p.adaptiveGoals ? "selected" : ""}>Off</option>
            <option value="true" ${p.adaptiveGoals ? "selected" : ""}>On</option>
          </select>
        </div>
        <div class="field">
          <label for="homeNutrientCardCount">Home nutrient tubes (1–4)</label>
          <input id="homeNutrientCardCount" name="homeNutrientCardCount" type="number" min="1" max="4" value="${p.homeNutrientCardCount ?? DEFAULT_NUTRIENT_CARD_COUNT}" />
        </div>
        <fieldset class="nutrient-picker">
          <legend>Home tube nutrients</legend>
          <p class="nutrient-picker__hint">Order = check order; first N tubes are shown.</p>
          <div class="nutrient-picker__list">
            ${HOME_TOP_NUTRIENTS.map(
              (n) => `
              <label class="nutrient-picker__row">
                <input type="checkbox" name="homeTopNutrients" value="${n.key}" ${selectedTubes.has(n.key) ? "checked" : ""} />
                <span>${n.label}</span>
              </label>`
            ).join("")}
          </div>
        </fieldset>
        <fieldset class="nutrient-picker">
          <legend>Food-row chips</legend>
          <div class="nutrient-picker__list">
            ${chipDefs
              .map(
                (n) => `
              <label class="nutrient-picker__row">
                <input type="checkbox" name="foodLogMacroChips" value="${n.key}" ${selectedChips.has(n.key) ? "checked" : ""} />
                <span>${n.label} (${n.chipGlyph})</span>
              </label>`
              )
              .join("")}
          </div>
        </fieldset>
        <p style="color:var(--muted);font-size:0.8rem;margin:0;">Steps / Health Connect active calories are Android-only. Optional nutrient goals power non-macro tubes.</p>
        <button type="submit" class="btn btn--primary">Save</button>
      </form>`;
    this.querySelector("#home-form")?.addEventListener("submit", async (ev) => {
      ev.preventDefault();
      const fd = new FormData(/** @type {HTMLFormElement} */ (ev.target));
      const cardCount = Math.min(4, Math.max(1, Number(fd.get("homeNutrientCardCount") || DEFAULT_NUTRIENT_CARD_COUNT)));
      const tubeRaw = fd.getAll("homeTopNutrients").map(String);
      const chipRaw = fd.getAll("foodLogMacroChips").map(String);
      await prefs.save({
        showWater: fd.get("showWater") === "true",
        waterGoalMl: Number(fd.get("waterGoalMl") || 2000),
        showNicotine: fd.get("showNicotine") === "true",
        nicotineDailyLimit: Math.max(0, Number(fd.get("nicotineDailyLimit") || 0)),
        showCaffeine: fd.get("showCaffeine") === "true",
        optionalNutrientGoals: {
          ...(p.optionalNutrientGoals ?? {}),
          caffeineMg: Math.min(1000, Math.max(0, Number(fd.get("caffeineDailyLimitMg") ?? 400))),
        },
        showNotes: fd.get("showNotes") === "true",
        showFasting: fd.get("showFasting") === "true",
        fastingGoalHours: Math.min(48, Math.max(0, Number(fd.get("fastingGoalHours") || 0))),
        fastingEatHours: Math.min(24, Math.max(0, Number(fd.get("fastingEatHours") || 0))),
        fastingAutoWindows: fd.get("fastingAutoWindows") === "true",
        ...parseFastStartTime(fd.get("fastingStartTime")),        calorieGaugeMode: /** @type {any} */ (fd.get("calorieGaugeMode")),
        adaptiveGoals: fd.get("adaptiveGoals") === "true",
        homeNutrientCardCount: cardCount,
        homeTopNutrients: normalizeHomeTopNutrients(tubeRaw, cardCount),
        foodLogMacroChips: normalizeFoodLogChips(chipRaw),
      });
      location.hash = SETTINGS_PARENT.home;
    });
    // Popular-protocol quick picks set both inputs (fast + eat; saved with the form).
    this.querySelectorAll("[data-fasting-preset]").forEach((btn) => {
      btn.addEventListener("click", () => {
        const fast = Number(btn.getAttribute("data-fasting-preset"));
        const fastInput = /** @type {HTMLInputElement|null} */ (this.querySelector("#fastingGoalHours"));
        const eatInput = /** @type {HTMLInputElement|null} */ (this.querySelector("#fastingEatHours"));
        if (fastInput) fastInput.value = String(fast);
        if (eatInput) eatInput.value = String(24 - fast);
        this.querySelectorAll("[data-fasting-preset]").forEach((b) =>
          b.classList.toggle("chip--active", b === btn),
        );
      });
    });
    bindSubpageBack(this, SETTINGS_PARENT.home);
  }

  async renderNutrients() {
    const p = await prefs.load();
    const g = mergeOptionalGoals(p.optionalNutrientGoals);
    this.innerHTML = `
      ${subpageBar("Optional nutrients", { backHref: SETTINGS_PARENT.nutrients })}
      <form class="entry-form card" id="nutrients-form">
        <p style="color:var(--muted);font-size:0.85rem;margin:0;">Daily goals for fiber and micros. Used by Home tubes when those nutrients are selected.</p>
        <div class="field-row field-row--2">
          ${OPTIONAL_GOAL_FIELDS.map(
            ([k, label]) => `
            <div class="field">
              <label for="${k}">${label}</label>
              <input id="${k}" name="${k}" type="number" min="0" max="${MAX_CUSTOM_GOAL_BY_KEY[k] ?? ""}" step="1" value="${g[k] ?? ""}" />
              ${k === "vitaminDMcg" ? `<div class="field-hint" data-vitd-iu-hint>${g.vitaminDMcg} mcg ≈ ${g.vitaminDMcg * 40} IU</div>` : ""}
            </div>`
          ).join("")}
        </div>
        <button type="submit" class="btn btn--primary">Save</button>
      </form>`;
    this.querySelector("#nutrients-form")?.addEventListener("submit", async (ev) => {
      ev.preventDefault();
      const fd = new FormData(/** @type {HTMLFormElement} */ (ev.target));
      /** @type {Record<string, number>} */
      const optionalNutrientGoals = {};
      for (const [k] of OPTIONAL_GOAL_FIELDS) {
        const raw = fd.get(k);
        const n = raw !== "" && raw != null ? Number(raw) : DEFAULT_OPTIONAL_NUTRIENT_GOALS[k];
        const cap = MAX_CUSTOM_GOAL_BY_KEY[k];
        const clamped = Number.isFinite(n) ? Math.max(0, Math.min(n, cap ?? Number.MAX_SAFE_INTEGER)) : DEFAULT_OPTIONAL_NUTRIENT_GOALS[k];
        optionalNutrientGoals[k] = clamped;
      }
      await prefs.save({ optionalNutrientGoals });
      location.hash = SETTINGS_PARENT.nutrients;
    });
    bindSubpageBack(this, SETTINGS_PARENT.nutrients);
  }

  async renderData() {
    this.innerHTML = `
      ${subpageBar("Health & Data", { backHref: SETTINGS_PARENT.data })}
      <div class="card">
        <p style="color:var(--muted);margin:0 0 0.6rem;font-size:0.85rem;">
          Formats match the Android app. Move data freely between the two.
        </p>
        <div class="field-row field-row--2">
          <div class="field">
            <label for="export-range">Diary range</label>
            <select id="export-range">
              <option value="all">All</option>
              <option value="month">Last 30 days</option>
              <option value="week">Last 7 days</option>
              <option value="today">Today</option>
            </select>
          </div>
          <div class="field">
            <label for="export-format">Diary format</label>
            <select id="export-format">
              <option value="json">JSON</option>
              <option value="csv">CSV</option>
              <option value="md">Markdown</option>
            </select>
          </div>
        </div>
        <div class="btn-row">
          <button class="btn btn--ghost" id="export-diary" type="button">Export diary</button>
          <label class="btn btn--ghost" style="cursor:pointer;">Import diary JSON
            <input type="file" accept="application/json" id="import-diary" style="display:none;" />
          </label>
        </div>
        <div class="field" style="margin-top:0.8rem;">
          <label for="body-format">Body metrics format</label>
          <select id="body-format">
            <option value="json">JSON</option>
            <option value="csv">CSV</option>
          </select>
        </div>
        <div class="btn-row">
          <button class="btn btn--ghost" id="export-body" type="button">Export body metrics</button>
          <label class="btn btn--ghost" style="cursor:pointer;">Import body JSON
            <input type="file" accept="application/json" id="import-body" style="display:none;" />
          </label>
        </div>
        <p id="import-status" style="color:var(--muted);font-size:0.85rem;margin-top:0.5rem;"></p>
        <button class="btn btn--danger" id="clear-all" style="margin-top:0.8rem;" type="button">Clear all local data</button>
      </div>
      <nav class="settings-nav" aria-label="Sync">
        <a href="#/settings?section=sync">Sync <span>WebDAV / sync file</span></a>
      </nav>`;
    this.querySelector("#export-diary")?.addEventListener("click", () => this.onExportDiary());
    this.querySelector("#export-body")?.addEventListener("click", () => this.onExportBodyMetrics());
    this.querySelector("#import-diary")?.addEventListener("change", (ev) => this.onImportDiary(ev));
    this.querySelector("#import-body")?.addEventListener("change", (ev) => this.onImportBodyMetrics(ev));
    this.querySelector("#clear-all")?.addEventListener("click", async () => {
      const ok = await openConfirm({
        title: "Clear all data",
        message: "Delete all diary, metrics, profile, and chat on this device?",
        confirmLabel: "Delete everything",
        danger: true,
      });
      if (!ok) return;
      await clearAllUserData();
      location.hash = "#/onboarding";
    });
    bindSubpageBack(this, SETTINGS_PARENT.data);
  }

  async renderSync() {
    const cfg = await loadWebDavSettings();
    this.innerHTML = `
      ${subpageBar("Sync", { backHref: SETTINGS_PARENT.sync })}
      <div class="card">
        <p style="color:var(--muted);margin:0 0 0.6rem;font-size:0.85rem;">
          Optional user-hosted sync. Chompass has no cloud account. Point both the PWA and Android app at the same WebDAV file (e.g. Nextcloud), or move a sync JSON by hand. API keys and food photos are not included.
        </p>
        <div class="btn-row">
          <button class="btn btn--ghost" id="export-sync" type="button">Export sync JSON</button>
          <label class="btn btn--ghost" style="cursor:pointer;">Import sync JSON
            <input type="file" accept="application/json" id="import-sync" style="display:none;" />
          </label>
        </div>
        <form class="entry-form" id="webdav-form" style="margin-top:1rem;">
          <div class="field">
            <label for="webdav-url">WebDAV file URL</label>
            <input id="webdav-url" name="url" type="url" placeholder="https://uXXXXX.your-storagebox.de/sync.json" value="${cfg.url.replace(/"/g, "&quot;")}" />
          </div>
          <div class="field-row field-row--2">
            <div class="field">
              <label for="webdav-user">Username</label>
              <input id="webdav-user" name="username" autocomplete="username" value="${cfg.username.replace(/"/g, "&quot;")}" />
            </div>
            <div class="field">
              <label for="webdav-pass">Password</label>
              <input id="webdav-pass" name="password" type="password" autocomplete="current-password" value="${cfg.password.replace(/"/g, "&quot;")}" />
            </div>
          </div>
          <label class="field" style="display:flex;align-items:center;gap:0.6rem;margin-top:0.75rem;">
            <input id="webdav-auto" name="autoSync" type="checkbox" ${cfg.autoSync ? "checked" : ""} />
            <span>Sync on open <span style="color:var(--muted);font-size:0.85rem;">(once a day; off by default)</span></span>
          </label>
          <div class="btn-row">
            <button class="btn" id="save-webdav" type="submit">Save WebDAV</button>
            <button class="btn btn--ghost" id="sync-now" type="button">Sync now</button>
          </div>
        </form>
        <p id="sync-status" style="color:var(--muted);font-size:0.85rem;margin-top:0.5rem;">
          ${cfg.lastSyncAt ? `Last sync: ${cfg.lastSyncAt}` : "Not synced yet."}
        </p>
      </div>`;
    const status = /** @type {HTMLElement|null} */ (this.querySelector("#sync-status"));
    this.querySelector("#export-sync")?.addEventListener("click", async () => {
      const doc = await buildLocalSyncDocument();
      await downloadJson(doc, `Chompass-sync-${new Date().toISOString().slice(0, 10)}.json`);
      if (status) status.textContent = "Sync JSON exported.";
    });
    this.querySelector("#import-sync")?.addEventListener("change", async (ev) => {
      const input = /** @type {HTMLInputElement} */ (ev.target);
      const file = input.files?.[0];
      if (!file) return;
      try {
        const doc = JSON.parse(await file.text());
        await importAndMergeSyncDocument(doc);
        if (status) status.textContent = "Sync JSON imported and merged.";
      } catch (err) {
        if (status) status.textContent = err instanceof Error ? err.message : "Import failed";
      } finally {
        input.value = "";
      }
    });
    this.querySelector("#webdav-form")?.addEventListener("submit", async (ev) => {
      ev.preventDefault();
      const form = /** @type {HTMLFormElement} */ (ev.target);
      const fd = new FormData(form);
      await saveWebDavSettings({
        url: String(fd.get("url") ?? ""),
        username: String(fd.get("username") ?? ""),
        password: String(fd.get("password") ?? ""),
        etag: cfg.etag,
        lastSyncAt: cfg.lastSyncAt,
        autoSync: Boolean(fd.get("autoSync")),
        autoSyncDay: cfg.autoSyncDay,
      });
      const saved = await loadWebDavSettings();
      const urlInput = /** @type {HTMLInputElement|null} */ (this.querySelector("#webdav-url"));
      if (urlInput) urlInput.value = saved.url;
      if (status) status.textContent = "WebDAV settings saved.";
    });
    this.querySelector("#sync-now")?.addEventListener("click", async () => {
      if (status) status.textContent = "Syncing…";
      const result = await syncWebDavNow();
      if (status) status.textContent = result.message;
    });
    bindSubpageBack(this, SETTINGS_PARENT.sync);
  }

  async renderAi() {
    const configuredProviders = await listConfiguredProviders();
    const p = await prefs.load();
    const initialProvider =
      (p.primaryAiProvider && PROVIDERS[p.primaryAiProvider] ? p.primaryAiProvider : null) ||
      configuredProviders[0] ||
      "gemini";
    const saved = await loadProviderKey(/** @type {any} */ (initialProvider)).catch(() => null);
    const fallbackProvider = p.fallbackAiProvider && PROVIDERS[p.fallbackAiProvider] ? p.fallbackAiProvider : "gemini";
    const primaryModel = resolveProviderModel(initialProvider, saved?.model, "primary");
    const fallbackModel = resolveProviderModel(fallbackProvider, p.fallbackAiModel, "fallback");

    const keyStatusLabel = saved ? "Key configured" : "No key saved";
    const keyStatusClass = saved ? "ai-key-status ai-key-status--ok" : "ai-key-status";
    const configuredList = configuredProviders.length
      ? configuredProviders.map((id) => PROVIDERS[id].label).join(", ")
      : "none";

    this.innerHTML = `
      ${subpageBar("AI & Speech", { backHref: SETTINGS_PARENT.ai })}
      <div class="card">
        <p style="color:var(--muted);margin:0 0 0.6rem;font-size:0.85rem;">
          Keys are encrypted at rest with Web Crypto (AES-GCM) in IndexedDB, then sent only from your browser to the provider you choose. Not a Chompass server.
        </p>
        <p style="color:var(--muted);margin:0 0 0.6rem;font-size:0.85rem;">
          With a cloud provider, food photos, meal notes, Coach chat (including your profile and recent logs), and your profile when AI estimates goals (plan and adaptive goals) are sent to that provider. Only the on-device Gemma 4 models (Android) guarantee that nothing leaves your device; the web app always uses a cloud provider.
        </p>
        <form class="entry-form" id="ai-key-form">
          <div class="field">
            <label class="checkbox-row">
              <input type="checkbox" name="aiFeaturesEnabled" value="true" ${p.aiFeaturesEnabled !== false ? "checked" : ""} />
              <span>${escapeHtml(t("settings.ai.ai_features"))}</span>
            </label>
            <p class="field-hint">${escapeHtml(t("settings.ai.ai_features_hint"))}</p>
          </div>
          <div class="field">
            <label for="ai-provider">Provider</label>
            <select id="ai-provider" name="provider">
              ${Object.entries(PROVIDERS)
                .map(
                  ([id, meta]) =>
                    `<option value="${id}" ${initialProvider === id ? "selected" : ""}>${meta.label}</option>`
                )
                .join("")}
            </select>
          </div>
          <div class="field">
            <div class="ai-key-label-row">
              <label for="ai-key">API key</label>
              <span id="ai-key-status" class="${keyStatusClass}" data-has-key="${saved ? "1" : "0"}">${keyStatusLabel}</span>
            </div>
            <input id="ai-key" name="apiKey" type="password" autocomplete="off" placeholder="${saved ? "•••••••• (leave blank to keep)" : "AIza… or sk-…"}" />
          </div>
          <div class="field-row field-row--2">
            <div class="field">
              <label for="ai-model">Model</label>
              <select id="ai-model" name="model">
                ${modelSelectOptionsHtml(initialProvider, primaryModel, "primary")}
              </select>
              <input id="ai-model-custom" name="modelCustom" type="text" placeholder="Custom model id" style="display:none;margin-top:0.4rem;" />
            </div>
            <div class="field" id="ai-reasoning-field" style="display:${initialProvider === "openai_compatible" ? "" : "none"}">
              <label for="ai-reasoning">Reasoning effort (OpenRouter)</label>
              <select id="ai-reasoning" name="reasoningEffort">
                ${["auto", "low", "medium", "high"]
                  .map(
                    (v) =>
                      `<option value="${v}" ${(p.openrouterReasoningEffort || "auto") === v ? "selected" : ""}>${
                        v === "auto" ? "Auto" : v[0].toUpperCase() + v.slice(1)
                      }</option>`
                  )
                  .join("")}
              </select>
            </div>
          </div>
          <div class="field-row field-row--2">
            <div class="field" id="ai-vision-field" style="display:${initialProvider === "openai_compatible" ? "" : "none"}">
              <label for="ai-vision-model">Vision model (photos)</label>
              <select id="ai-vision-model" name="visionModel">
                <option value="">(same as Model)</option>
                ${visionModelOptionsHtml(initialProvider, saved?.visionModel, primaryModel)}
              </select>
              <input id="ai-vision-model-custom" name="visionModelCustom" type="text" placeholder="Custom model id" style="display:none;margin-top:0.4rem;" />
            </div>
            <div class="field">
              <label for="ai-base-url">Base URL (openai-compatible)</label>
              <input id="ai-base-url" name="baseUrl" type="text" placeholder="https://api.openai.com/v1" value="${escapeAttr(saved?.baseUrl || "")}" />
            </div>
          </div>
          <div class="btn-row">
            <button type="submit" class="btn btn--primary">Save key</button>
            <button type="button" class="btn" id="ai-key-test">Test key</button>
            <button type="button" class="btn btn--danger" id="ai-key-remove">Remove</button>
          </div>
          <p id="ai-key-feedback" class="ai-key-feedback" role="status" aria-live="polite"></p>
        </form>
        <p style="color:var(--muted);font-size:0.85rem;margin-top:0.5rem;">
          Providers with a saved key: ${configuredList}
        </p>
      </div>
      <form class="entry-form card" id="ai-extra-form">
        <div class="field">
          <label for="userContext">Custom instructions</label>
          <textarea id="userContext" name="userContext" rows="3" placeholder="Preferences the coach and food AI should follow…">${escapeAttr(p.userContext || "")}</textarea>
        </div>
        <div class="field">
          <label class="checkbox-row">
            <input type="checkbox" name="mealConstituentsEnabled" value="true" ${p.mealConstituentsEnabled !== false ? "checked" : ""} />
            <span>${escapeHtml(t("settings.ai.meal_constituents"))}</span>
          </label>
          <p class="field-hint">${escapeHtml(t("settings.ai.meal_constituents_hint"))}</p>
        </div>
        <div class="field">
          <label for="servingUnitInferenceMode">${escapeHtml(t("settings.ai.serving_unit_mode"))}</label>
          <select id="servingUnitInferenceMode" name="servingUnitInferenceMode">
            <option value="gramsOnly" ${p.servingUnitInferenceMode === "gramsOnly" || !p.servingUnitInferenceMode ? "selected" : ""}>${escapeHtml(t("settings.ai.serving_unit_grams_only"))}</option>
            <option value="heuristic" ${p.servingUnitInferenceMode === "heuristic" ? "selected" : ""}>${escapeHtml(t("settings.ai.serving_unit_heuristic"))}</option>
            <option value="aiCall" ${p.servingUnitInferenceMode === "aiCall" ? "selected" : ""}>${escapeHtml(t("settings.ai.serving_unit_ai_call"))}</option>
          </select>
          <p class="field-hint">${escapeHtml(t("settings.ai.serving_unit_mode_hint"))}</p>
        </div>
        <div class="field">
          <label for="aiFallbackEnabled">Fallback provider on failure</label>
          <select id="aiFallbackEnabled" name="aiFallbackEnabled">
            <option value="false" ${!p.aiFallbackEnabled ? "selected" : ""}>Off</option>
            <option value="true" ${p.aiFallbackEnabled ? "selected" : ""}>On</option>
          </select>
        </div>
        <div class="field-row field-row--2">
          <div class="field">
            <label for="fallbackAiProvider">Fallback provider</label>
            <select id="fallbackAiProvider" name="fallbackAiProvider">
              ${Object.entries(PROVIDERS)
                .map(
                  ([id, meta]) =>
                    `<option value="${id}" ${fallbackProvider === id ? "selected" : ""}>${meta.label}</option>`
                )
                .join("")}
            </select>
          </div>
          <div class="field">
            <label for="fallbackAiModel">Fallback model</label>
            <select id="fallbackAiModel" name="fallbackAiModel">
              ${modelSelectOptionsHtml(fallbackProvider, fallbackModel, "fallback")}
            </select>
            <input id="fallbackAiModel-custom" name="fallbackAiModelCustom" type="text" placeholder="Custom model id" style="display:none;margin-top:0.4rem;" />
          </div>
        </div>
        <button type="submit" class="btn btn--primary">Save AI prefs</button>
      </form>
      <nav class="settings-nav" aria-label="Speech">
        <a href="#/settings?section=speech">Speech <span>Voice language (browser)</span></a>
      </nav>`;

    const providerSel = /** @type {HTMLSelectElement} */ (this.querySelector("#ai-provider"));
    const modelSel = /** @type {HTMLSelectElement} */ (this.querySelector("#ai-model"));
    const modelCustom = /** @type {HTMLInputElement} */ (this.querySelector("#ai-model-custom"));
    const fallbackProviderSel = /** @type {HTMLSelectElement} */ (this.querySelector("#fallbackAiProvider"));
    const fallbackModelSel = /** @type {HTMLSelectElement} */ (this.querySelector("#fallbackAiModel"));
    const fallbackModelCustom = /** @type {HTMLInputElement} */ (this.querySelector("#fallbackAiModel-custom"));

    const syncCustomVisibility = (sel, customInput) => {
      const show = sel.value === "__custom__";
      customInput.style.display = show ? "block" : "none";
      if (show) customInput.focus();
    };

    const setKeyStatus = (hasKey) => {
      const statusEl = /** @type {HTMLElement|null} */ (this.querySelector("#ai-key-status"));
      const keyInput = /** @type {HTMLInputElement|null} */ (this.querySelector("#ai-key"));
      if (statusEl) {
        statusEl.textContent = hasKey ? "Key configured" : "No key saved";
        statusEl.className = hasKey ? "ai-key-status ai-key-status--ok" : "ai-key-status";
        statusEl.dataset.hasKey = hasKey ? "1" : "0";
      }
      if (keyInput) {
        keyInput.placeholder = hasKey ? "•••••••• (leave blank to keep)" : "AIza… or sk-…";
        keyInput.value = "";
      }
    };

    const refreshPrimaryModels = async () => {
      const id = providerSel.value;
      const cfg = await loadProviderKey(/** @type {any} */ (id)).catch(() => null);
      const resolved = resolveProviderModel(id, cfg?.model, "primary");
      modelSel.innerHTML = modelSelectOptionsHtml(id, resolved, "primary");
      modelCustom.value = "";
      modelCustom.style.display = "none";
      const baseUrl = /** @type {HTMLInputElement|null} */ (this.querySelector("#ai-base-url"));
      if (baseUrl) baseUrl.value = cfg?.baseUrl || "";
      setKeyStatus(Boolean(cfg?.apiKey));
      this.setAiKeyFeedback("");
    };

    const refreshFallbackModels = () => {
      const id = fallbackProviderSel.value;
      const resolved = resolveProviderModel(id, p.fallbackAiModel, "fallback");
      fallbackModelSel.innerHTML = modelSelectOptionsHtml(id, resolved, "fallback");
      fallbackModelCustom.value = "";
      fallbackModelCustom.style.display = "none";
    };

    providerSel.addEventListener("change", () => {
      void refreshPrimaryModels();
      const isOpenAiCompatible = providerSel.value === "openai_compatible";
      const reasoningField = /** @type {HTMLElement|null} */ (this.querySelector("#ai-reasoning-field"));
      if (reasoningField) reasoningField.style.display = isOpenAiCompatible ? "" : "none";
      const visionField = /** @type {HTMLElement|null} */ (this.querySelector("#ai-vision-field"));
      if (visionField) visionField.style.display = isOpenAiCompatible ? "" : "none";
    });
    modelSel.addEventListener("change", () => syncCustomVisibility(modelSel, modelCustom));
    const visionModelSel = /** @type {HTMLSelectElement|null} */ (this.querySelector("#ai-vision-model"));
    const visionModelCustom = /** @type {HTMLInputElement|null} */ (this.querySelector("#ai-vision-model-custom"));
    if (visionModelSel && visionModelCustom) {
      visionModelSel.addEventListener("change", () => syncCustomVisibility(visionModelSel, visionModelCustom));
    }
    fallbackProviderSel.addEventListener("change", () => refreshFallbackModels());
    fallbackModelSel.addEventListener("change", () => syncCustomVisibility(fallbackModelSel, fallbackModelCustom));

    this.querySelector("#ai-key-form")?.addEventListener("submit", (ev) => this.onSaveAiKey(ev));
    this.querySelector("#ai-key-test")?.addEventListener("click", () => this.onTestAiKey());
    this.querySelector("#ai-key-remove")?.addEventListener("click", () => this.onRemoveAiKey());
    if (this._aiFlash) {
      this.setAiKeyFeedback(this._aiFlash, "ok");
      this._aiFlash = "";
    }
    this.querySelector("#ai-extra-form")?.addEventListener("submit", async (ev) => {
      ev.preventDefault();
      const fd = new FormData(/** @type {HTMLFormElement} */ (ev.target));
      let fbModel = String(fd.get("fallbackAiModel") || "");
      if (fbModel === "__custom__") fbModel = String(fd.get("fallbackAiModelCustom") || "").trim();
      const fbProvider = String(fd.get("fallbackAiProvider") || "gemini");
      await prefs.save({
        aiFeaturesEnabled: fd.get("aiFeaturesEnabled") === "true",
        userContext: String(fd.get("userContext") || ""),
        mealConstituentsEnabled: fd.get("mealConstituentsEnabled") === "true",
        servingUnitInferenceMode: /** @type {any} */ (String(fd.get("servingUnitInferenceMode") || "gramsOnly")),
        aiFallbackEnabled: fd.get("aiFallbackEnabled") === "true",
        fallbackAiProvider: fbProvider,
        fallbackAiModel: resolveProviderModel(fbProvider, fbModel, "fallback"),
      });
      location.hash = SETTINGS_PARENT.ai;
    });
    bindSubpageBack(this, SETTINGS_PARENT.ai);
  }

  async renderInstall() {
    const { promptInstall, hasDeferredInstallPrompt, isStandalone } = await import("../lib/install-prompt.js");
    const already = isStandalone();
    this.innerHTML = `
      ${subpageBar("Install app", { backHref: SETTINGS_PARENT.install })}
      <div class="card">
        <p style="margin:0 0 0.75rem;">Install Chompass to your home screen or dock for quicker access and a full-screen app. Your data stays in this browser.</p>
        ${
          already
            ? `<p class="install-note" style="margin:0;">You are already running the installed app.</p>`
            : `<div class="btn-row">
                <button type="button" class="btn btn--primary" id="install-cta">${
                  hasDeferredInstallPrompt() ? "Install" : "Add to Home Screen"
                }</button>
              </div>`
        }
      </div>
      <div class="card">
        <h2 class="chart-title">iPhone / iPad (Safari)</h2>
        <ol class="install-steps">
          <li>Open Chompass in <strong>Safari</strong> (required for a true home-screen app).</li>
          <li>Tap the <strong>Share</strong> button (square with an upward arrow).</li>
          <li>Choose <strong>Add to Home Screen</strong>, then Add.</li>
          <li>Open Chompass from the <strong>home-screen icon</strong> (not a Safari tab) for the full-screen app.</li>
        </ol>
        <p class="install-note">Brave, Chrome, and Firefox on iOS cannot install home-screen web apps. Apple only allows Safari to. If you use one of those, copy the address into Safari and follow the steps above.</p>
      </div>
      <div class="card">
        <h2 class="chart-title">Android (Chrome, Edge, Brave)</h2>
        <ol class="install-steps">
          <li>Open the browser menu (⋮).</li>
          <li>Tap <strong>Install app</strong> or <strong>Add to Home screen</strong>.</li>
          <li>Confirm. Open Chompass from the new icon afterward.</li>
        </ol>
        <p class="install-note">Many Chromium browsers do not show an automatic install popup. Use the menu. An Install banner may also appear when the browser allows it.</p>
      </div>
      <div class="card">
        <h2 class="chart-title">Firefox (Android)</h2>
        <ol class="install-steps">
          <li>Tap the Firefox menu (⋮).</li>
          <li>Tap <strong>Add to Home screen</strong> or <strong>Add app to Home screen</strong>.</li>
          <li>Confirm, then open from the new icon.</li>
        </ol>
        <p class="install-note">Firefox has no in-page install popup. If the menu item does nothing, set a Home app under Android Settings → Apps → Default apps (it must not be “None”). Desktop Firefox: bookmark the page; full PWA install is limited.</p>
      </div>
      <div class="card">
        <h2 class="chart-title">DuckDuckGo (Android)</h2>
        <ol class="install-steps">
          <li>Menu → <strong>Add to Home</strong> creates a shortcut only (not a full PWA).</li>
          <li>If that does nothing, your launcher may block shortcuts. Try Chrome or Firefox instead.</li>
        </ol>
        <p class="install-note">For a full-screen installed app, open this page in Chrome, Edge, or Brave, then use Install app / Add to Home screen.</p>
      </div>
      <div class="card">
        <h2 class="chart-title">Desktop (Chrome / Edge)</h2>
        <ol class="install-steps">
          <li>Look for the install icon in the address bar, or open the browser menu.</li>
          <li>Choose <strong>Install Chompass</strong> (or Install app).</li>
          <li>Launch from your dock, taskbar, or app launcher.</li>
        </ol>
        <p class="install-note">Chromium-based browsers work best for install, camera barcode, and speech. Meal photo and barcode also work with desktop webcams over HTTPS.</p>
      </div>
      <div class="card">
        <h2 class="chart-title">Already installed?</h2>
        <p style="margin:0;">Open Chompass from the home-screen or dock icon (not a normal browser tab) for the full-screen shell and offline app assets.</p>
      </div>`;
    this.querySelector("#install-cta")?.addEventListener("click", () => {
      void promptInstall();
    });
    bindSubpageBack(this, SETTINGS_PARENT.install);
  }

  async renderAbout() {
    this.innerHTML = `
      ${subpageBar("About", { backHref: SETTINGS_PARENT.about })}
      <div class="card">
        <p style="margin:0 0 0.6rem;">Chompass browser PWA. Local storage, no analytics. Compatible with the Android app diary and body-metrics JSON.</p>
        <p style="margin:0;"><a href="#/settings?section=install">How to install</a> this app on your phone or computer.</p>
      </div>
      <div class="card">
        <p style="margin:0;">Chompass is free and open source. If you'd like to say thanks, <a href="https://ko-fi.com/fitguy" target="_blank" rel="noopener noreferrer">buy fitguy a yogurt</a> on Ko-fi.</p>
      </div>
      <div class="card methods-card">
        <h2 class="chart-title">Calculation methods</h2>
        <dl class="methods-list">
          <dt>BMR-MSJ</dt><dd>Mifflin–St Jeor from sex, age, height, weight.</dd>
          <dt>BMR-KM</dt><dd>Katch–McArdle when body-fat % is set (lean mass based).</dd>
          <dt>TDEE</dt><dd>BMR × activity factor (PAL).</dd>
          <dt>CAL-ADJ</dt><dd>Goal calories from weekly kg pace × 7700/7.</dd>
          <dt>MACRO</dt><dd>Protein by activity (+ cut boost); fat 0.6×kg; carbs remainder. Keto clamps net carbs.</dd>
          <dt>FCAST</dt><dd>Theil–Sen weight slope + sparse-logging intake average.</dd>
          <dt>ADAPT</dt><dd>Weekly adaptive calorie nudge (±150) with floors/ceilings.</dd>
          <dt>US Navy BF%</dt><dd>From neck / waist / hips tape measures.</dd>
          <dt>RFM BF%</dt><dd>From waist and height (no neck). Display until you confirm Use as my body fat.</dd>
        </dl>
        <p style="color:var(--muted);font-size:0.85rem;margin:0.8rem 0 0;">Canonical register: <code>docs/CALCULATION_METHODS.md</code>. AI estimates are always reviewed before save.</p>
      </div>`;
    bindSubpageBack(this, SETTINGS_PARENT.about);
  }

  /** @param {string} message @param {"ok"|"err"|""} [kind] */
  setAiKeyFeedback(message, kind = "") {
    const el = /** @type {HTMLElement|null} */ (this.querySelector("#ai-key-feedback"));
    if (!el) return;
    el.textContent = message;
    el.className = kind ? `ai-key-feedback ai-key-feedback--${kind}` : "ai-key-feedback";
  }

  async onSaveAiKey(ev) {
    ev.preventDefault();
    const fd = new FormData(/** @type {HTMLFormElement} */ (ev.target));
    const provider = /** @type {any} */ (fd.get("provider"));
    const existing = await loadProviderKey(provider).catch(() => null);
    const typed = String(fd.get("apiKey") || "").trim();
    const apiKey = typed || existing?.apiKey || "";
    if (!apiKey) {
      this.setAiKeyFeedback("Enter an API key to save, or leave blank only when a key is already configured.", "err");
      return;
    }
    let model = String(fd.get("model") || "").trim();
    if (model === "__custom__") model = String(fd.get("modelCustom") || "").trim();
    model = resolveProviderModel(provider, model || existing?.model, "primary");
    let visionModel = String(fd.get("visionModel") || "").trim();
    if (visionModel === "__custom__") visionModel = String(fd.get("visionModelCustom") || "").trim();
    const baseUrl = String(fd.get("baseUrl") || "").trim();
    await saveProviderKey(provider, apiKey, {
      model: model || undefined,
      baseUrl: baseUrl || undefined,
      visionModel: visionModel || undefined,
    });
    await prefs.save({
      primaryAiProvider: provider,
      openrouterReasoningEffort: String(fd.get("reasoningEffort") || "auto"),
    });
    this._aiFlash = typed ? "API key saved." : "Provider settings updated (existing key kept).";
    this.render();
  }

  async onTestAiKey() {
    const form = /** @type {HTMLFormElement|null} */ (this.querySelector("#ai-key-form"));
    if (!form) return;
    const fd = new FormData(form);
    const provider = String(fd.get("provider") || "");
    const existing = await loadProviderKey(/** @type {any} */ (provider)).catch(() => null);
    const apiKey = String(fd.get("apiKey") || "").trim() || existing?.apiKey || "";
    if (!apiKey) {
      this.setAiKeyFeedback("Paste a key (or save one first) before testing.", "err");
      return;
    }
    if (provider !== "gemini") {
      this.setAiKeyFeedback("Quick test is available for Google (Gemini) keys. Save the key and try Analyze or Coach to verify other providers.", "err");
      return;
    }
    const btn = /** @type {HTMLButtonElement|null} */ (this.querySelector("#ai-key-test"));
    if (btn) btn.disabled = true;
    this.setAiKeyFeedback("Testing…");
    const result = await validateGeminiApiKey(apiKey);
    if (btn) btn.disabled = false;
    this.setAiKeyFeedback(result.ok ? "Key works." : /** @type {{ok:false, message:string}} */ (result).message, result.ok ? "ok" : "err");
  }

  async onRemoveAiKey() {
    const el = /** @type {HTMLSelectElement|null} */ (this.querySelector("#ai-provider"));
    const provider = /** @type {any} */ (el?.value);
    if (!provider) return;
    await deleteProviderKey(provider);
    this._aiFlash = "API key removed.";
    this.render();
  }

  async onExportDiary() {
    const format = /** @type {HTMLSelectElement|null} */ (this.querySelector("#export-format"))?.value || "json";
    const range = /** @type {HTMLSelectElement|null} */ (this.querySelector("#export-range"))?.value || "all";
    const allEntries = await foodEntries.all();
    const entries = filterDiaryRange(allEntries, /** @type {any} */ (range));
    const prof = await profileStore.load();
    const dates = entries.map((e) => e.date).sort();
    const dateRange = { start: dates[0] ?? "", end: dates[dates.length - 1] ?? "" };
    const targets = prof ? dailyTargets(prof) : null;
    const journal = await goalJournal.all();
    const allNotes = await dailyNotes.all();
    const notes = allNotes
      .filter((n) => n.date >= dateRange.start && n.date <= dateRange.end)
      .map((n) => ({ date: n.date, text: n.text }));
    if (format === "csv") {
      await downloadText(exportDiaryCsv(entries), `Chompass-Food-Diary-${dateRange.start}_to_${dateRange.end}.csv`, "text/csv");
      return;
    }
    if (format === "md") {
      await downloadText(
        exportDiaryMarkdown(entries, dateRange, targets, notes),
        `Chompass-Food-Diary-${dateRange.start}_to_${dateRange.end}.md`,
        "text/markdown"
      );
      return;
    }
    /** @type {Record<string, {calories: number, proteinG: number, carbsG: number, fatG: number}>} */
    const targetsByDay = {};
    if (prof && targets) {
      // Day types (#60): journal-first (frozen actuals for past days), live
      // resolution for gaps/today — matches the Android DiaryExporter.
      const today = new Date().toISOString().slice(0, 10);
      for (const e of entries) {
        if (!targetsByDay[e.date]) {
          targetsByDay[e.date] = resolveDayJournaled(journal, prof.macroPlan ?? null, targets, e.date, today).targets;
        }
      }
    }
    const doc = exportDiary({ entries, targets: targetsByDay, dateRange, notes });
    await downloadJson(doc, `Chompass-Food-Diary-${dateRange.start}_to_${dateRange.end}.json`);
  }

  async onExportBodyMetrics() {
    const format = /** @type {HTMLSelectElement|null} */ (this.querySelector("#body-format"))?.value || "json";
    const [w, bf, m] = await Promise.all([weights.all(), bodyFat.all(), measurements.all()]);
    if (format === "csv") {
      await downloadText(exportBodyMetricsCsv({ weights: w, bodyFat: bf, measurements: m }), "Chompass-Body-Metrics.csv", "text/csv");
      return;
    }
    const doc = exportBodyMetrics({ weights: w, bodyFat: bf, measurements: m });
    await downloadJson(doc, `Chompass-Weight-Import.json`);
  }

  async onImportDiary(ev) {
    const file = ev.target.files?.[0];
    if (!file) return;
    const status = this.querySelector("#import-status");
    try {
      const doc = JSON.parse(await file.text());
      const entries = importDiary(doc);
      await Promise.all(entries.map((e) => foodEntries.put(e)));
      if (status) status.textContent = `Imported ${entries.length} food entries.`;
    } catch (err) {
      if (status) status.textContent = `Import failed: ${err.message}`;
    }
    ev.target.value = "";
  }

  async onImportBodyMetrics(ev) {
    const file = ev.target.files?.[0];
    if (!file) return;
    const status = this.querySelector("#import-status");
    try {
      const doc = JSON.parse(await file.text());
      const { weights: w, bodyFat: bf, measurements: m } = await importBodyMetrics(doc);
      await Promise.all([...w.map((r) => weights.put(r)), ...bf.map((r) => bodyFat.put(r)), ...m.map((r) => measurements.put(r))]);
      if (status) status.textContent = `Imported ${w.length} weights, ${bf.length} body-fat, ${m.length} measurements.`;
    } catch (err) {
      if (status) status.textContent = `Import failed: ${err.message}`;
    }
    ev.target.value = "";
  }
}

function escapeHtml(s) {
  return String(s).replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]);
}

function escapeAttr(s) {
  return String(s).replace(/&/g, "&amp;").replace(/"/g, "&quot;").replace(/</g, "&lt;");
}

customElements.define("settings-view", SettingsView);

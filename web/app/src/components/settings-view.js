// @ts-check
import {
  profile as profileStore,
  foodEntries,
  weights,
  bodyFat,
  measurements,
  prefs,
  clearAllUserData,
} from "../lib/db.js";
import { dailyTargets, bmr, tdee } from "../lib/chompass-core/formulas.js";
import { computeWeightForecast } from "../lib/chompass-core/forecast.js";
import { exportDiary, importDiary } from "../lib/chompass-core/diary-format.js";
import { exportBodyMetrics, importBodyMetrics } from "../lib/chompass-core/body-metrics-format.js";
import {
  exportDiaryMarkdown,
  exportDiaryCsv,
  exportBodyMetricsCsv,
  filterDiaryRange,
} from "../lib/chompass-core/export-text.js";
import { PROVIDERS, modelSelectOptionsHtml, resolveProviderModel } from "../lib/ai/providers.js";
import { saveProviderKey, deleteProviderKey, listConfiguredProviders, loadProviderKey } from "../lib/ai/key-storage.js";
import { validateGeminiApiKey } from "../lib/ai/validate-key.js";
import {
  calculateGoalsWithAi,
  recalculatedFromFormulas,
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
} from "../lib/home-nutrients.js";

const ACTIVITY_LEVELS = [
  { id: "sedentary", label: "Sedentary" },
  { id: "light", label: "Light" },
  { id: "moderate", label: "Moderate" },
  { id: "active", label: "Active" },
  { id: "very_active", label: "Very active" },
  { id: "extra_active", label: "Extra active" },
];
const ACCENTS = ["teal", "blue", "green", "purple", "pink", "orange", "indigo", "neutral"];

const SPEECH_LANGS = [
  { id: "", label: "Browser default" },
  { id: "en-US", label: "English (US)" },
  { id: "en-GB", label: "English (UK)" },
  { id: "de-DE", label: "Deutsch" },
  { id: "fr-FR", label: "Français" },
  { id: "es-ES", label: "Español" },
  { id: "it-IT", label: "Italiano" },
  { id: "nl-NL", label: "Nederlands" },
  { id: "pt-BR", label: "Português (BR)" },
  { id: "sv-SE", label: "Svenska" },
];

const OPTIONAL_GOAL_FIELDS = Object.keys(DEFAULT_OPTIONAL_NUTRIENT_GOALS).map((key) => {
  const def = nutrientDef(key);
  return /** @type {[string, string]} */ ([key, def ? `${def.label} (${def.unit})` : key]);
});

/** Parent hash for nested settings pages (mirrors Android hub groups). */
const SETTINGS_PARENT = {
  personal: "#/settings",
  profile: "#/settings",
  goals: "#/settings",
  nutrients: "#/settings?section=goals",
  app: "#/settings",
  units: "#/settings?section=app",
  home: "#/settings?section=app",
  install: "#/settings?section=app",
  ai: "#/settings",
  speech: "#/settings?section=ai",
  data: "#/settings",
  sync: "#/settings?section=data",
  about: "#/settings",
};

export class SettingsView extends HTMLElement {
  connectedCallback() {
    const params = new URLSearchParams(location.hash.split("?")[1] ?? "");
    this.section = params.get("section") || "hub";
    // Legacy alias: profile → personal (Android “Personal Info” group).
    if (this.section === "profile") this.section = "personal";
    this.render();
  }

  async render() {
    if (this.section === "hub") {
      this.innerHTML = `
        <h1 class="screen-title">Settings</h1>
        <nav class="settings-nav" aria-label="Settings sections">
          <a href="#/settings?section=personal">Personal Info <span>Gender, height, weight, measurements</span></a>
          <a href="#/settings?section=goals">Goals &amp; Nutrition <span>Diet, macros, optional nutrients</span></a>
          <a href="#/settings?section=app">App &amp; Display <span>Units, home, install</span></a>
          <a href="#/settings?section=ai">AI &amp; Speech <span>Providers, models, speech</span></a>
          <a href="#/settings?section=data">Health, Data &amp; Sync <span>Import / export, WebDAV</span></a>
          <a href="#/settings?section=about">About <span>Formulas &amp; methods</span></a>
        </nav>
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
      ${subpageBar("App & Display", { backHref: SETTINGS_PARENT.app })}
      <nav class="settings-nav" aria-label="App settings">
        <a href="#/settings?section=units">Units &amp; schedule <span>Units, week, meals, accent</span></a>
        <a href="#/settings?section=home">Home display <span>Water, gauge, chips</span></a>
        <a href="#/settings?section=install">Install app <span>Home screen &amp; browsers</span></a>
      </nav>`;
    bindSubpageBack(this, SETTINGS_PARENT.app);
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
        <div class="field">
          <label for="activityLevel">Activity</label>
          <select id="activityLevel" name="activityLevel">
            ${ACTIVITY_LEVELS.map((a) => `<option value="${a.id}" ${p.activityLevel === a.id ? "selected" : ""}>${a.label}</option>`).join("")}
          </select>
        </div>
        <button type="submit" class="btn btn--primary">Save</button>
      </form>
      <nav class="settings-nav" aria-label="Related">
        <a href="#/measurements">Body measurements <span>Tape / US Navy</span></a>
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
            <input id="customCalories" name="customCalories" type="number" min="0" value="${p.customCalories ?? ""}" placeholder="${targets.calories}" />
          </div>
        </div>
        <p style="color:var(--muted);font-size:0.82rem;margin:0 0 0.5rem;">Formula targets: ${targets.calories} kcal · ${Math.round(targets.proteinG)}P / ${Math.round(targets.carbsG)}C / ${Math.round(targets.fatG)}F. Leave blank to use formula.</p>
        <div class="field-row">
          <div class="field">
            <label for="customProtein">Custom protein g</label>
            <input id="customProtein" name="customProtein" type="number" min="0" value="${p.customProtein ?? ""}" placeholder="${Math.round(targets.proteinG)}" />
          </div>
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
          BMR ${Math.round(bmr(p))} · TDEE ${Math.round(tdee(p))} kcal
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
      </nav>`;
    this.querySelector("#goals-form")?.addEventListener("submit", async (ev) => {
      ev.preventDefault();
      const fd = new FormData(/** @type {HTMLFormElement} */ (ev.target));
      const paceRaw = fd.get("weeklyChangeKg");
      const goalW = fd.get("goalWeightKg");
      const custom = fd.get("customCalories");
      const customProtein = fd.get("customProtein");
      const customCarbs = fd.get("customCarbs");
      const customFat = fd.get("customFat");
      await profileStore.save({
        ...p,
        goal: /** @type {any} */ (fd.get("goal")),
        weeklyChangeKg: paceRaw ? Number(paceRaw) : null,
        goalWeightKg: goalW ? Number(goalW) : null,
        ketoMode: fd.get("ketoMode") === "true",
        customCalories: custom ? Number(custom) : null,
        customProtein: customProtein ? Number(customProtein) : null,
        customCarbs: customCarbs ? Number(customCarbs) : null,
        customFat: customFat ? Number(customFat) : null,
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
        ? "Uses your AI provider with your profile and recent food/weight logs to refresh calorie targets. Macros stay formula-based on the web app."
        : "No AI key configured — resets calories to formula defaults from your height/weight/activity/goal. Add an AI key in Settings for the same AI recalculation as Android.",
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
        await profileStore.save(recalculatedFromFormulas(profile));
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
      await profileStore.save({ ...profile, customCalories: result.calories });
      const reason = result.reason ? ` ${result.reason}` : "";
      this.render();
      const after = /** @type {HTMLElement | null} */ (this.querySelector("#recalc-status"));
      if (after) {
        after.hidden = false;
        after.textContent = `Updated to ${result.calories} kcal.${reason}`;
      }
    } catch (err) {
      const msg = err instanceof Error ? err.message : String(err);
      if (status) {
        status.hidden = false;
        status.textContent = `Couldn't recalculate — goals unchanged. Check your AI key in Settings. (${msg})`;
      }
      if (btn) btn.disabled = false;
    }
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
          <label for="weekStartsOnMonday">Week starts</label>
          <select id="weekStartsOnMonday" name="weekStartsOnMonday">
            <option value="true" ${p.weekStartsOnMonday !== false ? "selected" : ""}>Monday</option>
            <option value="false" ${p.weekStartsOnMonday === false ? "selected" : ""}>Sunday</option>
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
        accent: String(fd.get("accent") || "teal"),
        weekStartsOnMonday: fd.get("weekStartsOnMonday") === "true",
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
      ${subpageBar("Speech", { backHref: SETTINGS_PARENT.speech })}
      <form class="entry-form card" id="speech-form">
        <p style="color:var(--muted);margin:0 0 0.75rem;font-size:0.88rem;">
          Voice dictation uses the browser’s on-device speech recognition (Chrome/Edge best). Cloud speech engines are Android-only.
        </p>
        <div class="field">
          <label for="speechLang">Language</label>
          <select id="speechLang" name="speechLang">
            ${SPEECH_LANGS.map((l) => `<option value="${l.id}" ${l.id === current ? "selected" : ""}>${l.label}</option>`).join("")}
          </select>
        </div>
        <button type="submit" class="btn btn--primary">Save</button>
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
          <label for="calorieGaugeMode">Calorie gauge</label>
          <select id="calorieGaugeMode" name="calorieGaugeMode">
            <option value="static" ${p.calorieGaugeMode !== "add_active" ? "selected" : ""}>Static (full target)</option>
            <option value="add_active" ${p.calorieGaugeMode === "add_active" ? "selected" : ""}>Add active (sedentary budget)</option>
          </select>
          <p class="nutrient-picker__hint">Add active: set Activity Level to everyday baseline (not peak training). Measured active burn is Android/Health Connect only; here the estimate is TDEE − BMR.</p>
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
        calorieGaugeMode: /** @type {any} */ (fd.get("calorieGaugeMode")),
        adaptiveGoals: fd.get("adaptiveGoals") === "true",
        homeNutrientCardCount: cardCount,
        homeTopNutrients: normalizeHomeTopNutrients(tubeRaw, cardCount),
        foodLogMacroChips: normalizeFoodLogChips(chipRaw),
      });
      location.hash = SETTINGS_PARENT.home;
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
              <input id="${k}" name="${k}" type="number" min="0" step="1" value="${g[k] ?? ""}" />
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
        optionalNutrientGoals[k] = Number.isFinite(n) ? Math.max(0, n) : DEFAULT_OPTIONAL_NUTRIENT_GOALS[k];
      }
      await prefs.save({ optionalNutrientGoals });
      location.hash = SETTINGS_PARENT.nutrients;
    });
    bindSubpageBack(this, SETTINGS_PARENT.nutrients);
  }

  async renderData() {
    this.innerHTML = `
      ${subpageBar("Health, Data & Sync", { backHref: SETTINGS_PARENT.data })}
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
          Optional user-hosted sync. Chompass has no cloud account — point both the PWA and Android app at the same WebDAV file (e.g. Nextcloud), or move a sync JSON by hand. API keys and food photos are not included.
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
        <form class="entry-form" id="ai-key-form">
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
    });
    modelSel.addEventListener("change", () => syncCustomVisibility(modelSel, modelCustom));
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
        userContext: String(fd.get("userContext") || ""),
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
          <li>Open Chompass from the <strong>home-screen icon</strong> — not a Safari tab — for the full-screen app.</li>
        </ol>
        <p class="install-note">Brave, Chrome, and Firefox on iOS cannot install home-screen web apps — Apple only allows Safari to. If you use one of those, copy the address into Safari and follow the steps above.</p>
      </div>
      <div class="card">
        <h2 class="chart-title">Android (Chrome, Edge, Brave)</h2>
        <ol class="install-steps">
          <li>Open the browser menu (⋮).</li>
          <li>Tap <strong>Install app</strong> or <strong>Add to Home screen</strong>.</li>
          <li>Confirm. Open Chompass from the new icon afterward.</li>
        </ol>
        <p class="install-note">Many Chromium browsers do not show an automatic install popup — use the menu. An Install banner may also appear when the browser allows it.</p>
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
          <li>If that does nothing, your launcher may block shortcuts — try Chrome or Firefox instead.</li>
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
    const baseUrl = String(fd.get("baseUrl") || "").trim();
    await saveProviderKey(provider, apiKey, { model: model || undefined, baseUrl: baseUrl || undefined });
    await prefs.save({ primaryAiProvider: provider });
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
    if (format === "csv") {
      await downloadText(exportDiaryCsv(entries), `Chompass-Food-Diary-${dateRange.start}_to_${dateRange.end}.csv`, "text/csv");
      return;
    }
    if (format === "md") {
      await downloadText(
        exportDiaryMarkdown(entries, dateRange, targets),
        `Chompass-Food-Diary-${dateRange.start}_to_${dateRange.end}.md`,
        "text/markdown"
      );
      return;
    }
    /** @type {Record<string, {calories: number, proteinG: number, carbsG: number, fatG: number}>} */
    const targetsByDay = {};
    if (targets) {
      for (const e of entries) targetsByDay[e.date] = targets;
    }
    const doc = exportDiary({ entries, targets: targetsByDay, dateRange });
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

function escapeAttr(s) {
  return String(s).replace(/&/g, "&amp;").replace(/"/g, "&quot;").replace(/</g, "&lt;");
}

customElements.define("settings-view", SettingsView);

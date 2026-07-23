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
import { dailyTargets, bmr, tdee } from "../lib/nofud-core/formulas.js";
import { exportDiary, importDiary } from "../lib/nofud-core/diary-format.js";
import { exportBodyMetrics, importBodyMetrics } from "../lib/nofud-core/body-metrics-format.js";
import { PROVIDERS } from "../lib/ai/providers.js";
import { saveProviderKey, deleteProviderKey, listConfiguredProviders } from "../lib/ai/key-storage.js";
import { openConfirm } from "../lib/ui/dialog.js";
import { subpageBar, bindSubpageBack } from "../lib/ui/subpage.js";

const ACTIVITY_LEVELS = ["sedentary", "light", "moderate", "active", "very_active", "extra_active"];
const ACCENTS = ["teal", "blue", "green", "purple", "pink", "orange", "indigo", "neutral"];

export class SettingsView extends HTMLElement {
  connectedCallback() {
    const params = new URLSearchParams(location.hash.split("?")[1] ?? "");
    this.section = params.get("section") || "hub";
    this.render();
  }

  async render() {
    if (this.section === "hub") {
      this.innerHTML = `
        <h1 class="screen-title">Settings</h1>
        <nav class="settings-nav" aria-label="Settings sections">
          <a href="#/settings?section=profile">Profile <span>Body &amp; identity</span></a>
          <a href="#/settings?section=goals">Goals &amp; diet <span>Calories, keto</span></a>
          <a href="#/settings?section=units">Units &amp; appearance <span>kg/lb, theme</span></a>
          <a href="#/settings?section=home">Home display <span>Water, gauge</span></a>
          <a href="#/settings?section=data">Data <span>Import / export / clear</span></a>
          <a href="#/settings?section=ai">AI keys <span>BYOK providers</span></a>
          <a href="#/settings?section=about">About &amp; methods <span>Formulas</span></a>
          <a href="#/measurements">Body measurements <span>Tape metrics</span></a>
        </nav>`;
      return;
    }

    if (this.section === "profile") {
      await this.renderProfile();
      return;
    }
    if (this.section === "goals") {
      await this.renderGoals();
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
    if (this.section === "data") {
      await this.renderData();
      return;
    }
    if (this.section === "ai") {
      await this.renderAi();
      return;
    }
    if (this.section === "about") {
      this.innerHTML = `
        ${subpageBar("About & methods", { backHref: "#/settings" })}
        <div class="card">
          <p style="margin:0 0 0.6rem;">NoFUD companion PWA — local-first, no analytics. Compatible with the Android app's diary and body-metrics JSON.</p>
          <p style="margin:0;color:var(--muted);font-size:0.9rem;">
            Deterministic formulas (BMR Mifflin / Katch-McArdle, TDEE, macros, keto carbs, US Navy BF%, FCAST / ADAPT)
            mirror <code>docs/CALCULATION_METHODS.md</code>. AI estimates are reviewed before save.
          </p>
        </div>`;
      bindSubpageBack(this, "#/settings");
      return;
    }
    this.section = "hub";
    this.render();
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
      ${subpageBar("Profile", { backHref: "#/settings" })}
      <form class="entry-form card" id="profile-form">
        <div class="field-row">
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
            ${ACTIVITY_LEVELS.map((a) => `<option value="${a}" ${p.activityLevel === a ? "selected" : ""}>${a.replace("_", " ")}</option>`).join("")}
          </select>
        </div>
        <button type="submit" class="btn btn--primary">Save</button>
      </form>`;
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
      location.hash = "#/settings";
    });
    bindSubpageBack(this, "#/settings");
  }

  async renderGoals() {
    const p = await this.loadProfile();
    const targets = dailyTargets(p);
    this.innerHTML = `
      ${subpageBar("Goals & diet", { backHref: "#/settings" })}
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
            <input id="customCalories" name="customCalories" type="number" min="0" value="${p.customCalories ?? ""}" placeholder="formula" />
          </div>
        </div>
        <button type="submit" class="btn btn--primary">Save goals</button>
        <button type="button" class="btn btn--ghost" id="clear-custom">Clear custom calories</button>
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
      </div>`;
    this.querySelector("#goals-form")?.addEventListener("submit", async (ev) => {
      ev.preventDefault();
      const fd = new FormData(/** @type {HTMLFormElement} */ (ev.target));
      const paceRaw = fd.get("weeklyChangeKg");
      const goalW = fd.get("goalWeightKg");
      const custom = fd.get("customCalories");
      await profileStore.save({
        ...p,
        goal: /** @type {any} */ (fd.get("goal")),
        weeklyChangeKg: paceRaw ? Number(paceRaw) : null,
        goalWeightKg: goalW ? Number(goalW) : null,
        ketoMode: fd.get("ketoMode") === "true",
        customCalories: custom ? Number(custom) : null,
      });
      location.hash = "#/settings";
    });
    this.querySelector("#clear-custom")?.addEventListener("click", async () => {
      await profileStore.save({ ...p, customCalories: null });
      this.render();
    });
    bindSubpageBack(this, "#/settings");
  }

  async renderUnits() {
    const p = await prefs.load();
    this.innerHTML = `
      ${subpageBar("Units & appearance", { backHref: "#/settings" })}
      <form class="entry-form card" id="units-form">
        <div class="field-row">
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
      });
      window.dispatchEvent(new Event("nofud-prefs-changed"));
      location.hash = "#/settings";
    });
    bindSubpageBack(this, "#/settings");
  }

  async renderHome() {
    const p = await prefs.load();
    this.innerHTML = `
      ${subpageBar("Home display", { backHref: "#/settings" })}
      <form class="entry-form card" id="home-form">
        <div class="field">
          <label for="showWater">Show water row</label>
          <select id="showWater" name="showWater">
            <option value="true" ${p.showWater !== false ? "selected" : ""}>On</option>
            <option value="false" ${p.showWater === false ? "selected" : ""}>Off</option>
          </select>
        </div>
        <div class="field">
          <label for="waterGoalMl">Water goal (ml)</label>
          <input id="waterGoalMl" name="waterGoalMl" type="number" min="0" value="${p.waterGoalMl ?? 2500}" />
        </div>
        <div class="field">
          <label for="calorieGaugeMode">Calorie gauge</label>
          <select id="calorieGaugeMode" name="calorieGaugeMode">
            <option value="static" ${p.calorieGaugeMode !== "add_active" ? "selected" : ""}>Static (full target)</option>
            <option value="add_active" ${p.calorieGaugeMode === "add_active" ? "selected" : ""}>Add active (sedentary budget)</option>
          </select>
        </div>
        <div class="field">
          <label for="adaptiveGoals">Adaptive goals (Progress)</label>
          <select id="adaptiveGoals" name="adaptiveGoals">
            <option value="false" ${!p.adaptiveGoals ? "selected" : ""}>Off</option>
            <option value="true" ${p.adaptiveGoals ? "selected" : ""}>On</option>
          </select>
        </div>
        <button type="submit" class="btn btn--primary">Save</button>
      </form>`;
    this.querySelector("#home-form")?.addEventListener("submit", async (ev) => {
      ev.preventDefault();
      const fd = new FormData(/** @type {HTMLFormElement} */ (ev.target));
      await prefs.save({
        showWater: fd.get("showWater") === "true",
        waterGoalMl: Number(fd.get("waterGoalMl") || 2500),
        calorieGaugeMode: /** @type {any} */ (fd.get("calorieGaugeMode")),
        adaptiveGoals: fd.get("adaptiveGoals") === "true",
      });
      location.hash = "#/settings";
    });
    bindSubpageBack(this, "#/settings");
  }

  async renderData() {
    this.innerHTML = `
      ${subpageBar("Data", { backHref: "#/settings" })}
      <div class="card">
        <p style="color:var(--muted);margin:0 0 0.6rem;font-size:0.85rem;">
          Same JSON format as the Android app — move data freely between the two.
        </p>
        <div class="btn-row">
          <button class="btn btn--ghost" id="export-diary">Export diary</button>
          <label class="btn btn--ghost" style="cursor:pointer;">Import diary
            <input type="file" accept="application/json" id="import-diary" style="display:none;" />
          </label>
        </div>
        <div class="btn-row">
          <button class="btn btn--ghost" id="export-body">Export body metrics</button>
          <label class="btn btn--ghost" style="cursor:pointer;">Import body metrics
            <input type="file" accept="application/json" id="import-body" style="display:none;" />
          </label>
        </div>
        <p id="import-status" style="color:var(--muted);font-size:0.85rem;margin-top:0.5rem;"></p>
        <button class="btn btn--danger" id="clear-all" style="margin-top:0.8rem;">Clear all local data</button>
      </div>`;
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
    bindSubpageBack(this, "#/settings");
  }

  async renderAi() {
    const configuredProviders = await listConfiguredProviders();
    this.innerHTML = `
      ${subpageBar("AI keys", { backHref: "#/settings" })}
      <div class="card">
        <p style="color:var(--muted);margin:0 0 0.6rem;font-size:0.85rem;">
          Keys are encrypted at rest and only sent directly from your browser to the provider.
        </p>
        <form class="entry-form" id="ai-key-form">
          <div class="field">
            <label for="ai-provider">Provider</label>
            <select id="ai-provider" name="provider">
              ${Object.entries(PROVIDERS).map(([id, meta]) => `<option value="${id}">${meta.label}</option>`).join("")}
            </select>
          </div>
          <div class="field">
            <label for="ai-key">API key</label>
            <input id="ai-key" name="apiKey" type="password" autocomplete="off" placeholder="sk-…" />
          </div>
          <div class="field-row">
            <div class="field">
              <label for="ai-model">Model (optional)</label>
              <input id="ai-model" name="model" type="text" placeholder="provider default" />
            </div>
            <div class="field">
              <label for="ai-base-url">Base URL (openai-compatible)</label>
              <input id="ai-base-url" name="baseUrl" type="text" placeholder="https://api.openai.com/v1" />
            </div>
          </div>
          <div class="btn-row">
            <button type="submit" class="btn btn--primary">Save key</button>
            <button type="button" class="btn btn--danger" id="ai-key-remove">Remove</button>
          </div>
        </form>
        <p style="color:var(--muted);font-size:0.85rem;margin-top:0.5rem;">
          Configured: ${configuredProviders.length ? configuredProviders.map((id) => PROVIDERS[id].label).join(", ") : "none"}
        </p>
      </div>`;
    this.querySelector("#ai-key-form")?.addEventListener("submit", (ev) => this.onSaveAiKey(ev));
    this.querySelector("#ai-key-remove")?.addEventListener("click", () => this.onRemoveAiKey());
    bindSubpageBack(this, "#/settings");
  }

  async onSaveAiKey(ev) {
    ev.preventDefault();
    const fd = new FormData(ev.target);
    const provider = /** @type {any} */ (fd.get("provider"));
    const apiKey = String(fd.get("apiKey") || "").trim();
    if (!apiKey) return;
    const model = String(fd.get("model") || "").trim();
    const baseUrl = String(fd.get("baseUrl") || "").trim();
    await saveProviderKey(provider, apiKey, { model: model || undefined, baseUrl: baseUrl || undefined });
    this.render();
  }

  async onRemoveAiKey() {
    const el = /** @type {HTMLSelectElement|null} */ (this.querySelector("#ai-provider"));
    const provider = /** @type {any} */ (el?.value);
    if (!provider) return;
    await deleteProviderKey(provider);
    this.render();
  }

  async onExportDiary() {
    const entries = await foodEntries.all();
    const prof = await profileStore.load();
    /** @type {Record<string, {calories: number, proteinG: number, carbsG: number, fatG: number}>} */
    const targets = {};
    if (prof) {
      const t = dailyTargets(prof);
      for (const e of entries) targets[e.date] = t;
    }
    const dates = entries.map((e) => e.date).sort();
    const dateRange = { start: dates[0] ?? "", end: dates[dates.length - 1] ?? "" };
    const doc = exportDiary({ entries, targets, dateRange });
    downloadJson(doc, `NoFUD-Food-Diary-${dateRange.start}_to_${dateRange.end}.json`);
  }

  async onExportBodyMetrics() {
    const [w, bf, m] = await Promise.all([weights.all(), bodyFat.all(), measurements.all()]);
    const doc = exportBodyMetrics({ weights: w, bodyFat: bf, measurements: m });
    downloadJson(doc, `NoFUD-Weight-Import.json`);
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

function downloadJson(doc, filename) {
  const blob = new Blob([JSON.stringify(doc, null, 2)], { type: "application/json" });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = filename;
  a.click();
  URL.revokeObjectURL(url);
}

customElements.define("settings-view", SettingsView);

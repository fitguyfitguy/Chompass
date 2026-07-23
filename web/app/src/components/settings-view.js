// @ts-check
import { profile as profileStore, foodEntries, weights, bodyFat, measurements } from "../lib/db.js";
import { dailyTargets, bmr, tdee } from "../lib/nofud-core/formulas.js";
import { exportDiary, importDiary } from "../lib/nofud-core/diary-format.js";
import { exportBodyMetrics, importBodyMetrics } from "../lib/nofud-core/body-metrics-format.js";
import { PROVIDERS } from "../lib/ai/providers.js";
import { saveProviderKey, deleteProviderKey, listConfiguredProviders } from "../lib/ai/key-storage.js";

const ACTIVITY_LEVELS = ["sedentary", "light", "moderate", "active", "very_active", "extra_active"];

export class SettingsView extends HTMLElement {
  connectedCallback() {
    this.render();
  }

  async render() {
    const p = (await profileStore.load()) ?? {
      sex: "other", age: 30, heightCm: 170, weightKg: 70,
      bodyFatPercentage: null, activityLevel: "moderate", goal: "maintain",
      weeklyChangeKg: null, ketoMode: false,
    };

    const targets = dailyTargets(p);
    const configuredProviders = await listConfiguredProviders();

    this.innerHTML = `
      <h1 style="font-family:var(--font-display);font-size:1.4rem;margin:0.2rem 0 1rem;">Profile & settings</h1>
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
          <div class="field">
            <label for="heightCm">Height cm</label>
            <input id="heightCm" name="heightCm" type="number" min="1" value="${p.heightCm}" />
          </div>
        </div>
        <div class="field-row">
          <div class="field">
            <label for="weightKg">Weight kg</label>
            <input id="weightKg" name="weightKg" type="number" step="0.1" min="1" value="${p.weightKg}" />
          </div>
          <div class="field">
            <label for="bodyFatPercentage">Body fat %</label>
            <input id="bodyFatPercentage" name="bodyFatPercentage" type="number" step="0.1" min="0" max="100"
              value="${p.bodyFatPercentage != null ? p.bodyFatPercentage * 100 : ""}" />
          </div>
          <div class="field">
            <label for="weeklyChangeKg">Pace kg/wk</label>
            <input id="weeklyChangeKg" name="weeklyChangeKg" type="number" step="0.05" min="0"
              value="${p.weeklyChangeKg ?? ""}" placeholder="0.5" />
          </div>
        </div>
        <div class="field-row">
          <div class="field">
            <label for="activityLevel">Activity</label>
            <select id="activityLevel" name="activityLevel">
              ${ACTIVITY_LEVELS.map((a) => `<option value="${a}" ${p.activityLevel === a ? "selected" : ""}>${a.replace("_", " ")}</option>`).join("")}
            </select>
          </div>
          <div class="field">
            <label for="goal">Goal</label>
            <select id="goal" name="goal">
              ${["lose", "maintain", "gain"].map((g) => `<option value="${g}" ${p.goal === g ? "selected" : ""}>${g}</option>`).join("")}
            </select>
          </div>
          <div class="field">
            <label for="ketoMode">Diet mode</label>
            <select id="ketoMode" name="ketoMode">
              <option value="false" ${!p.ketoMode ? "selected" : ""}>Standard</option>
              <option value="true" ${p.ketoMode ? "selected" : ""}>Keto</option>
            </select>
          </div>
        </div>
        <button type="submit" class="btn btn--primary">Save profile</button>
      </form>

      <div class="card">
        <h2 style="margin:0 0 0.6rem;font-size:1rem;">Calculated targets</h2>
        <p style="color:var(--muted);margin:0 0 0.6rem;font-size:0.85rem;">
          BMR ${Math.round(bmr(p))} kcal · TDEE ${Math.round(tdee(p))} kcal
        </p>
        <div class="totals-ring">
          <div><strong>${targets.calories}</strong><span>Calories</span></div>
          <div><strong>${Math.round(targets.proteinG)}</strong><span>Protein g</span></div>
          <div><strong>${Math.round(targets.carbsG)}</strong><span>Carbs g</span></div>
          <div><strong>${Math.round(targets.fatG)}</strong><span>Fat g</span></div>
        </div>
      </div>

      <div class="card">
        <h2 style="margin:0 0 0.6rem;font-size:1rem;">Diary export / import</h2>
        <p style="color:var(--muted);margin:0 0 0.6rem;font-size:0.85rem;">
          Same JSON format as the Android app — move your data freely between the two.
        </p>
        <div class="btn-row">
          <button class="btn btn--ghost" id="export-diary">Export diary JSON</button>
          <label class="btn btn--ghost" style="cursor:pointer;">
            Import diary JSON
            <input type="file" accept="application/json" id="import-diary" style="display:none;" />
          </label>
        </div>
        <div class="btn-row">
          <button class="btn btn--ghost" id="export-body">Export body-metrics JSON</button>
          <label class="btn btn--ghost" style="cursor:pointer;">
            Import body-metrics JSON
            <input type="file" accept="application/json" id="import-body" style="display:none;" />
          </label>
        </div>
        <p id="import-status" style="color:var(--muted);font-size:0.85rem;margin-top:0.5rem;"></p>
      </div>

      <div class="card">
        <h2 style="margin:0 0 0.6rem;font-size:1rem;">AI Coach (bring your own key)</h2>
        <p style="color:var(--muted);margin:0 0 0.6rem;font-size:0.85rem;">
          Your key is encrypted at rest with a non-extractable device key and is only ever sent directly
          from your browser to the provider you choose below — never through a NoFUD server.
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
              <label for="ai-base-url">Base URL (openai-compatible only)</label>
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
      </div>
    `;

    this.querySelector("#profile-form").addEventListener("submit", (ev) => this.onSaveProfile(ev));
    this.querySelector("#export-diary").addEventListener("click", () => this.onExportDiary());
    this.querySelector("#export-body").addEventListener("click", () => this.onExportBodyMetrics());
    this.querySelector("#import-diary").addEventListener("change", (ev) => this.onImportDiary(ev));
    this.querySelector("#import-body").addEventListener("change", (ev) => this.onImportBodyMetrics(ev));
    this.querySelector("#ai-key-form").addEventListener("submit", (ev) => this.onSaveAiKey(ev));
    this.querySelector("#ai-key-remove").addEventListener("click", () => this.onRemoveAiKey());
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
    const provider = /** @type {any} */ (this.querySelector("#ai-provider").value);
    await deleteProviderKey(provider);
    this.render();
  }

  async onSaveProfile(ev) {
    ev.preventDefault();
    const fd = new FormData(ev.target);
    const bfRaw = fd.get("bodyFatPercentage");
    const paceRaw = fd.get("weeklyChangeKg");
    /** @type {import('../lib/nofud-core/models.js').UserProfile} */
    const p = {
      sex: /** @type {any} */ (fd.get("sex")),
      age: Number(fd.get("age")),
      heightCm: Number(fd.get("heightCm")),
      weightKg: Number(fd.get("weightKg")),
      bodyFatPercentage: bfRaw ? Number(bfRaw) / 100 : null,
      activityLevel: /** @type {any} */ (fd.get("activityLevel")),
      goal: /** @type {any} */ (fd.get("goal")),
      weeklyChangeKg: paceRaw ? Number(paceRaw) : null,
      ketoMode: fd.get("ketoMode") === "true",
    };
    await profileStore.save(p);
    this.render();
  }

  async onExportDiary() {
    const entries = await foodEntries.all();
    const prof = await profileStore.load();
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
      status.textContent = `Imported ${entries.length} food entries.`;
    } catch (err) {
      status.textContent = `Import failed: ${err.message}`;
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
      await Promise.all([
        ...w.map((r) => weights.put(r)),
        ...bf.map((r) => bodyFat.put(r)),
        ...m.map((r) => measurements.put(r)),
      ]);
      status.textContent = `Imported ${w.length} weights, ${bf.length} body-fat, ${m.length} measurements.`;
    } catch (err) {
      status.textContent = `Import failed: ${err.message}`;
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

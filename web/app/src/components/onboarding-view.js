// @ts-check
import { profile as profileStore, prefs } from "../lib/db.js";
import { dailyTargets } from "../lib/nofud-core/formulas.js";
import { PROVIDERS, modelSelectOptionsHtml, resolveProviderModel } from "../lib/ai/providers.js";
import { saveProviderKey } from "../lib/ai/key-storage.js";

/** @typedef {{ id: string, title: string, hideChrome?: boolean, hideCta?: boolean }} OnboardingStepDef */

/** @type {OnboardingStepDef[]} */
const STEPS = [
  { id: "welcome", title: "Welcome to NoFUD", hideChrome: true },
  { id: "sex", title: "About you" },
  { id: "age", title: "Age" },
  { id: "body", title: "Height & weight" },
  { id: "bodyFat", title: "Body fat (optional)" },
  { id: "activity", title: "Activity level" },
  { id: "goal", title: "Your goal" },
  { id: "diet", title: "Diet mode" },
  { id: "goalDetails", title: "Pace & goal weight" },
  { id: "ai", title: "Connect AI (optional)" },
  { id: "building", title: "Building your plan", hideChrome: true, hideCta: true },
  { id: "done", title: "Your plan is ready", hideChrome: true },
];

const ACTIVITY_LEVELS = [
  { id: "sedentary", label: "Sedentary", sub: "Desk job, little exercise" },
  { id: "light", label: "Light", sub: "Walks or light training 1–3×/week" },
  { id: "moderate", label: "Moderate", sub: "Training 3–5×/week" },
  { id: "active", label: "Active", sub: "Hard training most days" },
  { id: "very_active", label: "Very active", sub: "Physical job + training" },
  { id: "extra_active", label: "Extra active", sub: "Very hard training / labor" },
];

const GOALS = [
  { id: "lose", label: "Lose weight", sub: "Steady calorie deficit" },
  { id: "maintain", label: "Maintain", sub: "Hold your current weight" },
  { id: "gain", label: "Gain weight", sub: "Surplus for muscle or mass" },
];

const SEX_OPTIONS = [
  { id: "male", label: "Male" },
  { id: "female", label: "Female" },
  { id: "other", label: "Other / prefer not to say" },
];

const BUILD_ITEMS = [
  "Calorie target",
  "Protein goal",
  "Carb & fat split",
  "Activity adjustment",
  "Safety checks",
];

const BACK_ICON = `<svg viewBox="0 0 24 24" aria-hidden="true"><path fill="currentColor" d="M15.41 7.41 14 6l-6 6 6 6 1.41-1.41L10.83 12z"/></svg>`;

export class OnboardingView extends HTMLElement {
  constructor() {
    super();
    this.step = 0;
    this.buildPct = 0;
    /** @type {ReturnType<typeof setInterval>|null} */
    this._buildTimer = null;
    this.aiDraft = {
      provider: "gemini",
      model: PROVIDERS.gemini.defaultModel,
      apiKey: "",
      skip: false,
    };
    this.draft = /** @type {import('../lib/nofud-core/models.js').UserProfile} */ ({
      sex: "other",
      age: 30,
      heightCm: 170,
      weightKg: 70,
      bodyFatPercentage: null,
      activityLevel: "moderate",
      goal: "maintain",
      weeklyChangeKg: 0.5,
      ketoMode: false,
      goalWeightKg: null,
      customCalories: null,
    });
  }

  disconnectedCallback() {
    if (this._buildTimer) clearInterval(this._buildTimer);
  }

  connectedCallback() {
    this.render();
  }

  /** Progress fraction excluding welcome/building/done chrome steps for the bar. */
  progressPct() {
    const trackable = STEPS.filter((s) => !s.hideChrome);
    const id = STEPS[this.step]?.id;
    const idx = trackable.findIndex((s) => s.id === id);
    if (idx < 0) return this.step === 0 ? 0 : 1;
    return (idx + 1) / trackable.length;
  }

  render() {
    const s = STEPS[this.step];
    const showChrome = !s.hideChrome;
    const showCta = !s.hideCta;
    const isWelcome = s.id === "welcome";
    const isDone = s.id === "done";
    const ctaLabel = isWelcome ? "Get started" : isDone ? "Start logging" : "Continue";
    const ctaClass = isWelcome || isDone ? "btn btn--primary btn--gradient" : "btn btn--primary";

    this.innerHTML = `
      <div class="onboarding-step">
        ${
          showChrome
            ? `<div class="onboarding-chrome">
                <button type="button" class="onboarding-chrome__back" data-prev aria-label="Back">${BACK_ICON}</button>
                <div class="onboarding-progress-track" aria-hidden="true">
                  <span class="onboarding-progress-fill" style="width:${(this.progressPct() * 100).toFixed(1)}%"></span>
                </div>
              </div>
              <h1 class="screen-title">${s.title}</h1>`
            : ""
        }
        ${this.stepBody(s.id)}
        ${
          showCta
            ? `<div class="btn-row" style="flex-direction:column;align-items:stretch;">
                <button type="button" class="${ctaClass}" data-next>${ctaLabel}</button>
                ${s.id === "ai" ? `<button type="button" class="onboarding-skip" data-skip-ai>Skip for now</button>` : ""}
                ${s.id === "bodyFat" ? `<button type="button" class="onboarding-skip" data-skip-bf>Skip</button>` : ""}
              </div>`
            : ""
        }
      </div>
    `;

    this.querySelector("[data-prev]")?.addEventListener("click", () => this.goPrev());
    this.querySelector("[data-next]")?.addEventListener("click", () => this.onNext());
    this.querySelector("[data-skip-ai]")?.addEventListener("click", () => {
      this.aiDraft.skip = true;
      this.aiDraft.apiKey = "";
      this.goNext();
    });
    this.querySelector("[data-skip-bf]")?.addEventListener("click", () => {
      this.draft.bodyFatPercentage = null;
      this.goNext();
    });

    this.bindStepInteractions(s.id);

    if (s.id === "building") {
      this.startBuilding();
    }
  }

  /** @param {string} id */
  bindStepInteractions(id) {
    this.querySelectorAll("[data-choice]").forEach((btn) => {
      btn.addEventListener("click", () => {
        const field = btn.getAttribute("data-field");
        const value = btn.getAttribute("data-choice");
        if (!field || value == null) return;
        if (field === "sex") this.draft.sex = /** @type {any} */ (value);
        if (field === "activityLevel") this.draft.activityLevel = /** @type {any} */ (value);
        if (field === "goal") this.draft.goal = /** @type {any} */ (value);
        if (field === "ketoMode") this.draft.ketoMode = value === "true";
        this.render();
      });
    });

    if (id === "ai") {
      const providerSel = /** @type {HTMLSelectElement|null} */ (this.querySelector("#ob-ai-provider"));
      const modelSel = /** @type {HTMLSelectElement|null} */ (this.querySelector("#ob-ai-model"));
      providerSel?.addEventListener("change", () => {
        this.aiDraft.provider = providerSel.value;
        this.aiDraft.model = resolveProviderModel(providerSel.value, null, "primary");
        if (modelSel) modelSel.innerHTML = modelSelectOptionsHtml(providerSel.value, this.aiDraft.model, "primary");
      });
      modelSel?.addEventListener("change", () => {
        this.aiDraft.model = modelSel.value === "__custom__" ? PROVIDERS[this.aiDraft.provider]?.defaultModel || "" : modelSel.value;
      });
    }
  }

  /** @param {string} id */
  stepBody(id) {
    const d = this.draft;
    if (id === "welcome") {
      return `
        <div class="onboarding-welcome">
          <img class="onboarding-welcome__logo" src="icons/icon-192.png" alt="" width="88" height="88" />
          <h1 class="onboarding-welcome__title">Track food.<span class="onboarding-welcome__title-accent">Keep control.</span></h1>
          <p class="onboarding-welcome__sub">Privacy-first calorie tracking in your browser. Data stays on this device and exports to the Android app.</p>
          <ul class="onboarding-features">
            <li><span class="onboarding-features__icon">1</span><div><strong>Local-first diary</strong><span>Meals, macros, and progress without accounts.</span></div></li>
            <li><span class="onboarding-features__icon">2</span><div><strong>Bring your own AI</strong><span>Optional Gemini / Claude keys — never leaves your device except to the provider.</span></div></li>
            <li><span class="onboarding-features__icon">3</span><div><strong>Android parity</strong><span>Same formulas and export format as the native app.</span></div></li>
          </ul>
        </div>`;
    }
    if (id === "sex") {
      return choiceGrid(
        "sex",
        SEX_OPTIONS.map((o) => ({ id: o.id, label: o.label })),
        d.sex
      );
    }
    if (id === "age") {
      return `<div class="field"><label for="age">Age</label><input id="age" type="number" min="1" max="120" value="${d.age}" /></div>`;
    }
    if (id === "body") {
      return `<div class="field-row">
        <div class="field"><label for="heightCm">Height (cm)</label><input id="heightCm" type="number" min="1" value="${d.heightCm}" /></div>
        <div class="field"><label for="weightKg">Weight (kg)</label><input id="weightKg" type="number" step="0.1" min="1" value="${d.weightKg}" /></div>
      </div>`;
    }
    if (id === "bodyFat") {
      return `<p style="color:var(--muted);margin:0;">Optional — enables Katch–McArdle BMR when set.</p>
        <div class="field"><label for="bodyFatPercentage">Body fat %</label>
          <input id="bodyFatPercentage" type="number" step="0.1" min="2" max="65" value="${d.bodyFatPercentage ?? ""}" placeholder="e.g. 18" /></div>`;
    }
    if (id === "activity") {
      return choiceGrid(
        "activityLevel",
        ACTIVITY_LEVELS.map((a) => ({ id: a.id, label: a.label, sub: a.sub })),
        d.activityLevel
      );
    }
    if (id === "goal") {
      return choiceGrid(
        "goal",
        GOALS.map((g) => ({ id: g.id, label: g.label, sub: g.sub })),
        d.goal
      );
    }
    if (id === "diet") {
      return choiceGrid(
        "ketoMode",
        [
          { id: "false", label: "Standard", sub: "Balanced macros from your targets" },
          { id: "true", label: "Keto", sub: "Net-carb clamp with higher fat" },
        ],
        d.ketoMode ? "true" : "false"
      );
    }
    if (id === "goalDetails") {
      if (d.goal === "maintain") {
        return `<p style="color:var(--muted);margin:0;">You'll maintain around your current weight. You can set a goal weight later in Settings.</p>`;
      }
      return `<div class="field-row">
        <div class="field"><label for="weeklyChangeKg">Pace (kg/week)</label>
          <input id="weeklyChangeKg" type="number" step="0.05" min="0.05" max="1.5" value="${d.weeklyChangeKg}" /></div>
        <div class="field"><label for="goalWeightKg">Goal weight (kg)</label>
          <input id="goalWeightKg" type="number" step="0.1" min="0" value="${d.goalWeightKg ?? ""}" placeholder="optional" /></div>
      </div>`;
    }
    if (id === "ai") {
      const provider = this.aiDraft.provider;
      return `
        <p style="color:var(--muted);margin:0;">Optional bring-your-own key for food photo estimates and the coach. Defaults match Android: Gemini Flash 3.6 with 3.5 Flash Lite fallback.</p>
        <div class="field">
          <label for="ob-ai-provider">Provider</label>
          <select id="ob-ai-provider">
            ${Object.entries(PROVIDERS)
              .map(([pid, meta]) => `<option value="${pid}" ${provider === pid ? "selected" : ""}>${meta.label}</option>`)
              .join("")}
          </select>
        </div>
        <div class="field">
          <label for="ob-ai-model">Model</label>
          <select id="ob-ai-model">${modelSelectOptionsHtml(provider, this.aiDraft.model, "primary")}</select>
        </div>
        <div class="field">
          <label for="ob-ai-key">API key</label>
          <input id="ob-ai-key" type="password" autocomplete="off" placeholder="Paste key to enable AI" value="${escapeAttr(this.aiDraft.apiKey)}" />
        </div>`;
    }
    if (id === "building") {
      const doneCount = Math.min(BUILD_ITEMS.length, Math.floor((this.buildPct / 100) * BUILD_ITEMS.length));
      return `
        <div class="onboarding-building">
          <div class="onboarding-building__pct">${Math.round(this.buildPct)}%</div>
          <div class="onboarding-building__bar"><span class="onboarding-building__fill" style="width:${this.buildPct}%"></span></div>
          <p style="color:var(--muted);margin:0;">Calculating targets from your profile…</p>
          <ul class="onboarding-checklist">
            ${BUILD_ITEMS.map(
              (label, i) =>
                `<li class="${i < doneCount ? "is-done" : ""}"><span class="onboarding-checklist__mark"></span>${label}</li>`
            ).join("")}
          </ul>
        </div>`;
    }
    if (id === "done") {
      const targets = dailyTargets(this.draft);
      return `
        <div class="onboarding-plan-ready">
          <div class="onboarding-plan-ready__cals">${Math.round(targets.calories)}</div>
          <p class="onboarding-plan-ready__unit">kcal / day</p>
          <div class="onboarding-plan-macros">
            <div class="onboarding-plan-macro onboarding-plan-macro--protein">
              <span class="onboarding-plan-macro__value">${Math.round(targets.proteinG)}g</span>
              <span class="onboarding-plan-macro__label">Protein</span>
            </div>
            <div class="onboarding-plan-macro onboarding-plan-macro--carbs">
              <span class="onboarding-plan-macro__value">${Math.round(targets.carbsG)}g</span>
              <span class="onboarding-plan-macro__label">Carbs</span>
            </div>
            <div class="onboarding-plan-macro onboarding-plan-macro--fat">
              <span class="onboarding-plan-macro__value">${Math.round(targets.fatG)}g</span>
              <span class="onboarding-plan-macro__label">Fat</span>
            </div>
          </div>
          <p style="color:var(--muted);margin:0;font-size:0.85rem;">You can fine-tune targets anytime in Settings. Estimates aren’t medical advice.</p>
        </div>`;
    }
    return "";
  }

  collect() {
    const val = (id) => /** @type {HTMLInputElement|HTMLSelectElement|null} */ (this.querySelector(`#${id}`))?.value;
    if (this.querySelector("#age")) this.draft.age = Number(val("age")) || this.draft.age;
    if (this.querySelector("#heightCm")) this.draft.heightCm = Number(val("heightCm")) || this.draft.heightCm;
    if (this.querySelector("#weightKg")) this.draft.weightKg = Number(val("weightKg")) || this.draft.weightKg;
    if (this.querySelector("#bodyFatPercentage")) {
      const v = val("bodyFatPercentage");
      this.draft.bodyFatPercentage = v ? Number(v) : null;
    }
    if (this.querySelector("#weeklyChangeKg")) {
      const v = val("weeklyChangeKg");
      this.draft.weeklyChangeKg = v ? Number(v) : 0.5;
    }
    if (this.querySelector("#goalWeightKg")) {
      const v = val("goalWeightKg");
      this.draft.goalWeightKg = v ? Number(v) : null;
    }
    if (this.querySelector("#ob-ai-key")) {
      this.aiDraft.apiKey = String(val("ob-ai-key") || "");
      const providerEl = /** @type {HTMLSelectElement|null} */ (this.querySelector("#ob-ai-provider"));
      const modelEl = /** @type {HTMLSelectElement|null} */ (this.querySelector("#ob-ai-model"));
      if (providerEl) this.aiDraft.provider = providerEl.value;
      if (modelEl && modelEl.value !== "__custom__") this.aiDraft.model = modelEl.value;
      this.aiDraft.skip = !this.aiDraft.apiKey.trim();
    }
  }

  goPrev() {
    if (this._buildTimer) {
      clearInterval(this._buildTimer);
      this._buildTimer = null;
    }
    this.step = Math.max(0, this.step - 1);
    // Skip goalDetails when maintain and going back from ai
    if (STEPS[this.step]?.id === "goalDetails" && this.draft.goal === "maintain") {
      this.step = Math.max(0, this.step - 1);
    }
    this.render();
  }

  goNext() {
    this.step = Math.min(STEPS.length - 1, this.step + 1);
    if (STEPS[this.step]?.id === "goalDetails" && this.draft.goal === "maintain") {
      this.step += 1;
    }
    this.render();
  }

  startBuilding() {
    if (this._buildTimer) clearInterval(this._buildTimer);
    this.buildPct = 0;
    const tick = () => {
      this.buildPct = Math.min(100, this.buildPct + 8);
      if (this.buildPct >= 100) {
        if (this._buildTimer) clearInterval(this._buildTimer);
        this._buildTimer = null;
        setTimeout(() => {
          this.step = STEPS.findIndex((s) => s.id === "done");
          this.render();
        }, 280);
        return;
      }
      // Re-render checklist without resetting timer
      const root = this.querySelector(".onboarding-building");
      if (!root) return;
      const doneCount = Math.min(BUILD_ITEMS.length, Math.floor((this.buildPct / 100) * BUILD_ITEMS.length));
      root.innerHTML = `
        <div class="onboarding-building__pct">${Math.round(this.buildPct)}%</div>
        <div class="onboarding-building__bar"><span class="onboarding-building__fill" style="width:${this.buildPct}%"></span></div>
        <p style="color:var(--muted);margin:0;">Calculating targets from your profile…</p>
        <ul class="onboarding-checklist">
          ${BUILD_ITEMS.map(
            (label, i) =>
              `<li class="${i < doneCount ? "is-done" : ""}"><span class="onboarding-checklist__mark"></span>${label}</li>`
          ).join("")}
        </ul>`;
    };
    this._buildTimer = setInterval(tick, 220);
    tick();
  }

  async onNext() {
    this.collect();
    const id = STEPS[this.step]?.id;
    if (id === "done") {
      await this.finish();
      return;
    }
    if (id === "ai") {
      // proceed into building
    }
    this.goNext();
  }

  async finish() {
    await profileStore.save(this.draft);
    /** @type {Partial<import('../lib/db.js').AppPrefs>} */
    const prefPatch = {
      onboardingComplete: true,
      aiFallbackEnabled: true,
      fallbackAiProvider: "gemini",
      fallbackAiModel: PROVIDERS.gemini.defaultFallbackModel,
    };
    if (!this.aiDraft.skip && this.aiDraft.apiKey.trim()) {
      const provider = /** @type {any} */ (this.aiDraft.provider);
      const model = resolveProviderModel(provider, this.aiDraft.model, "primary");
      await saveProviderKey(provider, this.aiDraft.apiKey.trim(), { model });
      prefPatch.primaryAiProvider = provider;
    }
    await prefs.save(prefPatch);
    location.hash = "#/home";
  }
}

/**
 * @param {string} field
 * @param {{id: string, label: string, sub?: string}[]} options
 * @param {string} selected
 */
function choiceGrid(field, options, selected) {
  return `<div class="onboarding-choice-grid" role="listbox" aria-label="${field}">
    ${options
      .map(
        (o) => `
      <button type="button" class="onboarding-choice ${o.id === selected ? "is-selected" : ""}"
        data-field="${field}" data-choice="${o.id}" role="option" aria-selected="${o.id === selected}">
        <span>
          <span class="onboarding-choice__label">${o.label}</span>
          ${o.sub ? `<span class="onboarding-choice__sub">${o.sub}</span>` : ""}
        </span>
        <span class="onboarding-choice__check" aria-hidden="true"></span>
      </button>`
      )
      .join("")}
  </div>`;
}

function escapeAttr(s) {
  return String(s)
    .replace(/&/g, "&amp;")
    .replace(/"/g, "&quot;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;");
}

customElements.define("onboarding-view", OnboardingView);

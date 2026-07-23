// @ts-check
import { profile as profileStore, prefs } from "../lib/db.js";
import { dailyTargets } from "../lib/nofud-core/formulas.js";
import { PROVIDERS, modelSelectOptionsHtml, resolveProviderModel } from "../lib/ai/providers.js";
import { saveProviderKey } from "../lib/ai/key-storage.js";
import { validateGeminiApiKey } from "../lib/ai/validate-key.js";
import { maybeShowPostOnboardingInstallSheet } from "../lib/install-prompt.js";
import { openInput } from "../lib/ui/dialog.js";

/** @typedef {{ id: string, title: string, hideChrome?: boolean, hideCta?: boolean }} OnboardingStepDef */

/** @type {OnboardingStepDef[]} */
const STEPS = [
  { id: "welcome", title: "Welcome to NoFUD", hideChrome: true },
  { id: "sex", title: "About you" },
  { id: "age", title: "Birthday & age" },
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
      /** @type {string} last key string that passed validation */
      validatedKey: "",
      /** @type {""|"ok"|"err"} */
      testKind: "",
      testMessage: "",
      howtoOpen: false,
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
      customProtein: null,
      customCarbs: null,
      customFat: null,
      birthday: null,
      goalBodyFatPercentage: null,
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
      this.aiDraft.validatedKey = "";
      this.aiDraft.testKind = "";
      this.aiDraft.testMessage = "";
      this.goNext();
    });
    this.querySelector("[data-skip-bf]")?.addEventListener("click", () => {
      this.draft.bodyFatPercentage = null;
      this.draft.goalBodyFatPercentage = null;
      this.goNext();
    });

    this.bindStepInteractions(s.id);

    if (s.id === "done") {
      this.querySelectorAll("[data-edit-plan]").forEach((btn) => {
        btn.addEventListener("click", () => this.editPlanField(btn.getAttribute("data-edit-plan")));
      });
    }

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
      const keyInput = /** @type {HTMLInputElement|null} */ (this.querySelector("#ob-ai-key"));
      const howto = /** @type {HTMLDetailsElement|null} */ (this.querySelector("#ob-ai-howto"));
      providerSel?.addEventListener("change", () => {
        this.collectAiDraft();
        this.aiDraft.provider = providerSel.value;
        this.aiDraft.model = resolveProviderModel(providerSel.value, null, "primary");
        this.aiDraft.validatedKey = "";
        this.aiDraft.testKind = "";
        this.aiDraft.testMessage = "";
        if (modelSel) modelSel.innerHTML = modelSelectOptionsHtml(providerSel.value, this.aiDraft.model, "primary");
        this.render();
      });
      modelSel?.addEventListener("change", () => {
        this.aiDraft.model = modelSel.value === "__custom__" ? PROVIDERS[this.aiDraft.provider]?.defaultModel || "" : modelSel.value;
      });
      keyInput?.addEventListener("input", () => {
        this.aiDraft.apiKey = keyInput.value;
        if (this.aiDraft.validatedKey && keyInput.value.trim() !== this.aiDraft.validatedKey) {
          this.aiDraft.validatedKey = "";
          this.aiDraft.testKind = "";
          this.aiDraft.testMessage = "";
          const status = this.querySelector("#ob-ai-test-status");
          if (status) {
            status.textContent = "";
            status.className = "onboarding-ai-test-status";
          }
        }
      });
      howto?.addEventListener("toggle", () => {
        this.aiDraft.howtoOpen = howto.open;
      });
      this.querySelector("[data-test-ai-key]")?.addEventListener("click", () => {
        void this.testAiKey({ advanceOnSuccess: false });
      });
    }
  }

  collectAiDraft() {
    if (!this.querySelector("#ob-ai-key")) return;
    const val = (id) => /** @type {HTMLInputElement|HTMLSelectElement|null} */ (this.querySelector(`#${id}`))?.value;
    this.aiDraft.apiKey = String(val("ob-ai-key") || "");
    const providerEl = /** @type {HTMLSelectElement|null} */ (this.querySelector("#ob-ai-provider"));
    const modelEl = /** @type {HTMLSelectElement|null} */ (this.querySelector("#ob-ai-model"));
    if (providerEl) this.aiDraft.provider = providerEl.value;
    if (modelEl && modelEl.value !== "__custom__") this.aiDraft.model = modelEl.value;
    this.aiDraft.skip = !this.aiDraft.apiKey.trim();
  }

  /**
   * @param {{advanceOnSuccess?: boolean}} [opts]
   * @returns {Promise<boolean>}
   */
  async testAiKey(opts = {}) {
    const { advanceOnSuccess = false } = opts;
    this.collectAiDraft();
    const key = this.aiDraft.apiKey.trim();
    const status = /** @type {HTMLElement|null} */ (this.querySelector("#ob-ai-test-status"));
    const btn = /** @type {HTMLButtonElement|null} */ (this.querySelector("[data-test-ai-key]"));
    const nextBtn = /** @type {HTMLButtonElement|null} */ (this.querySelector("[data-next]"));

    const setStatus = (message, kind) => {
      this.aiDraft.testMessage = message;
      this.aiDraft.testKind = kind;
      if (status) {
        status.textContent = message;
        status.className = kind ? `onboarding-ai-test-status onboarding-ai-test-status--${kind}` : "onboarding-ai-test-status";
      }
    };

    if (!key) {
      setStatus("Paste an API key first.", "err");
      return false;
    }
    if (this.aiDraft.provider !== "gemini") {
      // Non-Gemini: no probe in this pass — treat as ready to continue.
      this.aiDraft.validatedKey = key;
      setStatus("Key saved for continue (quick test is Gemini-only).", "ok");
      if (advanceOnSuccess) this.goNext();
      return true;
    }
    if (this.aiDraft.validatedKey === key) {
      setStatus("Key works.", "ok");
      if (advanceOnSuccess) this.goNext();
      return true;
    }

    if (btn) btn.disabled = true;
    if (nextBtn) nextBtn.disabled = true;
    setStatus("Testing…", "");
    const result = await validateGeminiApiKey(key);
    if (btn) btn.disabled = false;
    if (nextBtn) nextBtn.disabled = false;

    if (!result.ok) {
      this.aiDraft.validatedKey = "";
      setStatus(/** @type {{ok:false, message:string}} */ (result).message, "err");
      return false;
    }
    this.aiDraft.validatedKey = key;
    setStatus("Key works.", "ok");
    if (advanceOnSuccess) this.goNext();
    return true;
  }

  /** @param {string} id */
  stepBody(id) {
    const d = this.draft;
    if (id === "welcome") {
      return `
        <div class="onboarding-welcome">
          <img class="onboarding-welcome__logo" src="icons/icon-192.png" alt="" width="88" height="88" />
          <h1 class="onboarding-welcome__title">Track food.<span class="onboarding-welcome__title-accent">Keep control.</span></h1>
          <p class="onboarding-welcome__sub">Ad-free calorie tracking in your browser. Data stays on this device and exports to the Android app.</p>
          <ul class="onboarding-features">
            <li><span class="onboarding-features__icon">1</span><div><strong>Local-first diary</strong><span>Meals, macros, and progress without accounts.</span></div></li>
            <li><span class="onboarding-features__icon">2</span><div><strong>Bring your own AI</strong><span>Optional Gemini / Claude keys. Keys leave this device only for the provider you choose.</span></div></li>
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
      return `
        <p style="color:var(--muted);margin:0 0 0.75rem;">Birthday is preferred (matches Android). Age is used when birthday is blank.</p>
        <div class="field"><label for="birthday">Birthday</label>
          <input id="birthday" type="date" max="${todayIso()}" value="${d.birthday ?? ""}" /></div>
        <div class="field"><label for="age">Age (years)</label>
          <input id="age" type="number" min="1" max="120" value="${d.age}" /></div>`;
    }
    if (id === "body") {
      return `<div class="field-row">
        <div class="field"><label for="heightCm">Height (cm)</label><input id="heightCm" type="number" min="1" value="${d.heightCm}" /></div>
        <div class="field"><label for="weightKg">Weight (kg)</label><input id="weightKg" type="number" step="0.1" min="1" value="${d.weightKg}" /></div>
      </div>`;
    }
    if (id === "bodyFat") {
      const bfPct =
        d.bodyFatPercentage != null
          ? d.bodyFatPercentage > 1
            ? d.bodyFatPercentage
            : Math.round(d.bodyFatPercentage * 1000) / 10
          : "";
      const goalBf =
        d.goalBodyFatPercentage != null
          ? Math.round(d.goalBodyFatPercentage * 1000) / 10
          : "";
      return `<p style="color:var(--muted);margin:0;">Optional. Enables Katch-McArdle BMR when set.</p>
        <div class="field"><label for="bodyFatPercentage">Body fat %</label>
          <input id="bodyFatPercentage" type="number" step="0.1" min="2" max="65" value="${bfPct}" placeholder="e.g. 18" /></div>
        <div class="field"><label for="goalBodyFatPercentage">Goal body fat % (optional)</label>
          <input id="goalBodyFatPercentage" type="number" step="0.1" min="2" max="65" value="${goalBf}" placeholder="e.g. 15" /></div>`;
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
      const testClass = this.aiDraft.testKind
        ? `onboarding-ai-test-status onboarding-ai-test-status--${this.aiDraft.testKind}`
        : "onboarding-ai-test-status";
      return `
        <p style="color:var(--muted);margin:0;">Optional bring-your-own key for food photo estimates and the coach. Defaults match Android: Gemini Flash 3.6 with 3.5 Flash Lite fallback.</p>
        <details class="micros-details onboarding-ai-howto" id="ob-ai-howto" ${this.aiDraft.howtoOpen ? "open" : ""}>
          <summary>How to get a free Google AI Studio key</summary>
          <ol>
            <li>Open <a href="https://aistudio.google.com/apikey" target="_blank" rel="noopener noreferrer">aistudio.google.com/apikey</a></li>
            <li>Sign in with Google and create an API key</li>
            <li>Copy it and paste below, then tap Test key</li>
          </ol>
        </details>
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
          <div class="onboarding-ai-test-row">
            <button type="button" class="btn" data-test-ai-key>Test key</button>
            <span id="ob-ai-test-status" class="${testClass}" role="status" aria-live="polite">${escapeAttr(this.aiDraft.testMessage)}</span>
          </div>
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
      const pinned = (key) => this.draft[key] != null;
      return `
        <div class="onboarding-plan-ready">
          <button type="button" class="onboarding-plan-ready__cals-btn" data-edit-plan="customCalories" aria-label="Edit calorie target">
            <div class="onboarding-plan-ready__cals">${Math.round(targets.calories)}</div>
            <p class="onboarding-plan-ready__unit">kcal / day${pinned("customCalories") ? " · custom" : ""}</p>
          </button>
          <div class="onboarding-plan-macros">
            <button type="button" class="onboarding-plan-macro onboarding-plan-macro--protein" data-edit-plan="customProtein">
              <span class="onboarding-plan-macro__value">${Math.round(targets.proteinG)}g</span>
              <span class="onboarding-plan-macro__label">Protein${pinned("customProtein") ? " ·" : ""}</span>
            </button>
            <button type="button" class="onboarding-plan-macro onboarding-plan-macro--carbs" data-edit-plan="customCarbs">
              <span class="onboarding-plan-macro__value">${Math.round(targets.carbsG)}g</span>
              <span class="onboarding-plan-macro__label">Carbs${pinned("customCarbs") ? " ·" : ""}</span>
            </button>
            <button type="button" class="onboarding-plan-macro onboarding-plan-macro--fat" data-edit-plan="customFat">
              <span class="onboarding-plan-macro__value">${Math.round(targets.fatG)}g</span>
              <span class="onboarding-plan-macro__label">Fat${pinned("customFat") ? " ·" : ""}</span>
            </button>
          </div>
          <p style="color:var(--muted);margin:0;font-size:0.85rem;">Tap a target to customize. You can fine-tune anytime in Settings. Estimates aren’t medical advice.</p>
        </div>`;
    }
    return "";
  }

  collect() {
    const val = (id) => /** @type {HTMLInputElement|HTMLSelectElement|null} */ (this.querySelector(`#${id}`))?.value;
    if (this.querySelector("#birthday")) {
      const b = val("birthday");
      this.draft.birthday = b || null;
      if (b) {
        const derived = ageFromBirthday(b);
        if (derived != null) this.draft.age = derived;
      }
    }
    if (this.querySelector("#age") && !this.draft.birthday) {
      this.draft.age = Number(val("age")) || this.draft.age;
    } else if (this.querySelector("#age") && this.draft.birthday) {
      // Keep age field in sync if user edits age after clearing birthday later
      const ageVal = Number(val("age"));
      if (ageVal && !this.draft.birthday) this.draft.age = ageVal;
    }
    if (this.querySelector("#heightCm")) this.draft.heightCm = Number(val("heightCm")) || this.draft.heightCm;
    if (this.querySelector("#weightKg")) this.draft.weightKg = Number(val("weightKg")) || this.draft.weightKg;
    if (this.querySelector("#bodyFatPercentage")) {
      const v = val("bodyFatPercentage");
      this.draft.bodyFatPercentage = v ? Number(v) / 100 : null;
    }
    if (this.querySelector("#goalBodyFatPercentage")) {
      const v = val("goalBodyFatPercentage");
      this.draft.goalBodyFatPercentage = v ? Number(v) / 100 : null;
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
      this.collectAiDraft();
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
    if (id === "ai" && this.aiDraft.apiKey.trim()) {
      const ok = await this.testAiKey({ advanceOnSuccess: true });
      if (!ok) return;
      return;
    }
    this.goNext();
  }

  /**
   * @param {string | null} field
   */
  async editPlanField(field) {
    if (!field) return;
    const targets = dailyTargets(this.draft);
    /** @type {Record<string, {label: string, unit: string, current: number}>} */
    const meta = {
      customCalories: { label: "Daily calories", unit: "kcal", current: targets.calories },
      customProtein: { label: "Protein", unit: "g", current: targets.proteinG },
      customCarbs: { label: "Carbs", unit: "g", current: targets.carbsG },
      customFat: { label: "Fat", unit: "g", current: targets.fatG },
    };
    const m = meta[field];
    if (!m) return;
    const raw = await openInput({
      title: `Edit ${m.label.toLowerCase()}`,
      label: m.label,
      value: String(Math.round(m.current)),
      type: "number",
      inputMode: "decimal",
      unit: m.unit,
      confirmLabel: "Save",
      placeholder: "Leave blank to reset",
    });
    if (raw == null) return;
    const trimmed = raw.trim();
    if (!trimmed) {
      this.draft[field] = null;
    } else {
      const n = Number(trimmed);
      if (!Number.isFinite(n) || n < 0) return;
      this.draft[field] = Math.round(n);
    }
    this.render();
  }

  async finish() {
    if (this.draft.birthday) {
      const derived = ageFromBirthday(this.draft.birthday);
      if (derived != null) this.draft.age = derived;
    }
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
    maybeShowPostOnboardingInstallSheet();
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

function todayIso() {
  return new Date().toISOString().slice(0, 10);
}

/** @param {string} iso */
function ageFromBirthday(iso) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(iso)) return null;
  const birth = new Date(`${iso}T00:00:00`);
  const now = new Date();
  let age = now.getFullYear() - birth.getFullYear();
  const m = now.getMonth() - birth.getMonth();
  if (m < 0 || (m === 0 && now.getDate() < birth.getDate())) age -= 1;
  if (age < 1 || age > 120) return null;
  return age;
}

customElements.define("onboarding-view", OnboardingView);

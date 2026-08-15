// @ts-check
import { profile as profileStore, prefs } from "../lib/db.js";
import { dailyTargets } from "../lib/chompass-core/formulas.js";
import { PROVIDERS, modelSelectOptionsHtml, resolveProviderModel } from "../lib/ai/providers.js";
import { saveProviderKey } from "../lib/ai/key-storage.js";
import { validateGeminiApiKey } from "../lib/ai/validate-key.js";
import { maybeShowPostOnboardingInstallSheet } from "../lib/install-prompt.js";
import { openInput, openConfirm } from "../lib/ui/dialog.js";
import { t } from "../lib/i18n/index.js";

/** Gemini first — matches Android AI Studio default/recommend. */
const ONBOARDING_PROVIDER_ORDER = ["gemini", "anthropic", "openai_compatible"];

/** @typedef {{ id: string, title?: string, titleKey?: string, hideChrome?: boolean, hideCta?: boolean }} OnboardingStepDef */

/** @type {OnboardingStepDef[]} */
const STEPS = [
  { id: "welcome", titleKey: "onboarding.welcome_title", hideChrome: true },
  { id: "sex", titleKey: "onboarding.step.sex" },
  { id: "age", titleKey: "onboarding.step.age" },
  { id: "body", titleKey: "onboarding.step.body" },
  { id: "bodyFat", titleKey: "onboarding.step.body_fat" },
  { id: "activity", titleKey: "onboarding.step.activity" },
  { id: "goal", titleKey: "onboarding.step.goal" },
  { id: "diet", titleKey: "onboarding.step.diet" },
  { id: "goalDetails", titleKey: "onboarding.step.goal_details" },
  { id: "ai", titleKey: "onboarding.step.ai" },
  { id: "building", titleKey: "onboarding.step.building", hideChrome: true, hideCta: true },
  { id: "done", titleKey: "onboarding.step.done", hideChrome: true },
];

const ACTIVITY_LEVELS = [
  { id: "sedentary", labelKey: "onboarding.activity.sedentary", subKey: "onboarding.activity.sedentary_sub" },
  { id: "light", labelKey: "onboarding.activity.light", subKey: "onboarding.activity.light_sub" },
  { id: "moderate", labelKey: "onboarding.activity.moderate", subKey: "onboarding.activity.moderate_sub" },
  { id: "active", labelKey: "onboarding.activity.active", subKey: "onboarding.activity.active_sub" },
  { id: "very_active", labelKey: "onboarding.activity.very_active", subKey: "onboarding.activity.very_active_sub" },
  { id: "extra_active", labelKey: "onboarding.activity.extra_active", subKey: "onboarding.activity.extra_active_sub" },
];

const GOALS = [
  { id: "lose", labelKey: "onboarding.goal.lose", subKey: "onboarding.goal.lose_sub" },
  { id: "maintain", labelKey: "onboarding.goal.maintain", subKey: "onboarding.goal.maintain_sub" },
  { id: "gain", labelKey: "onboarding.goal.gain", subKey: "onboarding.goal.gain_sub" },
];

const SEX_OPTIONS = [
  { id: "male", labelKey: "onboarding.sex.male" },
  { id: "female", labelKey: "onboarding.sex.female" },
  { id: "other", labelKey: "onboarding.sex.other" },
];

const BUILD_ITEM_KEYS = [
  "onboarding.building.calorie_target",
  "onboarding.building.protein_goal",
  "onboarding.building.carb_fat",
  "onboarding.building.activity",
  "onboarding.building.safety",
];

const BACK_ICON = `<svg viewBox="0 0 24 24" aria-hidden="true"><path fill="currentColor" d="M15.41 7.41 14 6l-6 6 6 6 1.41-1.41L10.83 12z"/></svg>`;

const PACE_KG_BY_INDEX = [0.25, 0.5, 1.0];
const PACE_ICONS = [
  { labelKey: "onboarding.pace.slow", svg: `<svg viewBox="0 0 24 24" aria-hidden="true"><path fill="currentColor" d="M13.5 5.5a1.75 1.75 0 1 0 0-3.5 1.75 1.75 0 0 0 0 3.5zM9.8 21l1-4.4-2.1-2 .6-3.4c1 1.2 2.6 2 4.3 2v-2c-1.5 0-2.7-.7-3.4-1.8l-1-1.6c-.4-.6-1-1-1.7-1-.3 0-.5.1-.8.1L2 8.3V13h2V9.6l1.8-.7-1.6 8.1L8.3 21h1.5z"/></svg>` },
  { labelKey: "onboarding.pace.recommended", svg: `<svg viewBox="0 0 24 24" aria-hidden="true"><path fill="currentColor" d="M13.5 5.5a1.75 1.75 0 1 0 0-3.5 1.75 1.75 0 0 0 0 3.5zM9.9 19l-1.5 2H6l2.3-3.4-1-4.7-1.4 1.2L3 16.5 1.6 15l2.9-2.9 3.1-1 1.7-2.7c.3-.5.9-.8 1.5-.7l4.2.7v2l-3.6-.6-1 1.6 3.9 1.6-1 6h-2z"/></svg>` },
  { labelKey: "onboarding.pace.fast", svg: `<svg viewBox="0 0 24 24" aria-hidden="true"><path fill="currentColor" d="M11 21 15 12H10.5L13.5 3 9 11.5h4.5L11 21z"/></svg>` },
];

const PACE_CAPTION_KEYS = [
  "onboarding.pace.caption_slow",
  "onboarding.pace.caption_recommended",
  "onboarding.pace.caption_fast",
];

/** @param {number} kg */
function paceIndexFromKg(kg) {
  if (Math.abs(kg - PACE_KG_BY_INDEX[0]) < 0.01) return 0;
  if (Math.abs(kg - PACE_KG_BY_INDEX[2]) < 0.01) return 2;
  return 1;
}

function disclaimerCardsHtml() {
  return `
    <div class="onboarding-disclaimers">
      <aside class="onboarding-disclaimer onboarding-disclaimer--health">
        <strong>${t("onboarding.health.title")}</strong>
        <p>${t("onboarding.health.body")}</p>
      </aside>
      <aside class="onboarding-disclaimer onboarding-disclaimer--accuracy">
        <strong>${t("onboarding.accuracy.title")}</strong>
        <p>${t("onboarding.accuracy.body")}</p>
      </aside>
    </div>`;
}

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
    this.draft = /** @type {import('../lib/chompass-core/models.js').UserProfile} */ ({
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
    const ctaLabel = isWelcome
      ? t("onboarding.get_started")
      : isDone
        ? t("onboarding.start_logging")
        : t("onboarding.continue");
    const ctaClass = isWelcome || isDone ? "btn btn--primary btn--gradient" : "btn btn--primary";
    const title = s.titleKey ? t(s.titleKey) : s.title || "";

    this.innerHTML = `
      <div class="onboarding-step">
        ${
          showChrome
            ? `<div class="onboarding-chrome">
                <button type="button" class="onboarding-chrome__back" data-prev aria-label="${t("onboarding.back")}">${BACK_ICON}</button>
                <div class="onboarding-progress-track" aria-hidden="true">
                  <span class="onboarding-progress-fill" style="width:${(this.progressPct() * 100).toFixed(1)}%"></span>
                </div>
              </div>
              <h1 class="screen-title">${title}</h1>`
            : ""
        }
        ${this.stepBody(s.id)}
        ${
          showCta
            ? `<div class="btn-row" style="flex-direction:column;align-items:stretch;">
                <button type="button" class="${ctaClass}" data-next>${ctaLabel}</button>
                ${s.id === "ai" ? `<button type="button" class="onboarding-skip" data-skip-ai>${t("onboarding.skip_ai")}</button>` : ""}
                ${s.id === "bodyFat" ? `<button type="button" class="onboarding-skip" data-skip-bf>${t("onboarding.skip")}</button>` : ""}
              </div>`
            : ""
        }
      </div>
    `;

    this.querySelector("[data-prev]")?.addEventListener("click", () => this.goPrev());
    this.querySelector("[data-next]")?.addEventListener("click", () => this.onNext());
    this.querySelector("[data-skip-ai]")?.addEventListener("click", () => {
      void this.skipAiSetup();
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

    if (id === "goalDetails") {
      const slider = /** @type {HTMLInputElement|null} */ (this.querySelector("#weeklyChangeKg"));
      slider?.addEventListener("input", () => {
        this.draft.weeklyChangeKg = PACE_KG_BY_INDEX[Number(slider.value)] ?? 0.5;
        this.refreshPaceUI();
      });
      const goalWeight = /** @type {HTMLInputElement|null} */ (this.querySelector("#goalWeightKg"));
      goalWeight?.addEventListener("input", () => {
        const v = goalWeight.value;
        this.draft.goalWeightKg = v ? Number(v) : null;
        this.refreshPaceUI();
      });
    }

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
      this.querySelector("[data-recommend-ai-studio]")?.addEventListener("click", () => {
        this.collectAiDraft();
        this.aiDraft.provider = "gemini";
        this.aiDraft.model = resolveProviderModel("gemini", null, "primary");
        this.aiDraft.validatedKey = "";
        this.aiDraft.testKind = "";
        this.aiDraft.testMessage = "";
        this.render();
      });
    }
  }

  /** Confirm leaving without a cloud key (PWA has no on-device LLM — set up later in Settings). */
  async skipAiSetup() {
    const ok = await openConfirm({
      title: t("onboarding.ai.skip_title"),
      message: t("onboarding.ai.skip_body"),
      confirmLabel: t("onboarding.ai.skip_confirm"),
      cancelLabel: t("action.cancel"),
    });
    if (!ok) return;
    this.aiDraft.skip = true;
    this.aiDraft.apiKey = "";
    this.aiDraft.validatedKey = "";
    this.aiDraft.testKind = "";
    this.aiDraft.testMessage = "";
    this.goNext();
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
      setStatus(t("onboarding.ai.paste_key_first"), "err");
      return false;
    }
    if (this.aiDraft.provider !== "gemini") {
      // Non-Gemini: no probe in this pass — treat as ready to continue.
      this.aiDraft.validatedKey = key;
      setStatus(t("onboarding.ai.key_saved_gemini_only"), "ok");
      if (advanceOnSuccess) this.goNext();
      return true;
    }
    if (this.aiDraft.validatedKey === key) {
      setStatus(t("onboarding.ai.key_works"), "ok");
      if (advanceOnSuccess) this.goNext();
      return true;
    }

    if (btn) btn.disabled = true;
    if (nextBtn) nextBtn.disabled = true;
    setStatus(t("onboarding.ai.testing"), "");
    const result = await validateGeminiApiKey(key);
    if (btn) btn.disabled = false;
    if (nextBtn) nextBtn.disabled = false;

    if (!result.ok) {
      this.aiDraft.validatedKey = "";
      setStatus(/** @type {{ok:false, message:string}} */ (result).message, "err");
      return false;
    }
    this.aiDraft.validatedKey = key;
    setStatus(t("onboarding.ai.key_works"), "ok");
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
          <h1 class="onboarding-welcome__title">${t("onboarding.welcome_body")}</h1>
          <p class="onboarding-welcome__sub">${t("onboarding.welcome_sub")}</p>
          <ul class="onboarding-features">
            <li><span class="onboarding-features__icon">1</span><div><strong>${t("onboarding.feature.local_title")}</strong><span>${t("onboarding.feature.local_body")}</span></div></li>
            <li><span class="onboarding-features__icon">2</span><div><strong>${t("onboarding.feature.ai_title")}</strong><span>${t("onboarding.feature.ai_body")}</span></div></li>
            <li><span class="onboarding-features__icon">3</span><div><strong>${t("onboarding.feature.parity_title")}</strong><span>${t("onboarding.feature.parity_body")}</span></div></li>
          </ul>
        </div>`;
    }
    if (id === "sex") {
      return choiceGrid(
        "sex",
        SEX_OPTIONS.map((o) => ({ id: o.id, label: t(o.labelKey) })),
        d.sex
      );
    }
    if (id === "age") {
      return `
        <p style="color:var(--muted);margin:0 0 0.75rem;">${t("onboarding.age.hint")}</p>
        <div class="field"><label for="birthday">${t("onboarding.age.birthday")}</label>
          <input id="birthday" type="date" max="${todayIso()}" value="${d.birthday ?? ""}" /></div>
        <div class="field"><label for="age">${t("onboarding.age.years")}</label>
          <input id="age" type="number" min="1" max="120" value="${d.age}" /></div>`;
    }
    if (id === "body") {
      return `<div class="field-row">
        <div class="field"><label for="heightCm">${t("onboarding.body.height_cm")}</label><input id="heightCm" type="number" min="1" value="${d.heightCm}" /></div>
        <div class="field"><label for="weightKg">${t("onboarding.body.weight_kg")}</label><input id="weightKg" type="number" step="0.1" min="1" value="${d.weightKg}" /></div>
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
      return `<p style="color:var(--muted);margin:0;">${t("onboarding.body_fat.hint")}</p>
        <div class="field"><label for="bodyFatPercentage">${t("onboarding.body_fat.pct")}</label>
          <input id="bodyFatPercentage" type="number" step="0.1" min="2" max="65" value="${bfPct}" placeholder="${escapeAttr(t("onboarding.body_fat.placeholder"))}" /></div>
        <div class="field"><label for="goalBodyFatPercentage">${t("onboarding.body_fat.goal_pct")}</label>
          <input id="goalBodyFatPercentage" type="number" step="0.1" min="2" max="65" value="${goalBf}" placeholder="${escapeAttr(t("onboarding.body_fat.goal_placeholder"))}" /></div>`;
    }
    if (id === "activity") {
      return choiceGrid(
        "activityLevel",
        ACTIVITY_LEVELS.map((a) => ({ id: a.id, label: t(a.labelKey), sub: t(a.subKey) })),
        d.activityLevel
      );
    }
    if (id === "goal") {
      return choiceGrid(
        "goal",
        GOALS.map((g) => ({ id: g.id, label: t(g.labelKey), sub: t(g.subKey) })),
        d.goal
      );
    }
    if (id === "diet") {
      return choiceGrid(
        "ketoMode",
        [
          { id: "false", label: t("onboarding.diet.standard"), sub: t("onboarding.diet.standard_sub") },
          { id: "true", label: t("onboarding.diet.keto"), sub: t("onboarding.diet.keto_sub") },
        ],
        d.ketoMode ? "true" : "false"
      );
    }
    if (id === "goalDetails") {
      if (d.goal === "maintain") {
        return `<p style="color:var(--muted);margin:0;">${t("onboarding.pace.maintain_hint")}</p>`;
      }
      return `${this.paceSelectorHtml()}
      <div class="field" style="margin-top:1.1rem;">
        <label for="goalWeightKg">${t("onboarding.pace.goal_weight_kg")}</label>
        <input id="goalWeightKg" type="number" step="0.1" min="0" value="${d.goalWeightKg ?? ""}" placeholder="${escapeAttr(t("onboarding.pace.optional"))}" />
      </div>`;
    }
    if (id === "ai") {
      const provider = this.aiDraft.provider;
      const testClass = this.aiDraft.testKind
        ? `onboarding-ai-test-status onboarding-ai-test-status--${this.aiDraft.testKind}`
        : "onboarding-ai-test-status";
      const providerOptions = ONBOARDING_PROVIDER_ORDER.filter((pid) => PROVIDERS[pid])
        .map((pid) => {
          const meta = PROVIDERS[pid];
          return `<option value="${pid}" ${provider === pid ? "selected" : ""}>${meta.label}</option>`;
        })
        .join("");
      const howto =
        provider === "gemini"
          ? `<details class="micros-details onboarding-ai-howto" id="ob-ai-howto" ${this.aiDraft.howtoOpen ? "open" : ""}>
          <summary>${t("onboarding.ai.howto_summary")}</summary>
          <ol>
            <li>${t("onboarding.ai.howto_1").replace(
              "aistudio.google.com/apikey",
              `<a href="https://aistudio.google.com/apikey" target="_blank" rel="noopener noreferrer">aistudio.google.com/apikey</a>`
            )}</li>
            <li>${t("onboarding.ai.howto_2")}</li>
            <li>${t("onboarding.ai.howto_3")}</li>
          </ol>
        </details>`
          : "";
      return `
        <p style="color:var(--muted);margin:0;">${t("onboarding.ai.hint")}</p>
        <button type="button" class="onboarding-ai-recommend" data-recommend-ai-studio>
          <span class="onboarding-ai-recommend__star" aria-hidden="true">★</span>
          <span class="onboarding-ai-recommend__copy">
            <strong>${t("onboarding.ai.recommended_title")}</strong>
            <span>${t("onboarding.ai.recommended_subtitle")}</span>
          </span>
        </button>
        ${howto}
        <div class="field">
          <label for="ob-ai-provider">${t("onboarding.ai.provider")}</label>
          <select id="ob-ai-provider">${providerOptions}</select>
        </div>
        <div class="field">
          <label for="ob-ai-model">${t("onboarding.ai.model")}</label>
          <select id="ob-ai-model">${modelSelectOptionsHtml(provider, this.aiDraft.model, "primary")}</select>
        </div>
        <div class="field">
          <label for="ob-ai-key">${t("onboarding.ai.key")}</label>
          <input id="ob-ai-key" type="password" autocomplete="off" placeholder="${escapeAttr(t("onboarding.ai.key_placeholder"))}" value="${escapeAttr(this.aiDraft.apiKey)}" />
          <div class="onboarding-ai-test-row">
            <button type="button" class="btn" data-test-ai-key>${t("onboarding.ai.test_key")}</button>
            <span id="ob-ai-test-status" class="${testClass}" role="status" aria-live="polite">${escapeAttr(this.aiDraft.testMessage)}</span>
          </div>
        </div>
        <p class="onboarding-ai-footer">${t("onboarding.ai.footer")}</p>
        <details class="onboarding-ai-privacy">
          <summary>${t("onboarding.ai.privacy_summary")}</summary>
          <p>${t("onboarding.ai.privacy_lead")}</p>
          <ul>
            <li>${t("onboarding.ai.privacy_food")}</li>
            <li>${t("onboarding.ai.privacy_coach")}</li>
            <li>${t("onboarding.ai.privacy_goals")}</li>
          </ul>
          <p>${t("onboarding.ai.privacy_private")}</p>
        </details>`;
    }
    if (id === "building") {
      const doneCount = Math.min(BUILD_ITEM_KEYS.length, Math.floor((this.buildPct / 100) * BUILD_ITEM_KEYS.length));
      return `
        <div class="onboarding-building">
          <div class="onboarding-building__pct">${Math.round(this.buildPct)}%</div>
          <div class="onboarding-building__bar"><span class="onboarding-building__fill" style="width:${this.buildPct}%"></span></div>
          <p style="color:var(--muted);margin:0;">${t("onboarding.building.calculating")}</p>
          <ul class="onboarding-checklist">
            ${BUILD_ITEM_KEYS.map(
              (key, i) =>
                `<li class="${i < doneCount ? "is-done" : ""}"><span class="onboarding-checklist__mark"></span>${t(key)}</li>`
            ).join("")}
          </ul>
        </div>`;
    }
    if (id === "done") {
      const targets = dailyTargets(this.draft);
      const pinned = (key) => this.draft[key] != null;
      const customMark = ` · ${t("onboarding.plan.custom")}`;
      return `
        <div class="onboarding-plan-ready">
          <button type="button" class="onboarding-plan-ready__cals-btn" data-edit-plan="customCalories" aria-label="${escapeAttr(t("onboarding.plan.edit_calories_a11y"))}">
            <div class="onboarding-plan-ready__cals">${Math.round(targets.calories)}</div>
            <p class="onboarding-plan-ready__unit">${t("onboarding.plan.kcal_day")}${pinned("customCalories") ? customMark : ""}</p>
          </button>
          <div class="onboarding-plan-macros">
            <button type="button" class="onboarding-plan-macro onboarding-plan-macro--protein" data-edit-plan="customProtein">
              <span class="onboarding-plan-macro__value">${Math.round(targets.proteinG)}g</span>
              <span class="onboarding-plan-macro__label">${t("onboarding.plan.protein")}${pinned("customProtein") ? " ·" : ""}</span>
            </button>
            <button type="button" class="onboarding-plan-macro onboarding-plan-macro--carbs" data-edit-plan="customCarbs">
              <span class="onboarding-plan-macro__value">${Math.round(targets.carbsG)}g</span>
              <span class="onboarding-plan-macro__label">${t("onboarding.plan.carbs")}${pinned("customCarbs") ? " ·" : ""}</span>
            </button>
            <button type="button" class="onboarding-plan-macro onboarding-plan-macro--fat" data-edit-plan="customFat">
              <span class="onboarding-plan-macro__value">${Math.round(targets.fatG)}g</span>
              <span class="onboarding-plan-macro__label">${t("onboarding.plan.fat")}${pinned("customFat") ? " ·" : ""}</span>
            </button>
          </div>
          <p style="color:var(--muted);margin:0;font-size:0.85rem;">${t("onboarding.plan.customize_hint")}</p>
          ${disclaimerCardsHtml()}
        </div>`;
    }
    return "";
  }

  /** Estimated-days-to-goal + caption for the current draft pace. */
  paceInfo() {
    const d = this.draft;
    const idx = paceIndexFromKg(d.weeklyChangeKg);
    const kg = PACE_KG_BY_INDEX[idx];
    const diffKg = Math.abs((d.goalWeightKg ?? d.weightKg) - d.weightKg);
    const estimatedDays = kg > 0 ? Math.round((diffKg / kg) * 7) : 0;
    return { idx, kg, estimatedDays, caption: t(PACE_CAPTION_KEYS[idx]) };
  }

  paceSelectorHtml() {
    const { idx, kg, estimatedDays, caption } = this.paceInfo();
    return `
      <div class="onboarding-pace">
        <div class="onboarding-pace__readout">
          <span class="onboarding-pace__value" id="pace-value">${kg.toFixed(2)}</span>
          <span class="onboarding-pace__unit">${t("onboarding.pace.kg_week")}</span>
        </div>
        <div class="onboarding-pace__icons" id="pace-icons">
          ${PACE_ICONS.map(
            (p, i) => `<span class="onboarding-pace__icon ${i === idx ? "is-selected" : ""}" data-pace-icon="${i}">
              ${p.svg}<span class="onboarding-pace__icon-label">${t(p.labelKey)}</span>
            </span>`
          ).join("")}
        </div>
        <input id="weeklyChangeKg" class="onboarding-pace__slider" type="range" min="0" max="2" step="1" value="${idx}" aria-label="${escapeAttr(t("onboarding.pace.weekly_aria"))}" />
        <div class="onboarding-pace__card" id="pace-card">
          <p class="onboarding-pace__eta">${t("onboarding.pace.eta_prefix")} <strong id="pace-days">${estimatedDays}</strong> ${t("onboarding.pace.days_unit")}</p>
          <p class="onboarding-pace__caption" id="pace-caption">${caption}</p>
        </div>
      </div>`;
  }

  /** Update the pace readout/icons/card in place without a full re-render (keeps focus on inputs). */
  refreshPaceUI() {
    const { idx, kg, estimatedDays, caption } = this.paceInfo();
    const value = this.querySelector("#pace-value");
    if (value) value.textContent = kg.toFixed(2);
    this.querySelectorAll("[data-pace-icon]").forEach((el) => {
      el.classList.toggle("is-selected", Number(el.getAttribute("data-pace-icon")) === idx);
    });
    const days = this.querySelector("#pace-days");
    if (days) days.textContent = String(estimatedDays);
    const cap = this.querySelector("#pace-caption");
    if (cap) cap.textContent = caption;
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
    // weeklyChangeKg is kept in sync live by the pace slider's `input` handler.
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
      const root = this.querySelector(".onboarding-building");
      if (!root) return;
      const doneCount = Math.min(BUILD_ITEM_KEYS.length, Math.floor((this.buildPct / 100) * BUILD_ITEM_KEYS.length));
      root.innerHTML = `
        <div class="onboarding-building__pct">${Math.round(this.buildPct)}%</div>
        <div class="onboarding-building__bar"><span class="onboarding-building__fill" style="width:${this.buildPct}%"></span></div>
        <p style="color:var(--muted);margin:0;">${t("onboarding.building.calculating")}</p>
        <ul class="onboarding-checklist">
          ${BUILD_ITEM_KEYS.map(
            (key, i) =>
              `<li class="${i < doneCount ? "is-done" : ""}"><span class="onboarding-checklist__mark"></span>${t(key)}</li>`
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
      if (this.aiDraft.apiKey.trim()) {
        const ok = await this.testAiKey({ advanceOnSuccess: true });
        if (!ok) return;
        return;
      }
      await this.skipAiSetup();
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
      customCalories: { label: t("onboarding.plan.edit_calories_a11y"), unit: "kcal", current: targets.calories },
      customProtein: { label: t("onboarding.plan.protein"), unit: "g", current: targets.proteinG },
      customCarbs: { label: t("onboarding.plan.carbs"), unit: "g", current: targets.carbsG },
      customFat: { label: t("onboarding.plan.fat"), unit: "g", current: targets.fatG },
    };
    const m = meta[field];
    if (!m) return;
    const raw = await openInput({
      title: m.label,
      label: m.label,
      value: String(Math.round(m.current)),
      type: "number",
      inputMode: "decimal",
      unit: m.unit,
      confirmLabel: t("action.save"),
      placeholder: t("onboarding.pace.optional"),
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

/** @param {string} s */
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

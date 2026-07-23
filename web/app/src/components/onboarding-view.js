// @ts-check
import { profile as profileStore, prefs } from "../lib/db.js";

const STEPS = [
  { id: "welcome", title: "Welcome to NoFUD" },
  { id: "sex", title: "Sex" },
  { id: "age", title: "Age" },
  { id: "body", title: "Height & weight" },
  { id: "activity", title: "Activity level" },
  { id: "goal", title: "Goal" },
  { id: "diet", title: "Diet mode" },
  { id: "done", title: "Your plan is ready" },
];

const ACTIVITY_LEVELS = ["sedentary", "light", "moderate", "active", "very_active", "extra_active"];

export class OnboardingView extends HTMLElement {
  constructor() {
    super();
    this.step = 0;
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

  connectedCallback() {
    this.render();
  }

  render() {
    const s = STEPS[this.step];
    this.innerHTML = `
      <div class="onboarding-step">
        <p class="onboarding-progress">Step ${this.step + 1} of ${STEPS.length}</p>
        <h1 class="screen-title">${s.title}</h1>
        ${this.stepBody(s.id)}
        <div class="btn-row">
          ${this.step > 0 ? `<button type="button" class="btn btn--ghost" data-prev>Back</button>` : ""}
          <button type="button" class="btn btn--primary" data-next>${this.step === STEPS.length - 1 ? "Start logging" : "Continue"}</button>
        </div>
      </div>
    `;
    this.querySelector("[data-prev]")?.addEventListener("click", () => {
      this.step = Math.max(0, this.step - 1);
      this.render();
    });
    this.querySelector("[data-next]")?.addEventListener("click", () => this.onNext());
  }

  stepBody(id) {
    const d = this.draft;
    if (id === "welcome") {
      return `<p style="color:var(--muted);">Privacy-first calorie tracking in your browser. Data stays on this device and can export to the Android app.</p>`;
    }
    if (id === "sex") {
      return `<div class="field"><label for="sex">Sex</label>
        <select id="sex">${["male", "female", "other"].map((x) => `<option value="${x}" ${d.sex === x ? "selected" : ""}>${x}</option>`).join("")}</select></div>`;
    }
    if (id === "age") {
      return `<div class="field"><label for="age">Age</label><input id="age" type="number" min="1" max="120" value="${d.age}" /></div>`;
    }
    if (id === "body") {
      return `<div class="field-row">
        <div class="field"><label for="heightCm">Height cm</label><input id="heightCm" type="number" min="1" value="${d.heightCm}" /></div>
        <div class="field"><label for="weightKg">Weight kg</label><input id="weightKg" type="number" step="0.1" min="1" value="${d.weightKg}" /></div>
      </div>`;
    }
    if (id === "activity") {
      return `<div class="field"><label for="activityLevel">Activity</label>
        <select id="activityLevel">${ACTIVITY_LEVELS.map((a) => `<option value="${a}" ${d.activityLevel === a ? "selected" : ""}>${a.replace("_", " ")}</option>`).join("")}</select></div>`;
    }
    if (id === "goal") {
      return `<div class="field-row">
        <div class="field"><label for="goal">Goal</label>
          <select id="goal">${["lose", "maintain", "gain"].map((g) => `<option value="${g}" ${d.goal === g ? "selected" : ""}>${g}</option>`).join("")}</select></div>
        <div class="field"><label for="weeklyChangeKg">Pace kg/wk</label>
          <input id="weeklyChangeKg" type="number" step="0.05" min="0" value="${d.weeklyChangeKg}" /></div>
        <div class="field"><label for="goalWeightKg">Goal weight kg</label>
          <input id="goalWeightKg" type="number" step="0.1" min="0" value="${d.goalWeightKg ?? ""}" placeholder="optional" /></div>
      </div>`;
    }
    if (id === "diet") {
      return `<div class="field"><label for="ketoMode">Diet mode</label>
        <select id="ketoMode">
          <option value="false" ${!d.ketoMode ? "selected" : ""}>Standard</option>
          <option value="true" ${d.ketoMode ? "selected" : ""}>Keto</option>
        </select></div>`;
    }
    return `<p style="color:var(--muted);">Targets are calculated from your profile. You can fine-tune them anytime in Settings.</p>`;
  }

  collect() {
    const val = (id) => /** @type {HTMLInputElement|HTMLSelectElement|null} */ (this.querySelector(`#${id}`))?.value;
    if (this.querySelector("#sex")) this.draft.sex = /** @type {any} */ (val("sex"));
    if (this.querySelector("#age")) this.draft.age = Number(val("age"));
    if (this.querySelector("#heightCm")) this.draft.heightCm = Number(val("heightCm"));
    if (this.querySelector("#weightKg")) this.draft.weightKg = Number(val("weightKg"));
    if (this.querySelector("#activityLevel")) this.draft.activityLevel = /** @type {any} */ (val("activityLevel"));
    if (this.querySelector("#goal")) this.draft.goal = /** @type {any} */ (val("goal"));
    if (this.querySelector("#weeklyChangeKg")) {
      const v = val("weeklyChangeKg");
      this.draft.weeklyChangeKg = v ? Number(v) : 0.5;
    }
    if (this.querySelector("#goalWeightKg")) {
      const v = val("goalWeightKg");
      this.draft.goalWeightKg = v ? Number(v) : null;
    }
    if (this.querySelector("#ketoMode")) this.draft.ketoMode = val("ketoMode") === "true";
  }

  async onNext() {
    this.collect();
    if (this.step < STEPS.length - 1) {
      this.step += 1;
      this.render();
      return;
    }
    await profileStore.save(this.draft);
    await prefs.save({ onboardingComplete: true });
    location.hash = "#/home";
  }
}

customElements.define("onboarding-view", OnboardingView);

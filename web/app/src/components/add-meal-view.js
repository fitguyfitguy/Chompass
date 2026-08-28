// @ts-check
import { foodEntries, prefs } from "../lib/db.js";
import { decodeMealShare } from "../lib/meal-share.js";
import { guessMealTypeFromPrefs } from "../lib/meal-schedule.js";
import { subpageBar, bindSubpageBack } from "../lib/ui/subpage.js";
import { todayIso } from "../lib/date.js";
import { formatNumber, t } from "../lib/i18n/index.js";
import { ALL_MICRO_KEYS } from "../lib/home-nutrients.js";
import { escapeHtml } from "../lib/ui/html.js";

/** Import shared meals from `#/add-meal?d=` (Android MealShare bridge). */
export class AddMealView extends HTMLElement {
  async connectedCallback() {
    const params = new URLSearchParams(location.hash.split("?")[1] ?? "");
    this.encoded = params.get("d") || "";
    this.meals = decodeMealShare(location.hash) || decodeMealShare(`d=${this.encoded}`);
    this.render();
  }

  async render() {
    if (!this.meals?.length) {
      this.innerHTML = `
        ${subpageBar(t("add_meal.title"), { backHref: "#/home" })}
        <div class="card">
          <p style="color:var(--muted);margin:0;">${t("add_meal.invalid_link")}</p>
        </div>`;
      bindSubpageBack(this, "#/home");
      return;
    }

    this.innerHTML = `
      ${subpageBar(t("add_meal.title"), { backHref: "#/home" })}
      <div class="card">
        <p style="color:var(--muted);margin:0 0 0.8rem;">${t("add_meal.intro")}</p>
        <div class="recents-list">
          ${this.meals
            .map(
              (m, i) => `
            <label class="copy-select__row">
              <input type="checkbox" data-idx="${i}" checked />
              <span><strong>${escapeHtml(m.name)}</strong><br/>
              <span class="recents-meta">${formatNumber(Math.round(m.calories))} kcal · ${Math.round(m.proteinG)}P / ${Math.round(m.carbsG)}C / ${Math.round(m.fatG)}F · ${m.mealType}</span></span>
            </label>`
            )
            .join("")}
        </div>
        <button type="button" class="btn btn--primary" data-log style="margin-top:0.8rem;">${t("add_meal.log_selected")}</button>
      </div>`;
    bindSubpageBack(this, "#/home");
    this.querySelector("[data-log]")?.addEventListener("click", () => this.logSelected());
  }

  async logSelected() {
    const idxs = [...this.querySelectorAll("[data-idx]:checked")].map((el) => Number(el.getAttribute("data-idx")));
    const appPrefs = await prefs.load();
    const date = todayIso();
    const now = new Date();
    const time = `${String(now.getHours()).padStart(2, "0")}:${String(now.getMinutes()).padStart(2, "0")}`;
    for (const i of idxs) {
      const m = this.meals[i];
      if (!m) continue;
      await foodEntries.put({
        id: crypto.randomUUID(),
        name: m.name,
        calories: m.calories,
        proteinG: m.proteinG,
        carbsG: m.carbsG,
        fatG: m.fatG,
        quantityG: m.quantityG ?? null,
        mealType: m.mealType || guessMealTypeFromPrefs(appPrefs),
        date,
        time,
        source: "manual",
        note: m.note ?? null,
        grounding: null,
        recipeLogId: null,
        ...Object.fromEntries(ALL_MICRO_KEYS.map((k) => [k, m[k] ?? null])),
      });
    }
    location.hash = "#/home";
  }
}

customElements.define("add-meal-view", AddMealView);

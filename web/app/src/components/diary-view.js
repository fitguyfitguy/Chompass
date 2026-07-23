// @ts-check
import { foodEntries, profile as profileStore, water } from "../lib/db.js";
import { dailyTargets } from "../lib/nofud-core/formulas.js";

const MEAL_LABELS = { breakfast: "Breakfast", lunch: "Lunch", dinner: "Dinner", snack: "Snack" };
const MEAL_ORDER = ["breakfast", "lunch", "dinner", "snack"];

function todayIso() {
  return new Date().toISOString().slice(0, 10);
}

function shiftDate(iso, days) {
  const d = new Date(`${iso}T00:00:00`);
  d.setDate(d.getDate() + days);
  return d.toISOString().slice(0, 10);
}

function formatDayLabel(iso) {
  const d = new Date(`${iso}T00:00:00`);
  if (iso === todayIso()) return "Today";
  if (iso === shiftDate(todayIso(), -1)) return "Yesterday";
  return d.toLocaleDateString(undefined, { weekday: "short", month: "short", day: "numeric" });
}

export class DiaryView extends HTMLElement {
  constructor() {
    super();
    this.date = todayIso();
  }

  connectedCallback() {
    this.render();
  }

  async render() {
    const [entries, prof] = await Promise.all([foodEntries.byDate(this.date), profileStore.load()]);

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

    const targets = prof ? dailyTargets(prof) : null;

    this.innerHTML = `
      <div class="day-nav">
        <button data-action="prev" aria-label="Previous day">‹</button>
        <span class="day-nav__label">${formatDayLabel(this.date)}</span>
        <button data-action="next" aria-label="Next day">›</button>
      </div>
      <div class="card">
        <div class="totals-ring">
          <div><strong>${Math.round(totals.calories)}${targets ? ` / ${targets.calories}` : ""}</strong><span>Calories</span></div>
          <div><strong>${Math.round(totals.proteinG)}${targets ? ` / ${Math.round(targets.proteinG)}` : ""}</strong><span>Protein g</span></div>
          <div><strong>${Math.round(totals.carbsG)}${targets ? ` / ${Math.round(targets.carbsG)}` : ""}</strong><span>Carbs g</span></div>
          <div><strong>${Math.round(totals.fatG)}${targets ? ` / ${Math.round(targets.fatG)}` : ""}</strong><span>Fat g</span></div>
        </div>
      </div>
      ${
        entries.length === 0
          ? `<p class="empty-state">No entries yet for this day. Tap + to log food.</p>`
          : MEAL_ORDER.filter((m) => entries.some((e) => e.mealType === m))
              .map(
                (mealType) => `
        <div class="meal-group">
          <h2>${MEAL_LABELS[mealType]}</h2>
          ${entries
            .filter((e) => e.mealType === mealType)
            .map(
              (e) => `
            <button class="food-item" data-entry-id="${e.id}">
              <span>
                <span class="food-item__name">${escapeHtml(e.name)}</span><br/>
                <span class="food-item__meta">${e.quantityG != null ? `${e.quantityG}g · ` : ""}${e.time}</span>
              </span>
              <span class="food-item__cals">${Math.round(e.calories)} kcal</span>
            </button>`
            )
            .join("")}
        </div>`
              )
              .join("")
      }
      <button class="fab" aria-label="Add food entry" data-action="add">+</button>
    `;

    this.querySelector('[data-action="prev"]').addEventListener("click", () => this.go(-1));
    this.querySelector('[data-action="next"]').addEventListener("click", () => this.go(1));
    this.querySelector('[data-action="add"]').addEventListener("click", () => this.openEntryForm());
    this.querySelectorAll("[data-entry-id]").forEach((el) => {
      el.addEventListener("click", () => {
        const entry = entries.find((e) => e.id === el.getAttribute("data-entry-id"));
        this.openEntryForm(entry);
      });
    });
  }

  go(deltaDays) {
    this.date = shiftDate(this.date, deltaDays);
    this.render();
  }

  openEntryForm(entry) {
    location.hash = entry ? `#/entry/${entry.id}?date=${this.date}` : `#/entry/new?date=${this.date}`;
  }
}

function escapeHtml(s) {
  return s.replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]);
}

customElements.define("diary-view", DiaryView);

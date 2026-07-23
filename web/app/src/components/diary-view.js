// @ts-check
import { foodEntries, profile as profileStore, water, prefs } from "../lib/db.js";
import { dailyTargets, estimatedDailyActiveCalories } from "../lib/nofud-core/formulas.js";

const MEAL_LABELS = { breakfast: "Breakfast", lunch: "Lunch", dinner: "Dinner", snack: "Snack" };
const MEAL_ORDER = ["breakfast", "lunch", "dinner", "snack"];
const WATER_PRESETS = [250, 500, 750];

function todayIso() {
  return new Date().toISOString().slice(0, 10);
}

function shiftDate(iso, days) {
  const d = new Date(`${iso}T00:00:00`);
  d.setDate(d.getDate() + days);
  return d.toISOString().slice(0, 10);
}

function weekDates(selectedIso) {
  const selected = new Date(`${selectedIso}T00:00:00`);
  const dow = selected.getDay(); // 0 Sun
  const mondayOffset = dow === 0 ? -6 : 1 - dow;
  const monday = new Date(selected);
  monday.setDate(selected.getDate() + mondayOffset);
  return Array.from({ length: 7 }, (_, i) => {
    const d = new Date(monday);
    d.setDate(monday.getDate() + i);
    return d.toISOString().slice(0, 10);
  });
}

function ringSvg(eaten, target) {
  const size = 176;
  const stroke = 12;
  const r = (size - stroke) / 2;
  const c = 2 * Math.PI * r;
  const pct = target > 0 ? Math.min(1, eaten / target) : 0;
  const remaining = Math.max(0, Math.round(target - eaten));
  const over = eaten > target;
  return `
    <svg class="calorie-ring" viewBox="0 0 ${size} ${size}" role="img" aria-label="${Math.round(eaten)} of ${target} calories">
      <circle cx="${size / 2}" cy="${size / 2}" r="${r}" fill="none" stroke="var(--surface)" stroke-width="${stroke}" />
      <circle cx="${size / 2}" cy="${size / 2}" r="${r}" fill="none"
        stroke="${over ? "var(--over)" : "var(--teal)"}" stroke-width="${stroke}"
        stroke-linecap="round"
        stroke-dasharray="${(pct * c).toFixed(1)} ${c.toFixed(1)}"
        transform="rotate(-90 ${size / 2} ${size / 2})" />
      <text x="50%" y="46%" text-anchor="middle" class="calorie-ring__label">${over ? `+${Math.round(eaten - target)}` : remaining}</text>
      <text x="50%" y="58%" text-anchor="middle" class="calorie-ring__sub">${over ? "over" : "remaining"}</text>
    </svg>`;
}

function macroRow(key, label, value, target) {
  const pct = target > 0 ? Math.min(100, (value / target) * 100) : 0;
  return `
    <div class="macro-row macro-row--${key}">
      <span class="macro-row__name">${label}</span>
      <div class="macro-bar" aria-hidden="true"><span style="width:${pct.toFixed(1)}%"></span></div>
      <span class="macro-row__vals">${Math.round(value)} / ${Math.round(target)} g</span>
    </div>`;
}

export class DiaryView extends HTMLElement {
  constructor() {
    super();
    this.date = todayIso();
    this.fabOpen = false;
    /** @type {import('../lib/nofud-core/models.js').FoodEntry|null} */
    this._undoEntry = null;
  }

  connectedCallback() {
    this.render();
  }

  async render() {
    const [entries, prof, waterLogs, appPrefs, weekEntryFlags] = await Promise.all([
      foodEntries.byDate(this.date),
      profileStore.load(),
      water.byDate(this.date),
      prefs.load(),
      this.weekHasEntries(),
    ]);

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
    let calorieTarget = targets?.calories ?? 0;
    if (prof && targets && appPrefs.calorieGaugeMode === "add_active") {
      const { sedentaryBudget } = estimatedDailyActiveCalories(prof, targets.calories);
      calorieTarget = Math.max(0, sedentaryBudget);
    }

    const waterMl = waterLogs.reduce((s, w) => s + w.amountMl, 0);
    const waterGoal = appPrefs.waterGoalMl ?? 2500;
    const days = weekDates(this.date);
    const today = todayIso();

    this.innerHTML = `
      <div class="week-strip" role="tablist" aria-label="Week">
        ${days
          .map((iso) => {
            const d = new Date(`${iso}T00:00:00`);
            const selected = iso === this.date ? " is-selected" : "";
            const isToday = iso === today ? " is-today" : "";
            const has = weekEntryFlags.has(iso) ? " has-entries" : "";
            return `
              <button type="button" class="week-day${selected}${isToday}${has}" data-date="${iso}" role="tab" aria-selected="${iso === this.date}">
                <span class="week-day__dow">${d.toLocaleDateString(undefined, { weekday: "narrow" })}</span>
                <span class="week-day__num">${d.getDate()}</span>
                <span class="week-day__dot" aria-hidden="true"></span>
              </button>`;
          })
          .join("")}
      </div>

      <div class="card">
        <div class="calorie-hero">
          ${targets ? ringSvg(totals.calories, calorieTarget) : `<p class="empty-state">Set up your profile in Settings to see calorie targets.</p>`}
        </div>
        ${
          targets
            ? `<div class="macro-rows">
                ${macroRow("protein", "Protein", totals.proteinG, targets.proteinG)}
                ${macroRow("carbs", "Carbs", totals.carbsG, targets.carbsG)}
                ${macroRow("fat", "Fat", totals.fatG, targets.fatG)}
              </div>`
            : ""
        }
      </div>

      ${
        appPrefs.showWater !== false
          ? `<div class="card water-row">
              <div class="water-row__meta"><strong>${waterMl} ml</strong> / ${waterGoal} ml water</div>
              <div class="water-presets">
                ${WATER_PRESETS.map((ml) => `<button type="button" class="chip" data-water="${ml}">+${ml}</button>`).join("")}
                <button type="button" class="chip" data-water-custom>Custom</button>
              </div>
            </div>`
          : ""
      }

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
                <span class="food-item__meta">${Math.round(e.proteinG)}P · ${Math.round(e.carbsG)}C · ${Math.round(e.fatG)}F${e.quantityG != null ? ` · ${e.quantityG}g` : ""} · ${e.time}</span>
              </span>
              <span class="food-item__cals">${Math.round(e.calories)} kcal</span>
            </button>`
            )
            .join("")}
        </div>`
              )
              .join("")
      }

      <div class="fab-stack">
        <div class="fab-menu${this.fabOpen ? " is-open" : ""}" id="fab-menu">
          <button type="button" data-action="manual">Manual</button>
          <button type="button" data-action="photo">Photo / text AI</button>
          <button type="button" data-action="scan">Barcode</button>
        </div>
        <button class="fab" aria-label="Add food" aria-expanded="${this.fabOpen}" data-action="fab">+</button>
      </div>
    `;

    this.querySelectorAll("[data-date]").forEach((el) => {
      el.addEventListener("click", () => {
        this.date = el.getAttribute("data-date") || this.date;
        this.render();
      });
    });
    this.querySelectorAll("[data-water]").forEach((el) => {
      el.addEventListener("click", () => this.addWater(Number(el.getAttribute("data-water"))));
    });
    this.querySelector("[data-water-custom]")?.addEventListener("click", () => {
      const raw = prompt("Water amount (ml)", "200");
      if (!raw) return;
      const ml = Number(raw);
      if (ml > 0) this.addWater(ml);
    });
    this.querySelector('[data-action="fab"]')?.addEventListener("click", () => {
      this.fabOpen = !this.fabOpen;
      this.render();
    });
    this.querySelector('[data-action="manual"]')?.addEventListener("click", () => {
      location.hash = `#/entry/new?date=${this.date}`;
    });
    this.querySelector('[data-action="photo"]')?.addEventListener("click", () => {
      location.hash = `#/analyze?date=${this.date}`;
    });
    this.querySelector('[data-action="scan"]')?.addEventListener("click", () => {
      location.hash = `#/scan?date=${this.date}`;
    });
    this.querySelectorAll("[data-entry-id]").forEach((el) => {
      el.addEventListener("click", () => {
        const entry = entries.find((e) => e.id === el.getAttribute("data-entry-id"));
        this.openEntryForm(entry);
      });
      el.addEventListener("contextmenu", (ev) => {
        ev.preventDefault();
        const entry = entries.find((e) => e.id === el.getAttribute("data-entry-id"));
        if (entry && confirm(`Delete “${entry.name}”?`)) this.deleteEntry(entry);
      });
    });
  }

  async weekHasEntries() {
    const days = weekDates(this.date);
    const flags = new Set();
    await Promise.all(
      days.map(async (d) => {
        const list = await foodEntries.byDate(d);
        if (list.length) flags.add(d);
      })
    );
    return flags;
  }

  async addWater(amountMl) {
    await water.put({ id: crypto.randomUUID(), date: this.date, amountMl });
    this.render();
  }

  async deleteEntry(entry) {
    this._undoEntry = entry;
    await foodEntries.delete(entry.id);
    this.render();
    this.showUndoToast();
  }

  showUndoToast() {
    document.querySelector(".toast")?.remove();
    const toast = document.createElement("div");
    toast.className = "toast";
    toast.innerHTML = `Deleted <button type="button">Undo</button>`;
    toast.querySelector("button")?.addEventListener("click", async () => {
      if (this._undoEntry) await foodEntries.put(this._undoEntry);
      this._undoEntry = null;
      toast.remove();
      this.render();
    });
    document.body.appendChild(toast);
    setTimeout(() => toast.remove(), 5000);
  }

  openEntryForm(entry) {
    location.hash = entry ? `#/entry/${entry.id}?date=${this.date}` : `#/entry/new?date=${this.date}`;
  }
}

function escapeHtml(s) {
  return s.replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]);
}

customElements.define("diary-view", DiaryView);

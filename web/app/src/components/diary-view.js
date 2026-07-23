// @ts-check
import { foodEntries, profile as profileStore, water, prefs } from "../lib/db.js";
import { dailyTargets, estimatedDailyActiveCalories } from "../lib/nofud-core/formulas.js";
import { openSheet } from "../lib/ui/sheet.js";
import { openConfirm, openInput } from "../lib/ui/dialog.js";

const MEAL_LABELS = { breakfast: "Breakfast", lunch: "Lunch", dinner: "Dinner", snack: "Snack" };
const MEAL_ORDER = ["breakfast", "lunch", "dinner", "snack"];
const WATER_PRESETS = [250, 500, 750];

const ICONS = {
  photo: `<svg viewBox="0 0 24 24" aria-hidden="true"><path fill="currentColor" d="M12 12.5a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5zM4 5h3.2l1.4-1.8c.2-.3.5-.4.8-.4h5.2c.3 0 .6.1.8.4L16.8 5H20c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V7c0-1.1.9-2 2-2zm8 13c2.8 0 5-2.2 5-5s-2.2-5-5-5-5 2.2-5 5 2.2 5 5 5z"/></svg>`,
  note: `<svg viewBox="0 0 24 24" aria-hidden="true"><path fill="currentColor" d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04a1 1 0 0 0 0-1.41l-2.34-2.34a1 1 0 0 0-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z"/></svg>`,
  manual: `<svg viewBox="0 0 24 24" aria-hidden="true"><path fill="currentColor" d="M14 2H6c-1.1 0-2 .9-2 2v16c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V8l-6-6zm2 16H8v-2h8v2zm0-4H8v-2h8v2zm-3-5V3.5L18.5 9H13z"/></svg>`,
  barcode: `<svg viewBox="0 0 24 24" aria-hidden="true"><path fill="currentColor" d="M3 5h2v14H3V5zm3 0h1v14H6V5zm2 0h3v14H8V5zm4 0h1v14h-1V5zm2 0h3v14h-3V5zm4 0h1v14h-1V5zm2 0h2v14h-2V5z"/></svg>`,
  recents: `<svg viewBox="0 0 24 24" aria-hidden="true"><path fill="currentColor" d="M13 3a9 9 0 1 0 8.94 10h-2.02A7 7 0 1 1 13 5v5.59l3.3 3.3 1.4-1.42L15 10.17V3h-2z"/></svg>`,
};

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
  const dow = selected.getDay();
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
      <circle class="calorie-ring__progress" cx="${size / 2}" cy="${size / 2}" r="${r}" fill="none"
        stroke="${over ? "var(--over)" : "var(--teal)"}" stroke-width="${stroke}"
        stroke-linecap="round"
        stroke-dasharray="0 ${c.toFixed(1)}"
        data-dash="${(pct * c).toFixed(1)} ${c.toFixed(1)}"
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
      <div class="macro-bar" aria-hidden="true"><span data-width="${pct.toFixed(1)}%"></span></div>
      <span class="macro-row__vals">${Math.round(value)} / ${Math.round(target)} g</span>
    </div>`;
}

function tile(action, label, sub, icon) {
  return `
    <button type="button" class="add-food-tile" data-add="${action}">
      <span class="add-food-tile__icon">${icon}</span>
      <span class="add-food-tile__label">${label}</span>
      ${sub ? `<span class="add-food-tile__sub">${sub}</span>` : ""}
    </button>`;
}

export class DiaryView extends HTMLElement {
  constructor() {
    super();
    this.date = todayIso();
    this.fabOpen = false;
    /** @type {import('../lib/nofud-core/models.js').FoodEntry|null} */
    this._undoEntry = null;
    /** @type {ReturnType<typeof openSheet> | null} */
    this._sheet = null;
  }

  connectedCallback() {
    this.render();
  }

  disconnectedCallback() {
    this._sheet?.close();
    this._sheet = null;
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
    const waterPct = waterGoal > 0 ? Math.min(100, (waterMl / waterGoal) * 100) : 0;
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
            const future = iso > today;
            return `
              <button type="button" class="week-day${selected}${isToday}${has}" data-date="${iso}" role="tab"
                aria-selected="${iso === this.date}" ${future ? "disabled" : ""}>
                <span class="week-day__dow">${d.toLocaleDateString(undefined, { weekday: "narrow" })}</span>
                <span class="week-day__num">${d.getDate()}</span>
                <span class="week-day__dot" aria-hidden="true"></span>
              </button>`;
          })
          .join("")}
      </div>

      <div class="card card--glass home-hero" data-day-swipe>
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
          ? `<div class="card card--glass water-row">
              <div class="water-row__top">
                <div class="water-row__meta"><strong>${waterMl} ml</strong> / ${waterGoal} ml water</div>
                <div class="water-presets">
                  ${WATER_PRESETS.map((ml) => `<button type="button" class="chip" data-water="${ml}">+${ml}</button>`).join("")}
                  <button type="button" class="chip" data-water-custom>Custom</button>
                </div>
              </div>
              <div class="water-bar" role="progressbar" aria-valuemin="0" aria-valuemax="${waterGoal}" aria-valuenow="${waterMl}" aria-label="Water intake">
                <span data-width="${waterPct.toFixed(1)}%"></span>
              </div>
            </div>`
          : ""
      }

      ${
        entries.length === 0
          ? `<p class="empty-state">No entries yet for this day. Tap + to log food.</p>`
          : MEAL_ORDER.filter((m) => entries.some((e) => e.mealType === m))
              .map((mealType) => {
                const mealEntries = entries.filter((e) => e.mealType === mealType);
                return `
        <div class="meal-group">
          <h2>${MEAL_LABELS[mealType]}</h2>
          ${mealEntries
            .map(
              (e) => `
            <div class="food-swipe" data-entry-id="${e.id}">
              <div class="food-swipe__behind">Delete</div>
              <div class="food-item">
                <button type="button" class="food-item__main" data-edit>
                  <span>
                    <span class="food-item__name">${escapeHtml(e.name)}</span><br/>
                    <span class="food-item__meta">${Math.round(e.proteinG)}P · ${Math.round(e.carbsG)}C · ${Math.round(e.fatG)}F${e.quantityG != null ? ` · ${e.quantityG}g` : ""} · ${e.time}</span>
                  </span>
                  <span class="food-item__cals">${Math.round(e.calories)} kcal</span>
                </button>
                <button type="button" class="food-item__menu" data-menu aria-label="More actions for ${escapeAttr(e.name)}">⋮</button>
              </div>
            </div>`
            )
            .join("")}
        </div>`;
              })
              .join("")
      }

      <button class="fab" aria-label="Add food" aria-expanded="${this.fabOpen}" data-action="fab">+</button>
    `;

    this.bindInteractions(entries, appPrefs);
    this.animateFills();
  }

  /**
   * @param {import('../lib/nofud-core/models.js').FoodEntry[]} entries
   * @param {Awaited<ReturnType<typeof prefs.load>>} appPrefs
   */
  bindInteractions(entries, appPrefs) {
    this.querySelectorAll("[data-date]").forEach((el) => {
      el.addEventListener("click", () => {
        const iso = el.getAttribute("data-date") || this.date;
        if (iso > todayIso()) return;
        this.date = iso;
        this.render();
      });
    });
    this.querySelectorAll("[data-water]").forEach((el) => {
      el.addEventListener("click", () => this.addWater(Number(el.getAttribute("data-water"))));
    });
    this.querySelector("[data-water-custom]")?.addEventListener("click", () => this.customWater());
    this.querySelector('[data-action="fab"]')?.addEventListener("click", () => this.openAddFoodSheet(appPrefs));

    this.querySelectorAll(".food-swipe").forEach((row) => {
      const id = row.getAttribute("data-entry-id");
      const entry = entries.find((e) => e.id === id);
      if (!entry) return;
      const item = row.querySelector(".food-item");
      row.querySelector("[data-edit]")?.addEventListener("click", () => this.openEntryForm(entry));
      row.querySelector("[data-menu]")?.addEventListener("click", async (ev) => {
        ev.stopPropagation();
        const ok = await openConfirm({
          title: "Delete entry",
          message: `Delete “${entry.name}”?`,
          confirmLabel: "Delete",
          danger: true,
        });
        if (ok) this.deleteEntry(entry);
      });
      if (item instanceof HTMLElement) this.bindSwipeDelete(row, item, entry);
    });

    this.bindDaySwipe(this.querySelector("[data-day-swipe]"));
  }

  animateFills() {
    requestAnimationFrame(() => {
      this.querySelectorAll("[data-width]").forEach((el) => {
        if (el instanceof HTMLElement) el.style.width = el.getAttribute("data-width") || "0%";
      });
      this.querySelectorAll(".calorie-ring__progress").forEach((el) => {
        if (el instanceof SVGElement) {
          const dash = el.getAttribute("data-dash");
          if (dash) el.setAttribute("stroke-dasharray", dash);
        }
      });
    });
  }

  /** @param {Element | null} region */
  bindDaySwipe(region) {
    if (!(region instanceof HTMLElement)) return;
    let startX = 0;
    let startY = 0;
    let tracking = false;

    region.addEventListener(
      "touchstart",
      (ev) => {
        const t = /** @type {TouchEvent} */ (ev).changedTouches[0];
        startX = t.clientX;
        startY = t.clientY;
        tracking = true;
      },
      { passive: true }
    );

    region.addEventListener(
      "touchend",
      (ev) => {
        if (!tracking) return;
        tracking = false;
        const t = /** @type {TouchEvent} */ (ev).changedTouches[0];
        const dx = t.clientX - startX;
        const dy = t.clientY - startY;
        if (Math.abs(dx) < 56 || Math.abs(dx) < Math.abs(dy) * 1.4) return;
        const next = shiftDate(this.date, dx < 0 ? 1 : -1);
        if (next > todayIso()) return;
        this.date = next;
        this.render();
      },
      { passive: true }
    );
  }

  /**
   * @param {Element} row
   * @param {HTMLElement} item
   * @param {import('../lib/nofud-core/models.js').FoodEntry} entry
   */
  bindSwipeDelete(row, item, entry) {
    let startX = 0;
    let startY = 0;
    let dx = 0;
    let active = false;
    let horizontal = false;

    const reset = () => {
      item.style.transform = "";
      dx = 0;
      active = false;
      horizontal = false;
    };

    row.addEventListener(
      "touchstart",
      (ev) => {
        const t = /** @type {TouchEvent} */ (ev).changedTouches[0];
        startX = t.clientX;
        startY = t.clientY;
        active = true;
        horizontal = false;
        dx = 0;
      },
      { passive: true }
    );

    row.addEventListener(
      "touchmove",
      (ev) => {
        if (!active) return;
        const t = /** @type {TouchEvent} */ (ev).changedTouches[0];
        const moveX = t.clientX - startX;
        const moveY = t.clientY - startY;
        if (!horizontal) {
          if (Math.abs(moveX) < 10 && Math.abs(moveY) < 10) return;
          if (Math.abs(moveY) > Math.abs(moveX)) {
            active = false;
            return;
          }
          horizontal = true;
        }
        dx = Math.min(0, moveX);
        item.style.transition = "none";
        item.style.transform = `translateX(${dx}px)`;
      },
      { passive: true }
    );

    row.addEventListener(
      "touchend",
      () => {
        if (!active && !horizontal) return;
        item.style.transition = "";
        if (dx < -88) this.deleteEntry(entry);
        else reset();
      },
      { passive: true }
    );
  }

  /** @param {Awaited<ReturnType<typeof prefs.load>>} appPrefs */
  openAddFoodSheet(appPrefs) {
    if (this._sheet) {
      this._sheet.close();
      this._sheet = null;
      this.fabOpen = false;
      this.querySelector(".fab")?.setAttribute("aria-expanded", "false");
      return;
    }

    this.fabOpen = true;
    this.querySelector(".fab")?.setAttribute("aria-expanded", "true");

    const showWater = appPrefs.showWater !== false;
    const body = `
      <div class="add-food-heroes">
        ${tile("photo", "Photo", "Snap a meal", ICONS.photo)}
        ${tile("note", "Text / note", "Describe food", ICONS.note)}
        ${tile("manual", "Manual", "Enter macros", ICONS.manual)}
      </div>
      <p class="add-food-section">More</p>
      <div class="add-food-row">
        ${tile("scan", "Barcode", "", ICONS.barcode)}
        ${tile("recents", "Recents", "", ICONS.recents)}
      </div>
      ${
        showWater
          ? `<p class="add-food-section">Water</p>
             <div class="add-food-water">
               ${WATER_PRESETS.map((ml) => `<button type="button" class="chip" data-sheet-water="${ml}">+${ml} ml</button>`).join("")}
               <button type="button" class="chip" data-sheet-water-custom>Custom</button>
             </div>`
          : ""
      }
    `;

    const sheet = openSheet({
      title: "Add food",
      body,
      onClose: () => {
        this._sheet = null;
        this.fabOpen = false;
        this.querySelector(".fab")?.setAttribute("aria-expanded", "false");
      },
    });
    this._sheet = sheet;

    const go = (hash) => {
      sheet.close();
      location.hash = hash;
    };

    sheet.body.querySelector('[data-add="photo"]')?.addEventListener("click", () => go(`#/analyze?date=${this.date}`));
    sheet.body.querySelector('[data-add="note"]')?.addEventListener("click", () => go(`#/analyze?date=${this.date}`));
    sheet.body.querySelector('[data-add="manual"]')?.addEventListener("click", () => go(`#/entry/new?date=${this.date}`));
    sheet.body.querySelector('[data-add="scan"]')?.addEventListener("click", () => go(`#/scan?date=${this.date}`));
    sheet.body.querySelector('[data-add="recents"]')?.addEventListener("click", () => go(`#/analyze?date=${this.date}`));

    sheet.body.querySelectorAll("[data-sheet-water]").forEach((el) => {
      el.addEventListener("click", async () => {
        await this.addWater(Number(el.getAttribute("data-sheet-water")));
        sheet.close();
      });
    });
    sheet.body.querySelector("[data-sheet-water-custom]")?.addEventListener("click", async () => {
      sheet.close();
      await this.customWater();
    });
  }

  async customWater() {
    const raw = await openInput({
      title: "Add water",
      label: "Amount",
      value: "200",
      unit: "ml",
      inputMode: "numeric",
      type: "number",
      confirmLabel: "Add",
    });
    if (raw == null) return;
    const ml = Number(raw);
    if (ml > 0) await this.addWater(ml);
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

function escapeAttr(s) {
  return String(s).replace(/"/g, "&quot;");
}

customElements.define("diary-view", DiaryView);

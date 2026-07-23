// @ts-check
import { foodEntries, profile as profileStore, water, prefs } from "../lib/db.js";
import { dailyTargets, estimatedDailyActiveCalories } from "../lib/nofud-core/formulas.js";
import { openSheet } from "../lib/ui/sheet.js";
import { openConfirm, openInput } from "../lib/ui/dialog.js";
import { recentFoods } from "../lib/recent-foods.js";

const MEAL_LABELS = { breakfast: "Breakfast", lunch: "Lunch", dinner: "Dinner", snack: "Snack" };
const MEAL_ORDER = ["breakfast", "lunch", "dinner", "snack"];
const WATER_PRESETS = [250, 500, 750];
const HOME_DATE_KEY = "nofud-home-date";

const ICONS = {
  photo: `<svg viewBox="0 0 24 24" aria-hidden="true"><path fill="currentColor" d="M12 12.5a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5zM4 5h3.2l1.4-1.8c.2-.3.5-.4.8-.4h5.2c.3 0 .6.1.8.4L16.8 5H20c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V7c0-1.1.9-2 2-2zm8 13c2.8 0 5-2.2 5-5s-2.2-5-5-5-5 2.2-5 5 2.2 5 5 5z"/></svg>`,
  note: `<svg viewBox="0 0 24 24" aria-hidden="true"><path fill="currentColor" d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04a1 1 0 0 0 0-1.41l-2.34-2.34a1 1 0 0 0-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z"/></svg>`,
  manual: `<svg viewBox="0 0 24 24" aria-hidden="true"><path fill="currentColor" d="M14 2H6c-1.1 0-2 .9-2 2v16c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V8l-6-6zm2 16H8v-2h8v2zm0-4H8v-2h8v2zm-3-5V3.5L18.5 9H13z"/></svg>`,
  barcode: `<svg viewBox="0 0 24 24" aria-hidden="true"><path fill="currentColor" d="M3 5h2v14H3V5zm3 0h1v14H6V5zm2 0h3v14H8V5zm4 0h1v14h-1V5zm2 0h3v14h-3V5zm4 0h1v14h-1V5zm2 0h2v14h-2V5z"/></svg>`,
  recents: `<svg viewBox="0 0 24 24" aria-hidden="true"><path fill="currentColor" d="M13 3a9 9 0 1 0 8.94 10h-2.02A7 7 0 1 1 13 5v5.59l3.3 3.3 1.4-1.42L15 10.17V3h-2z"/></svg>`,
  breakfast: `<svg viewBox="0 0 24 24" aria-hidden="true"><path fill="currentColor" d="M18 2H6v6h12V2zm0 8H6c-1.1 0-2 .9-2 2v8c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2v-8c0-1.1-.9-2-2-2zM8 16H6v-2h2v2zm4 0h-2v-2h2v2zm4 0h-2v-2h2v2z"/></svg>`,
  lunch: `<svg viewBox="0 0 24 24" aria-hidden="true"><path fill="currentColor" d="M11 9H9V2H7v7H5V2H3v7c0 2.12 1.66 3.84 3.75 3.97V22h2.5v-9.03C11.34 12.84 13 11.12 13 9V2h-2v7zm5-3v8h2.5v8H21V2c-2.76 0-5 2.24-5 4z"/></svg>`,
  dinner: `<svg viewBox="0 0 24 24" aria-hidden="true"><path fill="currentColor" d="M8.1 13.34 3.91 9.16a4.008 4.008 0 0 1 0-5.66l7.05 7.05-2.86 2.79zm6.78-.02c1.58.92 3.68.55 5.05-.81s1.74-3.46.81-5.05l-3.14 3.14L14.2 8.2l3.14-3.14c-1.58-.92-3.68-.55-5.05.81s-1.74 3.46-.81 5.05l-7.06 7.05 1.41 1.41 5.05-5.05L15 18.95l1.41-1.41-1.53-4.22z"/></svg>`,
  snack: `<svg viewBox="0 0 24 24" aria-hidden="true"><path fill="currentColor" d="M12 6c1.11 0 2-.9 2-2 0-.38-.1-.73-.29-1.03L12 0l-1.71 2.97c-.19.3-.29.65-.29 1.03 0 1.1.9 2 2 2zm4.6 9.99c-.84-.33-1.4-.99-1.58-1.82-.03-.15-.05-.3-.05-.46 0-.84.41-1.58 1.04-2.04C16.66 11.2 17 10.39 17 9.5c0-1.52-.98-2.81-2.34-3.28C14.21 5.91 13.14 5.75 12 5.75s-2.21.16-2.66.47C7.98 6.69 7 7.98 7 9.5c0 .89.34 1.7.99 2.17.63.46 1.04 1.2 1.04 2.04 0 .16-.02.31-.05.46-.18.83-.74 1.49-1.58 1.82C5.85 16.66 5 17.95 5 19.5V21h14v-1.5c0-1.55-.85-2.84-2.4-3.51z"/></svg>`,
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

function clampDate(iso) {
  const today = todayIso();
  return iso > today ? today : iso;
}

function loadHomeDate() {
  try {
    const saved = sessionStorage.getItem(HOME_DATE_KEY);
    if (saved && /^\d{4}-\d{2}-\d{2}$/.test(saved)) return clampDate(saved);
  } catch {
    /* private mode */
  }
  return todayIso();
}

/** @param {string} iso */
function saveHomeDate(iso) {
  try {
    sessionStorage.setItem(HOME_DATE_KEY, iso);
  } catch {
    /* ignore */
  }
}

/** Semicircle (~180°) calorie gauge — Android HomeCalorieHero shape. */
function ringSvg(eaten, target) {
  const width = 220;
  const height = 118;
  const stroke = 14;
  const r = (width - stroke) / 2;
  const cx = width / 2;
  const cy = height - stroke / 2;
  const halfC = Math.PI * r;
  const pct = target > 0 ? Math.min(1, eaten / target) : 0;
  const remaining = Math.max(0, Math.round(target - eaten));
  const over = eaten > target;
  const x1 = (cx - r).toFixed(1);
  const x2 = (cx + r).toFixed(1);
  const y = cy.toFixed(1);
  const arc = `M ${x1} ${y} A ${r.toFixed(1)} ${r.toFixed(1)} 0 0 1 ${x2} ${y}`;
  return `
    <svg class="calorie-ring calorie-ring--semi" viewBox="0 0 ${width} ${height}" role="img"
      aria-label="${Math.round(eaten)} of ${target} calories">
      <path d="${arc}" fill="none" stroke="var(--surface)" stroke-width="${stroke}" stroke-linecap="round" />
      <path class="calorie-ring__progress" d="${arc}" fill="none"
        stroke="${over ? "var(--over)" : "var(--teal)"}" stroke-width="${stroke}" stroke-linecap="round"
        stroke-dasharray="0 ${halfC.toFixed(1)}"
        data-dash="${(pct * halfC).toFixed(1)} ${halfC.toFixed(1)}" />
      <text x="50%" y="62%" text-anchor="middle" class="calorie-ring__label">${over ? `+${Math.round(eaten - target)}` : remaining}</text>
      <text x="50%" y="82%" text-anchor="middle" class="calorie-ring__sub">${over ? "over" : "remaining"}</text>
    </svg>`;
}

/** Vertical macro tube — Android MacroCard. */
function macroTube(key, label, value, target) {
  const pct = target > 0 ? Math.min(100, (value / target) * 100) : 0;
  let status;
  if (target <= 0) status = "—";
  else if (Math.round(value) === Math.round(target)) status = "goal";
  else if (value < target) status = `${Math.round(target - value)} g left`;
  else status = `+${Math.round(value - target)} g`;
  return `
    <div class="macro-tube macro-tube--${key}">
      <span class="macro-tube__value">${Math.round(value)}</span>
      <div class="macro-tube__track" aria-hidden="true">
        <span class="macro-tube__fill" data-height="${pct.toFixed(1)}%"></span>
      </div>
      <span class="macro-tube__label">${label}</span>
      <span class="macro-tube__status">${status}</span>
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

/**
 * @param {import('../lib/nofud-core/models.js').FoodEntry[]} mealEntries
 * @param {string} mealType
 */
function mealCard(mealType, mealEntries) {
  const totals = mealEntries.reduce(
    (acc, e) => {
      acc.calories += e.calories;
      acc.proteinG += e.proteinG;
      acc.carbsG += e.carbsG;
      acc.fatG += e.fatG;
      return acc;
    },
    { calories: 0, proteinG: 0, carbsG: 0, fatG: 0 }
  );
  const icon = ICONS[mealType] || ICONS.snack;
  return `
    <section class="meal-card card card--glass">
      <header class="meal-card__header">
        <span class="meal-card__icon meal-card__icon--${mealType}">${icon}</span>
        <div class="meal-card__titles">
          <h2 class="meal-card__title">${MEAL_LABELS[mealType]}</h2>
          <p class="meal-card__summary">
            <span class="meal-card__kcal">${Math.round(totals.calories)} kcal</span>
            · ${Math.round(totals.proteinG)}P · ${Math.round(totals.carbsG)}C · ${Math.round(totals.fatG)}F
          </p>
        </div>
      </header>
      <div class="meal-card__list">
        ${mealEntries
          .map(
            (e) => `
          <div class="food-swipe" data-entry-id="${e.id}">
            <div class="food-swipe__behind food-swipe__behind--dup" aria-hidden="true">Duplicate</div>
            <div class="food-swipe__behind food-swipe__behind--del" aria-hidden="true">Delete</div>
            <div class="food-item">
              <button type="button" class="food-item__main" data-edit>
                <span class="food-item__text">
                  <span class="food-item__name">${escapeHtml(e.name)}</span>
                  <span class="food-item__meta">${Math.round(e.proteinG)}P · ${Math.round(e.carbsG)}C · ${Math.round(e.fatG)}F${e.quantityG != null ? ` · ${e.quantityG}g` : ""} · ${e.time}</span>
                </span>
                <span class="food-item__cals">${Math.round(e.calories)}</span>
              </button>
              <button type="button" class="food-item__menu" data-menu aria-label="More actions for ${escapeAttr(e.name)}">⋮</button>
            </div>
          </div>`
          )
          .join("")}
      </div>
    </section>`;
}

export class DiaryView extends HTMLElement {
  constructor() {
    super();
    this.date = loadHomeDate();
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

  /** @param {string} iso */
  setDate(iso) {
    this.date = clampDate(iso);
    saveHomeDate(this.date);
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
    const nextWeekStart = shiftDate(days[0], 7);
    const canNextWeek = nextWeekStart <= today;

    this.innerHTML = `
      <div class="week-nav">
        <button type="button" class="week-nav__btn" data-week="-1" aria-label="Previous week">
          <svg viewBox="0 0 24 24" width="20" height="20" aria-hidden="true"><path fill="currentColor" d="M15.41 7.41 14 6l-6 6 6 6 1.41-1.41L10.83 12z"/></svg>
        </button>
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
        <button type="button" class="week-nav__btn" data-week="1" aria-label="Next week" ${canNextWeek ? "" : "disabled"}>
          <svg viewBox="0 0 24 24" width="20" height="20" aria-hidden="true"><path fill="currentColor" d="M8.59 16.59 10 18l6-6-6-6-1.41 1.41L13.17 12z"/></svg>
        </button>
      </div>

      <div class="card card--glass home-hero" data-day-swipe>
        <div class="calorie-hero">
          ${targets ? ringSvg(totals.calories, calorieTarget) : `<p class="empty-state">Set up your profile in Settings to see calorie targets.</p>`}
        </div>
        ${
          targets
            ? `<div class="macro-tubes">
                ${macroTube("protein", "Protein", totals.proteinG, targets.proteinG)}
                ${macroTube("carbs", "Carbs", totals.carbsG, targets.carbsG)}
                ${macroTube("fat", "Fat", totals.fatG, targets.fatG)}
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
              .map((mealType) => mealCard(mealType, entries.filter((e) => e.mealType === mealType)))
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
        this.setDate(iso);
        this.render();
      });
    });
    this.querySelectorAll("[data-week]").forEach((el) => {
      el.addEventListener("click", () => {
        const dir = Number(el.getAttribute("data-week"));
        if (!dir || el.hasAttribute("disabled")) return;
        this.setDate(shiftDate(this.date, dir * 7));
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
      row.querySelector("[data-menu]")?.addEventListener("click", (ev) => {
        ev.stopPropagation();
        this.openFoodMenu(entry);
      });
      if (item instanceof HTMLElement) this.bindFoodSwipe(row, item, entry);
    });

    this.bindDaySwipe(this.querySelector("[data-day-swipe]"));
  }

  animateFills() {
    requestAnimationFrame(() => {
      this.querySelectorAll("[data-width]").forEach((el) => {
        if (el instanceof HTMLElement) el.style.width = el.getAttribute("data-width") || "0%";
      });
      this.querySelectorAll("[data-height]").forEach((el) => {
        if (el instanceof HTMLElement) el.style.height = el.getAttribute("data-height") || "0%";
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
    /** @type {number | null} */
    let pointerId = null;

    region.addEventListener("pointerdown", (ev) => {
      const pev = /** @type {PointerEvent} */ (ev);
      if (pev.pointerType === "mouse" && pev.button !== 0) return;
      startX = pev.clientX;
      startY = pev.clientY;
      tracking = true;
      pointerId = pev.pointerId;
    });

    region.addEventListener("pointerup", (ev) => {
      const pev = /** @type {PointerEvent} */ (ev);
      if (!tracking || pev.pointerId !== pointerId) return;
      tracking = false;
      pointerId = null;
      const dx = pev.clientX - startX;
      const dy = pev.clientY - startY;
      if (Math.abs(dx) < 56 || Math.abs(dx) < Math.abs(dy) * 1.4) return;
      this.setDate(shiftDate(this.date, dx < 0 ? 1 : -1));
      this.render();
    });

    region.addEventListener("pointercancel", () => {
      tracking = false;
      pointerId = null;
    });
  }

  /**
   * Swipe-left → delete; swipe-right → duplicate. Pointer Events for desktop parity.
   * @param {Element} row
   * @param {HTMLElement} item
   * @param {import('../lib/nofud-core/models.js').FoodEntry} entry
   */
  bindFoodSwipe(row, item, entry) {
    let startX = 0;
    let startY = 0;
    let dx = 0;
    let active = false;
    let horizontal = false;
    /** @type {number | null} */
    let pointerId = null;

    const reset = () => {
      item.style.transform = "";
      dx = 0;
      active = false;
      horizontal = false;
      pointerId = null;
      row.classList.remove("is-swiping-left", "is-swiping-right");
    };

    row.addEventListener("pointerdown", (ev) => {
      const pev = /** @type {PointerEvent} */ (ev);
      if (pev.pointerType === "mouse" && pev.button !== 0) return;
      if (/** @type {Element} */ (pev.target).closest("[data-menu]")) return;
      startX = pev.clientX;
      startY = pev.clientY;
      active = true;
      horizontal = false;
      dx = 0;
      pointerId = pev.pointerId;
      try {
        row.setPointerCapture(pev.pointerId);
      } catch {
        /* ignore */
      }
    });

    row.addEventListener("pointermove", (ev) => {
      const pev = /** @type {PointerEvent} */ (ev);
      if (!active || pev.pointerId !== pointerId) return;
      const moveX = pev.clientX - startX;
      const moveY = pev.clientY - startY;
      if (!horizontal) {
        if (Math.abs(moveX) < 10 && Math.abs(moveY) < 10) return;
        if (Math.abs(moveY) > Math.abs(moveX)) {
          active = false;
          return;
        }
        horizontal = true;
      }
      dx = moveX;
      item.style.transition = "none";
      item.style.transform = `translateX(${dx}px)`;
      row.classList.toggle("is-swiping-left", dx < 0);
      row.classList.toggle("is-swiping-right", dx > 0);
    });

    const end = (ev) => {
      const pev = /** @type {PointerEvent} */ (ev);
      if (pev.pointerId !== pointerId && pointerId != null) return;
      if (!active && !horizontal) {
        reset();
        return;
      }
      item.style.transition = "";
      if (dx < -88) this.deleteEntry(entry);
      else if (dx > 88) this.duplicateEntry(entry);
      else reset();
    };

    row.addEventListener("pointerup", end);
    row.addEventListener("pointercancel", () => reset());
  }

  /** @param {import('../lib/nofud-core/models.js').FoodEntry} entry */
  openFoodMenu(entry) {
    const sheet = openSheet({
      title: entry.name,
      body: `
        <div class="sheet-actions" role="menu">
          <button type="button" role="menuitem" data-act="edit">Edit</button>
          <button type="button" role="menuitem" data-act="meal">Change meal</button>
          <button type="button" role="menuitem" data-act="dup">Duplicate</button>
          <button type="button" role="menuitem" data-act="del" class="is-danger">Delete</button>
        </div>`,
    });

    sheet.body.querySelector('[data-act="edit"]')?.addEventListener("click", () => {
      sheet.close();
      this.openEntryForm(entry);
    });
    sheet.body.querySelector('[data-act="dup"]')?.addEventListener("click", () => {
      sheet.close();
      this.duplicateEntry(entry);
    });
    sheet.body.querySelector('[data-act="del"]')?.addEventListener("click", async () => {
      sheet.close();
      const ok = await openConfirm({
        title: "Delete entry",
        message: `Delete “${entry.name}”?`,
        confirmLabel: "Delete",
        danger: true,
      });
      if (ok) this.deleteEntry(entry);
    });
    sheet.body.querySelector('[data-act="meal"]')?.addEventListener("click", () => {
      sheet.close();
      this.openChangeMealSheet(entry);
    });
  }

  /** @param {import('../lib/nofud-core/models.js').FoodEntry} entry */
  openChangeMealSheet(entry) {
    const sheet = openSheet({
      title: "Change meal",
      body: `
        <div class="sheet-actions" role="listbox" aria-label="Meal type">
          ${MEAL_ORDER.map(
            (m) =>
              `<button type="button" role="option" data-meal="${m}" aria-selected="${entry.mealType === m}">
                ${MEAL_LABELS[m]}${entry.mealType === m ? " · current" : ""}
              </button>`
          ).join("")}
        </div>`,
    });
    sheet.body.querySelectorAll("[data-meal]").forEach((btn) => {
      btn.addEventListener("click", async () => {
        const meal = /** @type {"breakfast"|"lunch"|"dinner"|"snack"} */ (btn.getAttribute("data-meal"));
        sheet.close();
        if (!meal || meal === entry.mealType) return;
        await foodEntries.put({ ...entry, mealType: meal });
        this.render();
      });
    });
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
               <label class="add-food-water-slider">
                 <span>Custom</span>
                 <input type="range" min="50" max="1000" step="50" value="200" data-water-range aria-label="Water amount" />
                 <span data-water-range-val>200 ml</span>
                 <button type="button" class="chip" data-sheet-water-range>Add</button>
               </label>
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

    sheet.body.querySelector('[data-add="photo"]')?.addEventListener("click", () => go(`#/analyze?date=${this.date}&mode=photo`));
    sheet.body.querySelector('[data-add="note"]')?.addEventListener("click", () => go(`#/analyze?date=${this.date}&mode=note`));
    sheet.body.querySelector('[data-add="manual"]')?.addEventListener("click", () => go(`#/entry/new?date=${this.date}`));
    sheet.body.querySelector('[data-add="scan"]')?.addEventListener("click", () => go(`#/scan?date=${this.date}`));
    sheet.body.querySelector('[data-add="recents"]')?.addEventListener("click", () => {
      this.openRecentsSheet(sheet);
    });

    sheet.body.querySelectorAll("[data-sheet-water]").forEach((el) => {
      el.addEventListener("click", async () => {
        await this.addWater(Number(el.getAttribute("data-sheet-water")));
        sheet.close();
      });
    });

    const range = /** @type {HTMLInputElement | null} */ (sheet.body.querySelector("[data-water-range]"));
    const rangeVal = sheet.body.querySelector("[data-water-range-val]");
    range?.addEventListener("input", () => {
      if (rangeVal) rangeVal.textContent = `${range.value} ml`;
    });
    sheet.body.querySelector("[data-sheet-water-range]")?.addEventListener("click", async () => {
      const ml = Number(range?.value || 0);
      if (ml > 0) {
        await this.addWater(ml);
        sheet.close();
      }
    });
  }

  /**
   * Nested Recents chooser — dismiss then navigate to review form (Android pattern).
   * @param {ReturnType<typeof openSheet>} parentSheet
   */
  async openRecentsSheet(parentSheet) {
    const foods = await recentFoods(20);
    const body = foods.length
      ? `<div class="recents-list sheet-recents">
          ${foods
            .map(
              (r) => `
            <button type="button" data-recent='${escapeAttr(JSON.stringify(r))}'>
              <strong>${escapeHtml(r.name)}</strong><br/>
              <span class="recents-meta">${Math.round(r.calories)} kcal · ${Math.round(r.proteinG)}P / ${Math.round(r.carbsG)}C / ${Math.round(r.fatG)}F</span>
            </button>`
            )
            .join("")}
        </div>`
      : `<p class="empty-state" style="padding:1rem 0;">No recent foods yet. Log something first.</p>`;

    const sheet = openSheet({
      title: "Recents",
      body,
    });

    sheet.body.querySelectorAll("[data-recent]").forEach((btn) => {
      btn.addEventListener("click", () => {
        const raw = btn.getAttribute("data-recent");
        if (!raw) return;
        const prefill = JSON.parse(raw);
        sheet.close();
        parentSheet.close();
        location.hash = `#/entry/new?date=${this.date}&prefill=${encodeURIComponent(JSON.stringify(prefill))}`;
      });
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

  /** @param {import('../lib/nofud-core/models.js').FoodEntry} entry */
  async duplicateEntry(entry) {
    const copy = {
      ...entry,
      id: crypto.randomUUID(),
    };
    await foodEntries.put(copy);
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
  return String(s).replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]);
}

function escapeAttr(s) {
  return String(s).replace(/'/g, "&#39;").replace(/"/g, "&quot;");
}

customElements.define("diary-view", DiaryView);

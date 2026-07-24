// @ts-check
import { foodEntries, prefs } from "../lib/db.js";
import { subpageBar, bindSubpageBack } from "../lib/ui/subpage.js";
import { openConfirm } from "../lib/ui/dialog.js";
import { guessMealTypeFromPrefs } from "../lib/meal-schedule.js";
import { toggleFavorite, isFavorite } from "../lib/saved-meals.js";
import {
  ensureServingUnits,
  pickerOptions,
  optionMatching,
  isGramUnit,
  parseQuantity,
  formatQuantity,
  formatGramsDisplay,
  scaleNutrition,
  heuristicOptions,
  normalizedOptions,
  displayUnit,
  optionId,
} from "../lib/chompass-core/serving-units.js";
import { ALL_MICRO_KEYS } from "../lib/home-nutrients.js";

const MICRO_FIELDS = [
  ["sugarG", "Sugar g"],
  ["addedSugarG", "Added sugar g"],
  ["saturatedFatG", "Sat fat g"],
  ["monounsaturatedFatG", "Mono fat g"],
  ["polyunsaturatedFatG", "Poly fat g"],
  ["transFatG", "Trans fat g"],
  ["cholesterolMg", "Cholesterol mg"],
  ["sodiumMg", "Sodium mg"],
  ["potassiumMg", "Potassium mg"],
  ["calciumMg", "Calcium mg"],
  ["ironMg", "Iron mg"],
  ["magnesiumMg", "Magnesium mg"],
  ["zincMg", "Zinc mg"],
  ["vitaminAMcg", "Vit A mcg"],
  ["vitaminCMg", "Vit C mg"],
  ["vitaminDMcg", "Vit D mcg"],
  ["vitaminB12Mcg", "Vit B12 mcg"],
  ["vitaminEMg", "Vit E mg"],
  ["vitaminKMcg", "Vit K mcg"],
  ["folateMcg", "Folate mcg"],
  ["omega3G", "Omega-3 g"],
];

const NUTRITION_KEYS = ["calories", "proteinG", "carbsG", "fatG", "fiberG", ...ALL_MICRO_KEYS.filter((k) => k !== "fiberG")];

/** Manual food entry review/edit form. Also the landing spot for AI/barcode
 * prefill — those flows populate the same fields; the user always confirms
 * before save (never auto-committed). Section order mirrors Android FoodResultSheet. */
export class EntryForm extends HTMLElement {
  connectedCallback() {
    const params = new URLSearchParams(location.hash.split("?")[1] ?? "");
    this.date = params.get("date") ?? new Date().toISOString().slice(0, 10);
    this.entryId = location.hash.match(/#\/entry\/([^?]+)/)?.[1];
    this.existing = null;
    this.prefill = null;
    this.nutritionLocked = false;
    /** @type {Record<string, number|null>|null} */
    this.baseNutrition = null;
    this.baseGrams = 100;
    /** @type {import('../lib/chompass-core/serving-units.js').ServingUnitOption[]} */
    this.servingUnitOptions = [];
    this.selectedServingUnit = "g";
    this.quantityText = "100";
    this.servingReady = false;
    const prefillRaw = params.get("prefill");
    if (prefillRaw && (!this.entryId || this.entryId === "new")) {
      try {
        this.prefill = JSON.parse(decodeURIComponent(prefillRaw));
        this.nutritionLocked = Boolean(this.prefill?.source && this.prefill.source !== "manual");
      } catch {
        this.prefill = null;
      }
    }
    this.render();
  }

  /**
   * Snapshot base nutrition + serving state from a source entry/prefill.
   * @param {Record<string, any>} e
   */
  initServingState(e) {
    const ensured = ensureServingUnits({
      name: e.name,
      quantityG: e.quantityG,
      servingUnitOptions: e.servingUnitOptions,
      selectedServingUnit: e.selectedServingUnit,
      selectedServingQuantity: e.selectedServingQuantity,
    });
    this.servingUnitOptions = ensured.servingUnitOptions;
    this.selectedServingUnit = ensured.selectedServingUnit;
    this.baseGrams = ensured.quantityG > 0 ? ensured.quantityG : 100;
    this.quantityText = formatQuantity(
      ensured.selectedServingQuantity != null && ensured.selectedServingQuantity > 0
        ? ensured.selectedServingQuantity
        : this.baseGrams / (optionMatching(this.selectedServingUnit, this.servingUnitOptions).gramsPerUnit || 1)
    );
    /** @type {Record<string, number|null>} */
    const base = {
      calories: Number(e.calories ?? 0),
      proteinG: Number(e.proteinG ?? 0),
      carbsG: Number(e.carbsG ?? 0),
      fatG: Number(e.fatG ?? 0),
      fiberG: e.fiberG == null || e.fiberG === "" ? null : Number(e.fiberG),
    };
    for (const key of ALL_MICRO_KEYS) {
      if (key === "fiberG") continue;
      const v = e[key];
      base[key] = v == null || v === "" ? null : Number(v);
    }
    this.baseNutrition = base;
    this.servingReady = true;
  }

  currentServingGrams() {
    const option = optionMatching(this.selectedServingUnit, this.servingUnitOptions);
    const qty = parseQuantity(this.quantityText);
    if (qty == null || qty <= 0) return this.baseGrams;
    return qty * option.gramsPerUnit;
  }

  currentScale() {
    return this.baseGrams > 0 ? this.currentServingGrams() / this.baseGrams : 1;
  }

  async render() {
    if (this.entryId && this.entryId !== "new" && !this.existing) {
      const all = await foodEntries.byDate(this.date);
      this.existing = all.find((e) => e.id === this.entryId) ?? null;
    }
    const appPrefs = await prefs.load();
    const e = this.existing ?? this.prefill ?? {};
    if (!this.servingReady) this.initServingState(e);

    const defaultMeal = e.mealType || guessMealTypeFromPrefs(appPrefs);
    const isNew = !this.existing;
    const title = this.existing ? "Edit entry" : this.prefill ? "Review food" : "Log food";
    const primaryLabel = this.existing ? "Save" : "Log";
    const fav = this.existing ? await isFavorite(this.existing) : false;
    const scale = this.currentScale();
    const scaled = scaleNutrition(/** @type {Record<string, unknown>} */ (this.baseNutrition ?? {}), scale);
    const hasExtraMicros = MICRO_FIELDS.some(([k]) => scaled[k] != null);
    const lockAttr = this.nutritionLocked ? "readonly" : "";
    const lockClass = this.nutritionLocked ? "is-locked" : "";
    const picker = pickerOptions(this.servingUnitOptions);
    const selectedOption = optionMatching(this.selectedServingUnit, this.servingUnitOptions);
    const qtyNum = parseQuantity(this.quantityText);
    const showTotal = !isGramUnit(selectedOption);
    const servingGrams = this.currentServingGrams();

    const numVal = (v, step = false) => {
      if (v == null || v === "") return "";
      if (step) return formatQuantity(Number(v));
      return String(Math.round(Number(v)));
    };

    this.innerHTML = `
      ${subpageBar(title, { backHref: "#/home" })}
      <form class="entry-form entry-form--review">
        <section class="entry-section">
          <h2 class="entry-section__title">Food details</h2>
          <div class="field">
            <label for="name">Name</label>
            <input id="name" name="name" required value="${e.name ? escapeAttr(e.name) : ""}" />
          </div>
          ${
            e.note && isNew
              ? `<p class="entry-ai-note">${escapeHtml(String(e.note))}</p>`
              : ""
          }
        </section>

        <section class="entry-section entry-section--serving">
          <h2 class="entry-section__title">Serving</h2>
          <div class="serving-quantity-card" data-serving-card>
            <div class="serving-quantity-card__row">
              <span class="serving-quantity-card__label">Quantity</span>
              <div class="serving-quantity-card__controls">
                <input
                  id="servingQuantity"
                  name="servingQuantity"
                  class="serving-quantity-card__qty"
                  type="text"
                  inputmode="decimal"
                  autocomplete="off"
                  value="${escapeAttr(this.quantityText)}"
                  aria-label="Serving quantity"
                />
                <select id="servingUnit" name="servingUnit" class="serving-quantity-card__unit" aria-label="Serving unit">
                  ${picker
                    .map((opt) => {
                      const id = optionId(opt);
                      const label = displayUnit(opt, id === this.selectedServingUnit ? qtyNum : null);
                      return `<option value="${escapeAttr(id)}" ${id === this.selectedServingUnit ? "selected" : ""}>${escapeHtml(label)}</option>`;
                    })
                    .join("")}
                </select>
              </div>
            </div>
            ${
              showTotal
                ? `<div class="serving-quantity-card__total">
                     <span>Total</span>
                     <span data-serving-total>~${formatGramsDisplay(servingGrams)} g</span>
                   </div>`
                : ""
            }
          </div>
          <input type="hidden" name="quantityG" id="quantityG" value="${servingGrams}" />
        </section>

        <section class="entry-section ${lockClass}">
          <div class="entry-section__head">
            <h2 class="entry-section__title">Nutrition</h2>
            ${
              `<button type="button" class="btn btn--ghost btn--sm" data-toggle-lock>${this.nutritionLocked ? "Unlock" : "Lock"}</button>`
            }
          </div>
          <div class="field-row">
            <div class="field">
              <label for="calories">Calories</label>
              <input id="calories" name="calories" type="number" min="0" required value="${numVal(scaled.calories)}" ${lockAttr} data-nutrition />
            </div>
            <div class="field">
              <label for="proteinG">Protein g</label>
              <input id="proteinG" name="proteinG" type="number" min="0" step="0.1" value="${numVal(scaled.proteinG, true)}" ${lockAttr} data-nutrition />
            </div>
          </div>
          <div class="field-row">
            <div class="field">
              <label for="carbsG">Carbs g</label>
              <input id="carbsG" name="carbsG" type="number" min="0" step="0.1" value="${numVal(scaled.carbsG, true)}" ${lockAttr} data-nutrition />
            </div>
            <div class="field">
              <label for="fatG">Fat g</label>
              <input id="fatG" name="fatG" type="number" min="0" step="0.1" value="${numVal(scaled.fatG, true)}" ${lockAttr} data-nutrition />
            </div>
            <div class="field">
              <label for="fiberG">Fiber g</label>
              <input id="fiberG" name="fiberG" type="number" min="0" step="0.1" value="${scaled.fiberG == null ? "" : numVal(scaled.fiberG, true)}" ${lockAttr} data-nutrition />
            </div>
          </div>
        </section>

        <details class="micros-details" ${hasExtraMicros ? "open" : ""}>
          <summary>More nutrition</summary>
          <div class="field-row field-row--micros">
            ${MICRO_FIELDS.map(([key, label]) => {
              const v = scaled[key];
              return `
              <div class="field">
                <label for="${key}">${label}</label>
                <input id="${key}" name="${key}" type="number" min="0" step="0.1" value="${v == null ? "" : numVal(v, true)}" ${lockAttr} data-nutrition />
              </div>`;
            }).join("")}
          </div>
        </details>

        <section class="entry-section">
          <h2 class="entry-section__title">Meal</h2>
          <div class="field-row">
            <div class="field">
              <label for="mealType">Meal type</label>
              <select id="mealType" name="mealType">
                ${["breakfast", "lunch", "dinner", "snack"]
                  .map((m) => `<option value="${m}" ${defaultMeal === m ? "selected" : ""}>${m[0].toUpperCase()}${m.slice(1)}</option>`)
                  .join("")}
              </select>
            </div>
            <div class="field">
              <label for="time">Time</label>
              <input id="time" name="time" type="time" value="${e.time ?? nowHm()}" />
            </div>
          </div>
          <div class="field">
            <label for="note">Note (optional)</label>
            <textarea id="note" name="note" rows="2">${e.note && !isNew ? escapeHtml(String(e.note)) : isNew && e.note ? "" : e.note ?? ""}</textarea>
          </div>
        </section>

        ${this.existing ? `<button type="button" class="btn btn--ghost" data-action="favorite">${fav ? "Unfavorite" : "Favorite"}</button>` : ""}
        ${this.existing ? `<button type="button" class="btn btn--danger" data-action="delete">Delete</button>` : ""}
        <div class="subpage-cta btn-row">
          <button type="submit" class="btn btn--primary">${primaryLabel}</button>
          <button type="button" class="btn btn--ghost" data-action="cancel">Cancel</button>
        </div>
      </form>
    `;

    bindSubpageBack(this, "#/home");
    this.bindServingHandlers();
    this.querySelector("form")?.addEventListener("submit", (ev) => this.onSubmit(ev));
    this.querySelector('[data-action="cancel"]')?.addEventListener("click", () => {
      location.hash = "#/home";
    });
    this.querySelector('[data-action="delete"]')?.addEventListener("click", () => this.onDelete());
    this.querySelector('[data-action="favorite"]')?.addEventListener("click", async () => {
      if (!this.existing) return;
      await toggleFavorite(this.existing);
      this.servingReady = false;
      this.render();
    });
    this.querySelector("[data-toggle-lock]")?.addEventListener("click", () => {
      this.captureFormIntoSource();
      this.nutritionLocked = !this.nutritionLocked;
      this.servingReady = true;
      this.render();
    });
  }

  bindServingHandlers() {
    const qtyInput = /** @type {HTMLInputElement|null} */ (this.querySelector("#servingQuantity"));
    const unitSelect = /** @type {HTMLSelectElement|null} */ (this.querySelector("#servingUnit"));
    const nameInput = /** @type {HTMLInputElement|null} */ (this.querySelector("#name"));

    qtyInput?.addEventListener("input", () => {
      this.quantityText = qtyInput.value;
      this.applyScaleToNutritionFields();
    });

    unitSelect?.addEventListener("change", () => {
      const grams = this.currentServingGrams();
      this.selectedServingUnit = unitSelect.value;
      const option = optionMatching(this.selectedServingUnit, this.servingUnitOptions);
      const qty = option.gramsPerUnit > 0 ? grams / option.gramsPerUnit : grams;
      this.quantityText = formatQuantity(qty);
      if (qtyInput) qtyInput.value = this.quantityText;
      // Re-render so unit labels / total row update
      this.captureFormIntoSource();
      this.render();
    });

    nameInput?.addEventListener("blur", () => {
      const name = nameInput.value.trim();
      if (!name) return;
      const grams = this.currentServingGrams();
      const heur = heuristicOptions(name, grams);
      if (heur.length === 0) return;
      const merged = normalizedOptions([...this.servingUnitOptions, ...heur], grams);
      const before = JSON.stringify(this.servingUnitOptions.map(optionId));
      const after = JSON.stringify(merged.map(optionId));
      if (before === after) return;
      this.servingUnitOptions = merged;
      this.captureFormIntoSource();
      this.render();
    });

    for (const input of this.querySelectorAll("[data-nutrition]")) {
      input.addEventListener("change", () => this.onNutritionEdit(/** @type {HTMLInputElement} */ (input)));
    }
  }

  /** When unlocked, user edits display values → write back into base / scale. */
  onNutritionEdit(input) {
    if (this.nutritionLocked || !this.baseNutrition) return;
    const key = input.name;
    if (!NUTRITION_KEYS.includes(key) && key !== "fiberG") return;
    const scale = Math.max(this.currentScale(), 0.0001);
    const raw = input.value.trim();
    if (raw === "") {
      this.baseNutrition[key] = null;
      return;
    }
    const displayed = Number(raw);
    if (!Number.isFinite(displayed)) return;
    if (key === "calories") {
      this.baseNutrition.calories = Math.round(displayed / scale);
    } else {
      this.baseNutrition[key] = displayed / scale;
    }
  }

  applyScaleToNutritionFields() {
    if (!this.baseNutrition) return;
    const scale = this.currentScale();
    const scaled = scaleNutrition(this.baseNutrition, scale);
    const grams = this.currentServingGrams();
    const hidden = /** @type {HTMLInputElement|null} */ (this.querySelector("#quantityG"));
    if (hidden) hidden.value = String(grams);
    const totalEl = this.querySelector("[data-serving-total]");
    if (totalEl) totalEl.textContent = `~${formatGramsDisplay(grams)} g`;

    const setVal = (name, value, asInt = false) => {
      const el = /** @type {HTMLInputElement|null} */ (this.querySelector(`[name="${name}"]`));
      if (!el) return;
      if (value == null) {
        el.value = "";
        return;
      }
      el.value = asInt ? String(Math.round(Number(value))) : formatQuantity(Number(value));
    };
    setVal("calories", scaled.calories, true);
    setVal("proteinG", scaled.proteinG);
    setVal("carbsG", scaled.carbsG);
    setVal("fatG", scaled.fatG);
    setVal("fiberG", scaled.fiberG);
    for (const [key] of MICRO_FIELDS) {
      setVal(key, scaled[key]);
    }
  }

  /** Preserve in-progress form values into existing/prefill for re-render. */
  captureFormIntoSource() {
    const form = /** @type {HTMLFormElement|null} */ (this.querySelector("form"));
    if (!form) return;
    const fd = new FormData(form);
    /** @type {Record<string, any>} */
    const target = this.existing ?? this.prefill ?? (this.prefill = {});
    target.name = String(fd.get("name") || target.name || "");
    target.mealType = String(fd.get("mealType") || target.mealType || "snack");
    target.time = String(fd.get("time") || target.time || nowHm());
    target.note = fd.get("note") ? String(fd.get("note")) : target.note ?? null;
    target.quantityG = this.currentServingGrams();
    target.servingUnitOptions = this.servingUnitOptions;
    target.selectedServingUnit = this.selectedServingUnit;
    const qty = parseQuantity(this.quantityText);
    target.selectedServingQuantity = qty != null && qty > 0 ? qty : null;
    if (this.baseNutrition) {
      const scaled = scaleNutrition(this.baseNutrition, this.currentScale());
      Object.assign(target, scaled);
    }
    if (!this.existing && !this.prefill) this.prefill = target;
  }

  async onSubmit(ev) {
    ev.preventDefault();
    const fd = new FormData(/** @type {HTMLFormElement} */ (ev.target));
    // Apply any pending unlocked nutrition edits
    for (const input of this.querySelectorAll("[data-nutrition]")) {
      this.onNutritionEdit(/** @type {HTMLInputElement} */ (input));
    }
    const scale = this.currentScale();
    const scaled = scaleNutrition(/** @type {Record<string, unknown>} */ (this.baseNutrition ?? {}), scale);
    const servingGrams = this.currentServingGrams();
    const qty = parseQuantity(this.quantityText);

    /** @type {import('../lib/chompass-core/models.js').FoodEntry} */
    const entry = {
      id: this.existing?.id ?? crypto.randomUUID(),
      name: String(fd.get("name")),
      mealType: /** @type {any} */ (fd.get("mealType")),
      date: this.date,
      time: String(fd.get("time") || nowHm()),
      quantityG: servingGrams > 0 ? Math.round(servingGrams * 10) / 10 : null,
      servingUnitOptions: this.servingUnitOptions,
      selectedServingUnit: this.selectedServingUnit,
      selectedServingQuantity: qty != null && qty > 0 ? qty : null,
      calories: Number(scaled.calories ?? 0),
      proteinG: Number(scaled.proteinG ?? 0),
      carbsG: Number(scaled.carbsG ?? 0),
      fatG: Number(scaled.fatG ?? 0),
      fiberG: scaled.fiberG,
      source: this.existing?.source ?? this.prefill?.source ?? "manual",
      note: fd.get("note") ? String(fd.get("note")) : this.prefill?.note ?? null,
      grounding: this.existing?.grounding ?? null,
      recipeLogId: this.existing?.recipeLogId ?? null,
    };
    for (const key of ALL_MICRO_KEYS) {
      if (key === "fiberG") continue;
      entry[key] = scaled[key] ?? null;
    }
    await foodEntries.put(entry);
    location.hash = "#/home";
  }

  async onDelete() {
    if (!this.existing) return;
    const ok = await openConfirm({
      title: "Delete entry",
      message: `Delete “${this.existing.name}”?`,
      confirmLabel: "Delete",
      danger: true,
    });
    if (!ok) return;
    await foodEntries.delete(this.existing.id);
    location.hash = "#/home";
  }
}

function nowHm() {
  const d = new Date();
  return `${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}`;
}

function escapeAttr(s) {
  return String(s).replace(/&/g, "&amp;").replace(/"/g, "&quot;").replace(/</g, "&lt;");
}

function escapeHtml(s) {
  return String(s).replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]);
}

customElements.define("entry-form", EntryForm);

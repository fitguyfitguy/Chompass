// @ts-check
import { foodEntries, prefs } from "../lib/db.js";
import { subpageBar, bindSubpageBack } from "../lib/ui/subpage.js";
import { openConfirm } from "../lib/ui/dialog.js";
import { guessMealTypeFromPrefs } from "../lib/meal-schedule.js";
import { toggleFavorite, isFavorite } from "../lib/saved-meals.js";

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

/** Manual food entry review/edit form. Also the landing spot for AI/barcode
 * prefill — those flows populate the same fields; the user always confirms
 * before save (never auto-committed). */
export class EntryForm extends HTMLElement {
  connectedCallback() {
    const params = new URLSearchParams(location.hash.split("?")[1] ?? "");
    this.date = params.get("date") ?? new Date().toISOString().slice(0, 10);
    this.entryId = location.hash.match(/#\/entry\/([^?]+)/)?.[1];
    this.existing = null;
    this.prefill = null;
    const prefillRaw = params.get("prefill");
    if (prefillRaw && (!this.entryId || this.entryId === "new")) {
      try {
        this.prefill = JSON.parse(decodeURIComponent(prefillRaw));
      } catch {
        this.prefill = null;
      }
    }
    this.render();
  }

  async render() {
    if (this.entryId && this.entryId !== "new" && !this.existing) {
      const all = await foodEntries.byDate(this.date);
      this.existing = all.find((e) => e.id === this.entryId) ?? null;
    }
    const appPrefs = await prefs.load();
    const e = this.existing ?? this.prefill ?? {};
    const defaultMeal = e.mealType || guessMealTypeFromPrefs(appPrefs);
    const title = this.existing ? "Edit entry" : this.prefill ? "Review & confirm" : "Log food";
    const fav = this.existing ? await isFavorite(this.existing) : false;
    const hasExtraMicros = MICRO_FIELDS.some(([k]) => e[k] != null && e[k] !== "");

    this.innerHTML = `
      ${subpageBar(title, { backHref: "#/home" })}
      <form class="entry-form">
        <div class="field">
          <label for="name">Name</label>
          <input id="name" name="name" required value="${e.name ? escapeAttr(e.name) : ""}" />
        </div>
        <div class="field">
          <label for="mealType">Meal</label>
          <select id="mealType" name="mealType">
            ${["breakfast", "lunch", "dinner", "snack"]
              .map((m) => `<option value="${m}" ${defaultMeal === m ? "selected" : ""}>${m}</option>`)
              .join("")}
          </select>
        </div>
        <div class="field-row">
          <div class="field">
            <label for="calories">Calories</label>
            <input id="calories" name="calories" type="number" min="0" required value="${e.calories ?? ""}" />
          </div>
          <div class="field">
            <label for="quantityG">Grams</label>
            <input id="quantityG" name="quantityG" type="number" min="0" value="${e.quantityG ?? ""}" />
          </div>
          <div class="field">
            <label for="time">Time</label>
            <input id="time" name="time" type="time" value="${e.time ?? nowHm()}" />
          </div>
        </div>
        <div class="field-row">
          <div class="field">
            <label for="proteinG">Protein g</label>
            <input id="proteinG" name="proteinG" type="number" min="0" step="0.1" value="${e.proteinG ?? 0}" />
          </div>
          <div class="field">
            <label for="carbsG">Carbs g</label>
            <input id="carbsG" name="carbsG" type="number" min="0" step="0.1" value="${e.carbsG ?? 0}" />
          </div>
          <div class="field">
            <label for="fatG">Fat g</label>
            <input id="fatG" name="fatG" type="number" min="0" step="0.1" value="${e.fatG ?? 0}" />
          </div>
        </div>
        <div class="field">
          <label for="fiberG">Fiber g (optional)</label>
          <input id="fiberG" name="fiberG" type="number" min="0" step="0.1" value="${e.fiberG ?? ""}" />
        </div>
        <details class="micros-details" ${hasExtraMicros ? "open" : ""}>
          <summary>More nutrients</summary>
          <div class="field-row field-row--micros">
            ${MICRO_FIELDS.map(
              ([key, label]) => `
              <div class="field">
                <label for="${key}">${label}</label>
                <input id="${key}" name="${key}" type="number" min="0" step="0.1" value="${e[key] ?? ""}" />
              </div>`
            ).join("")}
          </div>
        </details>
        <div class="field">
          <label for="note">Note (optional)</label>
          <textarea id="note" name="note" rows="2">${e.note ?? ""}</textarea>
        </div>
        ${this.existing ? `<button type="button" class="btn btn--ghost" data-action="favorite">${fav ? "Unfavorite" : "Favorite"}</button>` : ""}
        ${this.existing ? `<button type="button" class="btn btn--danger" data-action="delete">Delete</button>` : ""}
        <div class="subpage-cta btn-row">
          <button type="submit" class="btn btn--primary">Save</button>
          <button type="button" class="btn btn--ghost" data-action="cancel">Cancel</button>
        </div>
      </form>
    `;

    bindSubpageBack(this, "#/home");
    this.querySelector("form")?.addEventListener("submit", (ev) => this.onSubmit(ev));
    this.querySelector('[data-action="cancel"]')?.addEventListener("click", () => {
      location.hash = "#/home";
    });
    this.querySelector('[data-action="delete"]')?.addEventListener("click", () => this.onDelete());
    this.querySelector('[data-action="favorite"]')?.addEventListener("click", async () => {
      if (!this.existing) return;
      await toggleFavorite(this.existing);
      this.render();
    });
  }

  async onSubmit(ev) {
    ev.preventDefault();
    const fd = new FormData(/** @type {HTMLFormElement} */ (ev.target));
    const optNum = (key) => {
      const raw = fd.get(key);
      return raw !== "" && raw != null ? Number(raw) : null;
    };
    /** @type {import('../lib/nofud-core/models.js').FoodEntry} */
    const entry = {
      id: this.existing?.id ?? crypto.randomUUID(),
      name: String(fd.get("name")),
      mealType: /** @type {any} */ (fd.get("mealType")),
      date: this.date,
      time: String(fd.get("time") || nowHm()),
      quantityG: optNum("quantityG"),
      calories: Number(fd.get("calories")),
      proteinG: Number(fd.get("proteinG") || 0),
      carbsG: Number(fd.get("carbsG") || 0),
      fatG: Number(fd.get("fatG") || 0),
      fiberG: optNum("fiberG"),
      source: this.existing?.source ?? this.prefill?.source ?? "manual",
      note: fd.get("note") ? String(fd.get("note")) : null,
      grounding: this.existing?.grounding ?? null,
      recipeLogId: this.existing?.recipeLogId ?? null,
    };
    for (const [key] of MICRO_FIELDS) {
      entry[key] = optNum(key);
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
  return String(s).replace(/"/g, "&quot;");
}

customElements.define("entry-form", EntryForm);

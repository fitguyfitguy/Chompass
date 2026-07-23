// @ts-check
import { foodEntries } from "../lib/db.js";

/** Manual food entry review/edit form. Also the landing spot for AI/barcode
 * prefill in later phases — those flows populate the same fields, the user
 * always confirms/edits here before save (never auto-committed). */
export class EntryForm extends HTMLElement {
  connectedCallback() {
    const params = new URLSearchParams(location.hash.split("?")[1] ?? "");
    this.date = params.get("date") ?? new Date().toISOString().slice(0, 10);
    this.entryId = location.hash.match(/#\/entry\/([^?]+)/)?.[1];
    this.existing = null;
    this.render();
  }

  async render() {
    if (this.entryId && this.entryId !== "new" && !this.existing) {
      const all = await foodEntries.byDate(this.date);
      this.existing = all.find((e) => e.id === this.entryId) ?? null;
    }
    const e = this.existing ?? {};

    this.innerHTML = `
      <h1 style="font-family:var(--font-display);font-size:1.4rem;margin:0 0 1rem;">
        ${this.existing ? "Edit entry" : "Log food"}
      </h1>
      <form class="entry-form">
        <div class="field">
          <label for="name">Name</label>
          <input id="name" name="name" required value="${e.name ? escapeAttr(e.name) : ""}" />
        </div>
        <div class="field">
          <label for="mealType">Meal</label>
          <select id="mealType" name="mealType">
            ${["breakfast", "lunch", "dinner", "snack"]
              .map((m) => `<option value="${m}" ${e.mealType === m ? "selected" : ""}>${m}</option>`)
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
          <label for="note">Note (optional)</label>
          <textarea id="note" name="note" rows="2">${e.note ?? ""}</textarea>
        </div>
        <div class="btn-row">
          <button type="submit" class="btn btn--primary">Save</button>
          <button type="button" class="btn btn--ghost" data-action="cancel">Cancel</button>
          ${this.existing ? `<button type="button" class="btn btn--danger" data-action="delete">Delete</button>` : ""}
        </div>
      </form>
    `;

    this.querySelector("form").addEventListener("submit", (ev) => this.onSubmit(ev));
    this.querySelector('[data-action="cancel"]').addEventListener("click", () => history.back());
    this.querySelector('[data-action="delete"]')?.addEventListener("click", () => this.onDelete());
  }

  async onSubmit(ev) {
    ev.preventDefault();
    const fd = new FormData(ev.target);
    /** @type {import('../lib/nofud-core/models.js').FoodEntry} */
    const entry = {
      id: this.existing?.id ?? crypto.randomUUID(),
      name: String(fd.get("name")),
      mealType: /** @type {any} */ (fd.get("mealType")),
      date: this.date,
      time: String(fd.get("time") || nowHm()),
      quantityG: fd.get("quantityG") ? Number(fd.get("quantityG")) : null,
      calories: Number(fd.get("calories")),
      proteinG: Number(fd.get("proteinG") || 0),
      carbsG: Number(fd.get("carbsG") || 0),
      fatG: Number(fd.get("fatG") || 0),
      source: this.existing ? "manual" : "manual",
      note: fd.get("note") ? String(fd.get("note")) : null,
      grounding: this.existing?.grounding ?? null,
    };
    await foodEntries.put(entry);
    location.hash = "#/diary";
  }

  async onDelete() {
    if (!this.existing) return;
    await foodEntries.delete(this.existing.id);
    location.hash = "#/diary";
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

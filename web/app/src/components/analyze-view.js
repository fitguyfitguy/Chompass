// @ts-check
import { listConfiguredProviders, loadProviderKey } from "../lib/ai/key-storage.js";
import { fileToJpegBase64 } from "../lib/ai/image.js";
import { analyzeFoodEntry } from "../lib/ai/food-analyze.js";
import { foodEntries } from "../lib/db.js";

export class AnalyzeView extends HTMLElement {
  async connectedCallback() {
    const params = new URLSearchParams(location.hash.split("?")[1] ?? "");
    this.date = params.get("date") ?? new Date().toISOString().slice(0, 10);
    this.providers = await listConfiguredProviders();
    this.activeProvider = this.providers[0] ?? null;
    this.previewUrl = null;
    this.file = null;
    this.busy = false;
    this.error = "";
    this.render();
  }

  disconnectedCallback() {
    if (this.previewUrl) URL.revokeObjectURL(this.previewUrl);
  }

  async render() {
    const recents = await recentFoods(12);

    if (!this.activeProvider) {
      this.innerHTML = `
        <h1 class="screen-title">Photo / text AI</h1>
        <div class="card">
          <p style="color:var(--muted);margin:0 0 0.8rem;">Add a BYOK API key in Settings to analyze food.</p>
          <a class="btn btn--primary" href="#/settings">Go to settings</a>
          <button type="button" class="btn btn--ghost" data-back style="margin-left:0.5rem;">Cancel</button>
        </div>`;
      this.querySelector("[data-back]")?.addEventListener("click", () => {
        location.hash = `#/home`;
      });
      return;
    }

    this.innerHTML = `
      <h1 class="screen-title">Log with AI</h1>
      ${this.previewUrl ? `<img class="analyze-preview" src="${this.previewUrl}" alt="Selected food photo" />` : ""}
      <form class="entry-form card" id="analyze-form">
        <div class="field">
          <label for="photo">Photo (optional)</label>
          <input id="photo" name="photo" type="file" accept="image/*" capture="environment" />
        </div>
        <div class="field">
          <label for="note">Describe the food</label>
          <textarea id="note" name="note" rows="3" placeholder="e.g. bowl of oatmeal with banana and peanut butter"></textarea>
        </div>
        <p id="analyze-status" style="color:var(--muted);font-size:0.85rem;margin:0;">
          ${this.error ? escapeHtml(this.error) : "Estimates are reviewed before saving — nothing is auto-logged."}
        </p>
        <div class="btn-row">
          <button type="submit" class="btn btn--primary" ${this.busy ? "disabled" : ""}>${this.busy ? "Analyzing…" : "Analyze"}</button>
          <button type="button" class="btn btn--ghost" data-back>Cancel</button>
        </div>
      </form>
      ${
        recents.length
          ? `<h2 class="section-label">Recent foods</h2>
             <div class="recents-list">
               ${recents
                 .map(
                   (r) => `
                 <button type="button" data-recent='${escapeAttr(JSON.stringify(r))}'>
                   <strong>${escapeHtml(r.name)}</strong><br/>
                   <span style="color:var(--muted);font-size:0.82rem;">${Math.round(r.calories)} kcal · ${Math.round(r.proteinG)}P / ${Math.round(r.carbsG)}C / ${Math.round(r.fatG)}F</span>
                 </button>`
                 )
                 .join("")}
             </div>`
          : ""
      }
    `;

    this.querySelector("#photo")?.addEventListener("change", (ev) => {
      const input = /** @type {HTMLInputElement} */ (ev.target);
      const file = input.files?.[0];
      if (this.previewUrl) URL.revokeObjectURL(this.previewUrl);
      this.file = file ?? null;
      this.previewUrl = file ? URL.createObjectURL(file) : null;
      this.render();
    });
    this.querySelector("#analyze-form")?.addEventListener("submit", (ev) => this.onAnalyze(ev));
    this.querySelector("[data-back]")?.addEventListener("click", () => {
      location.hash = `#/home`;
    });
    this.querySelectorAll("[data-recent]").forEach((btn) => {
      btn.addEventListener("click", () => {
        const raw = btn.getAttribute("data-recent");
        if (!raw) return;
        const prefill = JSON.parse(raw);
        location.hash = `#/entry/new?date=${this.date}&prefill=${encodeURIComponent(JSON.stringify(prefill))}`;
      });
    });
  }

  async onAnalyze(ev) {
    ev.preventDefault();
    if (this.busy) return;
    const fd = new FormData(/** @type {HTMLFormElement} */ (ev.target));
    const text = String(fd.get("note") || "").trim();
    if (!text && !this.file) {
      this.error = "Add a photo or a short description.";
      this.render();
      return;
    }
    this.busy = true;
    this.error = "";
    this.render();
    try {
      const config = await loadProviderKey(this.activeProvider);
      if (!config) throw new Error("Provider key missing — re-add it in Settings.");
      const image = this.file ? await fileToJpegBase64(this.file) : undefined;
      const estimate = await analyzeFoodEntry({
        providerId: this.activeProvider,
        config,
        text,
        image,
      });
      location.hash = `#/entry/new?date=${this.date}&prefill=${encodeURIComponent(JSON.stringify(estimate))}`;
    } catch (err) {
      this.error = err instanceof Error ? err.message : String(err);
      this.busy = false;
      this.render();
    }
  }
}

/** @param {number} limit */
async function recentFoods(limit) {
  const all = await foodEntries.all();
  /** @type {Map<string, any>} */
  const byName = new Map();
  const sorted = all.slice().sort((a, b) => `${b.date}T${b.time}`.localeCompare(`${a.date}T${a.time}`));
  for (const e of sorted) {
    const key = e.name.trim().toLowerCase();
    if (!key || byName.has(key)) continue;
    byName.set(key, {
      name: e.name,
      calories: e.calories,
      proteinG: e.proteinG,
      carbsG: e.carbsG,
      fatG: e.fatG,
      fiberG: e.fiberG ?? null,
      quantityG: e.quantityG ?? null,
      mealType: e.mealType,
      source: "manual",
      note: null,
    });
    if (byName.size >= limit) break;
  }
  return [...byName.values()];
}

function escapeHtml(s) {
  return String(s).replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]);
}

function escapeAttr(s) {
  return String(s).replace(/'/g, "&#39;").replace(/"/g, "&quot;");
}

customElements.define("analyze-view", AnalyzeView);

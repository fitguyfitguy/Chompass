// @ts-check
import { listConfiguredProviders, loadProviderKey } from "../lib/ai/key-storage.js";
import { fileToJpegBase64 } from "../lib/ai/image.js";
import { analyzeFoodEntry } from "../lib/ai/food-analyze.js";
import { recentFoods } from "../lib/recent-foods.js";
import { prefs } from "../lib/db.js";
import { subpageBar, bindSubpageBack } from "../lib/ui/subpage.js";
import { createSpeechCapture } from "../lib/speech.js";

const MAX_PHOTOS = 10;

export class AnalyzeView extends HTMLElement {
  async connectedCallback() {
    const params = new URLSearchParams(location.hash.split("?")[1] ?? "");
    this.date = params.get("date") ?? new Date().toISOString().slice(0, 10);
    this.mode = params.get("mode") === "note" ? "note" : "photo";
    this.providers = await listConfiguredProviders();
    const appPrefs = await prefs.load();
    /** @type {keyof typeof import('../lib/ai/providers.js').PROVIDERS | null} */
    this.activeProvider = null;
    if (appPrefs.primaryAiProvider && this.providers.includes(/** @type {any} */ (appPrefs.primaryAiProvider))) {
      this.activeProvider = /** @type {any} */ (appPrefs.primaryAiProvider);
    } else {
      this.activeProvider = this.providers[0] ?? null;
    }
    this.previewUrls = /** @type {string[]} */ ([]);
    /** @type {File[]} */
    this.files = [];
    this.busy = false;
    this.error = "";
    this.notePrefill = params.get("prefill") ? decodeURIComponent(params.get("prefill") || "") : "";
    this.render();
  }

  disconnectedCallback() {
    this.previewUrls.forEach((u) => URL.revokeObjectURL(u));
  }

  async render() {
    const recents = await recentFoods(12);
    const title = this.mode === "note" ? "Describe food" : "Photo AI";
    const speech = createSpeechCapture();

    if (!this.activeProvider) {
      this.innerHTML = `
        ${subpageBar(title, { backHref: "#/home" })}
        <div class="card">
          <p style="color:var(--muted);margin:0 0 0.8rem;">Add a BYOK API key in Settings to analyze food.</p>
          <a class="btn btn--primary" href="#/settings?section=ai">Go to settings</a>
        </div>`;
      bindSubpageBack(this, "#/home");
      return;
    }

    this.innerHTML = `
      ${subpageBar(title, { backHref: "#/home" })}
      ${
        this.previewUrls.length
          ? `<div class="analyze-thumbs">${this.previewUrls
              .map((u) => `<img class="analyze-preview" src="${u}" alt="Selected food photo" />`)
              .join("")}</div>`
          : ""
      }
      <form class="entry-form card analyze-mode--${this.mode}" id="analyze-form">
        <div class="field analyze-photo-field">
          <label for="photo">${this.mode === "photo" ? `Photos (up to ${MAX_PHOTOS})` : "Photo (optional)"}</label>
          <input id="photo" name="photo" type="file" accept="image/*" ${this.mode === "photo" ? "multiple" : ""} capture="environment" />
        </div>
        <div class="field analyze-note-field">
          <label for="note">${this.mode === "note" ? "Describe the food" : "Note (optional)"}</label>
          <textarea id="note" name="note" rows="3" placeholder="e.g. bowl of oatmeal with banana and peanut butter">${escapeAttr(this.notePrefill)}</textarea>
          ${
            speech.supported
              ? `<button type="button" class="btn btn--ghost" data-voice style="margin-top:0.4rem;">Voice dictation</button>`
              : ""
          }
        </div>
        <p id="analyze-status" style="color:var(--muted);font-size:0.85rem;margin:0;">
          ${this.error ? escapeHtml(this.error) : "Estimates are reviewed before saving — nothing is auto-logged."}
        </p>
        <div class="subpage-cta btn-row">
          <button type="submit" class="btn btn--primary" ${this.busy ? "disabled" : ""}>${this.busy ? "Analyzing…" : "Analyze"}</button>
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
                   <span class="recents-meta">${Math.round(r.calories)} kcal · ${Math.round(r.proteinG)}P / ${Math.round(r.carbsG)}C / ${Math.round(r.fatG)}F</span>
                 </button>`
                 )
                 .join("")}
             </div>`
          : ""
      }
    `;

    bindSubpageBack(this, "#/home");

    if (this.mode === "note") {
      requestAnimationFrame(() => {
        /** @type {HTMLTextAreaElement | null} */
        const note = this.querySelector("#note");
        note?.focus();
      });
    }

    this.querySelector("#photo")?.addEventListener("change", (ev) => {
      const input = /** @type {HTMLInputElement} */ (ev.target);
      this.previewUrls.forEach((u) => URL.revokeObjectURL(u));
      const list = [...(input.files || [])].slice(0, MAX_PHOTOS);
      this.files = list;
      this.previewUrls = list.map((f) => URL.createObjectURL(f));
      this.render();
    });
    this.querySelector("[data-voice]")?.addEventListener("click", () => {
      const note = /** @type {HTMLTextAreaElement | null} */ (this.querySelector("#note"));
      speech.start((text) => {
        if (note) note.value = note.value ? `${note.value} ${text}` : text;
      });
    });
    this.querySelector("#analyze-form")?.addEventListener("submit", (ev) => this.onAnalyze(ev));
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
    if (!text && !this.files.length) {
      this.error = this.mode === "note" ? "Add a short description." : "Add a photo or a short description.";
      this.render();
      return;
    }
    this.busy = true;
    this.error = "";
    this.render();
    try {
      const config = await loadProviderKey(this.activeProvider);
      if (!config) throw new Error("Provider key missing — re-add it in Settings.");
      const images = [];
      for (const file of this.files) {
        images.push(await fileToJpegBase64(file));
      }
      const estimate = await analyzeFoodEntry({
        providerId: this.activeProvider,
        config,
        text,
        images,
      });
      location.hash = `#/entry/new?date=${this.date}&prefill=${encodeURIComponent(JSON.stringify(estimate))}`;
    } catch (err) {
      this.error = err instanceof Error ? err.message : String(err);
      this.busy = false;
      this.render();
    }
  }
}

function escapeHtml(s) {
  return String(s).replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]);
}

function escapeAttr(s) {
  return String(s).replace(/&/g, "&amp;").replace(/'/g, "&#39;").replace(/"/g, "&quot;").replace(/</g, "&lt;");
}

customElements.define("analyze-view", AnalyzeView);

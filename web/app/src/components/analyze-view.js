// @ts-check
import { listConfiguredProviders, loadProviderKey } from "../lib/ai/key-storage.js";
import { fileToJpegBase64 } from "../lib/ai/image.js";
import {
  ANALYSIS_PHASE,
  ANALYSIS_PHASE_LABEL,
  ANALYSIS_PHASE_STEPS,
  analyzeFoodEntry,
  isAbortError,
} from "../lib/ai/food-analyze.js";
import { recentFoods } from "../lib/recent-foods.js";
import { prefs } from "../lib/db.js";
import { subpageBar, bindSubpageBack } from "../lib/ui/subpage.js";
import { createSpeechCapture } from "../lib/speech.js";

const MAX_PHOTOS = 10;

export class AnalyzeView extends HTMLElement {
  constructor() {
    super();
    this.analysisGeneration = 0;
    /** @type {AbortController | null} */
    this.abortController = null;
    /** @type {string | null} */
    this.phase = null;
    /** @type {string} */
    this.pendingNote = "";
  }

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
    this.phase = null;
    this.pendingNote = params.get("prefill") ? decodeURIComponent(params.get("prefill") || "") : "";
    this.notePrefill = this.pendingNote;
    this.render();
  }

  disconnectedCallback() {
    this.cancelInFlightAnalysis();
    this.previewUrls.forEach((u) => URL.revokeObjectURL(u));
  }

  cancelInFlightAnalysis() {
    this.analysisGeneration += 1;
    this.abortController?.abort();
    this.abortController = null;
    this.busy = false;
    this.phase = null;
  }

  /**
   * @param {string} phase
   * @param {number} generation
   */
  setPhase(phase, generation) {
    if (generation !== this.analysisGeneration) return;
    this.phase = phase;
    this.render();
  }

  async render() {
    const title = this.mode === "note" ? "Describe food" : "Photo AI";

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

    if (this.busy && this.phase) {
      this.innerHTML = `
        ${subpageBar(title, { backHref: "#/home" })}
        ${this.renderOverlay()}`;
      this.querySelector(".subpage-bar")?.classList.add("subpage-bar--above-overlay");
      bindSubpageBack(this, "#/home");
      return;
    }

    const recents = await recentFoods(12);
    const speech = createSpeechCapture();
    const inputsDisabled = this.busy ? "disabled" : "";

    this.innerHTML = `
      ${subpageBar(title, { backHref: "#/home" })}
      ${
        this.previewUrls.length
          ? `<div class="analyze-thumbs">${this.previewUrls
              .map((u) => `<img class="analyze-preview" src="${u}" alt="Selected food photo" />`)
              .join("")}</div>`
          : ""
      }
      <form class="entry-form card analyze-mode--${this.mode}" id="analyze-form" aria-busy="${this.busy ? "true" : "false"}">
        <div class="field analyze-photo-field">
          <label for="photo">${this.mode === "photo" ? `Photos (up to ${MAX_PHOTOS})` : "Photo (optional)"}</label>
          <input id="photo" name="photo" type="file" accept="image/*" ${this.mode === "photo" ? "multiple" : ""} capture="environment" ${inputsDisabled} />
        </div>
        <div class="field analyze-note-field">
          <label for="note">${this.mode === "note" ? "Describe the food" : "Note (optional)"}</label>
          <textarea id="note" name="note" rows="3" placeholder="e.g. bowl of oatmeal with banana and peanut butter" ${inputsDisabled}>${escapeAttr(this.pendingNote || this.notePrefill)}</textarea>
          ${
            speech.supported
              ? `<button type="button" class="btn btn--ghost" data-voice style="margin-top:0.4rem;" ${inputsDisabled}>Voice dictation</button>`
              : ""
          }
        </div>
        <p id="analyze-status" role="status" aria-live="polite" style="color:var(--muted);font-size:0.85rem;margin:0;">
          ${
            this.error
              ? escapeHtml(this.error)
              : "Estimates are reviewed before saving. Nothing is auto-logged."
          }
        </p>
        ${
          this.error
            ? `<div class="analyze-error-actions btn-row">
                 <button type="button" class="btn btn--primary" data-retry>Retry</button>
                 <button type="button" class="btn btn--ghost" data-discard>Discard</button>
               </div>`
            : `<div class="subpage-cta btn-row">
                 <button type="submit" class="btn btn--primary" ${this.busy ? "disabled" : ""}>Analyze</button>
               </div>`
        }
      </form>
      ${
        recents.length && !this.error
          ? `<h2 class="section-label">Recent foods</h2>
             <div class="recents-list">
               ${recents
                 .map(
                   (r) => `
                 <button type="button" data-recent='${escapeAttr(JSON.stringify(r))}' ${inputsDisabled}>
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

    if (this.mode === "note" && !this.error) {
      requestAnimationFrame(() => {
        /** @type {HTMLTextAreaElement | null} */
        const note = this.querySelector("#note");
        note?.focus();
      });
    }

    this.querySelector("#photo")?.addEventListener("change", (ev) => {
      if (this.busy) return;
      const input = /** @type {HTMLInputElement} */ (ev.target);
      this.previewUrls.forEach((u) => URL.revokeObjectURL(u));
      const list = [...(input.files || [])].slice(0, MAX_PHOTOS);
      this.files = list;
      this.previewUrls = list.map((f) => URL.createObjectURL(f));
      this.render();
    });
    this.querySelector("[data-voice]")?.addEventListener("click", () => {
      if (this.busy) return;
      const note = /** @type {HTMLTextAreaElement | null} */ (this.querySelector("#note"));
      speech.start((text) => {
        if (note) note.value = note.value ? `${note.value} ${text}` : text;
      });
    });
    this.querySelector("#analyze-form")?.addEventListener("submit", (ev) => this.onAnalyze(ev));
    this.querySelector("[data-retry]")?.addEventListener("click", () => this.retryAnalysis());
    this.querySelector("[data-discard]")?.addEventListener("click", () => {
      this.error = "";
      this.render();
    });
    this.querySelectorAll("[data-recent]").forEach((btn) => {
      btn.addEventListener("click", () => {
        if (this.busy) return;
        const raw = btn.getAttribute("data-recent");
        if (!raw) return;
        const prefill = JSON.parse(raw);
        location.hash = `#/entry/new?date=${this.date}&prefill=${encodeURIComponent(JSON.stringify(prefill))}`;
      });
    });
  }

  renderOverlay() {
    const phase = this.phase || ANALYSIS_PHASE.PREPARING;
    const currentIndex = ANALYSIS_PHASE_STEPS.indexOf(/** @type {any} */ (phase));
    const stepIndex = currentIndex >= 0 ? currentIndex : 0;
    const previewSrc = this.previewUrls[0];
    const stepsHtml = ANALYSIS_PHASE_STEPS.map((step, index) => {
      const done = index < stepIndex;
      const active = index === stepIndex;
      return `
        <div class="analyze-step ${done ? "analyze-step--done" : ""} ${active ? "analyze-step--active" : ""}" aria-hidden="true">
          ${done ? `<span class="analyze-step__check">✓</span>` : active ? `<span class="analyze-step__spin"></span>` : ""}
        </div>
        ${index < ANALYSIS_PHASE_STEPS.length - 1 ? `<div class="analyze-step__rail ${done ? "analyze-step__rail--done" : ""}"></div>` : ""}
      `;
    }).join("");

    return `
      <div class="analyze-overlay" role="status" aria-live="polite" aria-busy="true">
        ${
          previewSrc
            ? `<img class="analyze-overlay__preview" src="${escapeAttr(previewSrc)}" alt="" />`
            : `<div class="analyze-overlay__icon" aria-hidden="true">⌕</div>`
        }
        <div class="analyze-steps">${stepsHtml}</div>
        <p class="analyze-overlay__phase">${escapeHtml(ANALYSIS_PHASE_LABEL[phase] || ANALYSIS_PHASE_LABEL[ANALYSIS_PHASE.PREPARING])}</p>
        <div class="analyze-overlay__spinner" aria-hidden="true"></div>
      </div>
    `;
  }

  /** @param {Event} [ev] */
  async onAnalyze(ev) {
    ev?.preventDefault();
    if (this.busy) return;

    const noteEl = /** @type {HTMLTextAreaElement | null} */ (this.querySelector("#note"));
    const text = (noteEl?.value ?? this.pendingNote).trim();
    this.pendingNote = text;

    if (!text && !this.files.length) {
      this.error = this.mode === "note" ? "Add a short description." : "Add a photo or a short description.";
      this.render();
      return;
    }

    await this.runAnalysis(text);
  }

  async retryAnalysis() {
    if (this.busy) return;
    const noteEl = /** @type {HTMLTextAreaElement | null} */ (this.querySelector("#note"));
    const text = (noteEl?.value ?? this.pendingNote).trim();
    this.pendingNote = text;
    if (!text && !this.files.length) {
      this.error = this.mode === "note" ? "Add a short description." : "Add a photo or a short description.";
      this.render();
      return;
    }
    await this.runAnalysis(text);
  }

  /** @param {string} text */
  async runAnalysis(text) {
    const generation = ++this.analysisGeneration;
    this.abortController?.abort();
    const ac = new AbortController();
    this.abortController = ac;
    this.busy = true;
    this.error = "";
    this.phase = ANALYSIS_PHASE.PREPARING;
    this.render();

    try {
      const config = await loadProviderKey(/** @type {import('../lib/ai/key-storage.js').ProviderId} */ (this.activeProvider));
      if (generation !== this.analysisGeneration || ac.signal.aborted) return;
      if (!config) throw new Error("Provider key missing. Re-add it in Settings.");

      const images = [];
      for (const file of this.files) {
        if (generation !== this.analysisGeneration || ac.signal.aborted) return;
        images.push(await fileToJpegBase64(file));
      }

      const estimate = await analyzeFoodEntry({
        providerId: /** @type {any} */ (this.activeProvider),
        config,
        text,
        images,
        signal: ac.signal,
        onPhase: (phase) => this.setPhase(phase, generation),
      });

      if (generation !== this.analysisGeneration || ac.signal.aborted) return;
      location.hash = `#/entry/new?date=${this.date}&prefill=${encodeURIComponent(JSON.stringify(estimate))}`;
    } catch (err) {
      if (generation !== this.analysisGeneration) return;
      if (isAbortError(err) || ac.signal.aborted) {
        this.busy = false;
        this.phase = null;
        this.abortController = null;
        return;
      }
      this.error = err instanceof Error ? err.message : String(err);
      this.busy = false;
      this.phase = null;
      this.abortController = null;
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

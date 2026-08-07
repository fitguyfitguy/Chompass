// @ts-check
import {
  listConfiguredProviders,
  loadProviderKey,
} from "../lib/ai/key-storage.js";
import { fileToJpegBase64 } from "../lib/ai/image.js";
import {
  ANALYSIS_PHASE,
  analyzeFoodEntry,
  isAbortError,
} from "../lib/ai/food-analyze.js";
import { collectOffPromptContext } from "../lib/ai/off-prompt-context.js";
import { recentFoods } from "../lib/recent-foods.js";
import { prefs } from "../lib/db.js";
import { subpageBar, bindSubpageBack } from "../lib/ui/subpage.js";
import { createSpeechCapture } from "../lib/speech.js";
import { startPhotoAiFlow } from "../lib/ui/photo-ai-flow.js";
import { shouldUseNativeCaptureHint } from "../lib/media-devices.js";
import { renderAnalyzeOverlayHtml } from "../lib/ui/analyze-overlay.js";
import { DEMO_PLATE_ESTIMATE, runDemoAnalyze } from "../demo/mock-ai.js";

const MAX_PHOTOS = 10;

/** Demo hero mode (web/app/demo.html): scripted AI reply, no provider key. */
const DEMO =
  typeof window !== "undefined" &&
  Boolean(/** @type {any} */ (window).CHOMPASS_DEMO);

export class AnalyzeView extends HTMLElement {
  constructor() {
    super();
    this.analysisGeneration = 0;
    /** @type {AbortController | null} */
    this.abortController = null;
    /** @type {string | null} */
    this.phase = null;
    /** @type {import('../lib/ai/partial-json.js').PartialFoodEstimate|null} */
    this.partial = null;
    /** @type {string} */
    this.pendingNote = "";
  }

  async connectedCallback() {
    const params = new URLSearchParams(location.hash.split("?")[1] ?? "");
    this.date = params.get("date") ?? new Date().toISOString().slice(0, 10);
    this.mode = params.get("mode") === "note" ? "note" : "photo";
    this.providers = await listConfiguredProviders();
    if (DEMO) this.providers = ["gemini"];
    const appPrefs = await prefs.load();
    /** @type {keyof typeof import('../lib/ai/providers.js').PROVIDERS | null} */
    this.activeProvider = null;
    if (
      appPrefs.primaryAiProvider &&
      this.providers.includes(/** @type {any} */ (appPrefs.primaryAiProvider))
    ) {
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
    this.pendingNote = params.get("prefill")
      ? decodeURIComponent(params.get("prefill") || "")
      : "";
    this.notePrefill = this.pendingNote;

    // Photo deep-link: hand off to in-app camera / multi-photo flow (Android
    // parity). In demo mode the hero shows a mock plate camera instead.
    if (this.mode === "photo" && this.activeProvider) {
      if (DEMO) {
        this.demoPlate = true;
        this.render();
        return;
      }
      this.innerHTML = `${subpageBar("Photo AI", { backHref: "#/home" })}<p class="empty-state" style="padding:1.5rem;">Opening camera…</p>`;
      bindSubpageBack(this, "#/home");
      startPhotoAiFlow({
        date: this.date,
        onCancel: () => {
          location.hash = "#/home";
        },
      });
      return;
    }

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

  /**
   * @param {import('../lib/ai/partial-json.js').PartialFoodEstimate} partial
   * @param {number} generation
   */
  setPartial(partial, generation) {
    if (generation !== this.analysisGeneration) return;
    this.partial = partial;
    this.phase = ANALYSIS_PHASE.CALLING_AI;
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

    // Demo-only mock plate camera: stands in for the real capture flow until
    // the driver calls captureMockPhoto(), which runs the scripted analysis.
    if (this.demoPlate && !this.busy && !this.error) {
      this.innerHTML = `
        ${subpageBar(title, { backHref: "#/home" })}
        ${this.mockPlateCamera()}`;
      bindSubpageBack(this, "#/home");
      return;
    }

    if (this.busy && this.phase) {
      this.innerHTML = `
        ${subpageBar(title, { backHref: "#/home" })}
        ${this.renderOverlay()}`;
      this.querySelector(".subpage-bar")?.classList.add(
        "subpage-bar--above-overlay",
      );
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
              .map(
                (u) =>
                  `<img class="analyze-preview" src="${u}" alt="Selected food photo" />`,
              )
              .join("")}</div>`
          : ""
      }
      <form class="entry-form card analyze-mode--${this.mode}" id="analyze-form" aria-busy="${this.busy ? "true" : "false"}">
        <div class="field analyze-photo-field">
          <label for="photo">${this.mode === "photo" ? `Photos (up to ${MAX_PHOTOS})` : "Photo (optional)"}</label>
          <input id="photo" name="photo" type="file" accept="image/*" ${this.mode === "photo" ? "multiple" : ""} ${shouldUseNativeCaptureHint() ? 'capture="environment"' : ""} ${inputsDisabled} />
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
                 </button>`,
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
        // preventScroll keeps focus from scrolling the marketing page when
        // the hero demo opens this view inside its iframe.
        note?.focus({ preventScroll: true });
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
      const note = /** @type {HTMLTextAreaElement | null} */ (
        this.querySelector("#note")
      );
      speech.start((text) => {
        if (note) note.value = note.value ? `${note.value} ${text}` : text;
      });
    });
    this.querySelector("#analyze-form")?.addEventListener("submit", (ev) =>
      this.onAnalyze(ev),
    );
    this.querySelector("[data-retry]")?.addEventListener("click", () =>
      this.retryAnalysis(),
    );
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

  /**
   * Demo-only mock plate camera for the marketing hero (web/app/demo.html):
   * a salmon, rice and broccoli plate on a warm surface inside the real
   * viewfinder, with breathing focus corners, a periodic tap-to-focus ring
   * and a camera HUD. Pure CSS/SVG — no camera, no rAF. The driver captures
   * via captureMockPhoto(), the scripted hand that submits the note form in
   * the note beat.
   */
  mockPlateCamera() {
    return `
      <div class="scanner-frame scanner-frame--plate" data-mock-plate>
        <div class="mock-stage mock-stage--bob">
          <svg class="mock-plate" viewBox="0 0 237 300" aria-hidden="true">
            <defs>
              <radialGradient id="plate-glow" cx="0.5" cy="0.45" r="0.7">
                <stop offset="0" stop-color="#ffffff" stop-opacity="0.07" />
                <stop offset="1" stop-color="#ffffff" stop-opacity="0" />
              </radialGradient>
              <linearGradient id="plate-bg" x1="0" y1="0" x2="0" y2="300" gradientUnits="userSpaceOnUse">
                <stop offset="0" stop-color="#19130e" />
                <stop offset="0.52" stop-color="#19130e" />
                <stop offset="0.6" stop-color="#241b13" />
                <stop offset="0.72" stop-color="#2e2318" />
                <stop offset="1" stop-color="#140f0a" />
              </linearGradient>
              <linearGradient id="plate-rim" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0" stop-color="#ffffff" />
                <stop offset="1" stop-color="#d9d3c2" />
              </linearGradient>
              <radialGradient id="plate-well" cx="0.45" cy="0.4" r="0.75">
                <stop offset="0" stop-color="#f7f2e4" />
                <stop offset="1" stop-color="#e4dcc6" />
              </radialGradient>
              <linearGradient id="plate-salmon" x1="0" y1="0" x2="1" y2="1">
                <stop offset="0" stop-color="#f49a6e" />
                <stop offset="1" stop-color="#d4633c" />
              </linearGradient>
              <filter id="plate-blur" x="-40%" y="-40%" width="180%" height="180%">
                <feGaussianBlur stdDeviation="4" />
              </filter>
              <clipPath id="plate-salmon-clip">
                <path d="M62 160c8-14 30-18 44-10l10 6c8 10 4 24-8 28-14 5-34-2-42-14z" />
              </clipPath>
            </defs>

            <rect width="237" height="300" fill="url(#plate-bg)" />
            <rect width="237" height="300" fill="url(#plate-glow)" />

            <!-- plate shadow on the table -->
            <ellipse cx="118" cy="186" rx="90" ry="42" fill="#000" opacity="0.55" filter="url(#plate-blur)" />
            <ellipse cx="118" cy="178" rx="88" ry="40" fill="#000" opacity="0.18" filter="url(#plate-blur)" />

            <!-- rim -->
            <ellipse cx="118" cy="166" rx="92" ry="45" fill="url(#plate-rim)" />
            <ellipse cx="118" cy="166" rx="92" ry="45" fill="none" stroke="#c9c2ae" stroke-width="1.2" />
            <ellipse cx="118" cy="166" rx="83" ry="40" fill="#f2ecdb" />
            <ellipse cx="118" cy="166" rx="83" ry="40" fill="none" stroke="#dcd5c1" stroke-width="1" />

            <!-- inner well + shadow -->
            <ellipse cx="118" cy="168" rx="67" ry="32" fill="url(#plate-well)" />
            <ellipse cx="118" cy="171" rx="64" ry="29" fill="#000" opacity="0.07" />

            <!-- salmon fillet with grill marks -->
            <path d="M62 160c8-14 30-18 44-10l10 6c8 10 4 24-8 28-14 5-34-2-42-14z" fill="url(#plate-salmon)" />
            <path d="M68 162c6-8 16-10 24-6" stroke="#f9c3a8" stroke-width="2.4" fill="none" stroke-linecap="round" opacity="0.85" />
            <g stroke="#b14e2a" stroke-width="2.2" opacity="0.5" stroke-linecap="round" clip-path="url(#plate-salmon-clip)">
              <path d="M72 184 98 158" />
              <path d="M82 188 108 162" />
              <path d="M94 190 116 170" />
            </g>

            <!-- rice -->
            <ellipse cx="150" cy="168" rx="24" ry="12" fill="#fbf7ea" stroke="#e7dfc8" stroke-width="0.8" />
            <ellipse cx="168" cy="176" rx="17" ry="10" fill="#fbf7ea" stroke="#e7dfc8" stroke-width="0.8" />
            <ellipse cx="152" cy="181" rx="15" ry="8" fill="#fbf7ea" stroke="#e7dfc8" stroke-width="0.8" />
            <g fill="#ffffff" stroke="#e9e2cd" stroke-width="0.5">
              <ellipse cx="144" cy="166" rx="3.4" ry="1.8" transform="rotate(-18 144 166)" />
              <ellipse cx="153" cy="164" rx="3.4" ry="1.8" transform="rotate(10 153 164)" />
              <ellipse cx="161" cy="167" rx="3.4" ry="1.8" transform="rotate(-8 161 167)" />
              <ellipse cx="158" cy="174" rx="3.4" ry="1.8" transform="rotate(20 158 174)" />
              <ellipse cx="170" cy="173" rx="3.2" ry="1.7" transform="rotate(-14 170 173)" />
              <ellipse cx="147" cy="176" rx="3.2" ry="1.7" transform="rotate(6 147 176)" />
              <ellipse cx="165" cy="180" rx="3.2" ry="1.7" transform="rotate(-22 165 180)" />
            </g>

            <!-- broccoli florets -->
            <path d="M88 148c-2-9 8-15 15-11 3-6 12-6 14 1 7-3 12 4 8 10 3 5-1 10-7 10H90z" fill="#4f7d3a" />
            <g fill="#6f9c55">
              <circle cx="92" cy="144" r="1.6" />
              <circle cx="100" cy="141" r="1.6" />
              <circle cx="107" cy="145" r="1.6" />
            </g>
            <path d="M94 162c-2-6 6-9 10-6 2-4 8-4 9 0 5-2 8 3 5 6 2 4-3 7-7 6l-12-3z" fill="#5a8a42" />
            <g fill="#7aa75e">
              <circle cx="98" cy="159" r="1.4" />
              <circle cx="106" cy="158" r="1.4" />
            </g>
            <rect x="96" y="168" width="6" height="10" rx="3" fill="#8fae6a" />
            <rect x="106" y="167" width="5" height="9" rx="2.5" fill="#8fae6a" />

            <!-- lemon wedge -->
            <ellipse cx="169" cy="190" rx="11" ry="5" fill="#000" opacity="0.16" />
            <path d="M162 187l15-10 3 13z" fill="#f9d848" stroke="#e3b92e" stroke-width="1" />
            <g stroke="#e3b92e" stroke-width="0.8" opacity="0.7">
              <path d="M162 187l8-5" />
              <path d="M163 190l9-4" />
            </g>

            <!-- cherry tomato -->
            <circle cx="177" cy="164" r="7" fill="#d94a3d" />
            <path d="M173 159c1-2 3-3 5-2 1-2 4-2 5 0 2-1 3 1 2 3-1 2-3 3-5 3h-6c-2 0-3-2-1-4z" fill="#4f7d3a" />
            <ellipse cx="175" cy="162" rx="2.6" ry="1.4" fill="#ff8d78" opacity="0.75" transform="rotate(-24 175 162)" />

            <!-- sauce dollop + herbs -->
            <ellipse cx="104" cy="193" rx="9" ry="5" fill="#9c5f33" opacity="0.92" />
            <ellipse cx="102" cy="191.5" rx="3.4" ry="1.6" fill="#c98a55" opacity="0.8" />
            <circle cx="126" cy="191" r="1.6" fill="#5f8a52" />
            <circle cx="132" cy="187" r="1.3" fill="#5f8a52" />

            <!-- pepper flecks -->
            <g fill="#3d2f22" opacity="0.45">
              <circle cx="84" cy="170" r="0.9" />
              <circle cx="92" cy="176" r="0.8" />
              <circle cx="140" cy="172" r="0.9" />
              <circle cx="158" cy="170" r="0.8" />
            </g>
          </svg>

          <div class="focus-corners" aria-hidden="true">
            <span class="focus-corners__tl"></span>
            <span class="focus-corners__tr"></span>
            <span class="focus-corners__bl"></span>
            <span class="focus-corners__br"></span>
          </div>
          <div class="focus-ring" aria-hidden="true"></div>
        </div>
        <div class="plate-vignette" aria-hidden="true"></div>
        <div class="cam-hud" aria-hidden="true"><span class="cam-hud__dot"></span>Photo AI</div>
        <p class="scanner-status scanner-status--hud" id="plate-status">Framing your plate…</p>
      </div>
    `;
  }

  /** Demo-only: capture the mock plate and run the scripted analysis. */
  async captureMockPhoto() {
    if (this.busy) return;
    this.demoPlate = false;
    this.pendingNote = "Grilled salmon, rice and broccoli";
    await this.runAnalysis(this.pendingNote);
  }

  renderOverlay() {
    const phase = this.phase || ANALYSIS_PHASE.PREPARING;
    return `
      <div class="analyze-overlay" role="status" aria-live="polite" aria-busy="true">
        ${renderAnalyzeOverlayHtml({
          phase,
          partial: this.partial,
          previewUrls: this.previewUrls,
          showCancel: false,
        })}
      </div>
    `;
  }

  /** @param {Event} [ev] */
  async onAnalyze(ev) {
    ev?.preventDefault();
    if (this.busy) return;

    const noteEl = /** @type {HTMLTextAreaElement | null} */ (
      this.querySelector("#note")
    );
    const text = (noteEl?.value ?? this.pendingNote).trim();
    this.pendingNote = text;

    if (!text && !this.files.length) {
      this.error =
        this.mode === "note"
          ? "Add a short description."
          : "Add a photo or a short description.";
      this.render();
      return;
    }

    await this.runAnalysis(text);
  }

  async retryAnalysis() {
    if (this.busy) return;
    const noteEl = /** @type {HTMLTextAreaElement | null} */ (
      this.querySelector("#note")
    );
    const text = (noteEl?.value ?? this.pendingNote).trim();
    this.pendingNote = text;
    if (!text && !this.files.length) {
      this.error =
        this.mode === "note"
          ? "Add a short description."
          : "Add a photo or a short description.";
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
    this.partial = null;
    this.render();

    try {
      if (DEMO) {
        const estimate = await runDemoAnalyze({
          signal: ac.signal,
          onPhase: (phase) => this.setPhase(phase, generation),
          onPartial: (partial) => this.setPartial(partial, generation),
          estimate: this.mode === "photo" ? DEMO_PLATE_ESTIMATE : undefined,
        });
        if (generation !== this.analysisGeneration || ac.signal.aborted) return;
        location.hash = `#/entry/new?date=${this.date}&prefill=${encodeURIComponent(JSON.stringify(estimate))}`;
        return;
      }
      const config = await loadProviderKey(
        /** @type {import('../lib/ai/key-storage.js').ProviderId} */ (
          this.activeProvider
        ),
      );
      if (generation !== this.analysisGeneration || ac.signal.aborted) return;
      if (!config)
        throw new Error("Provider key missing. Re-add it in Settings.");

      const offPromise = this.files.length
        ? (this.setPhase(ANALYSIS_PHASE.LOOKING_UP_BARCODE, generation),
          collectOffPromptContext(this.files))
        : Promise.resolve("");
      const imagesPromise = (async () => {
        const images = [];
        for (const file of this.files) {
          if (generation !== this.analysisGeneration || ac.signal.aborted)
            return images;
          images.push(await fileToJpegBase64(file));
        }
        return images;
      })();
      const [productContext, images] = await Promise.all([
        offPromise,
        imagesPromise,
      ]);
      if (generation !== this.analysisGeneration || ac.signal.aborted) return;

      const estimate = await analyzeFoodEntry({
        providerId: /** @type {any} */ (this.activeProvider),
        config,
        text,
        productContext: productContext || undefined,
        images,
        signal: ac.signal,
        onPhase: (phase) => this.setPhase(phase, generation),
        onPartial: (partial) => this.setPartial(partial, generation),
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
  return String(s).replace(
    /[&<>"']/g,
    (c) =>
      ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[
        c
      ],
  );
}

function escapeAttr(s) {
  return String(s)
    .replace(/&/g, "&amp;")
    .replace(/'/g, "&#39;")
    .replace(/"/g, "&quot;")
    .replace(/</g, "&lt;");
}

customElements.define("analyze-view", AnalyzeView);

// @ts-check
// Multi-photo review + context note before AI analyze
// (Android MultiPhotoCaptureSheet parity).
import { openSheet } from "./sheet.js";
import { openCameraCapture, pickFromGallery, isLiveCameraSupported } from "./camera-capture.js";
import { shouldUseNativeCaptureHint } from "../media-devices.js";
import { fileToJpegBase64 } from "../ai/image.js";
import { ANALYSIS_PHASE, isAbortError } from "../ai/analysis-phase.js";
import { analyzeFoodEntry } from "../ai/food-analyze.js";
import { collectOffPromptContext } from "../ai/off-prompt-context.js";
import { listConfiguredProviders, loadProviderKey } from "../ai/key-storage.js";
import { prefs } from "../db.js";
import { renderAnalyzeOverlayHtml } from "./analyze-overlay.js";

export const MAX_PHOTOS = 10;

/**
 * @typedef {{
 *   date: string,
 *   initialFiles?: File[],
 *   initialNote?: string,
 *   onComplete?: (estimate: object) => void,
 *   onCancel?: () => void,
 * }} PhotoAiFlowOptions
 */

/**
 * Full photo AI entry: camera → multi-photo review → analyze overlay → callback.
 * @param {PhotoAiFlowOptions} opts
 */
export async function startPhotoAiFlow(opts) {
  const providers = await listConfiguredProviders();
  if (!providers.length) {
    location.hash = "#/settings?section=ai";
    opts.onCancel?.();
    return;
  }

  /** @type {File[]} */
  let files = [...(opts.initialFiles || [])];
  let note = opts.initialNote || "";

  const openReview = () => {
    openMultiPhotoReview({
      files,
      note,
      onChange: (nextFiles, nextNote) => {
        files = nextFiles;
        note = nextNote;
      },
      onAddPhoto: async ({ fromLibrary }) => {
        if (files.length >= MAX_PHOTOS) return;
        if (fromLibrary || !isLiveCameraSupported()) {
          const picked = await pickFromGallery({ multiple: true, capture: false });
          files = [...files, ...picked].slice(0, MAX_PHOTOS);
          openReview();
          return;
        }
        openCameraCapture({
          onCapture: (file) => {
            files = [...files, file].slice(0, MAX_PHOTOS);
            openReview();
          },
          onCancel: () => openReview(),
          onGallery: async () => {
            const picked = await pickFromGallery({ multiple: true, capture: false });
            files = [...files, ...picked].slice(0, MAX_PHOTOS);
            openReview();
          },
        });
      },
      onAnalyze: async (finalFiles, finalNote) => {
        files = finalFiles;
        note = finalNote;
        await runPhotoAnalysis({
          date: opts.date,
          files,
          note,
          providers,
          onComplete: opts.onComplete,
          onCancel: opts.onCancel,
          onRetry: () => openReview(),
        });
      },
      onCancel: () => opts.onCancel?.(),
    });
  };

  if (files.length) {
    openReview();
    return;
  }

  if (!isLiveCameraSupported()) {
    const picked = await pickFromGallery({ multiple: true, capture: shouldUseNativeCaptureHint() });
    if (!picked.length) {
      opts.onCancel?.();
      return;
    }
    files = picked.slice(0, MAX_PHOTOS);
    openReview();
    return;
  }

  openCameraCapture({
    onCapture: (file) => {
      files = [file];
      openReview();
    },
    onCancel: () => opts.onCancel?.(),
    onGallery: async () => {
      const picked = await pickFromGallery({ multiple: true, capture: false });
      if (!picked.length) {
        opts.onCancel?.();
        return;
      }
      files = picked.slice(0, MAX_PHOTOS);
      openReview();
    },
  });
}

/**
 * @param {{
 *   files: File[],
 *   note: string,
 *   onChange: (files: File[], note: string) => void,
 *   onAddPhoto: (args: { fromLibrary: boolean }) => void,
 *   onAnalyze: (files: File[], note: string) => void,
 *   onCancel: () => void,
 * }} opts
 */
export function openMultiPhotoReview(opts) {
  /** @type {File[]} */
  let files = [...opts.files];
  let note = opts.note;
  /** @type {string[]} */
  const urls = files.map((f) => URL.createObjectURL(f));

  const body = document.createElement("div");
  body.className = "multi-photo-review";

  const render = () => {
    body.innerHTML = `
      <div class="multi-photo-review__toolbar">
        <button type="button" class="btn btn--ghost" data-cancel>Cancel</button>
        <span class="multi-photo-review__title">Meal photos</span>
        <button type="button" class="btn btn--primary" data-analyze ${files.length ? "" : "disabled"}>Analyze</button>
      </div>
      <p class="multi-photo-review__count">${files.length} photo${files.length === 1 ? "" : "s"}</p>
      <div class="multi-photo-review__row">
        ${urls
          .map(
            (u, i) => `
          <div class="multi-photo-review__thumb">
            <img src="${u}" alt="Meal photo ${i + 1}" />
            <button type="button" class="multi-photo-review__remove" data-remove="${i}" aria-label="Remove photo">✕</button>
          </div>`
          )
          .join("")}
        ${
          files.length < MAX_PHOTOS
            ? `<button type="button" class="multi-photo-review__add" data-add-camera aria-label="Add photo">+</button>
               <button type="button" class="multi-photo-review__add multi-photo-review__add--gallery" data-add-gallery aria-label="Add from gallery">🖼</button>`
            : ""
        }
      </div>
      <div class="field">
        <label for="multi-photo-note">Tell AI what this is (optional)</label>
        <details class="field-hint-details">
          <summary>Tips for a better estimate</summary>
          <p class="field-hint">
            Photos miss a lot. Add a short note when you can: be specific about
            amounts and brands; call out oils, dressings, and oily sauces; mention
            prep when it matters (fried, grilled, steamed, homemade). Exact grams or
            a nutrition-label photo help most.
          </p>
        </details>
        <p class="field-hint">Be specific — quantities, brands, oils or dressings, and how it was cooked.</p>
        <textarea id="multi-photo-note" rows="3" placeholder="e.g. 2 eggs scrambled in butter, Chipotle bowl with extra oil, grilled chicken no sauce">${escapeAttr(note)}</textarea>
      </div>
    `;

    body.querySelector("[data-cancel]")?.addEventListener("click", () => {
      sheet.close();
      opts.onCancel();
    });
    body.querySelector("[data-analyze]")?.addEventListener("click", () => {
      const noteEl = /** @type {HTMLTextAreaElement | null} */ (body.querySelector("#multi-photo-note"));
      note = noteEl?.value || "";
      opts.onChange(files, note);
      sheet.close();
      opts.onAnalyze(files, note);
    });
    body.querySelectorAll("[data-remove]").forEach((btn) => {
      btn.addEventListener("click", () => {
        const idx = Number(btn.getAttribute("data-remove"));
        URL.revokeObjectURL(urls[idx]);
        files.splice(idx, 1);
        urls.splice(idx, 1);
        opts.onChange(files, note);
        render();
      });
    });
    body.querySelector("[data-add-camera]")?.addEventListener("click", () => {
      const noteEl = /** @type {HTMLTextAreaElement | null} */ (body.querySelector("#multi-photo-note"));
      note = noteEl?.value || "";
      opts.onChange(files, note);
      sheet.close();
      opts.onAddPhoto({ fromLibrary: false });
    });
    body.querySelector("[data-add-gallery]")?.addEventListener("click", () => {
      const noteEl = /** @type {HTMLTextAreaElement | null} */ (body.querySelector("#multi-photo-note"));
      note = noteEl?.value || "";
      opts.onChange(files, note);
      sheet.close();
      opts.onAddPhoto({ fromLibrary: true });
    });
  };

  const sheet = openSheet({
    title: "",
    body,
    className: "sheet--multi-photo",
    onClose: () => {
      urls.forEach((u) => URL.revokeObjectURL(u));
    },
  });
  // Hide default title — toolbar has Cancel | Meal photos | Analyze
  sheet.panel.querySelector(".sheet__title")?.remove();
  render();
  return sheet;
}

/**
 * @param {{
 *   date: string,
 *   files: File[],
 *   note: string,
 *   providers: string[],
 *   onComplete?: (estimate: object) => void,
 *   onCancel?: () => void,
 *   onRetry?: () => void,
 * }} args
 */
async function runPhotoAnalysis(args) {
  const appPrefs = await prefs.load();
  /** @type {string | null} */
  let activeProvider = null;
  if (appPrefs.primaryAiProvider && args.providers.includes(appPrefs.primaryAiProvider)) {
    activeProvider = appPrefs.primaryAiProvider;
  } else {
    activeProvider = args.providers[0] ?? null;
  }
  if (!activeProvider) {
    location.hash = "#/settings?section=ai";
    args.onCancel?.();
    return;
  }

  const overlay = document.createElement("div");
  overlay.className = "analyze-overlay analyze-overlay--flow";
  overlay.setAttribute("role", "status");
  overlay.setAttribute("aria-live", "polite");
  document.body.appendChild(overlay);

  /** @type {string[]} */
  const previewUrls = args.files.map((f) => URL.createObjectURL(f));
  const ac = new AbortController();
  let generation = 1;
  /** @type {import('../ai/partial-json.js').PartialFoodEstimate|null} */
  let partial = null;

  const setPhase = (phase) => {
    overlay.innerHTML = renderAnalyzeOverlayHtml({
      phase,
      partial,
      previewUrls,
      showCancel: true,
    });
    overlay.querySelector("[data-cancel-analyze]")?.addEventListener("click", () => {
      generation += 1;
      ac.abort();
      cleanup();
      args.onCancel?.();
    });
  };

  const cleanup = () => {
    previewUrls.forEach((u) => URL.revokeObjectURL(u));
    overlay.remove();
  };

  setPhase(ANALYSIS_PHASE.PREPARING);

  try {
    const config = await loadProviderKey(/** @type {any} */ (activeProvider));
    if (!config) throw new Error("Provider key missing. Re-add it in Settings.");

    // Barcode/OFF scan in parallel with JPEG encode — both use original files.
    setPhase(ANALYSIS_PHASE.LOOKING_UP_BARCODE);
    const offPromise = collectOffPromptContext(args.files);
    /** @type {Promise<{mimeType: string, base64: string}[]>} */
    const imagesPromise = (async () => {
      /** @type {{mimeType: string, base64: string}[]} */
      const images = [];
      for (const file of args.files) {
        if (ac.signal.aborted) return images;
        images.push(await fileToJpegBase64(file));
      }
      return images;
    })();
    const [productContext, images] = await Promise.all([offPromise, imagesPromise]);
    if (ac.signal.aborted) return;

    const estimate = await analyzeFoodEntry({
      providerId: /** @type {any} */ (activeProvider),
      config,
      text: args.note.trim() || undefined,
      productContext: productContext || undefined,
      images,
      signal: ac.signal,
      onPhase: (phase) => {
        if (generation !== 1) return;
        setPhase(phase);
      },
      onPartial: (next) => {
        if (generation !== 1) return;
        partial = next;
        setPhase(ANALYSIS_PHASE.CALLING_AI);
      },
    });

    if (ac.signal.aborted || generation !== 1) return;
    cleanup();
    if (args.onComplete) {
      args.onComplete(estimate);
    } else {
      location.hash = `#/entry/new?date=${encodeURIComponent(args.date)}&prefill=${encodeURIComponent(JSON.stringify(estimate))}`;
    }
  } catch (err) {
    if (isAbortError(err) || ac.signal.aborted) {
      cleanup();
      return;
    }
    const message = err instanceof Error ? err.message : String(err);
    overlay.innerHTML = `
      <div class="analyze-overlay__icon" aria-hidden="true">!</div>
      <p class="analyze-overlay__phase">${escapeHtml(message)}</p>
      <div class="btn-row analyze-error-actions">
        <button type="button" class="btn btn--primary" data-retry>Retry</button>
        <button type="button" class="btn btn--ghost" data-discard>Discard</button>
      </div>
    `;
    overlay.querySelector("[data-retry]")?.addEventListener("click", () => {
      cleanup();
      args.onRetry?.();
    });
    overlay.querySelector("[data-discard]")?.addEventListener("click", () => {
      cleanup();
      args.onCancel?.();
    });
  }
}

function escapeAttr(s) {
  return String(s).replace(/&/g, "&amp;").replace(/"/g, "&quot;").replace(/</g, "&lt;");
}

function escapeHtml(s) {
  return String(s).replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]);
}

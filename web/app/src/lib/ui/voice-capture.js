// @ts-check
// Dedicated voice capture UI (Android VoiceInputSheet parity).
import { openSheet } from "./sheet.js";
import { createSpeechCapture } from "../speech.js";
import { prefs } from "../db.js";

/**
 * @param {{
 *   onResult: (text: string) => void,
 *   onCancel?: () => void,
 *   title?: string,
 * }} opts
 * @returns {Promise<{ close: () => void } | null>} null if speech unsupported
 */
export async function openVoiceCaptureSheet(opts) {
  const speech = createSpeechCapture();
  if (!speech.supported) return null;

  const appPrefs = await prefs.load();
  const lang = appPrefs.speechLang || navigator.language || "en-US";

  let used = false;
  let transcript = "";
  let listening = false;
  /** @type {(() => void) | null} */
  let stop = null;

  const body = document.createElement("div");
  body.className = "voice-capture";

  const render = () => {
    body.innerHTML = `
      <div class="voice-capture__status" aria-live="polite">
        ${listening ? "Listening…" : transcript ? "Got it — tap Use to continue" : "Tap the mic to start"}
      </div>
      <p class="voice-capture__transcript">${escapeHtml(transcript) || "<span class=\"voice-capture__placeholder\">Your words will appear here</span>"}</p>
      <div class="voice-capture__actions btn-row">
        <button type="button" class="btn btn--ghost" data-cancel>Cancel</button>
        <button type="button" class="voice-capture__mic ${listening ? "is-listening" : ""}" data-mic aria-label="${listening ? "Stop" : "Start listening"}">
          <svg viewBox="0 0 24 24" width="28" height="28" aria-hidden="true"><path fill="currentColor" d="M12 14c1.66 0 3-1.34 3-3V5c0-1.66-1.34-3-3-3S9 3.34 9 5v6c0 1.66 1.34 3 3 3zm5-3c0 2.76-2.24 5-5 5s-5-2.24-5-5H5c0 3.53 2.61 6.43 6 6.92V21h2v-3.08c3.39-.49 6-3.39 6-6.92h-2z"/></svg>
        </button>
        <button type="button" class="btn btn--primary" data-use ${transcript.trim() ? "" : "disabled"}>Use</button>
      </div>
      <p class="voice-capture__hint">Speak a food description, then Use to analyze.</p>
    `;

    body.querySelector("[data-cancel]")?.addEventListener("click", () => {
      stop?.();
      sheet.close();
    });
    body.querySelector("[data-mic]")?.addEventListener("click", () => {
      if (listening) {
        stop?.();
        listening = false;
        render();
        return;
      }
      listening = true;
      render();
      stop = speech.start(
        (text) => {
          transcript = transcript ? `${transcript} ${text}` : text;
          listening = false;
          stop = null;
          render();
        },
        (err) => {
          listening = false;
          stop = null;
          render();
          const status = body.querySelector(".voice-capture__status");
          if (status) status.textContent = `Voice error: ${err}`;
        },
        { lang }
      );
    });
    body.querySelector("[data-use]")?.addEventListener("click", () => {
      const text = transcript.trim();
      if (!text) return;
      used = true;
      stop?.();
      sheet.close();
      opts.onResult(text);
    });
  };

  const sheet = openSheet({
    title: opts.title || "Voice",
    body,
    className: "sheet--voice",
    onClose: () => {
      stop?.();
      if (!used) opts.onCancel?.();
    },
  });
  render();
  requestAnimationFrame(() => {
    body.querySelector("[data-mic]")?.dispatchEvent(new Event("click"));
  });
  return sheet;
}

function escapeHtml(s) {
  return String(s).replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]);
}

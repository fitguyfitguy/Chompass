// @ts-check
import { lookupBarcode } from "../lib/off-client.js";
import { createDetector } from "../lib/barcode-detect.js";
import { subpageBar, bindSubpageBack } from "../lib/ui/subpage.js";
import {
  buildMediaStreamConstraints,
  cameraErrorMessage,
  isLiveCameraSupported,
  listVideoInputDevices,
  loadPreferredVideoDeviceId,
  nextVideoDeviceId,
  prefersMobileCameraUx,
  savePreferredVideoDeviceId,
} from "../lib/media-devices.js";

/** Demo hero mode (web/app/demo.html): no camera, canned product lookup. */
const DEMO = typeof window !== "undefined" && Boolean(/** @type {any} */ (window).CHOMPASS_DEMO);

/** Canned Open Food Facts-style result for the demo barcode beat. */
const DEMO_PRODUCT = Object.freeze({
  name: "Oat drink, vanilla",
  brand: "Oatly",
  calories: 130,
  proteinG: 3,
  carbsG: 14,
  fatG: 6,
  quantityG: 250,
  servingUnitOptions: [{ unit: "serving", gramsPerUnit: 250, quantity: 1 }],
  selectedServingUnit: "serving",
  selectedServingQuantity: 1,
});

/**
 * Live scanning uses the native BarcodeDetector API when it works, and falls
 * back to the vendored zxing-wasm reader (lazy-loaded) when it is missing
 * (Firefox, Safari) or present but broken (Brave/Android Shields, degoogled
 * devices where Chromium's detector needs Google Play Services). Manual digit
 * entry remains the last resort. See src/lib/barcode-detect.js.
 *
 * Phone cameras prefer the rear lens; desktop webcams use landscape constraints
 * and an optional switch-camera control when multiple devices exist.
 */
export class BarcodeScanner extends HTMLElement {
  connectedCallback() {
    const params = new URLSearchParams(location.hash.split("?")[1] ?? "");
    this.date = params.get("date") ?? new Date().toISOString().slice(0, 10);
    this.supported = isLiveCameraSupported();
    this.mobileUx = prefersMobileCameraUx();
    this.stopped = false;
    this.busy = false;
    /** @type {string | null} */
    this.activeDeviceId = null;
    /** @type {MediaDeviceInfo[]} */
    this.videoDevices = [];
    if (DEMO) this.supported = false;
    this.render();
    if (this.supported) this.startCamera();
  }

  disconnectedCallback() {
    this.stopCamera();
  }

  render() {
    const desktopClass = this.mobileUx ? "" : " scanner-frame--desktop";
    this.innerHTML = `
      ${subpageBar("Scan barcode", { backHref: "#/home" })}
      ${
        DEMO
          ? this.demoMockFeed()
          : this.supported
            ? `
        <div class="scanner-frame${desktopClass}">
          <video id="scanner-video" autoplay playsinline muted></video>
          <div class="scanner-frame__reticle" aria-hidden="true"></div>
          <button type="button" class="scanner-switch" data-switch hidden aria-label="Switch camera">Switch camera</button>
        </div>
        <p id="scanner-status" class="scanner-status">Point the camera at a barcode.</p>
      `
            : `
        <p style="color:var(--muted);font-size:0.9rem;">
          Live barcode scanning isn't supported in this browser. Enter the number printed under the barcode instead.
          Camera access needs HTTPS (or localhost) and a browser that supports getUserMedia.
        </p>
      `
      }
      <form class="entry-form" id="manual-barcode-form">
        <div class="field">
          <label for="barcode">Barcode number</label>
          <input id="barcode" name="barcode" inputmode="numeric" pattern="[0-9]*" placeholder="e.g. 0049000028911" />
        </div>
        <div class="subpage-cta btn-row">
          <button type="submit" class="btn btn--primary">Look up</button>
        </div>
      </form>
      <p id="lookup-status" style="color:var(--muted);font-size:0.85rem;margin-top:0.5rem;"></p>
    `;

    bindSubpageBack(this, "#/home");
    this.querySelector("#manual-barcode-form")?.addEventListener("submit", (ev) => this.onManualLookup(ev));
    this.querySelector("[data-switch]")?.addEventListener("click", () => this.switchCamera());
  }

  /**
   * Demo-only mock camera feed for the marketing hero (web/app/demo.html):
   * a simple cereal box with an EAN-13 barcode on the front, drawn inside the
   * real viewfinder. Pure CSS/SVG — no getUserMedia, no rAF. No laser line:
   * like real scanner apps, corner brackets breathe over the barcode and snap
   * green with a check when the scan "locks", on a repeating cycle. Detection
   * is triggered by the demo driver calling lookupAndPrefill() after the
   * lock, the same scripted hand that types into the manual form elsewhere.
   */
  demoMockFeed() {
    // Barcode bars: all bars are the same height (real EAN-13); the guard
    // bars differ by width only. Digits = module width in drawing units.
    const pattern = "G1G1G2131122131231121G1G1G2131132112312231G1G2G";
    let x = 0;
    const rects = [];
    for (const ch of pattern) {
      const w = ch === "G" ? 2.2 : ch === "3" ? 2.7 : ch === "2" ? 2.2 : 1.7;
      rects.push(`<rect x="${x.toFixed(1)}" y="0" width="${w.toFixed(1)}" height="44" />`);
      x += w + 1.2;
    }
    const barsSvgW = Math.ceil(x + 1);
    // Fit the generated bars into the 100-unit-wide barcode card.
    const scale = (100 / barsSvgW).toFixed(4);
    return `
      <div class="scanner-frame scanner-frame--mock" data-mock-barcode>
        <div class="mock-stage mock-stage--bob">
          <svg class="mock-package" viewBox="0 0 237 300" aria-hidden="true">
            <defs>
              <radialGradient id="mock-stage-glow" cx="0.5" cy="0.45" r="0.7">
                <stop offset="0" stop-color="#ffffff" stop-opacity="0.08" />
                <stop offset="1" stop-color="#ffffff" stop-opacity="0" />
              </radialGradient>
              <linearGradient id="mock-box-front" x1="0" y1="0" x2="1" y2="0">
                <stop offset="0" stop-color="#f8cd63" />
                <stop offset="1" stop-color="#e3a83d" />
              </linearGradient>
              <linearGradient id="mock-box-side" x1="0" y1="0" x2="1" y2="0">
                <stop offset="0" stop-color="#c98e2f" />
                <stop offset="1" stop-color="#b57c26" />
              </linearGradient>
              <linearGradient id="mock-box-top" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0" stop-color="#fbe3a8" />
                <stop offset="1" stop-color="#eec56b" />
              </linearGradient>
              <radialGradient id="mock-box-shadow" cx="0.5" cy="0.5" r="0.5">
                <stop offset="0" stop-color="#000" stop-opacity="0.55" />
                <stop offset="0.75" stop-color="#000" stop-opacity="0.25" />
                <stop offset="1" stop-color="#000" stop-opacity="0" />
              </radialGradient>
            </defs>

            <rect width="237" height="300" fill="url(#mock-stage-glow)" />
            <ellipse cx="132" cy="252" rx="84" ry="14" fill="url(#mock-box-shadow)" />

            <!-- side + top faces (view from above-right: recede up-right);
                 overlap 1px under the front face to hide vertex seams -->
            <path d="M177 68 204 60 204 238 177 246z" fill="url(#mock-box-side)" />
            <path d="M57 68 177 68 204 60 83 60z" fill="url(#mock-box-top)" />
            <path d="M84 60 204 60" stroke="#000" stroke-width="1" opacity="0.08" />

            <!-- front face -->
            <rect x="58" y="68" width="120" height="178" rx="2" fill="url(#mock-box-front)" />
            <path d="M58 246h120" stroke="#b57c26" stroke-width="1" opacity="0.6" />

            <!-- front panel: brand + sun/bowl graphic -->
            <rect x="68" y="80" width="100" height="104" rx="5" fill="#fdf6e4" />
            <text x="118" y="97" text-anchor="middle" font-size="13" font-weight="700" font-family="system-ui, sans-serif" letter-spacing="3" fill="#8a5a1e">GRAIN</text>
            <text x="118" y="108" text-anchor="middle" font-size="6.5" font-family="system-ui, sans-serif" letter-spacing="1.5" fill="#b07f3a">OATS &amp; HONEY</text>
            <circle cx="118" cy="136" r="22" fill="#f7a83e" />
            <circle cx="112" cy="130" r="7" fill="#fbc96e" opacity="0.8" />
            <path d="M97 146a21 21 0 0 1 42 0z" fill="#fff" stroke="#e8c98a" stroke-width="1.2" />
            <g fill="#d9a441">
              <circle cx="112" cy="141" r="2.2" />
              <circle cx="121" cy="139" r="2.2" />
              <circle cx="118" cy="145" r="2.2" />
              <circle cx="127" cy="143" r="2.2" />
            </g>
            <text x="118" y="179" text-anchor="middle" font-size="6" font-family="system-ui, sans-serif" letter-spacing="1.2" fill="#b07f3a">NET WT 500 g</text>

            <!-- gloss streak -->
            <path d="M62 158 116 72l8 2-50 90z" fill="#fff" opacity="0.06" />

            <!-- barcode card -->
            <rect x="68" y="190" width="100" height="50" rx="4" fill="#fff" stroke="#e5dfd0" stroke-width="1" />
            <g transform="translate(68 192) scale(${scale})">
              ${rects.join("")}
            </g>
            <text x="118" y="236" text-anchor="middle" font-size="8" font-family="ui-monospace, SFMono-Regular, Menlo, monospace" letter-spacing="1.2" fill="#3a3a42">0 049000 028911 1</text>

            <!-- shelf line -->
            <path d="M70 250 168 250" stroke="#fff" stroke-width="1" opacity="0.08" />
          </svg>

          <div class="scanner-brackets" aria-hidden="true">
            <span class="scanner-brackets__tl"></span>
            <span class="scanner-brackets__tr"></span>
            <span class="scanner-brackets__bl"></span>
            <span class="scanner-brackets__br"></span>
            <span class="scanner-brackets__hit"></span>
            <span class="scanner-brackets__check">✓</span>
          </div>
        </div>
        <div class="cam-hud" aria-hidden="true"><span class="cam-hud__dot"></span>Scan barcode</div>
        <p class="scanner-status scanner-status--hud" id="scanner-status">Point the camera at a barcode.</p>
      </div>
    `;
  }

  /**
   * @param {{ deviceId?: string | null }} [opts]
   * @returns {Promise<boolean>}
   */
  async openStream(opts = {}) {
    const status = this.querySelector("#scanner-status");
    const preferred = opts.deviceId ?? loadPreferredVideoDeviceId();
    try {
      this.stream?.getTracks().forEach((t) => t.stop());
      try {
        this.stream = await navigator.mediaDevices.getUserMedia(
          buildMediaStreamConstraints({
            purpose: "barcode",
            mobileUx: this.mobileUx,
            deviceId: preferred,
          }),
        );
      } catch (firstErr) {
        if (preferred) {
          this.stream = await navigator.mediaDevices.getUserMedia(
            buildMediaStreamConstraints({
              purpose: "barcode",
              mobileUx: this.mobileUx,
              deviceId: null,
            }),
          );
        } else {
          throw firstErr;
        }
      }
      const video = /** @type {HTMLVideoElement} */ (this.querySelector("#scanner-video"));
      if (!video) return false;
      video.srcObject = this.stream;
      await video.play().catch(() => {});
      const track = this.stream.getVideoTracks()[0];
      this.activeDeviceId = track?.getSettings?.().deviceId || preferred || null;
      if (this.activeDeviceId) savePreferredVideoDeviceId(this.activeDeviceId);
      this.videoDevices = await listVideoInputDevices();
      const switchBtn = /** @type {HTMLButtonElement | null} */ (this.querySelector("[data-switch]"));
      if (switchBtn) switchBtn.hidden = this.videoDevices.length < 2;
      return true;
    } catch (err) {
      if (status) status.textContent = `${cameraErrorMessage(err)} Enter the number manually below.`;
      return false;
    }
  }

  async startCamera() {
    const status = this.querySelector("#scanner-status");
    const ok = await this.openStream({ deviceId: loadPreferredVideoDeviceId() });
    if (!ok || this.stopped) return;
    this.detector = await createDetector((msg) => {
      if (status) status.textContent = msg;
    });
    if (this.stopped) return;
    if (!this.detector) {
      this.stopCamera();
      this.supported = false;
      this.render();
      return;
    }
    if (status) status.textContent = "Point the camera at a barcode.";
    this.scanLoop();
  }

  async switchCamera() {
    if (this.busy || this.stopped) return;
    const nextId = nextVideoDeviceId(this.videoDevices, this.activeDeviceId);
    if (!nextId || nextId === this.activeDeviceId) return;
    const status = this.querySelector("#scanner-status");
    const ok = await this.openStream({ deviceId: nextId });
    if (ok && status) status.textContent = "Point the camera at a barcode.";
  }

  stopCamera() {
    this.stopped = true;
    this.stream?.getTracks().forEach((t) => t.stop());
  }

  async scanLoop() {
    if (this.stopped || this.busy) return;
    const video = /** @type {HTMLVideoElement} */ (this.querySelector("#scanner-video"));
    if (video && video.readyState >= 2) {
      try {
        const barcode = await this.detector.detect(video);
        if (barcode) {
          this.busy = true;
          await this.onDetected(barcode);
          return;
        }
      } catch {
        // detector gave up entirely (wasm demotion failed) — manual entry only
        this.stopCamera();
        this.supported = false;
        this.render();
        return;
      }
    }
    if (this.stopped) return;
    if (this.detector.kind === "wasm") {
      // Full-frame wasm decoding every rAF frame is wasteful — ~10 fps is plenty.
      setTimeout(() => requestAnimationFrame(() => this.scanLoop()), 100);
    } else {
      requestAnimationFrame(() => this.scanLoop());
    }
  }

  async onDetected(barcode) {
    this.stopCamera();
    await this.lookupAndPrefill(barcode);
  }

  async onManualLookup(ev) {
    ev.preventDefault();
    const barcode = new FormData(ev.target).get("barcode");
    if (barcode) await this.lookupAndPrefill(String(barcode));
  }

  async lookupAndPrefill(barcode) {
    const status = this.querySelector("#lookup-status");
    if (status) status.textContent = "Looking up…";
    try {
      const prefill = DEMO ? { ...DEMO_PRODUCT } : await lookupBarcode(barcode);
      if (!prefill) {
        if (status)
          status.textContent = `No product found for ${barcode}. Try manual entry, or edit portions after saving.`;
        return;
      }
      const q = encodeURIComponent(JSON.stringify({ ...prefill, mealType: "snack", source: "barcode" }));
      location.hash = `#/entry/new?date=${encodeURIComponent(this.date)}&prefill=${q}`;
    } catch (err) {
      if (status) status.textContent = `Lookup failed: ${err.message}`;
    }
  }
}

customElements.define("barcode-scanner", BarcodeScanner);

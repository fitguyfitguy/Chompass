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
        this.supported
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
          })
        );
      } catch (firstErr) {
        if (preferred) {
          this.stream = await navigator.mediaDevices.getUserMedia(
            buildMediaStreamConstraints({
              purpose: "barcode",
              mobileUx: this.mobileUx,
              deviceId: null,
            })
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
    status.textContent = "Looking up…";
    try {
      const prefill = DEMO ? { ...DEMO_PRODUCT } : await lookupBarcode(barcode);
      if (!prefill) {
        status.textContent = `No product found for ${barcode}. Try manual entry, or edit portions after saving.`;
        return;
      }
      const q = encodeURIComponent(JSON.stringify({ ...prefill, mealType: "snack", source: "barcode" }));
      location.hash = `#/entry/new?date=${encodeURIComponent(this.date)}&prefill=${q}`;
    } catch (err) {
      status.textContent = `Lookup failed: ${err.message}`;
    }
  }
}

customElements.define("barcode-scanner", BarcodeScanner);

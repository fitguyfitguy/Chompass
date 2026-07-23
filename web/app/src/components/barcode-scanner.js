// @ts-check
import { lookupBarcode } from "../lib/off-client.js";
import { createDetector } from "../lib/barcode-detect.js";
import { subpageBar, bindSubpageBack } from "../lib/ui/subpage.js";

/**
 * Live scanning uses the native BarcodeDetector API when it works, and falls
 * back to the vendored zxing-wasm reader (lazy-loaded) when it is missing
 * (Firefox, Safari) or present but broken (Brave/Android Shields, degoogled
 * devices where Chromium's detector needs Google Play Services). Manual digit
 * entry remains the last resort. See src/lib/barcode-detect.js.
 */
export class BarcodeScanner extends HTMLElement {
  connectedCallback() {
    const params = new URLSearchParams(location.hash.split("?")[1] ?? "");
    this.date = params.get("date") ?? new Date().toISOString().slice(0, 10);
    this.supported = !!navigator.mediaDevices?.getUserMedia;
    this.stopped = false;
    this.busy = false;
    this.render();
    if (this.supported) this.startCamera();
  }

  disconnectedCallback() {
    this.stopCamera();
  }

  render() {
    this.innerHTML = `
      ${subpageBar("Scan barcode", { backHref: "#/home" })}
      ${
        this.supported
          ? `
        <div class="scanner-frame">
          <video id="scanner-video" autoplay playsinline muted></video>
          <div class="scanner-frame__reticle" aria-hidden="true"></div>
        </div>
        <p id="scanner-status" class="scanner-status">Point the camera at a barcode.</p>
      `
          : `
        <p style="color:var(--muted);font-size:0.9rem;">
          Live barcode scanning isn't supported in this browser. Enter the number printed under the barcode instead.
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
  }

  async startCamera() {
    const status = this.querySelector("#scanner-status");
    try {
      this.stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: "environment" } });
      const video = /** @type {HTMLVideoElement} */ (this.querySelector("#scanner-video"));
      video.srcObject = this.stream;
    } catch (err) {
      if (status) status.textContent = `Camera unavailable (${err.message}). Enter the number manually below.`;
      return;
    }
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
      // Full-frame wasm decoding every rAF frame is wasteful on phones — ~10 fps
      // is plenty for handheld scanning.
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
      const prefill = await lookupBarcode(barcode);
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

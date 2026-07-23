// @ts-check
import { lookupBarcode } from "../lib/off-client.js";

/**
 * Live scanning uses the BarcodeDetector API (Chrome/Edge/Android WebView).
 * Browsers without it (Firefox, Safari) fall back to manual digit entry
 * rather than vendoring a JS decoder like zxing-js — pulling in a ~200KB
 * decoder library would cut against the "no bundler, no runtime deps"
 * architecture for one degraded-browser path. See web/README.md.
 */
export class BarcodeScanner extends HTMLElement {
  connectedCallback() {
    const params = new URLSearchParams(location.hash.split("?")[1] ?? "");
    this.date = params.get("date") ?? new Date().toISOString().slice(0, 10);
    this.supported = "BarcodeDetector" in window;
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
      <h1 style="font-family:var(--font-display);font-size:1.3rem;margin:0 0 1rem;">Scan barcode</h1>
      ${
        this.supported
          ? `
        <div class="scanner-frame">
          <video id="scanner-video" autoplay playsinline muted></video>
        </div>
        <p id="scanner-status" style="color:var(--muted);font-size:0.85rem;margin-top:0.6rem;">Point the camera at a barcode.</p>
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
        <div class="btn-row">
          <button type="submit" class="btn btn--primary">Look up</button>
          <button type="button" class="btn btn--ghost" data-action="cancel">Cancel</button>
        </div>
      </form>
      <p id="lookup-status" style="color:var(--muted);font-size:0.85rem;margin-top:0.5rem;"></p>
    `;

    this.querySelector("#manual-barcode-form").addEventListener("submit", (ev) => this.onManualLookup(ev));
    this.querySelector('[data-action="cancel"]').addEventListener("click", () => history.back());
  }

  async startCamera() {
    try {
      this.stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: "environment" } });
      const video = /** @type {HTMLVideoElement} */ (this.querySelector("#scanner-video"));
      video.srcObject = this.stream;
      // @ts-ignore BarcodeDetector isn't in the standard TS DOM lib yet
      this.detector = new window.BarcodeDetector({ formats: ["ean_13", "ean_8", "upc_a", "upc_e", "code_128"] });
      this.scanLoop();
    } catch (err) {
      const status = this.querySelector("#scanner-status");
      if (status) status.textContent = `Camera unavailable (${err.message}). Enter the number manually below.`;
    }
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
        const codes = await this.detector.detect(video);
        if (codes.length > 0) {
          this.busy = true;
          await this.onDetected(codes[0].rawValue);
          return;
        }
      } catch {
        // transient mid-frame detection errors are expected — keep scanning
      }
    }
    if (!this.stopped) requestAnimationFrame(() => this.scanLoop());
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

// @ts-check
/**
 * Barcode detection with a tiered strategy:
 *   1. Native BarcodeDetector when it actually works. On Android, Chromium
 *      delegates detection to Google Play Services — Brave (Shields) and
 *      degoogled devices expose the API but detect() throws forever or always
 *      returns empty. A one-time canvas probe (render a known EAN-13, try to
 *      detect it) catches both failure modes. Live video that keeps returning
 *      empty (without throwing) is demoted to wasm after a short grace period.
 *   2. Vendored zxing-wasm reader (vendor/zxing/, lazy-loaded via <script>
 *      tag) when native is missing or probed broken.
 *   3. null — caller falls back to manual digit entry.
 */

export const FORMATS = ["ean_13", "ean_8", "upc_a", "upc_e", "code_128"];

/** Native format name → zxing-wasm BarcodeFormat name. */
export const ZXING_FORMAT_MAP = {
  ean_13: "EAN13",
  ean_8: "EAN8",
  upc_a: "UPCA",
  upc_e: "UPCE",
  code_128: "Code128",
};

// Bump when the probe logic changes to invalidate cached verdicts.
const PROBE_CACHE_KEY = "nofud.barcodeDetectorProbe.v2";
const PROBE_TIMEOUT_MS = 2000;
const PROBE_VALUE = "4006381333931";
// Consecutive live detect() throws before demoting a probe-passing native
// detector (canvas probe can pass while video-frame detection is broken).
export const NATIVE_FAILURE_LIMIT = 30;
// Sustained empty native results on ready video frames (Brave often never
// throws — detect() just returns []). Grace period so users can aim first.
export const NATIVE_EMPTY_DEMOTE_MS = 3500;

// EAN-13 module encoding (ISO/IEC 15420). L-codes for odd-parity left digits;
// G-codes for even-parity left digits; right digits use the complement of L.
const L_CODES = ["0001101", "0011001", "0010011", "0111101", "0100011", "0110001", "0101111", "0111011", "0110111", "0001011"];
const G_CODES = ["0100111", "0110011", "0011011", "0100001", "0011101", "0111001", "0000101", "0010001", "0001001", "0010111"];
// First digit selects the left-half parity pattern (L = odd, G = even).
const PARITY = ["LLLLLL", "LLGLGG", "LLGGLG", "LLGGGL", "LGLLGG", "LGGLLG", "LGGGLL", "LGLGLG", "LGLGGL", "LGGLGL"];

/**
 * Encode an EAN-13 as its 95-module bit string ("1" = dark bar).
 * @param {string} digits 13-digit string
 * @returns {string}
 */
export function ean13Modules(digits) {
  if (!/^\d{13}$/.test(digits)) throw new Error(`not an EAN-13: ${digits}`);
  const parity = PARITY[Number(digits[0])];
  let out = "101";
  for (let i = 0; i < 6; i++) {
    const d = Number(digits[i + 1]);
    out += parity[i] === "L" ? L_CODES[d] : G_CODES[d];
  }
  out += "01010";
  for (let i = 7; i < 13; i++) {
    const d = Number(digits[i]);
    out += L_CODES[d].replace(/[01]/g, (c) => (c === "0" ? "1" : "0"));
  }
  return out + "101";
}

/**
 * Render the probe EAN-13 onto a canvas (quiet zones + black bars on white).
 * @param {HTMLCanvasElement} canvas
 */
export function drawTestBarcode(canvas) {
  const modules = ean13Modules(PROBE_VALUE);
  const scale = 3;
  const quiet = 12 * scale;
  canvas.width = modules.length * scale + quiet * 2;
  canvas.height = 120;
  const ctx = /** @type {CanvasRenderingContext2D} */ (canvas.getContext("2d"));
  ctx.fillStyle = "#fff";
  ctx.fillRect(0, 0, canvas.width, canvas.height);
  ctx.fillStyle = "#000";
  for (let i = 0; i < modules.length; i++) {
    if (modules[i] === "1") ctx.fillRect(quiet + i * scale, 10, scale, canvas.height - 20);
  }
}

/**
 * Pure strategy selection — see createDetector for the real wiring.
 * @param {{ hasNative: boolean, probeResult: "ok" | "broken" }} args
 * @returns {"native" | "wasm"}
 */
export function chooseStrategy({ hasNative, probeResult }) {
  return hasNative && probeResult === "ok" ? "native" : "wasm";
}

/**
 * Whether a live native detector should fall back to wasm.
 * @param {{
 *   emptyMs: number,
 *   throwCount: number,
 *   emptyLimitMs?: number,
 *   throwLimit?: number,
 * }} args
 * @returns {boolean}
 */
export function shouldDemoteNative({
  emptyMs,
  throwCount,
  emptyLimitMs = NATIVE_EMPTY_DEMOTE_MS,
  throwLimit = NATIVE_FAILURE_LIMIT,
}) {
  return throwCount >= throwLimit || emptyMs >= emptyLimitMs;
}

/** Probe whether the native detector can read a known barcode off a canvas. */
async function probeNativeDetector() {
  try {
    const canvas = document.createElement("canvas");
    drawTestBarcode(canvas);
    // @ts-ignore BarcodeDetector isn't in the standard TS DOM lib yet
    const detector = new window.BarcodeDetector({ formats: ["ean_13"] });
    const codes = await Promise.race([
      detector.detect(canvas),
      new Promise((_, reject) => setTimeout(() => reject(new Error("probe timeout")), PROBE_TIMEOUT_MS)),
    ]);
    return codes.length > 0 && codes[0].rawValue === PROBE_VALUE ? "ok" : "broken";
  } catch {
    return "broken";
  }
}

function cachedProbeResult() {
  try {
    const v = localStorage.getItem(PROBE_CACHE_KEY);
    return v === "ok" || v === "broken" ? v : null;
  } catch {
    return null;
  }
}

function cacheProbeResult(result) {
  try {
    localStorage.setItem(PROBE_CACHE_KEY, result);
  } catch {
    // private mode etc. — probe again next time
  }
}

/** Lazy-load the vendored zxing-wasm IIFE build and return a wasm detector. */
async function createWasmDetector() {
  const vendorDir = new URL("../../vendor/zxing/", import.meta.url);
  // @ts-ignore ZXingWASM global is installed by the vendored script
  if (!window.ZXingWASM) {
    await new Promise((resolve, reject) => {
      const script = document.createElement("script");
      script.src = new URL("zxing-reader.js", vendorDir).href;
      script.onload = () => resolve(undefined);
      script.onerror = () => reject(new Error("failed to load zxing-reader.js"));
      document.head.append(script);
    });
  }
  // @ts-ignore
  const zxing = window.ZXingWASM;
  zxing.prepareZXingModule({
    overrides: { locateFile: (/** @type {string} */ path) => new URL(path, vendorDir).href },
  });
  const formats = FORMATS.map((f) => ZXING_FORMAT_MAP[f]);
  // Instantiate the wasm module now so failures surface here, not mid-scan.
  const warmup = new ImageData(2, 2);
  await zxing.readBarcodes(warmup, { formats });

  const canvas = document.createElement("canvas");
  const ctx = /** @type {CanvasRenderingContext2D} */ (canvas.getContext("2d", { willReadFrequently: true }));
  return {
    kind: /** @type {const} */ ("wasm"),
    /** @param {HTMLVideoElement} video @returns {Promise<string | null>} */
    async detect(video) {
      if (!video.videoWidth || !video.videoHeight) return null;
      canvas.width = video.videoWidth;
      canvas.height = video.videoHeight;
      ctx.drawImage(video, 0, 0);
      const image = ctx.getImageData(0, 0, canvas.width, canvas.height);
      const results = await zxing.readBarcodes(image, { formats });
      return results.length > 0 ? results[0].text : null;
    },
  };
}

/**
 * Build the best available detector.
 * @param {(msg: string) => void} [onStatus]
 * @returns {Promise<{ kind: "native" | "wasm", detect(video: HTMLVideoElement): Promise<string | null> } | null>}
 */
export async function createDetector(onStatus = () => {}) {
  const hasNative = "BarcodeDetector" in window;
  /** @type {"ok" | "broken"} */ let probeResult = "broken";
  if (hasNative) {
    const cached = cachedProbeResult();
    probeResult = cached ?? (await probeNativeDetector());
    if (!cached) cacheProbeResult(probeResult);
  }

  const loadWasm = async () => {
    onStatus("Loading scanner engine…");
    try {
      return await createWasmDetector();
    } catch {
      return null;
    }
  };

  if (chooseStrategy({ hasNative, probeResult }) === "wasm") return loadWasm();

  // @ts-ignore BarcodeDetector isn't in the standard TS DOM lib yet
  const native = new window.BarcodeDetector({ formats: FORMATS });
  let failures = 0;
  /** @type {number | null} */
  let emptySince = null;
  /** @type {Awaited<ReturnType<typeof createWasmDetector>> | null} */
  let demoted = null;

  /** @returns {Promise<string | null>} */
  const demoteToWasm = async (/** @type {HTMLVideoElement} */ video) => {
    cacheProbeResult("broken");
    demoted = await loadWasm();
    if (!demoted) throw new Error("barcode detection unavailable");
    out.kind = "wasm";
    return demoted.detect(video);
  };

  const out = {
    kind: /** @type {"native" | "wasm"} */ ("native"),
    /** @param {HTMLVideoElement} video @returns {Promise<string | null>} */
    async detect(video) {
      if (demoted) return demoted.detect(video);
      try {
        const codes = await native.detect(video);
        failures = 0;
        if (codes.length > 0) {
          emptySince = null;
          return codes[0].rawValue;
        }
        // Empty (no throw): Brave/Shields often stays here forever. Demote after
        // a grace period of ready video frames so aiming time isn't punished.
        if (video.videoWidth > 0 && video.videoHeight > 0) {
          const now = performance.now();
          if (emptySince === null) emptySince = now;
          if (shouldDemoteNative({ emptyMs: now - emptySince, throwCount: failures })) {
            return demoteToWasm(video);
          }
        }
        return null;
      } catch {
        // Transient mid-frame errors are normal; a long unbroken streak means
        // video-frame detection is broken even though the canvas probe passed.
        emptySince = null;
        if (shouldDemoteNative({ emptyMs: 0, throwCount: ++failures })) {
          return demoteToWasm(video);
        }
        return null;
      }
    },
  };
  return out;
}

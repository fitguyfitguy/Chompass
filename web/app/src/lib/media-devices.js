// @ts-check
// Shared camera / webcam helpers for meal capture + barcode scanning.
// Prefer capability detection over UA sniffing so desktop webcams and
// tablets both get sensible constraints.

const PREFERRED_DEVICE_KEY = "chompass.preferredVideoDeviceId.v1";

/**
 * Secure context + MediaDevices.getUserMedia (required for live preview).
 * @returns {boolean}
 */
export function isLiveCameraSupported() {
  return Boolean(window.isSecureContext && navigator.mediaDevices?.getUserMedia);
}

/**
 * Coarse pointer / no-hover / narrow viewport → phone-style rear-camera UX.
 * Fine pointer + hover (typical laptop/desktop) → webcam-oriented UX.
 * @param {{ matchMedia?: (q: string) => MediaQueryList }} [env]
 * @returns {boolean}
 */
export function prefersMobileCameraUx(env = globalThis) {
  const mm = typeof env.matchMedia === "function" ? env.matchMedia.bind(env) : null;
  if (!mm) return true;
  try {
    if (mm("(pointer: fine)").matches && mm("(hover: hover)").matches) return false;
    if (mm("(pointer: coarse)").matches) return true;
    if (mm("(max-width: 720px)").matches) return true;
  } catch {
    // matchMedia can throw in odd test/jsdom shims
  }
  return false;
}

/**
 * Whether `<input capture="environment">` is useful (opens rear camera on phones).
 * On desktop it is ignored or confusing — prefer a normal file picker.
 * @param {{ matchMedia?: (q: string) => MediaQueryList }} [env]
 * @returns {boolean}
 */
export function shouldUseNativeCaptureHint(env = globalThis) {
  return prefersMobileCameraUx(env);
}

/**
 * @typedef {{
 *   deviceId?: string | null,
 *   facingMode?: "environment" | "user" | null,
 *   purpose?: "meal" | "barcode",
 *   mobileUx?: boolean,
 * }} VideoConstraintOptions
 */

/**
 * Build getUserMedia video constraints for phone cameras vs desktop webcams.
 * @param {VideoConstraintOptions} [opts]
 * @returns {MediaTrackConstraints}
 */
export function buildVideoConstraints(opts = {}) {
  const mobile = opts.mobileUx ?? prefersMobileCameraUx();
  const purpose = opts.purpose ?? "meal";
  const deviceId = opts.deviceId || null;

  /** @type {MediaTrackConstraints} */
  const video = {};

  if (deviceId) {
    video.deviceId = { exact: deviceId };
  } else if (opts.facingMode) {
    video.facingMode = { ideal: opts.facingMode };
  } else if (mobile) {
    video.facingMode = { ideal: "environment" };
  }

  if (mobile && purpose === "meal" && !deviceId) {
    video.width = { ideal: 1280 };
    video.height = { ideal: 1720 };
  } else {
    // Desktop webcams and barcode preview: landscape-friendly.
    video.width = { ideal: 1280 };
    video.height = { ideal: 720 };
  }

  return video;
}

/**
 * @param {VideoConstraintOptions} [opts]
 * @returns {MediaStreamConstraints}
 */
export function buildMediaStreamConstraints(opts = {}) {
  return { video: buildVideoConstraints(opts), audio: false };
}

/**
 * Map DOMException / browser errors to short user-facing copy.
 * @param {unknown} err
 * @returns {string}
 */
export function cameraErrorMessage(err) {
  const name = err && typeof err === "object" && "name" in err ? String(/** @type {{ name?: string }} */ (err).name) : "";
  const raw = err instanceof Error ? err.message : typeof err === "string" ? err : "";

  switch (name) {
    case "NotAllowedError":
    case "PermissionDeniedError":
      return "Camera permission denied. Allow camera access in the browser site settings, then try again.";
    case "NotFoundError":
    case "DevicesNotFoundError":
      return "No camera found. Connect a webcam or use a device with a camera.";
    case "NotReadableError":
    case "TrackStartError":
      return "Camera is in use by another app. Close it and try again.";
    case "OverconstrainedError":
    case "ConstraintNotSatisfiedError":
      return "Could not match the requested camera. Try switching cameras or use a photo from files.";
    case "SecurityError":
      return "Camera blocked on this page. Use HTTPS (or localhost) and allow camera access.";
    case "AbortError":
      return "Camera start was interrupted. Try again.";
    default:
      break;
  }

  if (/permission|denied|not allowed/i.test(raw)) {
    return "Camera permission denied. Allow camera access in the browser site settings, then try again.";
  }
  if (/not found|no device|requested device not found/i.test(raw)) {
    return "No camera found. Connect a webcam or use a device with a camera.";
  }
  return raw || "Camera unavailable";
}

/**
 * @returns {string | null}
 */
export function loadPreferredVideoDeviceId() {
  try {
    return localStorage.getItem(PREFERRED_DEVICE_KEY);
  } catch {
    return null;
  }
}

/**
 * @param {string | null | undefined} deviceId
 */
export function savePreferredVideoDeviceId(deviceId) {
  try {
    if (!deviceId) localStorage.removeItem(PREFERRED_DEVICE_KEY);
    else localStorage.setItem(PREFERRED_DEVICE_KEY, deviceId);
  } catch {
    // private mode / blocked storage
  }
}

/**
 * List video inputs. Labels are often empty until after a getUserMedia grant.
 * @returns {Promise<MediaDeviceInfo[]>}
 */
export async function listVideoInputDevices() {
  if (!navigator.mediaDevices?.enumerateDevices) return [];
  try {
    const all = await navigator.mediaDevices.enumerateDevices();
    return all.filter((d) => d.kind === "videoinput");
  } catch {
    return [];
  }
}

/**
 * Next device in the list after `currentId` (wraps). Prefer a different id.
 * @param {MediaDeviceInfo[]} devices
 * @param {string | null | undefined} currentId
 * @returns {string | null}
 */
export function nextVideoDeviceId(devices, currentId) {
  if (!devices.length) return null;
  if (devices.length === 1) return devices[0].deviceId || null;
  const idx = devices.findIndex((d) => d.deviceId === currentId);
  const next = devices[(idx >= 0 ? idx + 1 : 0) % devices.length];
  return next?.deviceId || null;
}

/**
 * Crop rectangle for a capture frame.
 * `cropRatio` = width/height (e.g. 3/4). Null/undefined = full frame (desktop webcam).
 * @param {number} vw
 * @param {number} vh
 * @param {number | null | undefined} cropRatio
 * @returns {{ sx: number, sy: number, sw: number, sh: number }}
 */
export function frameCropRect(vw, vh, cropRatio) {
  const w = Math.max(1, Math.round(vw));
  const h = Math.max(1, Math.round(vh));
  if (!cropRatio || !Number.isFinite(cropRatio) || cropRatio <= 0) {
    return { sx: 0, sy: 0, sw: w, sh: h };
  }
  let sw = w;
  let sh = h;
  let sx = 0;
  let sy = 0;
  const videoRatio = w / h;
  if (videoRatio > cropRatio) {
    sw = Math.round(h * cropRatio);
    sx = Math.round((w - sw) / 2);
  } else if (videoRatio < cropRatio) {
    sh = Math.round(w / cropRatio);
    sy = Math.round((h - sh) / 2);
  }
  return { sx, sy, sw, sh };
}

/**
 * Meal photo crop: phone preview stays 3:4; desktop keeps the full webcam frame.
 * @param {{ mobileUx?: boolean }} [opts]
 * @returns {number | null}
 */
export function mealCaptureCropRatio(opts = {}) {
  const mobile = opts.mobileUx ?? prefersMobileCameraUx();
  return mobile ? 3 / 4 : null;
}

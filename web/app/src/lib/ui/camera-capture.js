// @ts-check
// In-app meal camera (Android InAppCameraCaptureDialog parity).
// Falls back to a hidden file input when getUserMedia is unavailable.

const MAX_EDGE = 1600;

/**
 * @returns {boolean}
 */
export function isLiveCameraSupported() {
  return Boolean(window.isSecureContext && navigator.mediaDevices?.getUserMedia);
}

/**
 * @typedef {{
 *   onCapture: (file: File) => void,
 *   onCancel?: () => void,
 *   onGallery?: () => void,
 *   allowGallery?: boolean,
 * }} CameraCaptureOptions
 */

/**
 * Open a full-screen camera overlay. Returns a controller with close().
 * @param {CameraCaptureOptions} opts
 */
export function openCameraCapture(opts) {
  if (!isLiveCameraSupported()) {
    pickFromGallery({ multiple: false, capture: true })
      .then((files) => {
        if (files[0]) opts.onCapture(files[0]);
        else opts.onCancel?.();
      })
      .catch(() => opts.onCancel?.());
    return { close() {} };
  }

  const host = document.createElement("div");
  host.className = "camera-capture";
  host.setAttribute("role", "dialog");
  host.setAttribute("aria-modal", "true");
  host.setAttribute("aria-label", "Take meal photo");

  host.innerHTML = `
    <div class="camera-capture__stage">
      <video class="camera-capture__video" autoplay playsinline muted></video>
      <div class="camera-capture__frame" aria-hidden="true"></div>
      <p class="camera-capture__error" hidden></p>
    </div>
    <button type="button" class="camera-capture__btn camera-capture__btn--close" data-close aria-label="Close">✕</button>
    <button type="button" class="camera-capture__btn camera-capture__btn--flash" data-flash hidden aria-label="Flash">Flash</button>
    <div class="camera-capture__controls">
      ${
        opts.allowGallery !== false
          ? `<button type="button" class="camera-capture__btn camera-capture__btn--gallery" data-gallery aria-label="Gallery">🖼</button>`
          : `<span></span>`
      }
      <button type="button" class="camera-capture__shutter" data-shutter aria-label="Shutter"></button>
      <span></span>
    </div>
  `;

  document.body.appendChild(host);
  document.body.classList.add("camera-open");

  /** @type {MediaStream | null} */
  let stream = null;
  /** @type {MediaStreamTrack | null} */
  let videoTrack = null;
  let torchOn = false;
  let capturing = false;
  let closed = false;

  const video = /** @type {HTMLVideoElement} */ (host.querySelector(".camera-capture__video"));
  const errorEl = /** @type {HTMLElement} */ (host.querySelector(".camera-capture__error"));
  const flashBtn = /** @type {HTMLButtonElement} */ (host.querySelector("[data-flash]"));
  const shutterBtn = /** @type {HTMLButtonElement} */ (host.querySelector("[data-shutter]"));

  const showError = (msg) => {
    errorEl.hidden = false;
    errorEl.textContent = msg;
  };

  const close = () => {
    if (closed) return;
    closed = true;
    stream?.getTracks().forEach((t) => t.stop());
    stream = null;
    host.remove();
    document.body.classList.remove("camera-open");
    document.removeEventListener("keydown", onKey);
  };

  /** @param {KeyboardEvent} ev */
  const onKey = (ev) => {
    if (ev.key === "Escape" && !capturing) {
      ev.preventDefault();
      close();
      opts.onCancel?.();
    }
  };
  document.addEventListener("keydown", onKey);

  host.querySelector("[data-close]")?.addEventListener("click", () => {
    if (capturing) return;
    close();
    opts.onCancel?.();
  });

  host.querySelector("[data-gallery]")?.addEventListener("click", async () => {
    if (capturing) return;
    if (opts.onGallery) {
      close();
      opts.onGallery();
      return;
    }
    try {
      const files = await pickFromGallery({ multiple: false, capture: false });
      if (files[0]) {
        close();
        opts.onCapture(files[0]);
      }
    } catch {
      // user cancelled gallery
    }
  });

  flashBtn.addEventListener("click", async () => {
    if (!videoTrack) return;
    try {
      torchOn = !torchOn;
      // @ts-ignore torch constraint
      await videoTrack.applyConstraints({ advanced: [{ torch: torchOn }] });
      flashBtn.classList.toggle("is-on", torchOn);
      flashBtn.textContent = torchOn ? "Flash on" : "Flash";
    } catch {
      flashBtn.hidden = true;
    }
  });

  shutterBtn.addEventListener("click", async () => {
    if (capturing || !video.srcObject) return;
    capturing = true;
    shutterBtn.classList.add("is-busy");
    shutterBtn.disabled = true;
    try {
      const file = await captureVideoFrame(video);
      close();
      opts.onCapture(file);
    } catch (err) {
      capturing = false;
      shutterBtn.classList.remove("is-busy");
      shutterBtn.disabled = false;
      showError(err instanceof Error ? err.message : "Capture failed");
    }
  });

  (async () => {
    try {
      stream = await navigator.mediaDevices.getUserMedia({
        video: {
          facingMode: { ideal: "environment" },
          width: { ideal: 1280 },
          height: { ideal: 1720 },
        },
        audio: false,
      });
      videoTrack = stream.getVideoTracks()[0] || null;
      video.srcObject = stream;
      await video.play().catch(() => {});
      const caps = videoTrack?.getCapabilities?.();
      // @ts-ignore torch capability
      if (caps && "torch" in caps && caps.torch) {
        flashBtn.hidden = false;
      }
    } catch (err) {
      showError(err instanceof Error ? err.message : "Camera unavailable");
      // Fall back to file picker after a short beat so the error is visible.
      setTimeout(async () => {
        if (closed) return;
        close();
        try {
          const files = await pickFromGallery({ multiple: false, capture: true });
          if (files[0]) opts.onCapture(files[0]);
          else opts.onCancel?.();
        } catch {
          opts.onCancel?.();
        }
      }, 600);
    }
  })();

  return { close };
}

/**
 * @param {{ multiple?: boolean, capture?: boolean }} [opts]
 * @returns {Promise<File[]>}
 */
export function pickFromGallery(opts = {}) {
  return new Promise((resolve, reject) => {
    const input = document.createElement("input");
    input.type = "file";
    input.accept = "image/*";
    if (opts.multiple) input.multiple = true;
    if (opts.capture) input.setAttribute("capture", "environment");
    input.style.position = "fixed";
    input.style.left = "-9999px";
    document.body.appendChild(input);
    let settled = false;
    const finish = (files) => {
      if (settled) return;
      settled = true;
      input.remove();
      window.removeEventListener("focus", onFocus);
      resolve(files);
    };
    input.addEventListener("change", () => {
      finish([...(input.files || [])]);
    });
    // If the user cancels, change may not fire — best-effort empty resolve on focus return.
    const onFocus = () => {
      setTimeout(() => {
        if (!settled && !input.files?.length) finish([]);
      }, 400);
    };
    window.addEventListener("focus", onFocus);
    input.click();
    // Safety: reject never — empty array means cancel
    void reject;
  });
}

/**
 * @param {HTMLVideoElement} video
 * @returns {Promise<File>}
 */
async function captureVideoFrame(video) {
  const vw = video.videoWidth || 720;
  const vh = video.videoHeight || 960;
  // Crop to 3:4 portrait centered in the frame (Android preview aspect).
  const targetRatio = 3 / 4;
  let sw = vw;
  let sh = vh;
  let sx = 0;
  let sy = 0;
  const videoRatio = vw / vh;
  if (videoRatio > targetRatio) {
    sw = Math.round(vh * targetRatio);
    sx = Math.round((vw - sw) / 2);
  } else if (videoRatio < targetRatio) {
    sh = Math.round(vw / targetRatio);
    sy = Math.round((vh - sh) / 2);
  }
  const scale = Math.min(1, MAX_EDGE / Math.max(sw, sh));
  const dw = Math.max(1, Math.round(sw * scale));
  const dh = Math.max(1, Math.round(sh * scale));
  const canvas = document.createElement("canvas");
  canvas.width = dw;
  canvas.height = dh;
  const ctx = canvas.getContext("2d");
  if (!ctx) throw new Error("Could not capture frame");
  ctx.drawImage(video, sx, sy, sw, sh, 0, 0, dw, dh);
  const blob = await new Promise((resolve, reject) => {
    canvas.toBlob((b) => (b ? resolve(b) : reject(new Error("JPEG encode failed"))), "image/jpeg", 0.88);
  });
  return new File([blob], `nofud-meal-${Date.now()}.jpg`, { type: "image/jpeg" });
}

// @ts-check
// Surfaces a toast when a new service worker has installed and is waiting,
// so a stale open tab can hard-reload onto the new version deliberately
// instead of the SW silently taking over mid-session (see sw.js). User data
// lives in IndexedDB, never in the SW cache, so this reload never loses data.

let reloadTriggered = false;

/**
 * @param {ServiceWorkerRegistration} registration
 */
export function initUpdatePrompt(registration) {
  if (registration.waiting && navigator.serviceWorker.controller) {
    showUpdateToast(registration.waiting);
  }

  registration.addEventListener("updatefound", () => {
    const installing = registration.installing;
    if (!installing) return;
    installing.addEventListener("statechange", () => {
      // "installed" + an existing controller means this is an update, not
      // the first-ever install (which has no controller yet).
      if (installing.state === "installed" && navigator.serviceWorker.controller) {
        showUpdateToast(installing);
      }
    });
  });

  navigator.serviceWorker.addEventListener("controllerchange", () => {
    if (reloadTriggered) location.reload();
  });

  // Chromium only checks sw.js for changes on navigation or ~daily; nudge it
  // whenever the tab regains focus so a long-lived open tab still notices.
  document.addEventListener("visibilitychange", () => {
    if (document.visibilityState === "visible") registration.update().catch(() => {});
  });
}

/**
 * @param {ServiceWorker} worker
 */
function showUpdateToast(worker) {
  const existing = document.querySelector(".update-toast");
  if (existing) return;

  const toast = document.createElement("div");
  toast.className = "update-toast";
  toast.setAttribute("role", "status");
  toast.innerHTML = `
    <div class="update-toast__text">
      <span>A new version of Chompass is ready.</span>
    </div>
    <div class="update-toast__actions">
      <button type="button" class="btn btn--primary update-toast__reload">Reload</button>
      <button type="button" class="update-toast__dismiss" aria-label="Dismiss">✕</button>
    </div>
  `;

  toast.querySelector(".update-toast__dismiss")?.addEventListener("click", () => toast.remove());
  toast.querySelector(".update-toast__reload")?.addEventListener("click", () => {
    reloadTriggered = true;
    worker.postMessage("SKIP_WAITING");
  });

  document.body.appendChild(toast);
}

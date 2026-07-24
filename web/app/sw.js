// Hand-rolled service worker (no Workbox — that assumes build-time manifest
// injection, which fights the no-bundler decision for this app). Caches only
// the static app shell, cache-first, versioned; never touches user data
// (IndexedDB/Cache API stay cleanly separated).
const CACHE_NAME = "nofud-shell-v14";

const SHELL_ASSETS = [
  "./",
  "./index.html",
  "./manifest.json",
  "./css/main.css",
  "./fonts/fonts.css",
  "./fonts/manrope-400.woff2",
  "./fonts/manrope-500.woff2",
  "./fonts/manrope-600.woff2",
  "./fonts/manrope-700.woff2",
  "./src/app.js",
  "./src/components/diary-view.js",
  "./src/components/entry-form.js",
  "./src/components/settings-view.js",
  "./src/components/coach-view.js",
  "./src/components/barcode-scanner.js",
  "./src/components/progress-view.js",
  "./src/components/analyze-view.js",
  "./src/components/onboarding-view.js",
  "./src/components/measurements-view.js",
  "./src/components/add-meal-view.js",
  "./src/lib/db.js",
  "./src/lib/off-client.js",
  "./src/lib/barcode-detect.js",
  "./src/lib/media-devices.js",
  "./src/lib/charts.js",
  "./src/lib/dev-seed.js",
  "./src/lib/recent-foods.js",
  "./src/lib/saved-meals.js",
  "./src/lib/recipes.js",
  "./src/lib/meal-schedule.js",
  "./src/lib/meal-share.js",
  "./src/lib/speech.js",
  "./src/lib/download.js",
  "./src/lib/home-nutrients.js",
  "./src/lib/install-prompt.js",
  "./src/lib/ui/sheet.js",
  "./src/lib/ui/dialog.js",
  "./src/lib/ui/focus-trap.js",
  "./src/lib/ui/subpage.js",
  "./src/lib/ui/camera-capture.js",
  "./src/lib/ui/photo-ai-flow.js",
  "./src/lib/ui/voice-capture.js",
  "./src/lib/nofud-core/models.js",
  "./src/lib/nofud-core/formulas.js",
  "./src/lib/nofud-core/forecast.js",
  "./src/lib/nofud-core/diary-format.js",
  "./src/lib/nofud-core/body-metrics-format.js",
  "./src/lib/nofud-core/export-text.js",
  "./src/lib/ai/providers.js",
  "./src/lib/ai/key-storage.js",
  "./src/lib/ai/tools.js",
  "./src/lib/ai/coach.js",
  "./src/lib/ai/image.js",
  "./src/lib/ai/food-analyze.js",
  "./vendor/idb.js",
  "./vendor/zxing/zxing-reader.js",
  "./vendor/zxing/zxing_reader.wasm",
  "./icons/icon-192.png",
  "./icons/icon-512.png",
  "./icons/icon-maskable-512.png",
  "./icons/apple-touch-icon.png",
];

self.addEventListener("install", (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME).then((cache) => cache.addAll(SHELL_ASSETS)).then(() => self.skipWaiting())
  );
});

self.addEventListener("activate", (event) => {
  event.waitUntil(
    caches
      .keys()
      .then((names) => Promise.all(names.filter((n) => n !== CACHE_NAME).map((n) => caches.delete(n))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener("fetch", (event) => {
  if (event.request.method !== "GET") return;
  const url = new URL(event.request.url);
  if (url.origin !== location.origin) return; // never cache cross-origin (AI providers, Open Food Facts)

  event.respondWith(
    caches.match(event.request).then((cached) => {
      if (cached) return cached;
      return fetch(event.request).then((response) => {
        if (response.ok) {
          const copy = response.clone();
          caches.open(CACHE_NAME).then((cache) => cache.put(event.request, copy));
        }
        return response;
      });
    })
  );
});

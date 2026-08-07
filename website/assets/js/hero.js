(function () {
  "use strict";

  // The stage embeds the PWA through absolute URLs. When the built page is
  // opened straight from disk (file://), those resolve to file:// references
  // the browser blocks — so short-circuit with a plain-language hint instead
  // of letting the iframe throw "may not load or link to file:///" errors.
  if (location.protocol === "file:") {
    var fileHost = document.querySelector("[data-live-hero]");
    if (fileHost && !fileHost.querySelector(".hero-file-note")) {
      var fileVideo = fileHost.querySelector("video");
      if (fileVideo) fileVideo.pause();
      var note = document.createElement("p");
      note.className = "hero-file-note";
      note.textContent =
        "The live demo needs a web server — open http://localhost:1313/Chompass/ instead.";
      fileHost.appendChild(note);
    }
    return;
  }

  // Cinematic live hero. Replaces the 6 MB usage mp4 with the real PWA demo
  // (web/app/demo.html) rendered into a phone-proportioned canvas, plus a CSS
  // "camera" that zooms/crops into the app UI as the demo driver announces
  // scenes (chompass-hero postMessage). On wide screens the stage is a split
  // layout: the app on the left, a per-scene description panel on the right.
  // All motion is WAAPI transform keyframes on a single layer — compositor-only,
  // no rAF, no rasterization above 1:1 (canvas is laid out at 620px, zoom is
  // capped at devicePixelRatio). The video stays as the no-JS fallback with
  // preload="none" (poster only).

  var hero = document.querySelector("[data-live-hero]");
  if (!hero) return;
  var video = hero.querySelector("video");
  var tpl = hero.querySelector("template");
  if (!tpl) return; // video is optional: without the mp4, the stage replaces the screenshot fallback
  if (!("IntersectionObserver" in window)) return;

  var PHONE_W = 620; // phone-proportioned canvas: 620×1330 ≈ 0.466 aspect
  var PHONE_H = 1330; // canvas height: the app's mobile layout only depends on width;
  // Hero-crop rest frames the app's top region (day nav + ring + macros) so the
  // rest view stays readable on wide desktop and mobile instead of shrinking the
  // whole tall phone. The full phone frame is reserved for the intro — once per
  // page load, an exception rather than the norm.
  var HERO_CROP_H = 600;
  var REDUCED = matchMedia("(prefers-reduced-motion: reduce)").matches;
  var MAX_ZOOM = Math.min(window.devicePixelRatio || 1, 2); // crisp always
  // Tall sheets/overlays (entry review, barcode card) are framed top-first so
  // the title + editable fields land in frame instead of a mid-sheet crop.
  var FIT_TOP_SCENES = { "ai-review": true, "barcode-card": true };
  var RETRY_INTERVAL = 300;
  var RETRY_MAX = 12; // ~3.6s: covers sheet transitions and slow first renders
  // Per-scene camera durations: the AI overlay is opaque, so the camera
  // quick-cuts to it instead of panning across darkness; ring shots settle.
  var SCENE_DURATIONS = {
    "ai-stream": 600,
    "ai-ring": 900,
    "relog-ring": 900,
    // The warp beat re-announces the same scene per range step; a pan that
    // finishes inside each dwell reads as deliberate acceleration instead of
    // a camera that keeps getting cut off.
    "trend-warp": 1400,
  };

  var heroRoot = null; // .hero-stage (grid on wide screens)
  var stage = null; // .hero-stage__main: the camera cell (crop viewport)
  var camera = null;
  var iframe = null;
  var callout = null;
  var copyPanel = null;
  var copyTitle = null;
  var copyDesc = null;
  var anim = null;
  var ready = false;
  var revealed = false;
  var pendingScene = null;
  var retryTimer = null;
  /** @type {{key: string|null, selector: string, index: number}|null} */
  var lastScene = null;
  // Scene key currently shown in the text layer (callout / side panel). Kept
  // separate from lastScene: the same scene is announced repeatedly (chart
  // warp steps, stream re-crops, target retries) and the copy must NOT
  // re-animate for those — that flashing is what made the hero feel busy.
  var lastTextKey = null;
  var calloutSwapTimer = null;

  var CALLOUTS = {
    intro: "AI that reads labels, photos, and barcodes",
    "ai-typing": "Type a meal, get its macros",
    "ai-stream": "Your macros and micros, live",
    "ai-review": "You approve every entry",
    "ai-ring": "Budget updates as you log",
    "barcode-scan": "Scan any barcode",
    "barcode-card": "Facts from Open Food Facts",
    "plate-scan": "Snap your plate, get the macros",
    "trend-warp-close": "Daily noise, real trend",
    "trend-warp": "Your progress over time",
    "trend-stats": "Where you are vs your goal",
    "trend-bodyfat": "Body-fat tracking",
    "trend-forecast": "Days to your goal",
    "relog-chips": "Repeat a meal in one tap",
    "relog-ring": "Know what\'s left today",
  };

  // Wide-screen side panel copy, one entry per demo scene.
  var SCENES = {
    intro: {
      title: "Your food diary, on your device",
      desc: "Log food, weight, and body metrics entirely on-device — no account, no ads, no analytics.",
    },
    "ai-typing": {
      title: "Type a meal, get its macros",
      desc: "Plain language is enough — “chicken burrito bowl” works. Calories, protein, carbs, and fat come back without labels or barcodes.",
    },
    "ai-stream": {
      title: "Get AI macros and micros",
      desc: "Watch calories, protein, carbs, and fat appear as Chompass reads your note. Nothing is saved until you approve it.",
    },
    "ai-review": {
      title: "You approve every entry",
      desc: "Check the estimate, adjust the serving size, then confirm. Auto-logging is never on by default.",
    },
    "ai-ring": {
      title: "Your calorie budget, updated as you log",
      desc: "The ring shows what's left for the day the moment a meal lands — and with your day's burn on Android.",
    },
    "barcode-scan": {
      title: "Log any packaged food from its barcode",
      desc: "Point the camera at the code — live scanning reads it in a second, or type the digits straight in.",
    },
    "barcode-card": {
      title: "Product facts from Open Food Facts",
      desc: "Barcode lookups pull from 4.6M+ products, and every entry goes through the same review step before logging.",
    },
    "plate-scan": {
      title: "Log a meal from a photo",
      desc: "Snap your plate and Chompass reads it like a label — macros come back before anything is saved.",
    },
    "trend-warp-close": {
      title: "See the real trend behind daily weigh-ins",
      desc: "A month of daily readings looks like noise — that's normal. The trend line shows where things are actually going.",
    },
    "trend-warp": {
      title: "See progress across months or years",
      desc: "The same chart expands from one month to all time — daily noise becomes a clear trend.",
    },
    "trend-stats": {
      title: "Where you are against your goal",
      desc: "Current, goal, and net sit right above the chart — the state of your week at a glance.",
    },
    "trend-bodyfat": {
      title: "Body fat, tracked with your weight",
      desc: "Body-fat readings follow the same range filters as weight, side by side with the trend.",
    },
    "trend-forecast": {
      title: "Know when you'll reach your goal",
      desc: "Chompass projects your weekly rate, the 30-day outlook, and how many days are left to goal.",
    },
    "relog-chips": {
      title: "Repeat a meal in one tap",
      desc: "Favorites and past meals sit one tap away — relog without retyping or rescanning.",
    },
    "relog-ring": {
      title: "Know what's left for the day",
      desc: "The ring shows your remaining budget — relog a favorite and see the impact right away.",
    },
  };

  function stageSize() {
    return {
      w: stage.clientWidth || hero.clientWidth,
      h: stage.clientHeight || hero.clientHeight,
    };
  }

  /** Scale for the hero-crop rest: fills the stage with the app's top region. */
  function heroCropScale() {
    var size = stageSize();
    return Math.min(
      MAX_ZOOM,
      (size.w * 0.92) / PHONE_W,
      (size.h * 0.92) / HERO_CROP_H,
    );
  }

  /** Scale that fits the whole phone with margins (intro / pull-back only). */
  function frameScale() {
    var size = stageSize();
    return Math.min((size.w * 0.88) / PHONE_W, (size.h * 0.8) / PHONE_H);
  }

  function frameTransform() {
    return cropTransform(frameScale(), PHONE_W / 2, PHONE_H / 2);
  }

  /** translate+scale transform for a crop centered on canvas point (cx, cy).
      Content is composed slightly above the stage center (0.45h) so the
      bottom caption pill has clean room below it. */
  function cropTransform(s, cx, cy) {
    var size = stageSize();
    return {
      tx: size.w / 2 - s * cx,
      ty: size.h * 0.45 - s * cy,
      s: s,
    };
  }

  function restTransform() {
    return cropTransform(heroCropScale(), PHONE_W / 2, HERO_CROP_H / 2);
  }

  /** True when a rect is (at least partially) inside the phone canvas viewport. */
  function rectVisible(r) {
    return (
      r.y < PHONE_H - 8 && r.y + r.h > 8 && r.x < PHONE_W - 8 && r.x + r.w > 8
    );
  }

  /** Current camera transform from the computed matrix (translate then scale). */
  function currentTransform() {
    var m = new DOMMatrix(getComputedStyle(camera).transform);
    return { tx: m.e, ty: m.f, s: Math.sqrt(m.a * m.a + m.b * m.b) };
  }

  function transformString(t) {
    return (
      "translate(" +
      t.tx.toFixed(2) +
      "px," +
      t.ty.toFixed(2) +
      "px) scale(" +
      t.s.toFixed(4) +
      ")"
    );
  }

  /** Target element rect in canvas coordinates (iframe fills canvas 1:1). */
  function resolveRect(selector, index) {
    var doc = iframe && iframe.contentDocument;
    if (!doc || !selector) return null;
    var el = doc.querySelectorAll(selector)[index || 0];
    if (!el) return null;
    var r = el.getBoundingClientRect();
    if (!r.width || !r.height) return null;
    return { x: r.left, y: r.top, w: r.width, h: r.height };
  }

  /**
   * Camera target for a scene: (scale, translate) + a slow Ken Burns drift.
   * Returns `resolved: false` when the target is missing or offscreen so the
   * caller can retry instead of snapping to an unreadable fallback.
   */
  function resolveScene(key, selector, index) {
    var rect = resolveRect(selector, index);
    var size = stageSize();
    var rest = restTransform();
    var target;
    var driftPx = Math.min(54, Math.max(16, size.w * 0.04));
    var fitTop = FIT_TOP_SCENES[key] === true;
    var resolved = false;
    if (rect && rectVisible(rect)) {
      // Tall sheets/overlays frame their top content; cards/charts center.
      var w = Math.min(Math.max(rect.w, 40), PHONE_W);
      var h = fitTop
        ? Math.min(Math.max(rect.h, 40), PHONE_H * 0.52)
        : Math.min(Math.max(rect.h, 40), PHONE_H);
      var s = Math.min(MAX_ZOOM, (size.w * 0.9) / w, (size.h * 0.88) / h);
      s = Math.max(s, heroCropScale() * 0.8);
      var cx = rect.x + rect.w / 2;
      var cy = fitTop ? rect.y + h / 2 : rect.y + rect.h / 2;
      target = cropTransform(s, cx, cy);
      resolved = true;
    } else if (key === "intro") {
      // First impression: the whole phone, before the crops begin.
      target = frameTransform();
    } else {
      target = rest;
    }
    var drift =
      key === "intro" || key === "rest" || key === null
        ? null
        : fitTop ||
            key === "ai-stream" ||
            key === "ai-ring" ||
            key === "relog-ring"
          ? { ...target, ty: target.ty + driftPx } // gentle settle-down
          : { ...target, tx: target.tx - driftPx, ty: target.ty - driftPx }; // slow push-in
    return { target: target, drift: drift, resolved: resolved };
  }

  function animateTo(target, drift, duration) {
    if (anim) {
      try {
        anim.commitStyles();
      } catch (e) {
        /* older engines: animate from scratch */
      }
      anim.cancel();
      anim = null;
    }
    var from = currentTransform();
    var frames = [{ transform: transformString(from) }];
    if (drift && !REDUCED) {
      frames.push(
        { transform: transformString(target), offset: 0.5 },
        { transform: transformString(drift), offset: 1 },
      );
    } else {
      frames.push({ transform: transformString(target) });
    }
    anim = camera.animate(frames, {
      duration: duration,
      easing: "cubic-bezier(0.22, 1, 0.36, 1)",
      fill: "both",
    });
  }

  function showCallout(key) {
    if (!callout) return;
    var text = CALLOUTS[key] || "";
    if (text && callout.textContent === text && callout.classList.contains("is-visible")) {
      return;
    }
    if (calloutSwapTimer) {
      clearTimeout(calloutSwapTimer);
      calloutSwapTimer = null;
    }
    if (!text) {
      callout.classList.remove("is-visible");
      return;
    }
    if (callout.classList.contains("is-visible")) {
      // Gentle crossfade: fade the pill out, swap the words, fade back in —
      // no hard cut between captions.
      callout.classList.remove("is-visible");
      calloutSwapTimer = setTimeout(function () {
        callout.textContent = text;
        callout.classList.add("is-visible");
        calloutSwapTimer = null;
      }, 300);
    } else {
      callout.textContent = text;
      callout.classList.add("is-visible");
    }
  }

  /** Swap the wide-screen side panel copy for the current scene. */
  function showCopy(key) {
    if (!copyPanel || !copyTitle || !copyDesc) return;
    var info = SCENES[key];
    if (!info) {
      copyPanel.classList.remove("is-visible");
      return;
    }
    copyTitle.textContent = info.title;
    copyDesc.textContent = info.desc;
    copyPanel.classList.add("is-visible");
    copyTitle.classList.remove("swap");
    copyDesc.classList.remove("swap");
    void copyTitle.offsetWidth; // reflow so the fade-in restarts per scene
    copyTitle.classList.add("swap");
    copyDesc.classList.add("swap");
  }

  /** Fade the stage in only once the demo has content to show. */
  function revealStage() {
    if (revealed || !heroRoot) return;
    revealed = true;
    heroRoot.classList.add("is-ready");
    heroLog("stage revealed — demo home painted");
  }

  /** Reveal on the first scene, the home having painted, or a hard fallback. */
  function startRevealWatch() {
    var timer = setTimeout(revealStage, 8000);
    var poll = setInterval(function () {
      if (revealed) {
        clearInterval(poll);
        clearTimeout(timer);
        return;
      }
      var doc = iframe && iframe.contentDocument;
      if (doc && doc.querySelector(".fab")) {
        clearInterval(poll);
        clearTimeout(timer);
        revealStage();
      }
    }, 300);
  }

  function clearRetry() {
    if (retryTimer) {
      clearInterval(retryTimer);
      retryTimer = null;
    }
  }

  // Realtime tracing: [hero] lines log what the camera is actually doing for
  // each scene the demo announces — resolved zoom, retry, or fallback to rest
  // — so the on-screen view can be matched against the demo timeline.
  function heroLog(msg) {
    console.log("[hero] " + msg);
  }

  function sceneLabel(key) {
    return CALLOUTS[key] || (key === "rest" ? "rest (hero crop)" : key);
  }

  function applyScene(scene, duration) {
    if (REDUCED) {
      // Static: snap to the target without animating.
      camera.style.transform = transformString(scene.target);
      return;
    }
    animateTo(scene.target, scene.drift, duration);
  }

  function playScene(key, selector, index) {
    if (!camera) return;
    revealStage();
    lastScene = { key: key, selector: selector, index: index };
    // The camera re-targets on every announcement (warp steps widen the chart,
    // stream re-crops zoom into the partial card), but the text layer only
    // swaps when the scene key actually changes. Rest (null) keeps the current
    // caption so pull-backs read as a calm pause instead of a text reset.
    if (key !== lastTextKey && key !== null) {
      lastTextKey = key;
      showCallout(key);
      showCopy(key);
    }
    clearRetry();
    var duration = SCENE_DURATIONS[key] || 2200;
    var scene = resolveScene(key, selector, index);
    if (scene.resolved || !selector) {
      heroLog(
        'scene "' +
          sceneLabel(key) +
          '" — camera ' +
          scene.target.s.toFixed(2) +
          "x",
      );
      applyScene(scene, duration);
      return;
    }
    // The target was announced before it settled (sheet transition, slow first
    // render). Re-resolve on an interval; fall back to the hero-crop rest only
    // after the retry window so sheets never flash into an unreadable frame.
    heroLog('scene "' + sceneLabel(key) + '" — target not ready, retrying…');
    var tries = 0;
    retryTimer = setInterval(function () {
      tries += 1;
      var retried = resolveScene(key, selector, index);
      if (retried.resolved || tries >= RETRY_MAX) {
        clearRetry();
        if (!retried.resolved) {
          heroLog(
            'scene "' +
              sceneLabel(key) +
              '" — target missing after ' +
              tries +
              " tries, camera falls back to rest",
          );
        }
        applyScene(retried, duration);
      }
    }, RETRY_INTERVAL);
  }

  function onSceneMessage(data) {
    if (!ready) {
      pendingScene = data;
      return;
    }
    playScene(data.key, data.selector, data.index || 0);
  }

  function pause() {
    clearRetry();
    if (anim) {
      try {
        anim.commitStyles();
      } catch (e) {
        /* ignore */
      }
      anim.cancel();
      anim = null;
    }
  }

  function resume() {
    if (lastScene && camera && !REDUCED) {
      playScene(lastScene.key, lastScene.selector, lastScene.index);
    }
  }

  function applyRest() {
    camera.style.transform = transformString(restTransform());
  }

  window.addEventListener("message", function (ev) {
    var data = ev.data;
    if (!data || data.source !== "chompass-hero") return;
    if (data.type === "scene") onSceneMessage(data);
    else if (data.type === "rest")
      onSceneMessage({ key: null, selector: "", index: 0 });
  });

  var lazy = new IntersectionObserver(
    function (entries) {
      entries.forEach(function (entry) {
        if (!entry.isIntersecting) return;
        lazy.disconnect();
        var node = document.createElement("div");
        node.innerHTML = tpl.innerHTML;
        heroRoot = node.firstElementChild;
        var fallback = hero.querySelector(".video-fallback");
        if (video) video.replaceWith(heroRoot);
        else if (fallback) fallback.replaceWith(heroRoot);
        else hero.appendChild(heroRoot);
        stage = heroRoot.querySelector(".hero-stage__main") || heroRoot;
        camera = heroRoot.querySelector(".hero-camera");
        callout = heroRoot.querySelector(".hero-callout");
        copyPanel = heroRoot.querySelector(".hero-copy-panel");
        copyTitle =
          copyPanel && copyPanel.querySelector(".hero-copy-panel__title");
        copyDesc =
          copyPanel && copyPanel.querySelector(".hero-copy-panel__desc");
        iframe = heroRoot.querySelector("iframe");
        applyRest();
        startRevealWatch();
        if (iframe) {
          iframe.addEventListener(
            "load",
            function () {
              ready = true;
              if (pendingScene) {
                var sc = pendingScene;
                pendingScene = null;
                onSceneMessage(sc);
              }
            },
            { once: true },
          );
        }
      });
    },
    { rootMargin: "900px" },
  );
  lazy.observe(hero);

  // Pause the demo while scrolled away or backgrounded; resume when back.
  // Pausing is debounced: embedded webviews (IDE previews) can report the
  // hero as non-intersecting on flapping geometry, which would otherwise
  // stall the demo mid-scene. Only pause after the hero has been out of
  // view for the whole grace window.
  var pauseTimer = null;
  var PAUSE_GRACE_MS = 1000;
  function sendPause() {
    try {
      iframe.contentWindow.postMessage(
        { source: "chompass-hero", type: "pause" },
        window.location.origin,
      );
    } catch (e) {
      /* ignore */
    }
    pause();
    heroLog("paused — hero out of view");
  }
  function sendResume() {
    try {
      iframe.contentWindow.postMessage(
        { source: "chompass-hero", type: "play" },
        window.location.origin,
      );
    } catch (e) {
      /* ignore */
    }
    resume();
    heroLog("resumed");
  }
  var active = new IntersectionObserver(
    function (entries) {
      entries.forEach(function (entry) {
        if (!iframe) return;
        clearTimeout(pauseTimer);
        if (entry.isIntersecting) sendResume();
        else pauseTimer = setTimeout(sendPause, PAUSE_GRACE_MS);
      });
    },
    { threshold: 0 },
  );
  active.observe(hero);

  // Tab-hidden pauses the demo too, but debounced: webviews can flap
  // document.hidden. Real tab switches are only delayed by the grace window.
  var visibilityTimer = null;
  document.addEventListener("visibilitychange", function () {
    if (!iframe) return;
    clearTimeout(visibilityTimer);
    if (document.hidden)
      visibilityTimer = setTimeout(sendPause, PAUSE_GRACE_MS);
    else sendResume();
  });

  var resizeTimer = null;
  window.addEventListener("resize", function () {
    clearTimeout(resizeTimer);
    resizeTimer = setTimeout(function () {
      if (!camera) return;
      if (lastScene && !REDUCED) {
        playScene(lastScene.key, lastScene.selector, lastScene.index);
      } else {
        applyRest();
      }
    }, 200);
  });
})();

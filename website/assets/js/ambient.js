(function () {
  "use strict";

  // Ambient "wax" field: a direct port of the Android Morphe background
  // (android/.../ui/components/AmbientBackground.kt) to one WebGL1 pass at half
  // resolution. Eight blobs orbit, breathe and stretch; the fragment shader sums
  // their inverse-square falloff and colour-weights by the squared radius.
  // Static page content sits above it (main/.site-footer z-index 1); the header
  // and the landing hero panel are the only blurred chrome on top.

  var canvas = document.getElementById("ambient");
  if (!canvas || !canvas.getContext) return;
  var gl = canvas.getContext("webgl", {
    alpha: true,
    antialias: false,
    depth: false,
    stencil: false,
    premultipliedAlpha: true,
    preserveDrawingBuffer: false,
    powerPreference: "low-power",
  });
  if (!gl) {
    canvas.remove(); // no WebGL: flat #0f0e13, the pre-6.0.0 look
    return;
  }

  // WAX_BLOBS, AmbientBackground.kt:104-113. Fields:
  // x fraction, travel fraction, radius dp, climbMs, swayMs, breathMs, phase,
  // colour index.
  var BLOBS = [
    [0.22, 0.46, 64, 34000, 13000, 9000, 0.0, 0],
    [0.7, 0.4, 52, 41000, 15000, 11000, 2.1, 2],
    [0.45, 0.3, 46, 29000, 11000, 8000, 4.0, 1],
    [0.82, 0.34, 58, 37000, 17000, 10000, 1.2, 0],
    [0.15, 0.48, 42, 45000, 12000, 12000, 3.3, 2],
    [0.58, 0.38, 60, 32000, 14000, 9500, 5.1, 1],
    [0.35, 0.5, 38, 39000, 16000, 8500, 0.7, 0],
    [0.9, 0.28, 44, 43000, 10000, 10500, 2.8, 1],
  ];
  // Site accent #5CC48F + hueShift(+60,0.85) + hueShift(-75,0.90), the app's
  // ACCENT palette rule (AmbientBackground.kt:410-415).
  var COLORS = [
    [0.361, 0.769, 0.561],
    [0.424, 0.6, 0.769],
    [0.678, 0.769, 0.4],
  ];

  var FRAME_MS = 33.3333; // ~30fps, the app's MinFrameIntervalNs
  var IDLE_FRAME_MS = 66; // ~15fps after IDLE_AFTER_MS
  var IDLE_AFTER_MS = 6000;
  var HEAT_MS = 1500; // TouchHeatWindowNs
  var TOUCH_FRACTION = 0.22; // TouchRadiusFraction, of the shorter side
  var SPAN_MAX = 1180; // content column (--max 67.5rem) plus gutters

  // Blobs are placed by screen fraction, which works on a phone and fails on a
  // 4:1 desktop window: raw width fractions scatter the eight blobs past the
  // metaball threshold and the field vanishes. Compress them into the content
  // column and scale radii off the same column (360dp phone width = 1.0).
  function geometry(tMs, w, h) {
    var spanX = Math.min(w, SPAN_MAX);
    // Blob radii are dp on the app's 360x740dp phone and scale with the square
    // root of (column x height): a width-only basis is right in portrait but
    // over-fills a landscape viewport, where it drives the halo threshold over
    // the whole screen (100% halo, no dark structure at 1440x900) and the wax
    // becomes a flat tint. The geometric mean holds the app's measured coverage
    // (~45% body, ~93% halo) from a 390px phone to a 2560px desktop.
    var basis = Math.sqrt((spanX / 360) * (h / 740));
    var out = [];
    for (var i = 0; i < BLOBS.length; i++) {
      var b = BLOBS[i];
      var climb = (2 * Math.PI * tMs) / b[3] + b[6];
      var sway = Math.sin((2 * Math.PI * tMs) / b[4] + b[6] * 1.7);
      var breath = Math.sin((2 * Math.PI * tMs) / b[5] + b[6] * 0.6);
      out.push({
        x: w / 2 + (b[0] - 0.5) * spanX + 0.05 * spanX * sway,
        y: (0.5 - b[1] * Math.sin(climb)) * h,
        r: b[2] * basis * (1 + 0.09 * breath),
        stretch: 1 + 0.4 * Math.abs(Math.cos(climb)),
        c: b[7],
      });
    }
    return out;
  }

  var VERT = [
    "attribute vec2 aPos;",
    "void main() {",
    "  gl_Position = vec4(aPos, 0.0, 1.0);",
    "}",
  ].join("\n");

  // Port of WAX_SHADER (AmbientBackground.kt:130-170): same field, same
  // body/halo thresholds, same mid-row falloff, same per-pixel dither.
  var FRAG = [
    "#ifdef GL_FRAGMENT_PRECISION_HIGH",
    "precision highp float;",
    "#else",
    "precision mediump float;",
    "#endif",
    "uniform vec4 uBlob[8];", // xy = centre px, z = radius px, w = y stretch
    "uniform vec3 uColor[8];",
    "uniform vec2 uRes;",
    "uniform vec4 uTouch;", // xy px, z radius px, w heat 0..1
    "uniform float uAlpha, uBodyLo, uBodyHi, uHaloLo, uHaloHi, uHaloGain, uMidFloor;",
    "void main() {",
    "  vec2 p = gl_FragCoord.xy;",
    "  float field = 0.0;",
    "  vec3 color = vec3(0.0);",
    "  for (int i = 0; i < 8; i++) {",
    "    vec4 b = uBlob[i];",
    "    vec2 d = p - b.xy;",
    "    d.y /= b.w;",
    "    float r = b.z;",
    "    if (uTouch.w > 0.0) {",
    "      float td = distance(b.xy, uTouch.xy);",
    "      r *= 1.0 + 0.45 * uTouch.w * (1.0 - smoothstep(0.0, uTouch.z, td));",
    "    }",
    "    float f = r * r / max(dot(d, d), 1.0);",
    "    field += f;",
    "    color += uColor[i] * uColor[i] * f;",
    "  }",
    "  color = sqrt(color / max(field, 0.0001));",
    "  float body = smoothstep(uBodyLo, uBodyHi, field);",
    "  float halo = smoothstep(uHaloLo, uHaloHi, field) * uHaloGain;",
    "  float a = max(body, halo) * uAlpha;",
    "  float vy = abs(p.y / max(uRes.y, 1.0) - 0.5) * 2.0;",
    "  a *= mix(uMidFloor, 1.0, smoothstep(0.15, 0.85, vy));",
    "  float n = fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);",
    "  a = clamp(a + (n - 0.5) / 255.0, 0.0, 1.0);",
    "  gl_FragColor = vec4(color * a, a);",
    "}",
  ].join("\n");

  function compile(type, src) {
    var sh = gl.createShader(type);
    gl.shaderSource(sh, src);
    gl.compileShader(sh);
    if (!gl.getShaderParameter(sh, gl.COMPILE_STATUS)) {
      gl.deleteShader(sh);
      canvas.remove();
      return null;
    }
    return sh;
  }

  var vs = compile(gl.VERTEX_SHADER, VERT);
  var fs = vs && compile(gl.FRAGMENT_SHADER, FRAG);
  if (!fs) return;
  var prog = gl.createProgram();
  gl.attachShader(prog, vs);
  gl.attachShader(prog, fs);
  gl.linkProgram(prog);
  if (!gl.getProgramParameter(prog, gl.LINK_STATUS)) {
    canvas.remove();
    return;
  }
  gl.useProgram(prog);

  var buf = gl.createBuffer();
  gl.bindBuffer(gl.ARRAY_BUFFER, buf);
  gl.bufferData(
    gl.ARRAY_BUFFER,
    new Float32Array([-1, -1, 3, -1, -1, 3]),
    gl.STATIC_DRAW,
  );
  var aPos = gl.getAttribLocation(prog, "aPos");
  gl.enableVertexAttribArray(aPos);
  gl.vertexAttribPointer(aPos, 2, gl.FLOAT, false, 0, 0);

  function uni(name) {
    return gl.getUniformLocation(prog, name);
  }
  var uBlob = uni("uBlob[0]");
  var uColor = uni("uColor[0]");
  var uRes = uni("uRes");
  var uTouch = uni("uTouch");
  var uAlpha = uni("uAlpha");
  var uBodyLo = uni("uBodyLo");
  var uBodyHi = uni("uBodyHi");
  var uHaloLo = uni("uHaloLo");
  var uHaloHi = uni("uHaloHi");
  var uHaloGain = uni("uHaloGain");
  var uMidFloor = uni("uMidFloor");

  // AmbientContrast.BALANCED + waxFieldAlpha dark base, oled=false.
  gl.uniform1f(uAlpha, 0.14);
  gl.uniform1f(uBodyLo, 0.85);
  gl.uniform1f(uBodyHi, 1.15);
  gl.uniform1f(uHaloLo, 0.3);
  gl.uniform1f(uHaloHi, 0.9);
  gl.uniform1f(uHaloGain, 0.3);
  gl.uniform1f(uMidFloor, 0.75);

  var colors = new Float32Array(24);
  for (var ci = 0; ci < 8; ci++) {
    var cc = COLORS[BLOBS[ci][7]];
    colors[ci * 3] = cc[0];
    colors[ci * 3 + 1] = cc[1];
    colors[ci * 3 + 2] = cc[2];
  }
  gl.uniform3fv(uColor, colors);

  var blobData = new Float32Array(32);
  var W = 0;
  var H = 0;
  var scale = 1;
  var hx = 0;
  var hy = 0;
  var touchRadius = 0;
  var lastHeatTs = -1e9;

  gl.disable(gl.BLEND);
  gl.clearColor(0, 0, 0, 0);

  function draw(tMs) {
    if (!W || !H) return;
    var blobs = geometry(tMs, W, H);
    for (var i = 0; i < 8; i++) {
      var b = blobs[i];
      blobData[i * 4] = b.x * scale;
      // gl_FragCoord.y is bottom-origin; the Android canvas is top-down, so
      // flipping y here keeps each blob's direction of travel identical.
      blobData[i * 4 + 1] = (H - b.y) * scale;
      blobData[i * 4 + 2] = b.r * scale;
      blobData[i * 4 + 3] = b.stretch;
    }
    gl.uniform4fv(uBlob, blobData);
    gl.uniform2f(uRes, canvas.width, canvas.height);
    var heat = Math.max(0, 1 - (performance.now() - lastHeatTs) / HEAT_MS);
    gl.uniform4f(
      uTouch,
      hx * scale,
      (H - hy) * scale,
      touchRadius,
      heat,
    );
    gl.clear(gl.COLOR_BUFFER_BIT);
    gl.drawArrays(gl.TRIANGLES, 0, 3);
  }

  // WaxRasterScale = 0.5 (AmbientBackground.kt:193).
  function resize() {
    var dpr = Math.min(window.devicePixelRatio || 1, 2);
    W = canvas.clientWidth;
    H = canvas.clientHeight;
    if (!W || !H) return;
    scale = 0.5 * dpr;
    if (W * scale > 1920) scale = 1920 / W; // overdraw cap for wide/4K
    canvas.width = Math.max(1, Math.round(W * scale));
    canvas.height = Math.max(1, Math.round(H * scale));
    gl.viewport(0, 0, canvas.width, canvas.height);
    touchRadius = TOUCH_FRACTION * Math.min(W, H) * scale;
    draw(phaseMs); // never leave a blank canvas
  }

  var phaseMs = 0;
  var raf = 0;
  var lastTs = 0;
  var drawnAt = 0;
  var lastInteraction = 0;

  function stop() {
    if (raf) cancelAnimationFrame(raf);
    raf = 0;
    lastTs = 0;
    drawnAt = 0;
  }

  function tick(ts) {
    raf = requestAnimationFrame(tick);
    // A throttled background tab must not jump the phase on return.
    phaseMs += lastTs ? Math.min(ts - lastTs, 100) : 0;
    lastTs = ts;
    var interval = ts - lastInteraction > IDLE_AFTER_MS ? IDLE_FRAME_MS : FRAME_MS;
    if (ts - drawnAt < interval) return;
    drawnAt = ts;
    draw(phaseMs);
  }

  // Single static frame: reduced motion, Save-Data, or a browser that rejects
  // WebGL contexts after the guard above.
  var staticOnly =
    (navigator.connection && navigator.connection.saveData) || false;
  var motionQuery = matchMedia("(prefers-reduced-motion: reduce)");

  function canAnimate() {
    return !staticOnly && !motionQuery.matches;
  }

  function start() {
    if (raf || !canAnimate()) return;
    lastTs = 0;
    drawnAt = 0;
    raf = requestAnimationFrame(tick);
  }

  resize();
  if (canAnimate()) start();

  if (motionQuery.addEventListener) {
    motionQuery.addEventListener("change", function () {
      if (canAnimate()) start();
      else stop();
    });
  }

  var resizePending = false;
  window.addEventListener("resize", function () {
    if (resizePending) return;
    resizePending = true;
    requestAnimationFrame(function () {
      resizePending = false;
      resize();
    });
  });

  document.addEventListener("visibilitychange", function () {
    if (document.hidden) stop();
    else start();
  });

  window.addEventListener("pagehide", stop);

  ["pointerdown", "wheel", "touchstart", "keydown", "scroll"].forEach(
    function (type) {
      window.addEventListener(
        type,
        function () {
          lastInteraction = performance.now();
        },
        { passive: true },
      );
    },
  );

  if (matchMedia("(hover: hover) and (pointer: fine)").matches) {
    window.addEventListener(
      "pointermove",
      function (e) {
        if (!canAnimate()) return; // nothing repaints after frame 0
        lastInteraction = performance.now();
        lastHeatTs = lastInteraction;
        hx = e.clientX;
        hy = e.clientY;
      },
      { passive: true },
    );
  }
})();

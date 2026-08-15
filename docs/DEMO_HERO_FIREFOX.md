# Live demo hero: iframe restarts in Firefox / embedded previews (OPEN)

Status: **open**; partial fixes landed (see below); the core reload loop is
not yet reproduced in a controlled environment.

> **2026-08 update (Phase 0/3 instrumentation):** headless Firefox 153
> (geckodriver, real site build) now runs the demo **cleanly end-to-end**: a
> full 5-beat loop completes in ~91 s with **zero beat failures** and no
> document reloads, so the reload loop does not reproduce under headless
> automation (consistent with the doc note that it needs real-Firefox
> visibility/bfcache or embedded-webview conditions). The demo now ships
> with a driver watchdog that posts `stalled` to the parent (parent performs
> a bounded iframe restart: 30 s gap, max 3), a `hello → state` pause/play
> handshake that re-syncs a reloaded document, `?debug=1` lifecycle/error
> tracing (demo iframe + parent hero), and `window.__demoStats` telemetry.
> Repro recipe for a real session: open the deployed page, keep the hero
> scrolled away for >1 s (pause), scroll back, and watch for `loop 0`
> restarts in the iframe console; report whether `__demoStats.loopIndex`
> regresses to 0 after any `pageshow` (bfcache) event.
>
> **2026-08 second report (live site):** a restart loop was reproduced on
> the live site in the maintainer's environment (console shows VS Code
> webview artifacts: `Cascadia Mono` font blocks at "visibility level 2",
> `stylesheets-manager.js`, so the reloads are the embedded preview
> discarding/restoring the iframe; the loop restarts right after
> `route → #/home`, every ~4 s). Headless Firefox still runs clean, so the
> reload is embedder/browser-driven rather than a page bug. Also observed on
> Chromium: `beat "trend" failed: timeout waiting for range chips` right
> after a `paused → resumed` cycle: `waitFor` counted wall-clock time, so a
> pause mid-beat burned the 9 s budget. Fixed:
> - `waitFor` now counts only unpaused time (pauses no longer cause beat
>   failures); verified with an 11 s mid-beat pause → loop still completes
>   with 0 failures.
> - **Static mode:** the parent counts iframe document loads (persistent
>   load listener, not `{once:true}`); ≥3 reloads within 60 s switches the
>   driver to a single frozen home frame (`hello → state` now carries
>   `static: true`, closing the boot race). A restart loop now degrades to a
>   stable hero instead of looping forever. Verified headless: 4 rapid
>   reloads → static frame, loop frozen.
> - Camera transforms use `translate3d` to force a compositor layer in
>   Firefox (2D transforms can run on the main thread there → the jank the
>   maintainer saw vs Chromium).
>
> **2026-08 resolution (product decision):** the maintainer chose a
> browser-split hero: **Firefox renders a static hero showing the seeded
> logging view (home/diary), Chromium-based browsers get the animated usage
> demo.** Implemented as a UA switch in `hero.js` (`/Firefox\//` →
> `staticMode` from first load; `?demo=static` forces it anywhere). This
> makes the open reload-loop bug moot on Firefox by design (no auto-play
> there); Chromium keeps the animation with the 2-restart static fallback
> for flaky embeds (VS Code/Cursor webviews). Verified headless: Firefox UA
> → static diary frame, Chrome UA → full loop with 0 failures.
>
> **Follow-up: the Firefox hero is explorable.** The static frame is a live
> mini-PWA: `.hero-stage--explorable` re-enables pointer events on the
> phone iframe (`pointer-events: auto`, `touch-action: pan-y`) so visitors
> click through the real components (add-food sheet, mock AI entry, mock
> barcode, Progress charts) inside the phone; a "Live app: click to
> explore" pill fades after 7 s. The static frame is also fully populated:
> static mode seeds the 90-day diary (`reseedDiary`) so the logging view
> shows real entries, the calorie ring, and relog chips. Verified with real
> coordinate clicks through the camera transform (FAB → sheet, scrim →
> close, nav → Progress).

## Symptom

The live demo hero never gets past the first beat. The console shows
`[demo] ═══ loop 0 start ═══` repeating every few seconds (a fresh `loop 0`
only happens when the demo module re-executes, i.e. the **iframe document
reloads**), always right after `[demo] route → #/home` from `beat ai`, and
the parent page itself does **not** reload (no repeated
`[hero] stage revealed` lines).

Reported in:

- **Firefox** (maintainer): "never comes past the first part".
- **Cursor/VS Code built-in preview** (webview proxy on `localhost:1314`,
  `Cursor.exe`): same restart pattern, plus `file://` security-error noise,
  "Cascadia Mono" font blocks ("visibility level 2"), and stale cached demo
  files between sessions.

## Evidence

- Repeated `[demo] ═══ loop 0 start ═══` / `[demo] ▶ scene intro` /
  `[demo] ▶ beat ai (1/4) · loop 0` / `[demo] route → #/home`, then restart.
- No `[demo] loop 1 start` ever appears.
- Beat-failure warnings (`demo beat "…" failed, skipping`) do **not** appear:
  the driver never gets a chance to fail; the document is torn down first.
- `stylesheets-manager.js:669` lines in preview consoles come from a **stale
  cached build**: that file does not exist in the current repo.
- Headless Chromium runs are clean: 33-scene, 3-loop observations with zero
  errors and no restarts (intro only at `loop 0`, once per load).

## Already fixed (related, all verified)

| Fix                                                                                                                                                                                                                                                   | File                                                                        |
| ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------- |
| Reduced-motion freeze: demo stopped after one static frame when `prefers-reduced-motion: reduce` (Firefox honors the Windows "Show animations in Windows" setting). Driver now always runs the full sequence; the parent camera snaps between scenes. | `web/app/src/demo/demo-driver.js`                                           |
| Driver self-paused on its own `document.hidden`: embedded webviews report hidden while visible → demo stalled forever. Removed; the parent owns pause/resume.                                                                                        | `web/app/src/demo/demo-driver.js`                                           |
| Hugo dev-server rebuild storm on static-mount edits ("Syncing app/src/…" every ~3.5 s) fired livereload and reloaded open pages mid-demo. Dev publishDir moved outside the watched tree.                                                              | `devenv.nix` (`site-serve`, `site:serve`)                                   |
| `scrollIntoView`/`focus` in the driver scrolled the marketing page (scroll propagates through all ancestor scroll containers). `centerOn` now scrolls only the iframe document; focus uses `preventScroll`.                                           | `web/app/src/demo/demo-driver.js`, `web/app/src/components/analyze-view.js` |
| Pause/resume on flapping intersection geometry now debounced (1 s grace).                                                                                                                                                                             | `website/assets/js/hero.js`                                                 |
| Stale demo files in embedded previews: iframe src carries `?v=<site version>`; `file://`-opened builds show a hint instead of blocked resources.                                                                                                      | `website/layouts/index.html`, `website/assets/js/hero.js`                   |

## Next steps to pin the remaining reload

1. Real Firefox DevTools → Network panel: does `app/demo.html` (or any demo
   module) appear as a **new request** on each restart (document reload) or
   not (frame crash with auto-reload)? Also check the Console for
   "Unresponsive script" / crash indicators.
2. If it is a reload: capture `Page.frameNavigated`/`Page.frameStartedLoading`
   (Chrome) or the network waterfall in Firefox to see the triggering URL.
3. If it only reproduces in the webview preview and not in real Firefox,
   the reload is likely Cursor's iframe **discard/restore** (the webview
   treats the frame as hidden: see the "visibility level 2" font blocks).
   Reproduce by testing the demo in a real browser tab.
4. Headless Firefox cannot be driven via CDP (it speaks its own RDP); use
   geckodriver/Marionette or manual DevTools for an automated Firefox run.
5. Consider a driver-side watchdog: if the document stays at `loop 0` for
   more than ~2× loop duration without `loop 1` appearing, log a distinct
   marker to distinguish reload loops from hangs.

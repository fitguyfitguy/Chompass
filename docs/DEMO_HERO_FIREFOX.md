# Live demo hero: iframe restarts — Firefox / embedded previews (OPEN)

Status: **open** — partial fixes landed (see below); the core reload loop is
not yet reproduced in a controlled environment.

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
- Beat-failure warnings (`demo beat "…" failed, skipping`) do **not** appear —
  the driver never gets a chance to fail; the document is torn down first.
- `stylesheets-manager.js:669` lines in preview consoles come from a **stale
  cached build** — that file does not exist in the current repo.
- Headless Chromium runs are clean: 33-scene, 3-loop observations with zero
  errors and no restarts (intro only at `loop 0`, once per load).

## Already fixed (related, all verified)

| Fix                                                                                                                                                                                                                                                   | File                                                                        |
| ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------- |
| Reduced-motion freeze: demo stopped after one static frame when `prefers-reduced-motion: reduce` (Firefox honors the Windows "Show animations in Windows" setting). Driver now always runs the full sequence; the parent camera snaps between scenes. | `web/app/src/demo/demo-driver.js`                                           |
| Driver self-paused on its own `document.hidden` — embedded webviews report hidden while visible → demo stalled forever. Removed; the parent owns pause/resume.                                                                                        | `web/app/src/demo/demo-driver.js`                                           |
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
   treats the frame as hidden — see the "visibility level 2" font blocks) —
   reproduce by testing the demo in a real browser tab.
4. Headless Firefox cannot be driven via CDP (it speaks its own RDP); use
   geckodriver/Marionette or manual DevTools for an automated Firefox run.
5. Consider a driver-side watchdog: if the document stays at `loop 0` for
   more than ~2× loop duration without `loop 1` appearing, log a distinct
   marker to distinguish reload loops from hangs.

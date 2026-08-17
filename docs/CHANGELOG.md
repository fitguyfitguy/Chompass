# Changelog

All notable changes to Chompass are documented here.

Style: entries follow the release-text style guide (maintainer-local, not published; user-visible first, no emdashes, no internals). The version section is pasted verbatim onto the Codeberg release.

## [Unreleased]

### Fixed

- **Adding a search result no longer fails on some products** (Android): some products found in the food search could not be added because their code does not pass the standard check digit, even though Open Food Facts lists them. They now add normally. Follow-up to Codeberg [#26](https://codeberg.org/fitguy/Chompass/issues/26) by [@felixbrucker](https://codeberg.org/felixbrucker).

- **Barcode scanning prefers the retail barcode on mixed labels** (Android + PWA): when a label carries both a barcode and a QR or Data Matrix code, the scan now uses the barcode, which is the code the food database lists. Follow-up to Codeberg [#24](https://codeberg.org/fitguy/Chompass/issues/24) by [@felixbrucker](https://codeberg.org/felixbrucker).

## [3.16.1] - 2026-08-16

### Fixed

- **German and Russian labels fit at large font sizes** (Android): compact labels in German are shortened (for example "Mittag" instead of "Mittagessen"), Russian macro status lines use the shorter suffix form, and the macro card's status line shrinks to fit long values. Chips, tabs, buttons, meal slots, and bottom-nav labels no longer truncate or overflow.

## [3.16.0] - 2026-08-16

### Added

- **Reminder notifications open the right tab** (Android): tapping a weight or body-fat reminder now lands on the Progress tab instead of the main page; other reminders still open the main page. Closes Codeberg [#27](https://codeberg.org/fitguy/Chompass/issues/27) by [@DontBlameMe](https://codeberg.org/DontBlameMe).

- **Hide the coach tab** (Android): a new switch under Settings → AI &amp; Speech removes the coach tab from the bottom bar. The coach feature itself stays enabled; a full AI-off switch is planned. Part 1 of Codeberg [#20](https://codeberg.org/fitguy/Chompass/issues/20) by [@HattDroid](https://codeberg.org/HattDroid).

- **The app is now fully translated into German** (Android): the whole German pack is complete, from onboarding and settings to analysis and AI error messages, in one consistent informal voice. Community full-pack translation contributed by [@1260er](https://codeberg.org/1260er).

### Fixed

- **Food analysis no longer gets stuck on "Analyzing..."** (Android): food photos and notes now stop with a clear message when the AI provider stalls instead of finishing, and a result that arrived but never closed is kept instead of being left waiting forever. Closes Codeberg [#25](https://codeberg.org/fitguy/Chompass/issues/25).

- **The window no longer fights the light theme choice** (Android): the window behind the app (splash aside) used to follow the system dark setting even when you picked Light in the app. It now stays neutral and lets the app's own setting decide the look, so toggling dark on the phone can't leave a dark frame around the light app. Follow-up to Codeberg [#28](https://codeberg.org/fitguy/Chompass/issues/28) by [@DontBlameMe](https://codeberg.org/DontBlameMe).

- **Searching for food no longer closes the app while you type** (Android): a food search that is replaced by a new one now stops cleanly instead of piling up background requests, and offline food-database lookups no longer run at the same time, so the search sheet stays stable. Follow-up to Codeberg [#26](https://codeberg.org/fitguy/Chompass/issues/26) by [@felixbrucker](https://codeberg.org/felixbrucker).

- **Barcode scanning keeps trying until it reads the code** (Android + PWA): a half-read or unreadable frame no longer stops the scan with an error; the camera keeps scanning until the full code is read, so products scan more reliably. Follow-up to Codeberg [#24](https://codeberg.org/fitguy/Chompass/issues/24) by [@felixbrucker](https://codeberg.org/felixbrucker).

- **Turning off the "Ask for a Photo Note" setting no longer asks before analyzing** (Android): with the setting off, analyzing a food photo with no note runs straight through instead of asking "continue without a note?" every time. Reported by [@1260er](https://codeberg.org/1260er).

- **Searching for food no longer closes the app on incomplete products** (Android): a search hit missing one of the three macros (some Open Food Facts products only list some) used to crash the results list while it drew, closing the app to the home screen with no message. It now shows a dash for the missing value instead. Follow-up to Codeberg [#26](https://codeberg.org/fitguy/Chompass/issues/26) by [@felixbrucker](https://codeberg.org/felixbrucker).

- **The calorie ring's base marker stays visible** (Android): with Add Active on, the tick where your sedentary budget ends and the activity-earned zone begins no longer disappears once you eat past the base budget. The boundary now draws on top of the eaten fill.

- **Release builds ignore debug-only test commands** (Android): the extras that seed sample data, reset onboarding, or restore a test snapshot now only work in debug builds. On release builds, any app could previously fire them and overwrite your diary with sample data.

- **Malformed links and oversized images no longer crash the app** (Android): a `chompass://` link with an unknown destination is ignored instead of crashing, and sharing a very large image skips it instead of running out of memory.

- **AI results are checked before they are saved** (Android): food values from AI that are missing, negative, or absurd are clamped or dropped instead of landing in your diary as-is.

## [3.15.0] - 2026-08-15

### Added

- **Barcode scanning reads QR and Data Matrix codes** (Android + PWA): the square matrix codes printed on many packages now scan like a barcode, and products found in the Open Food Facts database log the same way. Codes that only carry an internal number are ignored. Follow-up to Codeberg [#24](https://codeberg.org/fitguy/Chompass/issues/24) by [@felixbrucker](https://codeberg.org/felixbrucker).

- **Onboarding explains what goes to your AI provider** (Android + PWA): during setup and under Settings → AI & Speech, the app now spells out which data leaves the device for each provider: food photos and meal notes for food analysis, "What if?" impact with today's diary totals, coach chat with your profile and recent logs, and your profile when AI estimates goals. Only the on-device Gemma 4 models keep everything on your phone; Ollama runs on your own computer. The web app always uses a cloud provider and says so in its onboarding. The Privacy Policy gains a data-sharing table.

### Fixed

- **Barcode scan now says why it failed** (Android): scanning a product that is not in the Open Food Facts database now shows a clear "product not found" message instead of "something went wrong", and temporary Open Food Facts hiccups are retried automatically before giving up. Closes Codeberg [#24](https://codeberg.org/fitguy/Chompass/issues/24) by [@felixbrucker](https://codeberg.org/felixbrucker).

- **Fixing a food's weight no longer changes its nutrition** (Android): for foods that came from other apps (diary import) and have no recorded serving, correcting the weight in the edit screen used to rescale the macros, which were already correct. The weight can now be corrected freely, and only counts as a serving once you set it yourself. Follow-up to Codeberg [#10](https://codeberg.org/fitguy/Chompass/issues/10) by [@felixbrucker](https://codeberg.org/felixbrucker).

- **WebDAV sync no longer crashes on some Huawei phones** (Android): on older Huawei EMUI builds based on Android 10, saving your diary to a WebDAV server stopped with an error and the sync never completed. Closes Codeberg [#23](https://codeberg.org/fitguy/Chompass/issues/23).

- **Plainer English wording** (Android + PWA): dialogs and settings copy reworded for clarity. Other languages are unaffected.

## [3.14.0] - 2026-08-15

### Added

- **Home ring spans your whole expected day** (Android): with *Add Active* on, the calorie ring now runs from zero to your projected daily burn, sedentary goal plus your usual active burn, instead of starting at the base goal in the morning. The goal line reads against that expected total, and the caption shows how much of your usual active burn you have covered so far, turning a different color once you burn more than usual. The calorie widget shows the same expected goal and remaining.

- **Reasoning effort for OpenRouter models** (Android + PWA): a new *Reasoning effort* option under Settings → AI & Speech controls the thinking budget of reasoning-capable models. Auto keeps the app default; Low to High trade speed and token cost for accuracy on hard photos or logs. Closes upstream [#194](https://github.com/apoorvdarshan/fud-ai/issues/194).

- **A separate model for photos** (Android + PWA): *Vision model* under Settings → AI & Speech lets photos use a different model than text, per provider. Leave it on *same as Model* for today's behavior, or set a vision-capable model when your main model is text-only. Closes upstream [#195](https://github.com/apoorvdarshan/fud-ai/issues/195).

- **Fixed launcher icon option** (Android): a new switch in Settings, next to Theme Color, keeps the launcher icon teal and stops it from following the theme color or wallpaper. Useful on launchers that briefly hide or close the app when the icon changes.

### Fixed

- **Morning budget no longer includes a guessed active burn** (Android): with *Add Active* on, the home ring and widget now use the active calories actually recorded so far, zero until your first workout or sync of the day, instead of substituting a whole-day estimate that inflated the budget and then dropped when the first measurement landed. The estimate still applies when no activity source is connected at all.

- **Active calories match the day you're viewing** (Android): with Health Connect connected and *Add Active* on, the home ring's active burn and budget no longer show a previous day's value after switching days or reopening the app. Closes Codeberg [#22](https://codeberg.org/fitguy/Chompass/issues/22) by [@HattDroid](https://codeberg.org/HattDroid).

- **Changing the theme no longer closes the app on some devices** (Android): on some Xiaomi and Samsung launchers, switching dark or light mode or the theme color could drop you back to the home screen when the launcher icon color changed. The icon now updates only while the app is in the background, and a light wallpaper no longer turns the launcher icon gray. Closes Codeberg [#13](https://codeberg.org/fitguy/Chompass/issues/13) by [@armishinwn](https://codeberg.org/armishinwn) and [#21](https://codeberg.org/fitguy/Chompass/issues/21) by [@HattDroid](https://codeberg.org/HattDroid).

## [3.13.0] - 2026-08-14

### Added

- **New default AI model**: photo and text food analysis now uses Gemini 3.7 Flash by default, with better accuracy for the same free-tier cost. Your saved model choice is untouched, and the free fallback stays the high-quota Flash-Lite model.
- **AI model availability hints**: the model picker now flags paid-only models (Pro models need billing on your AI Studio project) and notes when free-tier availability varies by account and region. The same note appears during onboarding.
- **Clearer AI error messages**: when a provider can't find the model you picked, the app now explains that it may be paid-only, region-restricted, or a wrong endpoint, and points to the fix, instead of showing raw provider text. The rate-limit message now points free-tier users at the Flash-Lite model.

- **Custom serving sizes for any food** (Android): tap the pencil next to the serving unit in any food review or edit flow to rename it and set its weight (a 120 g pizza slice can become "big slice" at 180 g). Works from plain grams too: a dish without a suggested unit (a homemade curry, a stew) can get a named serving like "bowl" at 300 g, and the serving survives later name edits. The entry remembers the custom serving, and quantity changes scale from it like before. Available in manual entry, AI photo/text results, saved entries, and ingredient rows.
- **More nutrients in manual entry** (Android): the manual food dialog now records the optional nutrients, not just fiber. Tap *More Nutrition* to expand the full list (sugars, fat types, cholesterol, sodium, potassium, vitamins, minerals, omega-3) and fill in the values you know; they land on the entry like any other logged nutrient.

### Fixed

- **Open food-entry dialogs survive rotation** (Android): rotating the phone no longer closes the in-progress text, voice, camera, or manual entry flow and discards what you typed. Captured photos and the photo review sheet already survived; now the open-sheet state and typed drafts do too.
- **Manual entry dialog scrolls when full** (Android): with *More Nutrition* expanded, the dialog content scrolls within the screen, so Save stays reachable on small screens and at large font sizes.

## [3.12.0] - 2026-08-13

### Added

- **Weather input for the dynamic water goal** (Android): the expected-high temperature behind the +4 %/°C factor can come from two sources: the manual °C wheel (default, unchanged) or Open-Meteo, which finds today's forecast high for a searched city. No account, no key, no location permission; attribution is shown in Settings. When no fresh forecast exists, the manual value is the fallback, so the goal, reminders, and widgets keep working.
- **Body-measurement trend plots** (Android): the Progress tab can now chart how each body measurement changes over time, with one card per site: current value, net change over the selected range, and a trend line. Off by default: enable sites in *Settings → App & Display → Customize progress*, which also hosts the Progress default range. Data comes from Personal Info → Body measurements. Plots follow the 1W to All range chips and the cm/in unit setting. Closes Codeberg [#18](https://codeberg.org/fitguy/Chompass/issues/18) by [@dorian-grosch](https://codeberg.org/dorian-grosch).

### Fixed

- **"What if?" popup scrolls when the suggestion is long** (Android): the dialog now scrolls within a fixed height, so long AI suggestions stay readable on small screens and at large font sizes. Closes Codeberg [#17](https://codeberg.org/fitguy/Chompass/issues/17) by [@dorian-grosch](https://codeberg.org/dorian-grosch).

## [3.11.0] - 2026-08-13

### Added

- **Manual entry with serving quantity** (Android): the manual food dialog now has a *Serving* card with quantity and unit, and the unit is suggested from the food name (pizza suggests slice, bread suggests slice, coffee suggests ml, the same heuristics the AI paths use). The serving is saved on the entry, so the edit sheet reopens with the right quantity, and later quantity changes scale from what you logged. Macros stay exactly what you typed. Untouched entries keep the old shape.
- **Copy and paste diary entries** (Android): long-press one or more food rows to enter selection mode, tap Copy, then tap the Paste chip to add the entries to any day you view. Pasted entries land as new rows in the current meal slot, batched into one write with Health Connect mirroring in the background, same as Copy from Day. The clipboard stays in memory until replaced or dismissed, and you can paste as often as you like. The chip shows a brief *Pasting…* state while the write runs.
- **Copy from Day lets you pick the destination day** (Android): the sheet now shows a *Copy To* row next to *Copy From*, so you can choose where the entries land without leaving the sheet (it still defaults to the day you're viewing). Handy for filling an old day with today's foods, or fixing a misfiled entry.

### Fixed

- **Logging no longer freezes after a quick save** (Android): a stale state could block every log, paste, and edit until the app restarted. All state writes are now atomic, so the race is gone.
- **Relog keeps the food photo** (Android): "Log again" (hub chips, Saved Meals, Copy from Day) now carries the entry's custom image over. The JPEG is copied to the new entry's own file on save, so both rows stay independent, and re-picking or deleting a photo on one entry leaves the other untouched. Closes Codeberg [#12](https://codeberg.org/fitguy/Chompass/issues/12) by [@armishinwn](https://codeberg.org/armishinwn).
- **Weekday initials follow the app language** (Android): the week strip's single-letter day labels come from the locale's narrow weekday names (Spanish: L M X J V S D) instead of hardcoded English M T W T F S S. Closes Codeberg [#15](https://codeberg.org/fitguy/Chompass/issues/15) by [@armishinwn](https://codeberg.org/armishinwn).
- **Widgets roll over to today at midnight** (Android): a background daily alarm just after midnight rewrites the widget snapshot, so a widget left on the home screen no longer shows yesterday's totals until the app happens to refresh it. Closes Codeberg [#16](https://codeberg.org/fitguy/Chompass/issues/16) by [@1260er](https://codeberg.org/1260er).
- **Analyze-food sheet** (Android): no more shaking at the scroll bottom. The same fix that cured the edit-food sheet now applies here. Closes Codeberg [#14](https://codeberg.org/fitguy/Chompass/issues/14) by [@armishinwn](https://codeberg.org/armishinwn).
- **Logging after a restart lands on the day you were viewing** (Android): if the app is killed while the food-analysis, photo, or note sheet is open, the restored sheet now remembers the diary day it was opened for, so an entry meant for yesterday no longer lands on today's log. Closes Codeberg [#16](https://codeberg.org/fitguy/Chompass/issues/16) by [@1260er](https://codeberg.org/1260er).
- **Saved Meals search covers your full history** (Android): searching now also matches foods older than the 30/90-day Recents and Frequent windows, so an old meal is findable again. Re-logging it keeps the original name, so it merges into the same food instead of becoming a "Name (2)".

## [3.10.0] - 2026-08-12

### Added

- **Arithmetic quantity entry** (Android + PWA): serving-quantity fields accept small math: `50×2`, `200−30`, `100÷4` resolve as absolute expressions (`× ÷` bind tighter than `+ −`), while `+20`/`-10` stay relative edits on the current amount. The serving card gains a `+ − × ÷` calculator row with a live `= result` preview; expressions commit to their resolved number on blur/unit change/save (Android collapses deltas immediately, as before). Locale-aware: comma decimals and whitespace parse in both implementations. Closes upstream [#171](https://github.com/apoorvdarshan/fud-ai/issues/171).
- **Custom optional-nutrient goals clamp + vitamin D hint** (Android + PWA): free-form goal values are capped per nutrient (sanity guard; the wheel stays the quick pick), and vitamin D shows a live `mcg ≈ IU` conversion (1 mcg = 40 IU) while entering a custom value, so a 250 mcg / 10,000 IU goal is legible. Closes upstream [#173](https://github.com/apoorvdarshan/fud-ai/issues/173).
- **Water log syncs to Health Connect** (Android): every logged drink is written out as a Health Connect `HydrationRecord` (tagged with the entry's UUID) when water tracking is enabled and the write permission is granted; deleting an entry deletes its record, and reconnecting backfills the whole log. After a reinstall or a new phone, the records Chompass itself wrote are read back (730-day window) and rebuilt into the local water log, recovering the original UUIDs so future deletes still match. The Progress wellness card keeps reading all hydration Health Connect holds: your own records included. Closes Codeberg [#9](https://codeberg.org/fitguy/Chompass/issues/9).
- **Water reminders say how much to drink** (Android): the reminder planner now computes the next-drink amount (*remaining goal ÷ cup size ÷ remaining window*) and the notification tells you: "Drink 300 ml · next in ~90 min". The Home water ring and the water widget show the next planned drink ("Next 18:20 · 300 ml"), and the reminder interval preview in Settings shows the per-cup quantity ("≈ every 90 min · 5 cups · 300 ml each"). The quantity rule is documented in the water register ([`CALCULATION_METHODS.md`](CALCULATION_METHODS.md)).
- **Ukrainian (uk) locale** (Android + PWA): complete 347-key catalog: Chompass's **16th language** (Android `values-uk` + PWA `uk.js`, both sides of the shared locale contract).

### Changed

- **Settings reorganized into per-domain screens** (Android): new **Food & Entry**, **Water**, **Notifications** and **Sync** sub-screens; App & Display shrinks from up to 24 inline rows to 7 static ones that deep-link to the domains; AI & Speech keeps provider wiring only. Settings become a **connected graph**: read-only cross-link rows navigate between related screens (Goals ↔ Water, Water ↔ Notifications, AI → Food & Entry, Data → Sync) with a `from` nav argument so the back label retraces the path; dependencies are shown disabled with a link instead of hidden (the water-reminder toggle stays visible). A new **Suggestions** card on the hub shows up to 3 dismissible nudges (water tracking, reminders, Adaptive Goals, Health Connect, notifications, WebDAV backup) gated by install age: rows only navigate, nothing is auto-enabled. Consistency pass: *Auto* chip replaces the lock glyph, shared footnote and related-links composables, danger-zone separation for Clear Food Log / Delete All Data. **No behavior, storage, or formula changes to existing settings keys.** Plan and execution log: [`archive/SETTINGS_OVERHAUL_PLAN.md`](archive/SETTINGS_OVERHAUL_PLAN.md).
- **Localization hygiene** (Android + PWA): 1,551 verbatim English copies removed from the 15 locale packs (Russian copies translated, neutral formats and brands dropped: the Android string check now fails on phrase-level EN-identical values); hardcoded UI text moved to `strings.xml` (native speech errors, camera flash labels, WebDAV/sync messages, on-device download failure, AI provider errors); PWA catalogs gained 63 keys and the i18n test now asserts no EN copies. The onboarding tagline was rewritten to plain language ("Track meals, stay balanced") and re-translated.

### Fixed

- **Fallback AI provider keeps its own base URL and API key** (Android): main and fallback slots no longer share one stored URL/key per provider: a fallback that reuses the primary provider (e.g. a second OpenAI-compatible endpoint with a different model) keeps its own endpoint and key instead of silently inheriting the primary's after restart. Fallback-slot values live under separate keys (`customBaseURL_fallback_*`, `apikey_fallback_*`); existing primary values are untouched. Users with a same-provider main+fallback should re-enter the fallback URL/key once.

## [3.9.0] - 2026-08-11

### Added

- **Dynamic water goal + adaptive reminders** (Android, **Beta**, opt-in, default off): the daily water goal adapts to body weight (35 ml/kg), the expected high temperature (manual entry, no location permission), profile activity level, and optionally subtracts water from food (coarse 60 % of diary grams, capped 1 L). The water reminder becomes a **drinking window + cup size plan** (defaults 08:00–21:00, 300 ml): reminders fire at an interval of *remaining goal ÷ cup ÷ remaining window* (e.g. 2,500 ml ÷ 300 ml over 13 h → about every 90 min), **recalculated after every entry** so logging a glass immediately re-paces the next reminder; goal reached or past the window end silences the chain until the next morning. Home water ring shows the computed goal with a tappable *auto* badge (jumps to Settings); the widget goal follows the calculator. Shipped with a **medical disclaimer** (estimate, not advice; consult a doctor if you have a health condition, and always drink enough even when the app does not remind you) in Settings next to the feature, in the Safety &amp; Medical notices, and in the onboarding safety card. Docs: [`WATER_DYNAMIC_GOAL_DESIGN.md`](WATER_DYNAMIC_GOAL_DESIGN.md) + WATER-DYN-A/B/C in [`CALCULATION_METHODS.md`](CALCULATION_METHODS.md) (science-backed audit trail: EFSA 2010 / IOM 2004 AIs, ACSM 2007 exercise sweat rates, 19–30 % food-moisture share). Closes Codeberg [#3](https://codeberg.org/fitguy/Chompass/issues/3) by [@1260er](https://codeberg.org/1260er).
- **Pick the emoji or photo shown for a food entry** (Android): tap the hero icon in the edit-food sheet to open a picker: 44 food emojis in a grid, a *Set photo* action (system photo picker), and *Remove photo*; photos are stored under the entry's own filename so re-picking overwrites in place without orphans. Closes Codeberg [#5](https://codeberg.org/fitguy/Chompass/issues/5) by [@armishinwn](https://codeberg.org/armishinwn).
- **Custom AI endpoints trust phone-installed CA certificates** (Android): self-hosted OpenAI-compatible servers are reachable again in release builds: cleartext LAN (`Cleartext is not allowed`) and self-signed HTTPS with the root CA installed on the phone (`Trust anchor for certification path not found`) both work. Cloud providers, OLLAMA loopback, WebDAV and STT keep the platform default, so a user-installed CA can never intercept cloud AI traffic. Closes Codeberg [#8](https://codeberg.org/fitguy/Chompass/issues/8) by [@darkxylese](https://codeberg.org/darkxylese).
- **Auto-capitalized AI food names** (Android): AI-generated ingredient and meal names are now title-cased ("grilled chicken breast with rice" → *Grilled Chicken Breast with Rice*, "hähnchen mit reis" → *Hähnchen mit Reis*) so they look consistent with manually entered foods; connector words (with/and/mit…) stay lowercase, and acronyms, brands and numbers are untouched. Closes Codeberg [#7](https://codeberg.org/fitguy/Chompass/issues/7) by [@Professional-Human](https://codeberg.org/Professional-Human).

### Changed

- **Cheaper AI coaching** (Android): the coach system prompt now uses Anthropic prompt caching: the byte-stable prefix (persona, tool guidance, profile, formulas) carries `cache_control: ephemeral`, while only the per-day tail (date, forecast, entry counts) re-sends, cutting BYOK token spend on every coach turn. Closes Codeberg [#1](https://codeberg.org/fitguy/Chompass/issues/1) by [@ILoveCats17](https://codeberg.org/ILoveCats17).
- **Resilient Open Food Facts search** (Android): transient backend failures (503 / non-JSON / network) retry per candidate with backoff, and failures no longer end the search: the query walks a candidate chain (full → without the separately-picked brand → drop the brand-ish first token → drop the last token), so "Aldi Laugen" still surfaces Laugen products when the AND query misses or hiccups; one empty-but-valid response is retried once before shortening (OFF intermittently answers empty then hit). Search cards show the plain product name with the brand as a separate field, no more doubled brand ("Aldi Aldi …"). Closes Codeberg [#4](https://codeberg.org/fitguy/Chompass/issues/4) by [@vincentmathis](https://codeberg.org/vincentmathis).

### Fixed

- **Stored food photos keep their orientation** (Android): EXIF rotation is baked into the stored JPEG, so camera/gallery photos no longer appear sideways.
- **Edit-food sheet** (Android): no more shaking or frozen list: the overscroll no-op now forwards scroll deltas like foundation's own, and the sheet zeroes the content-window insets, so drags scroll the content instead of bouncing the sheet. Closes Codeberg [#6](https://codeberg.org/fitguy/Chompass/issues/6) by [@armishinwn](https://codeberg.org/armishinwn).
- **Water quick-log presets sheet** (Android): up to 5 wheel pickers no longer push Save off-screen on small screens or at large font scale: wheels scroll within the remaining height, and add/remove + Save stay pinned. Closes Codeberg [#2](https://codeberg.org/fitguy/Chompass/issues/2) by [@1260er](https://codeberg.org/1260er).
- **Search food sheet** (Android): no more "The coroutine scope left the composition" error when deleting typed text mid-search: cancelled in-flight searches no longer write results or errors, clearing the field invalidates any pending search, and a failing source (e.g. one offline DB) can no longer hide the other sources' hits.

## [3.8.0] - 2026-08-07

### Added

- **Calorie hero upgrades** (Android): the Home ring now shows an **activity-earned tail** and an **active-burn arc** with ramp progress toward a typical day's burn, and a new info button opens a **calorie budget explanation dialog** that shows how today's budget is composed (also reachable from the diary gauge).
- **Live demo hero: mock scanning viewfinders** (web): the barcode beat now plays a drawn EAN-style code under a sweeping laser line in the real viewfinder before the Open Food Facts product card lands, and a new **plate-scan beat** frames a drawn salmon-and-rice plate, captures it, and streams the scripted analysis (macros fill in live, review-first, ring rise). Both feeds are pure CSS/SVG inside the demo shell, no camera and no video file, and they pause with the hero and freeze under reduced motion.
- **Website live hero demo upgrades**: wide screens get a **split stage** (the app on the left (phone-proportioned 620×1330 canvas, bigger than before), a live per-scene description panel on the right; the canvas is no longer an 826px-wide stretched layout, so the app looks like a real phone screen. The full-phone frame now appears **once per page load** (intro) instead of every loop, the home route renders immediately and seeds the demo database in the background so the stage fades in on a painted app screen (no blank first paint), and the AI-analysis beat **quick-cuts** to the analysis overlay (short camera duration instead of panning across the dark full-screen overlay) and then crops the final-size partial card, eliminating the dark "blank screens" during streaming. Scenes whose targets appear late are re-resolved by the camera (sheets no longer flash into an unreadable frame), tall review sheets are framed top-first, and the Progress beat is now a **warp-speed weight sequence**: close-up on noisy daily weigh-ins that rapidly expands 1M → 3M → 6M → 1Y → All across 2 years of history, ending on the current/goal/net stats and the weight-forecast card. The PWA source is now a **Hugo static mount** so the dev server never serves a stale `public/app/`, and opening the built page from `file://` shows a "needs a web server" hint instead of blocked-resource security errors.
- **Enter custom value** for every wheel-picked goal (calories, macros, keto net carbs, optional nutrients): type any non-negative number instead of scrolling the preset range: e.g. a 10,000 IU (250 mcg) vitamin D goal (Android).
- Serving-quantity fields accept **relative edits**: `+20` adds 20 to the current amount, `-10` subtracts (results at or below zero are ignored), in any unit: grams, slice, cup (Android).
- Add Food **Search food** sheet (Android): type to search **Open Food Facts** (live), **USDA FoodData Central** (offline), and the **Swiss Food Composition Database** (offline, en/de/fr/it names) with per-source chips and a provenance badge on every hit. Picking a hit prefills the review sheet with full micronutrients and the source stamped on the diary entry (`search` source; `grounding.sourceKind` = `usda` / `openFoodFacts` / `swiss`).
- Offline food databases now ship in all Android builds (~5.3 MB): the USDA Foundation+FNDDS SQLite (was debug-only) and a new Swiss SQLite built from the federal naehrwertdaten.ch CSVs (`scripts/build_swiss_food_index.py`). Grounded entry stays disabled.

- **Now on F-Droid:** `app.chompass` listing is live: install and auto-update through the F-Droid client ([f-droid.org/packages/app.chompass](https://f-droid.org/packages/app.chompass/)). Website and README download options updated (F-Droid first).

### Changed

- **Faster food entry** (Android): the review sheet dismisses right after the local commit: the diary row and cleared pending draft commit in one DataStore edit and the Health Connect mirror runs in the background; progressive-meal **Log meal**, **Copy From Day**, and recipe logging batch into a single DataStore edit instead of one full-file write per row. Hub recents/frequents read the diary snapshot once, aggregate off the main thread, prefetch on the Add Food tap, and cache per day.

### Fixed

- **ADD_ACTIVE gauge base** (Android): with **Energy Burn Goals** on, the sedentary base split and fallback now use the measured Health Connect active average instead of the PAL estimate (`tdee − bmr`), so the Home ring and widget budget converge to the stored goal on a typical day instead of a deflated value (e.g. 1448 instead of ~1903).

## [3.7.0] - 2026-08-03

### Added

- Photo accuracy tip card on early photo analyses, with dismissible guidance (Android).
- Settings toggle **Ask for a photo note**: highlight the note before Analyze; after repeated empty skips, offer to stop asking (Android).
- Dedicated onboarding **Before you start** disclaimers step (Android).
- PWA progressive meal draft and manual active burn logging (parity with Android).

### Changed

- Photo entry uses a lightweight pre-Analyze staging sheet (note, optional label/extra photos), then morphs into the Log sheet with a ready-gate; mid-flight tip or add-photo can re-analyze (Android).
- Confirm before analyzing with an empty note or fewer than two photos (Android).
- Onboarding AI setup: recommend Google AI Studio; skip AI with confirmation (Android + PWA).
- Background Health Connect sync is feature-gated (`READ_HEALTH_DATA_IN_BACKGROUND`) and requests that permission when enabled; history read is requested on connect when the module supports it (Android).
- Native speech recognition resolves usable `RecognitionService` packages more reliably (including third-party engines) and surfaces a clear error when none is available in the profile (Android).
- README / PRIVACY / F-Droid notes: APK vs framework HC, no sandboxed-Play requirement, file import fallback on de-Googled ROMs.

### Fixed

- Meal-header summaries on Home now show real combined fiber (Fi) and sugar (S) values when those chips are enabled, instead of 0 (Android + PWA).
- Health Connect availability copy no longer tells Android 14+ users to install the Play Store HC APK; status-aware messages cover update-required vs unavailable, with Open Health Connect / Play Store actions where appropriate (Android).

## [3.6.2] - 2026-08-01

### Fixed

- In-app Photo → Gallery uses an Activity-registered Photo Picker and app-scoped `FoodPhotoSession`, so picks open the multi-photo review sheet instead of dropping to Home (Android). Gallery no longer writes the share-image inbox.
- Notification taps target the enabled launcher icon alias (same as share/shortcuts), avoiding a second `MainActivity` that can steal entry results (Android).

## [3.6.1] - 2026-08-01

### Changed

- Add Food “Log again” chips prefer foods that match the current meal slot, then soft-boost favorites within that meal (Android + PWA).
- Ingredient-row macros use themed kcal/P/C/F colors; AI progressive analysis shows clearer nutrition rows (Android).
- Refreshed launcher and branding icons (new mark across densities; website logo and OG image).

### Fixed

- Gallery food-photo pick opens the multi-photo review sheet again instead of silently returning to Home (Android).
- Launcher Camera / Voice / Barcode shortcuts target the enabled launcher icon alias (same as share), so image capture and gallery pick no longer die on the Home screen after selecting a photo (Android).
- Launcher Voice (and Barcode) shortcuts keep their destination in the sticky inbox until the sheet dismisses, and System theme no longer remounts the whole UI on every resume: so Voice opens instead of dropping to Home (Android).

## [3.6.0] - 2026-08-01

### Added

- Protein goal modes: grams/day, g/kg body weight, or g/kg lean mass (Android Settings + PWA); rate modes update daily grams when weight or body fat changes.
- Manual active burn log from Add Food (Android): name + kcal merges into today’s ADD_ACTIVE budget (with Health Connect or activity-level estimate).
- Add Food “Log again” chips prefer favorites, then recents, then frequent (Android + PWA), with empty-state guidance.
- Progress chart default range setting, with last-viewed range remembered (Android + PWA).
- Display-only 7-day moving-average weight trend on Progress charts (Android + PWA); not used by Adaptive Goals.

### Changed

- Food review sheets put name, serving, macros, and meal first; portion check, ingredients, micros, and What-if sit below (Android + PWA).
- Clearer ADD_ACTIVE calorie-mode copy on Home and Home Display settings; waiting hint when burn is still zero.
- Share or pick up to 10 food photos into the multi-photo review sheet (Android; was capped at 2 for share-ins).
- PWA desktop home hero (≥900px): week-strip day arrows and horizontal calorie/macro bars; mobile keeps the semicircle gauge and vertical tubes.

### Fixed

- Sharing images into Chompass while launched from a launcher shortcut no longer drops the inbox when Home is stopped (Android).
- Gallery multi-photo import survives activity recreation more reliably (Android).

## [3.5.1] - 2026-08-01

### Added

- Health and accuracy disclaimers on onboarding plan-ready (Android + PWA): not medical advice; photo estimates and vague portion labels are often wrong; AI/LLM output is estimates only.
- Accuracy note under Android Settings → Safety & Medical.

### Changed

- PWA onboarding is fully localized across the shared 15-locale set (steps, choices, AI setup, plan-ready), not only welcome/CTA buttons.

## [3.5.0] - 2026-07-31

### Added

- Opt-in WebDAV **Sync on open** (Android + PWA): once per local day when the app is opened; off by default. Manual Sync now unchanged.
- Android launcher long-press shortcuts for Camera, Voice, and Barcode food logging.
- Meal constituents + g/unit (#154): composite AI meals can return editable `constituents[]` with per-row serving units; bounded client reconcile keeps row totals aligned with the meal. Diary **1.2**, sync **1.1**, and meal-share **v2** round-trip serving units and constituents (older versions still import).
- Settings toggle **Meal ingredient breakdown** (Android + PWA): opt out of AI `constituents[]`. Always off for on-device Gemma; on-device also skips the extra AI serving-unit call and uses heuristics instead.

### Changed

- Food review sheets (Android + PWA) show grouped ingredient rows: scale, edit, add, or remove constituents; meal macros follow the rows.
- Adaptive launcher icons (API 26+) with themed backgrounds.

### Fixed

- Clearer error when Gemini rejects the request because the network location is unsupported (VPN / region guidance).

## [3.4.0] - 2026-07-31

### Added

- Streaming AI food analysis: calories and macros fill in as the provider responds (Android and PWA when SSE is available).
- Ask AI to correct on a logged entry: quick chips, note, before/after field diff, then Save (Android and PWA).
- Quick context chips on photo review (No oil, Extra cheese, Large portion, Grilled).
- Shared 15-locale UI contract: PWA Language setting with core-surface catalogs; Android locale-aware date/number formatting.

### Changed

- Modal bottom sheets stay open while a busy operation is in progress (no accidental drag-dismiss).
- Photo context and edit-entry copy clarified (“Tell AI what this is”, “Ask AI to correct”).

## [3.3.3] - 2026-07-31

### Fixed

- WebDAV second sync no longer fails with "conflict persisted": `If-None-Match: *` is only used when creating the remote file; updates without a usable ETag overwrite after merge (and weak `W/` ETags are normalized for `If-Match`).

## [3.3.2] - 2026-07-31

### Fixed

- WebDAV Basic auth now uses UTF-8 (matching curl), so passwords with characters like ß or § work against hosts such as Hetzner Storage Box.

## [3.3.1] - 2026-07-31

### Added

- Progressive meal draft on Android: weigh and analyze ingredients one at a time, then log the combined meal (or add another item).
- Optional confirmed total portion weight during food entry clarification, so analysis can use a ground-truth gram amount.
- Desktop PWA layout at 900px+: left nav rail, wider content column, and centered sheets.
- PWA update toast to reload onto a new service-worker version without silently swapping mid-session (IndexedDB data kept).

### Fixed

- WebDAV sync URL normalization on Android and the PWA: missing scheme defaults to HTTPS; stacked schemes (e.g. `https://https://…`) are collapsed. Clearer URL hint for storage-box style hosts.

### Changed

- Food-logging accuracy docs and blog post updated with tiered input methods and revised benchmark metrics (maintainer tooling only; grounded entry remains off in shipping builds).

## [3.3.0] - 2026-07-29

### Changed

- Settings uses a compact hub with drill-down groups on Android and the PWA (Personal, Goals & Nutrition, App & Display, AI & Speech, Health/Data/Sync, About) for easier overview.

## [3.2.0] - 2026-07-28

### Added

- Optional user-hosted WebDAV sync between Android and the PWA (manual **Sync now**; API keys and food photos excluded).
- Photo food entry can decode a visible barcode and enrich AI analysis with Open Food Facts product context (Android and PWA).

## [3.1.3] - 2026-07-28

### Fixed

- Editing grams on a newly logged food (photo/text/voice review) now correctly rescales calories/protein/carbs/fat when saved: previously the on-screen preview updated live but the persisted diary entry kept the macros from the original serving size.
- The nutrition lock/unlock toggle on an already-saved diary entry now covers all macros and micronutrients (calories, protein, carbs, fat, fiber, and the "More Nutrition" fields), matching the new-entry review sheet. Previously only fiber was editable there.

## [3.1.2] - 2026-07-26

### Fixed

- Diary food-row swipe favorite and delete actions now use width-relative triggers, so they stay reachable on more screen sizes.

### Changed

- Maintainer tooling: ktlint in devenv, git commit-msg hooks, and F-Droid inclusion MR submit script targeting the existing fdroiddata MR.

## [3.1.1] - 2026-07-26

### Added

- Camera scale tip during food photo capture to help estimate portions more accurately (dismissible; remembered).

### Changed

- Clearer Active calorie / Activity Level copy (Health Connect, settings, PWA hint, calculation docs): Activity Level stays the everyday baseline when Add Active is on.

## [3.1.0] - 2026-07-25

### Added

- Opt-in **Portion size check (Beta)** for photo food entries: when the estimate looks uncertain, a Small / Regular / Large / Restaurant-size chip row appears; answering re-analyzes with that context (default off in Settings).
- Onboarding draft persistence so leaving mid-setup can resume later (Android).
- Accuracy documentation (`docs/ACCURACY.md`) and site copy explaining typed vs photo AI logging performance.

### Fixed

- Diary JSON import accepts legacy format version `1.0` (macros-only) as well as `1.1`, so older Fud AI / early NoFUD exports restore in Chompass.

### Changed

- PWA onboarding pace UI and service-worker shell cache bump; marketing site header/nav responsiveness and lightweight site shell on pages.

## [3.0.0] - 2026-07-24

**NoFUD is now Chompass** (chompass.app). New name, new fork-compass logo, same app, same maintainer, same license.

### Migration from NoFUD

- **Android:** the application ID changed from `org.codeberg.fitguy.nofud` to `app.chompass`, so Chompass installs as a _new app_. In NoFUD: Settings → Export (diary JSON + body metrics JSON). Install Chompass, then Settings → Import both files. Old NoFUD export files import unchanged. Uninstall NoFUD when done.
- **PWA:** the web app moved to `https://chompass.app/app/`. Browser storage does not carry across domains: export from the old PWA, import at the new address, then remove the old installation.
- Old `nofud://add-meal` and upstream `fudai://` share links continue to open in Chompass.

### Changed

- Application ID / package: `app.chompass`; project, themes, and resources renamed accordingly.
- New fork-compass launcher icon, PWA icons, and website logo (all 18 theme variants regenerated).
- Website and PWA hosted at `https://chompass.app/` (Codeberg Pages custom domain); Codeberg repo renamed to `fitguy/chompass`.
- Release APKs are now named `Chompass-fdroid-<version>*.apk`.
- Diary / body-metrics exports stamp `"app": "Chompass"`; importers on both platforms accept `chompass`, `nofud`, and `fud ai`.
- Primary meal-share deep link scheme is `chompass://` (`nofud://` and `fudai://` remain accepted for import).

## [2.0.0] - 2026-07-23

Major release: ships the **companion PWA** alongside Android, with shared export/formula contracts gated in release packaging. Android daily-driver UX is largely continuous with 1.14.x; Health Connect, widgets, notifications, on-device LLM, and full i18n remain Android-only. Grounded food entry stays WIP and disabled.

### Added

- Companion PWA at `chompass.app/app/`: diary, progress charts, manual/barcode/AI food entry, saved meals/recipes, copy-from-day / meal share, BYOK AI Coach, settings, and onboarding (data-compatible JSON with Android).
- Cross-app parity fixtures and JSON Schemas (`testdata/parity/`, `contracts/`) plus `release:check-parity` (PWA tests, typecheck, schema validation), also run inside `release:package`.
- AI API key validation during onboarding (Android and PWA).

### Changed

- Codeberg Pages deploy rsyncs the PWA into `/Chompass/app/` with the marketing site (`deploy_pages.sh` / `publish_release.sh`).
- Release asset management and distribution docs aligned with Codeberg quota policy (latest release, universal APK only).

## [1.14.10] - 2026-07-22

### Changed

- Regenerated launcher icons and in-app logos; default teal accent uses a deeper green-teal (`#006B5E`).

### Fixed

- About and Health Connect privacy / asset-credit links point at `docs/` paths after the maintainer-docs move.

## [1.14.9] - 2026-07-22

### Fixed

- Streak meal reminder no longer fires when today’s food diary already has entries (upstream #150).
- Copy-from-day stamps new entries with the current time and current meal instead of the source entry’s clock/meal (upstream #149).

## [1.14.8] - 2026-07-22

### Fixed

- Diary JSON import accepts format version 1.1 and restores micronutrients (Fud AI / Chompass exports after 1.14.7).

## [1.14.7] - 2026-07-22

### Added

- Activity level picker subtitles now include approximate daily step guides (upstream #141/#132).
- Diary export (JSON / CSV / Markdown) includes all stored micronutrients; export format version 1.1.
- Add-food sheet opens Recents, Frequent, or Favorites directly (upstream reuse-meal menu split).
- Health Connect privacy rationale activity for API ≤33 discovery (`HealthPermissionsRationaleActivity`).

### Changed

- Saved Meals Recents limited to last 30 days; Frequent to last 90 days (upstream rolling windows).
- AI read timeout: 30–600 s range; default 180 s applies to Ollama/Custom only (`AiHttp.clientForProvider`).
- Clear food log prunes orphaned image files instead of wiping the entire image cache.
- AI fallback provider is enabled by default for new installs / unset preference.
- Gemini model list updated (`gemini-3.6-flash`, `gemini-3.5-flash-lite`); Gemini fallback default is `gemini-3.5-flash-lite`; Gemini speech default is `gemini-3.6-flash`.
- Removed bundled exercise / muscle image assets (smaller APK).

### Fixed

- Saved Meals Recents / Frequent / Favorites no longer treat different servings of the same food as separate items. Re-logging with new grams, pieces, or units updates the template instead of stacking duplicates.
- Brand-new foods (scan, AI, manual, coach) that would collide on name are auto-renamed (`Name (2)`, …) so accidental collisions stay distinct from intentional re-logs.
- Logging from the review sheet no longer double-applies serving scale (could inflate calories when changing portion size).
- Anthropic responses with thinking blocks no longer fail parsing (#139).
- OpenRouter/OpenAI truncated or reasoning-only responses retry once with compact settings (#145).
- Ollama over HTTP on a LAN address works (cleartext permitted for user-supplied local endpoints).
- Orphaned food photo JPEGs from older builds are pruned safely at startup and after log edits.

## [1.14.6] - 2026-07-20

### Fixed

- Water tracking shows fl oz when using imperial units (home, widgets, add-food flows).
- AI API keys are trimmed on save and in request headers (fixes auth failures from pasted trailing newlines).
- Configurable AI read timeout in Settings (30–300 s, default 60 s).
- Max AI response tokens clamped to 256–8192.
- In-app camera preview matches the captured photo framing.
- Settings weekly goal pace shows correct lbs values in imperial mode.
- Undo snackbar after swipe-deleting a food entry.
- Home screen widgets time out stale DataStore reads instead of hanging on the loading spinner.
- Food log save finishes before clearing the draft (more durable if the app is killed mid-save).
- Less accidental day swipes and swipe-to-delete (higher gesture thresholds).

## [1.14.5] - 2026-07-20

### Changed

- Replace proprietary ML Kit barcode scanning with FOSS zxing-cpp (Apache-2.0). F-Droid and Codeberg builds now share the same on-device scanner and barcode tile.

## [1.14.4] - 2026-07-20

### Fixed

- F-Droid packaging: `-PreleaseAbi=arm64-v8a` now uses `ndk.abiFilters` with ABI splits disabled, so the APK is the plain `app-release-unsigned.apk` name (avoids F-Droid `output:` / “Failed to find any output apks”, and keeps native libs consistent for the scanner).

## [1.14.3] - 2026-07-20

### Fixed

- F-Droid build compatibility: remove Gradle foojay-resolver plugin (flagged by fdroid scanner); make ML Kit barcode optional via `-Pnofud.barcodeMlkit=false` (F-Droid builds hide the barcode tile).

## [1.14.2] - 2026-07-15

### Fixed

- On-device AI: the 1.14.1 memory guard for E4B photo analysis was accidentally blocking E2B photo analysis too. The preflight memory check and CPU/GPU backend split now only apply to E4B. E2B photo analysis works as before.

## [1.14.1] - 2026-07-15

### Fixed

- On-device AI: E4B photo analysis now runs text on CPU and vision on GPU. A memory preflight check shows an in-app message instead of letting the OS kill the app when free memory is too low. On-device images are downscaled to 1024px before vision inference.

## [1.14.0] - 2026-07-15

### Added

- **On-device AI (opt-in):** Settings → AI Provider → **On-Device (Private)** runs food text and photo analysis locally via Gemma 4 (E2B or E4B). Download the model once from Hugging Face (~2.4–3.4 GB); nothing you log is sent to a server. Automatic cloud fallback when Fallback Provider is enabled.
- Settings explains that on-device models are much smaller than cloud AI (Gemini, GPT, Claude, etc.) and often misread portions, brands, and photos.

### Changed

- On-device provider is now shown on supported devices (arm64/x86_64, 6 GB+ RAM).

## [1.13.0] - 2026-07-15

### Added

- Internal prep for on-device AI food analysis (Gemma 4 E2B-it). Runs fully on-device with automatic cloud fallback. Not enabled for any users yet; still behind an internal rollout flag until a second device is tested.

## [1.12.0] - 2026-07-14

### Added

- Recipes: multi-ingredient saved meals, created and edited via a dedicated recipe builder, with one-tap logging of every ingredient as its own diary entry
- Coach can propose logging food, weight, or water entries from chat; you confirm or discard before anything is saved
- Barcode lookup caching for faster repeat scans, including offline

### Changed

- Settings: removed the "What's New" section from About (changelog notes live on Codeberg releases instead)
- Food analysis prompts now prefer non-gram serving units where appropriate
- Wheel picker feedback matches Material3

## [1.11.0] - 2026-07-14

### Added

- Optional water tracking (off by default): quick-log from Home, daily goal, reminders, and a home-screen widget; stored locally only
- Customizable meal time boundaries in Settings (defaults match previous automatic breakfast/lunch/dinner/snack windows)
- Multi-photo meal capture: add up to 10 photos from camera or gallery before AI analysis
- Health Connect **Manage access** entry in Settings and onboarding to review permissions on Android 14+
- Configurable water quick-log presets for the Add food slider (ml or fl oz when using imperial units)

### Changed

- Home macro cards show grams remaining or over goal instead of a static goal subtitle
- Widget gauge labels scale down for long values so numbers do not crowd the ring
- Add food sheet: compact water slider (replaces large water tile grid)

## [1.10.0] - 2026-07-14

### Added

- Live progress while AI analyzes a food entry (preparing request, calling AI, reading result, inferring serving units)
- Fallback AI provider: when the primary provider fails (overload, rate limit, network), Chompass retries automatically with a configured fallback model

### Changed

- Home calorie gauge simplified: removed Net and Dual display modes; active calories use simpler labels in Static and Add Active modes
- Food photos downscaled before upload to AI providers (smaller payloads, faster analysis)

## [1.9.0] - 2026-07-14

### Added

- Serving unit inference settings (Settings → Food logging): choose grams-only, heuristic, or AI-inferred units, with customizable grams-per-unit heuristics per food category
- Loading indicators and disabled submit buttons while food entries are being saved, preventing duplicate submissions

### Changed

- Food diary stored in monthly buckets for faster add, update, and delete on large histories
- Home screen and food-entry code reorganized for faster UI
- Preferences and Health Connect code split into focused modules (no user-facing behavior change)

## [1.8.0] - 2026-07-09

### Added

- Import weight and body data from a file (Settings → Health &amp; Data): Chompass JSON/CSV exports, [openScale](https://github.com/oliexdev/openScale) CSV, and generic weight CSVs (MyFitnessPal / SparkyFitness style, kg or lb). Re-importing the same file is idempotent and never duplicates manual entries
- Export now covers weight, body-fat **and** body-measurement history, in either CSV or JSON
- Wellness card on the Progress tab: sleep, resting heart rate and hydration read from Health Connect (new Sleep, Resting Heart Rate and Hydration read permissions)
- Height now syncs to Health Connect (new Height write permission), so scales and other apps can use it
- Optional background sync (Settings → Health &amp; Data, **off by default**): checks Health Connect for new data every few hours even when Chompass is closed
- Nutrition calculation audit documentation ([`CALCULATION_METHODS.md`](CALCULATION_METHODS.md)) with formula register, scientific policy decisions, and release checklist
- Unit tests for BMR/TDEE, macro goals, keto carb heuristics, weight forecast, adaptive goals, and body-composition estimates
- Calculation Methods UI sections for weight forecast, adaptive goals, and tape-measure body metrics
- Golden scenario tests (`CalculationGoldenScenariosTest`) and shared `GoalFormulaReference` for AI prompt parity
- **System** accent theme (Android 12+): follows the device wallpaper / Material You palette; now the default in Settings → Appearance
- Dynamic launcher icon that matches your selected accent color
- Food entry thumbnails load off the UI thread; orphaned photos are removed when entries or favorites are deleted

### Changed

- Unified energy-balance constant to **7,700 kcal/kg** across goal pacing, forecasts, adaptive goals, and AI prompts (was 7,000 in goal math only; ~10% underestimate at 0.5 kg/week pace)
- Weight forecast uses **calendar-day intake averaging** when fewer than 50% of lookback days have food logs
- Observed weight trend now uses **Theil–Sen** robust regression instead of ordinary least squares
- AI goal prompts pull multiplier/protein constants from shared `GoalFormulaReference`
- Home calorie gauge **Add Active** and **Dual** modes now use your activity-level estimate (TDEE minus BMR) when Health Connect is unavailable; **Add Active** no longer double-counts activity when Health Connect is on (goal is split into sedentary base + today's burn)
- Home calorie gauge shows whether today's active burn is measured (Health Connect) or estimated, with breakdown labels and screen-reader text
- About screen attribution updated to Chompass by fitguy (fork of Fud AI)

## [1.7.0] - 2026-07-09

### Added

- Share photos into Chompass from the camera or gallery (system share sheet) to start an image food entry: up to two images, composed side-by-side like dual capture
- Activity card on the Progress tab: daily steps and exercise minutes from Health Connect (new Steps + Exercise read permissions; wearables via Gadgetbridge, Samsung Health, etc.)
- Live import of meals other apps log to Health Connect (incremental, deduplicated; own records are never echoed back)
- Health-ecosystem compatibility notes in README and Settings (Gadgetbridge, openScale, Samsung Health, Fitbit; all via Health Connect, no vendor SDKs)

### Fixed

- The floating "+" add button no longer sits underneath the bottom navigation bar
- Keto diet mode now reaches the AI goal calculation and meal advice prompts (previously only the Coach chat knew about it, so AI goals could contradict the app's keto carb target)
- AI responses (food names, coach replies, advice) follow the app's language instead of always answering in English

## [1.6.0] - 2026-07-08

### Added

- Optional glass blur effect with a settings toggle for frosted UI surfaces

### Changed

- Macro nutrient chips and color palette use Material theme colors across home and detail views
- Text input sheets use `FudGlassTextField` for the same glass styling
- UI components use `MaterialTheme` colors for consistent light/dark mode
- Release APK size cuts (debug symbols, native lib packaging, dependency metadata exclusions) for F-Droid and IzzyOnDroid compliance

## [1.5.1] - 2026-07-08

### Changed

- Publish both `play` and `fdroid` flavor APK assets on Codeberg releases (with `Chompass-play-*` and `Chompass-fdroid-*` filenames).

## [1.5.0] - 2026-07-08

### Added

- Bulk diary import for larger food-log datasets in one pass

### Changed

- Barcode scanning updates and smaller release APKs

### Fixed

- Import errors show plain messages during migration

## [1.4.0] - 2026-07-08

### Added

- Migration and export flow fixes

### Changed

- README install notes updated, including architecture-aware APK selection
- README feature list and package-size notes updated

## [1.3.0] - 2026-07-08

### Added

- Multi-architecture Android release packaging with dedicated APKs for `arm64-v8a`, `armeabi-v7a`, and `x86_64`
- Universal APK kept for users who want one download

### Changed

- Build/release pipeline emits ABI-targeted APKs for more devices
- Onboarding logo updates when the theme changes
- Docs updated for Android development and performance workflows

## [1.2.0] - 2026-07-08

### Added

- Android performance baseline capture workflow (`scripts/capture_android_perf_baseline.sh`, `docs/PERFORMANCE.md`)
- Pending food-input draft persistence to recover interrupted logging sessions
- Diet mode and keto-carb configuration support across onboarding, settings, and profile models

### Changed

- Progress charts use phased animations and loading states while data loads
- Progress data processing reorganized to reduce UI jank
- Home screen theming, shadows, and meal-section nutrient styling updated
- App/icon activity-alias theming fixed; install/distribution docs updated

### Fixed

- State restoration fixes in home/progress flows so in-flight input is not lost

## [1.1.0] - 2026-07-08

### Added

- Safety and medical guidance in onboarding and settings
- New food logging `AddFoodSheet` flow and camera capture fixes
- Codeberg release publishing helper script (`scripts/publish_release.sh`)

### Removed

- Legacy exercise data and related image assets

### Changed

- Food logging UX tweaks for photo and text input
- UI theme and component behavior fixes across key screens
- App icons/logos and localized strings updated
- Android development and release docs updated

## [1.0.0] - 2026-07-07

Initial public release of Chompass - an ad-free, privacy-focused Android fork of [Fud AI](https://github.com/apoorvdarshan/fud-ai).

### Added

- Chompass branding, Codeberg home, and `chompass://` meal-share deep links
- Upstream MIT attribution (`NOTICE`, `ASSET_CREDITS.md`, About screen, README)
- Original Chompass launcher icon and splash logo (see [ASSET_CREDITS.md](ASSET_CREDITS.md))
- [PRIVACY.md](PRIVACY.md) documents local-first, no-ads data practices
- `scripts/optimize_exercise_images.py` and `assets/exercises/IMAGE_MANIFEST.json` for bundled exercise photos
- About screen link to [ASSET_CREDITS.md](ASSET_CREDITS.md); `assets/muscle/LICENSE` (MIT)

### Removed

- Google AdMob / `play-services-ads` dependency and banner ad UI from the Android app
- Upstream package ID `com.apoorvdarshan.calorietracker`
- Unused `muscle_icon_group_*.png` muscle-filter assets

### Changed

- Application ID -> `app.chompass`
- App name, user-facing strings, privacy copy, and share text
- Source home from GitHub to Codeberg
- Exercise photos: single-frame WebP derivatives (max 800 px edge, ~19 MB total vs ~94 MB JPEG) via `scripts/optimize_exercise_images.py`
- Splash logos regenerated at 512 px (`scripts/generate_icons.py`) instead of 2048 px

### Preserved from upstream

- AI food logging (photo, voice, text, barcode)
- Coach chat, workouts library, Health Connect sync
- Home-screen widgets, diary export (JSON / Markdown / CSV)
- Meal import from upstream Fud AI (`fudai://` links)
- 15-language localization

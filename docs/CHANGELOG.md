# Changelog

All notable changes to Chompass are documented here.

Style: entries follow the release-text style guide (maintainer-local, not published; user-visible first, no emdashes, no internals). The version section is pasted verbatim onto the Codeberg release.


## [Unreleased]

### Changed

- **A named meal in the builder now logs as one food** (Android + web): leave the name empty to log each ingredient, or type a name to save one food with those ingredients listed inside it. Relogging and scaling then apply to the whole meal. Follows Codeberg [#91](https://codeberg.org/fitguy/Chompass/issues/91) by [@vandelli](https://codeberg.org/vandelli).
- **On-device AI food analysis is faster and more reliable** (Android): the on-device model now returns the core nutrition for a food without the long micronutrient list, cutting its response roughly in half. This means fewer "Could not understand the AI response" errors on phones where the reply previously ran long. Micronutrient estimates remain available with cloud providers. Follows Codeberg [#68](https://codeberg.org/fitguy/Chompass/issues/68) by [@Ludisc](https://codeberg.org/Ludisc) and [@mbethke](https://codeberg.org/mbethke).

### Fixed

- **Long names and large fonts no longer hide buttons** (Android): the recovered-analysis and paste chips on Home, meal headers, saved meal rows, the activity level row, and dialog buttons now truncate their label instead of pushing the close, log, or chevron controls off the row. The day type mode chips wrap to a second line instead of clipping.
- **The water tracker uses one color** (Android): the water card's progress bar, Auto badge, and next-drink line now use the water color like its icon, instead of the app accent.
- **Screen reader labels for the steppers** (Android): the plus and minus buttons for the Home nutrient card count and step goal now announce themselves.


## [4.7.0] - 2026-09-07

### Added

- **Product photos and an info card for barcode foods** (Android + web): scanning a barcode now shows the product's front photo from Open Food Facts in the review sheet, plus an info card with package size, ingredients, Nutri-Score, NOVA group, Eco-Score, allergens, labels, and categories. When the package size is known, the serving picker offers the whole package as a unit. A credit line names Open Food Facts contributors and the CC BY-SA 3.0 license, and opens the product page. The photo and card stay on your device: they are not included in exports or sync.

### Fixed

- **A late-night dinner now saves** (Android + web): setting Dinner to a time after midnight, such as 2:00, used to fail the meals editor check because the slot list still ran breakfast, lunch, dinner, snack. Start times are checked around the clock, so a 2am dinner in the usual meal order saves. Follows Codeberg [#88](https://codeberg.org/fitguy/Chompass/issues/88) by [@BrassCat](https://codeberg.org/BrassCat).
- **Barcode review keeps the product name and photo** (web): looking up a barcode with extra product details used to open a blank Log food form. The review now shows the name, photo, product card, and serving or package units.
- **Barcode product photos show the whole pack** (Android + web): the front photo sits in a wide letterbox instead of a cropped square, so a jar or bottle is not zoomed or squeezed. Tapping the photo opens the Open Food Facts product page.

## [4.6.1] - 2026-09-06

### Fixed

- **On-device photo analysis no longer refuses healthy free memory** (Android): the free-memory check before a photo analysis asked for a fixed extra margin that was sized for smaller phones, so phones with more RAM could be told "not enough memory" even when the analysis would have run fine. The margin now scales with the phone's memory. Follows Codeberg [#46](https://codeberg.org/fitguy/Chompass/issues/46) by [@marcelklehr](https://codeberg.org/marcelklehr) and [@invisibleman](https://codeberg.org/invisibleman).
- **The Meals editor no longer loses edits without a word** (Android): meal time edits made in the time picker looked saved, but closing the sheet without the Save button at the bottom threw them away silently. The sheet now asks before discarding unsaved changes. Follows Codeberg [#88](https://codeberg.org/fitguy/Chompass/issues/88) by [@BrassCat](https://codeberg.org/BrassCat).
- **Changing the amount on a relogged meal now scales its nutrition** (Android): reopening a saved meal before logging it ignored amount changes when the meal had no stored weight, so the values only updated after saving once. Amount edits now scale the nutrition right away whenever the meal's unit (slices, cups, and the like) says how much was originally eaten. Follows Codeberg [#89](https://codeberg.org/fitguy/Chompass/issues/89) by [@BrassCat](https://codeberg.org/BrassCat).
- **Coach with Search Grounding works on Gemini 3 models again** (Android): asking the coach anything while Google Search grounding was on failed with a provider error on Gemini 3 models, because the request did not allow the search tool to run beside the app's own tools. The request now allows both together. Follows Codeberg [#90](https://codeberg.org/fitguy/Chompass/issues/90) by [@smg950u](https://codeberg.org/smg950u).


## [4.6.0] - 2026-09-04

### Added

- **Meal nutrition vs your daily goals** (Android + web): tap a meal name on Home (Lunch, Dinner, and the rest) to see that slot's totals the same way as the day sheet, now with a percent of each daily goal. View more still opens the whole day. Follows Codeberg [#87](https://codeberg.org/fitguy/Chompass/issues/87) by [@bergieberg](https://codeberg.org/bergieberg).
- **Vitamins and minerals for each ingredient** (Android + web): meals broken into ingredients (chicken, rice, and the rest) can now show each ingredient's estimated fiber, sugars, vitamins, and minerals, with a percent of your daily goal. Open a meal's nutrition on Home, or expand an ingredient in the review and edit sheets. The ingredient values are labeled as estimates and may not add up exactly to the meal totals. Estimates arrive from the AI analysis when the meal's ingredient breakdown is on. Follows Codeberg [#86](https://codeberg.org/fitguy/Chompass/issues/86) by [@bergieberg](https://codeberg.org/bergieberg).
- **Accidentally dismissed meal reviews can be recovered** (Android): swiping the review sheet away before logging no longer throws the analysis away. Home shows a recovered analysis chip with the food's name; tap it to bring the review back without a new AI call, or close the chip to discard it for good.

### Changed

- **Gemini 3.8 Flash is the default AI model** (Android + web): new installs and anyone who never picked a model now get Google's newest Flash for photo and text analysis. The fallback model stays Gemini 3.5 Flash-Lite, and 3.7 Flash is still in the model list.

### Fixed

- **Meal schedules that cross midnight now save** (Android + web): the meals editor used to quietly put every meal time back to the defaults when the start times did not run in a plain morning to night order, for example a night shift plan with a meal after 0:00. Start times may now wrap past midnight, and when a schedule still cannot be saved the editor says so instead of silently restoring defaults. Follows Codeberg [#88](https://codeberg.org/fitguy/Chompass/issues/88) by [@BrassCat](https://codeberg.org/BrassCat).

- **Changing an ingredient amount now scales its vitamins and minerals** (Android): in the review, edit, and favorite sheets, editing an ingredient's quantity updated only its calories and macros; the vitamin and mineral values now follow the new amount.
- **Multi-ingredient meals no longer come back cut off on length-capped AI providers** (Android + web): with the default response length, Anthropic, OpenAI-compatible, and local Ollama models could return a truncated reply for meals broken into ingredients, which then failed with a truncation error. Those requests now get a higher response allowance while ingredient breakdown is on; your own response length setting still applies when it is higher.
- **Ingredient nutrition now behaves the same on the web app as in the app** (web): negative or oversized vitamin and mineral values in an imported file are handled the same as on Android, present zero values show as 0 instead of a dash, and a file with text where a number belongs no longer breaks the nutrition sheet.
- **Imported ingredient vitamins and minerals are cleaned** (Android): diary and sync imports now validate ingredient micro values, so a hand-edited file can no longer show a negative amount.

## [4.5.0] - 2026-09-03

### Added

- **Notice when a backup replaces your data** (Android): after a reinstall, Android can restore an older backup over the profile you just set up. The app now shows a one-time Backup restored dialog so that replacement is visible. Dismissing it remembers the notice until another restore happens. Follows Codeberg [#60](https://codeberg.org/fitguy/Chompass/issues/60) by [@Ir0nhid3](https://codeberg.org/Ir0nhid3).

### Fixed

- **Log meal stays on screen in the meal builder** (Android): with five or more ingredients the Log meal and Add another row sat below the fold with no way to reach it. The sheet now caps its height, the ingredient list scrolls, and those buttons stay pinned. Closes Codeberg [#84](https://codeberg.org/fitguy/Chompass/issues/84) by [@BrassCat](https://codeberg.org/BrassCat).
- **Save and Log stay visible at large font size** (Android): on a narrow screen with a large system font, long labels could hide the primary button on recipe builder and copy-from-day. The primary pill now keeps a visible single line; the title and secondary label yield first.
- **Long sheets keep their action buttons reachable** (Android): importing a shared meal, a long daily or context note, the fasting goal sheet, and live voice transcript no longer push Save, Add, or Analyze off the screen.


## [4.4.0] - 2026-09-01

### Added

- **Choose which nutrients Progress averages** (Android + web): Customize Progress (web: Settings, App) now lists every nutrient with a goal, each with a switch. Turn on iron, saturated fat, or vitamins and the averages card on Progress shows those rows. The fiber, sugar, and sodium trio stays as the default, so nothing changes until you pick. Past months count these nutrients from your existing logs. Follows Codeberg [#75](https://codeberg.org/fitguy/Chompass/issues/75) by [@bluepostofficebox](https://codeberg.org/bluepostofficebox).

### Fixed

- **Weight keeps the pounds you picked** (Android): weight was stored rounded to a tenth of a kilogram, so a pounds pick could come back different: 275 lbs showed 274.9, and 151.6 or 171.6 could shift by 0.1. Storage now keeps finer detail, and every tenth of a pound reads back exactly. Kilogram entries are unchanged. Closes Codeberg [#82](https://codeberg.org/fitguy/Chompass/issues/82) by [@bluepostofficebox](https://codeberg.org/bluepostofficebox).
- **Onboarding no longer wipes macro day types** (Android): finishing onboarding used to save a fresh default profile, which could drop settings onboarding doesn't ask about: macro day types, macro locks, the protein grams-per-kilo mode, and the name. Onboarding now keeps those and only writes the body stats and goal answers you entered.
- **Logged active burns always count** (Android + web): Add Food → Active burn now adds those calories to that day's goal even when the calorie gauge is set to Static goal. The static gauge still ignores automatic burn from Health Connect or the activity estimate, and a burn only affects the day it was logged on.


## [4.3.0] - 2026-08-31


### Added

- **Saved meals from Add Food** (Android + web): a Saved meals row opens Recents, favorites, and recipes. The compact chips stay. The extra Recents tile is gone. Closes Codeberg [#76](https://codeberg.org/fitguy/Chompass/issues/76) by [@BrassCat](https://codeberg.org/BrassCat).
- **Progress averages for fiber, sugar, and sodium** (Android + web): off until you turn them on in Customize Progress. A warning explains that Open Food Facts often omits them and photo analysis can guess. Closes Codeberg [#75](https://codeberg.org/fitguy/Chompass/issues/75) by [@bluepostofficebox](https://codeberg.org/bluepostofficebox).
- **Saved Meals review explains Log vs the pencil** (Android + web): a one-line hint says Log adds a diary meal, and the pencil on a favorite edits the saved food.
- **Edit or delete a logged active burn** (Android + web): Add Food → Active burn now lists that day's named logs. Tap a row to change the name or calories, or delete it. Closes Codeberg [#74](https://codeberg.org/fitguy/Chompass/issues/74) by [@bluepostofficebox](https://codeberg.org/bluepostofficebox).
- **Meal time uses a compact hour:minute wheel** (Android): tap Time on Edit Food for a small 00:00 wheel. Tap the number to type. The last choice is remembered. Closes part of Codeberg [#77](https://codeberg.org/fitguy/Chompass/issues/77) by [@BitLicker000](https://codeberg.org/BitLicker000).
- **System date and time pickers** (Android): Settings → App & Display has a toggle to use the phone's date and time dialogs when editing a meal. Off by default. Those dialogs follow dark mode. Closes part of Codeberg [#77](https://codeberg.org/fitguy/Chompass/issues/77) by [@BitLicker000](https://codeberg.org/BitLicker000).

- **Apply a new meal time to the rest of the slot** (Android): Edit Food can copy the new date and time onto the other items in that meal. Closes the rest of Codeberg [#77](https://codeberg.org/fitguy/Chompass/issues/77) by [@BitLicker000](https://codeberg.org/BitLicker000).


### Changed

- **Share uses the Android glyph** (Android): Edit Food share and Settings export no longer use the iOS share icon.
- **Meals editor meets 48 dp and TalkBack** (Android): move, name, and start-time controls are large enough to tap and have labels for the screen reader.

### Fixed

- **Saved AI server URL shows on the settings row** (Android): Settings → AI & Speech now shows the custom or Ollama URL you saved, and reopening the editor starts with that URL instead of a blank field. The fallback URL row does the same. Closes Codeberg [#52](https://codeberg.org/fitguy/Chompass/issues/52) by [@savionlee](https://codeberg.org/savionlee).
- **Barcode calories stay in kcal when carbs are missing** (Android + web): scanning a product that lists calories but omits carbs no longer divides those calories by about 4. If Open Food Facts stores carbs under total carbohydrates, that value is used, and the search results show those carbs too. Closes Codeberg [#71](https://codeberg.org/fitguy/Chompass/issues/71) by [@Melati-Pohan](https://codeberg.org/Melati-Pohan).

- **Add-another swipe returns to the in-progress meal** (Android): after the first ingredient is on the meal sheet, swiping down on the camera, photo note, or review goes back to that meal instead of the Add Food hub. The meal sheet also needs a firmer swipe to close, and a typed photo description survives rotation. Log meal and Add another truncate instead of stacking letter by letter in long languages. Closes Codeberg [#80](https://codeberg.org/fitguy/Chompass/issues/80) by [@BrassCat](https://codeberg.org/BrassCat).

- **Add another opens the full Add Food sheet** (Android + web): after the first ingredient, Add another and Add next ingredient open Photo, barcode, frequent, and note, not only the camera. Closes Codeberg [#78](https://codeberg.org/fitguy/Chompass/issues/78) by [@BitLicker000](https://codeberg.org/BitLicker000).


## [4.2.0] - 2026-08-28

### Added

- **Pick the weigh-in reminder time** (Android): Settings → Trackers & Reminders now lets you choose when the weight log reminder fires, instead of a fixed 8:00. Closes Codeberg [#69](https://codeberg.org/fitguy/Chompass/issues/69) by [@Making1167](https://codeberg.org/Making1167).
- **Food and body-fat reminder times** (Android): the food log reminder and the body-fat reminder now have a time wheel in Settings → Trackers & Reminders, like the daily summary and weigh-in reminder. Defaults stay 19:00 and 8:00.

### Changed

- **The web app uses your language in more places** (web): the entry form, the whole Settings screen, the diary menus, sheets and toasts, the coach, photo analysis and the update prompt now follow the app language instead of hardcoded English. German ships fully translated; other languages show English text on these surfaces until the next translation batch.
- **Trackers have their own colors** (Android): water reads blue and caffeine amber. Nicotine keeps the caution color. Progress bars stay the app accent.
- **The update prompt moved to the bottom** (web): the "new version is ready" prompt used to sit over the week pager, the first settings row and the coach headline. It now docks at the bottom like the install banner, and both reserve their space instead of covering content.
- **Typing stays put while the app updates** (web): sending a coach message no longer throws you out of the coach text field, and the entry form keeps your cursor in place when it refreshes mid-edit (unit change, serving lock, AI correction) instead of rebuilding the form around you.


### Fixed

- **Close sheets with a swipe or the back gesture** (web): bottom sheets like Add Food can now be swiped down from anywhere on the sheet, not just the small handle, and starting the swipe on a button no longer gets ignored. The back gesture or browser back button closes the open sheet instead of navigating away or leaving the app while the sheet stays open.
- **Integer wheels round typed decimals** (Android): typing 72.9 on a whole-number wheel now commits 73 instead of 72.
- **Typed notes survive picking photos** (web): on the photo and note analyze screen, choosing photos no longer wipes the description you already typed; voice dictation appends stay too.
- **Day notes survive the minute tick and get a working button** (web): leaving the diary open while writing a day note no longer gets the note wiped by the once-a-minute fasting refresh, and "Add a note for this day" now actually opens the editor. The note entry only shows when the Daily notes setting is on, matching Android.
- **Shared meals land on the right day** (web): importing a shared meal link near midnight no longer logs the foods to the wrong day. The entry form, photo analysis, barcode screen and onboarding derived "today" from UTC the same way and were fixed too.
- **Screen reader and keyboard fixes** (web): tapping around no longer re-announces the whole page; the week picker renders five weeks instead of 53 (35 day buttons instead of 371 for keyboard users); the desktop calorie bar now works with Enter and Space; delete confirmations start focus on Cancel so a hasty Enter no longer confirms the deletion; Escape closes one sheet at a time instead of all of them; chip rows and day pickers expose honest toggle state; recalculation, import, sync and coach status lines are announced when they change.
- **Custom nicotine and caffeine entries are honest** (web): a custom entry logs as kind "other" instead of pretending to be a cigarette or a coffee.
- **Progress stays light on repeat visits** (web): switching ranges or re-opening Progress no longer stacks leftover tap handlers from earlier renders, and the weight and body fat delete confirmations now follow the app language too.

## [4.1.0] - 2026-08-27

### Added

- **The diary shows what you logged, not grams** (Android + web): log food in cups, ounces, slices, or any custom serving and the food card shows that amount ("2 oz", "1 slice") instead of converting it back to grams. Entries without a matching serving keep showing grams, and totals stay gram-based under the hood. Progressive-meal ingredient rows follow the same rule. Closes Codeberg [#65](https://codeberg.org/fitguy/Chompass/issues/65) by [@swayevenly](https://codeberg.org/swayevenly).
- **Favorites are editable saved foods** (Android + web): every food in Favorites has an edit button. Change the meal type, name, serving, or macros and Save updates the saved food permanently, so the next time you log it the saved values come back. Renaming is refused when another food already uses that name. Reviewing a saved food now starts on its saved meal type on Android too (the web app already did); the one-tap plus button still logs into the current meal. Closes Codeberg [#66](https://codeberg.org/fitguy/Chompass/issues/66) by [@swayevenly](https://codeberg.org/swayevenly).
- **Waist body-fat estimate without a neck measure** (Android + web): RFM sits next to the Navy tape estimate. Optional Use as my body fat logs it like a normal reading. Tape estimates are typically a few percentage points from a scan.

### Fixed

- **Saved Meals stays on the tab you picked** (Android): switching between Recent, Frequent, and Favorites in the Saved Meals sheet no longer jumps back to the previous tab moments later.
- **Weight wheel keeps the tenth you see** (Android): logging 80.2 no longer lands as 80.3. The tenths wheel could show one value while Save stored a slightly different one. Typing a weight and tapping Save now stores exactly what you typed; it used to keep the previous weight unless you touched the wheel again. Codeberg [#63](https://codeberg.org/fitguy/Chompass/issues/63) by [@tuxMode](https://codeberg.org/tuxMode).
- **Water from food no longer double-counts slices** (Android): two slices totalling 220 g credit about 150 ml, not 250. The food-water toggle was multiplying the already-total grams by the slice count. Codeberg [#16](https://codeberg.org/fitguy/Chompass/issues/16) by [@1260er](https://codeberg.org/1260er).

### Changed

- **Active burn follows the day type** (Android + web): when you have enough recent Training and Rest days, the Home ring's typical active burn is the average for that day type, not one blended number. The caption reads like `380 of 620 active · Training`. Recalculate and Coach see the same per-type typicals.

## [4.0.0] - 2026-08-26

### Added

- **Custom meal names and extra meals** (Android + web): meals are no longer only breakfast, lunch, dinner, and snack. Rename any slot (for example Comida instead of Almuerzo), add extra meals, hide ones you do not use, reorder them, and set each start time so auto-guess still works. Names follow diary export, import, shared meals, and WebDAV. Settings → Meals. Closes Codeberg [#61](https://codeberg.org/fitguy/Chompass/issues/61) by [@dolc73](https://codeberg.org/dolc73).
- **Type numbers on wheels** (Android): tap the number on a calorie, macro, weight, or limit wheel to type the value, then Done. The last choice (type or wheel) is remembered. Dates, times, and units stay wheels. Closes Codeberg [#62](https://codeberg.org/fitguy/Chompass/issues/62) by [@swayevenly](https://codeberg.org/swayevenly).
- **Day types, different goals per day** (Android + web): set up to 7 named day types, each with its own calorie and macro targets, for example a high-carb Training day and a low-carb Rest day. Assign them the way your plan works: switch today's type by hand from a chip under the Home ring, map them to weekdays, or run a repeating cycle like 2 training days then a rest day, with one-day overrides when the schedule shifts. Off by default under Settings → Goals & Nutrition → Day types, and available outside keto mode (a switch to keto pauses the plan and keeps your day types).
- **Day types everywhere goals appear** (Android + web): Home's ring, macro cards and the active-burn split follow the day's type, and the daily summary notification and widgets show the right target at midnight. Coach knows today's type and the weekly average, AI Recalculate can adjust each day type separately (shown as before/after columns in the results sheet), and the weekly Adaptive tweak moves every day type while keeping their spread.
- **Goals history is frozen per day** (Android + web): the target that was in effect is recorded for each day, so Progress shows the actual per-day targets over your history and its range lines use the average of what you actually targeted, instead of painting today's goal across the whole past. Diary exports carry each day's targets, and the journal syncs through WebDAV and comes along in backups.
- **Coach sees your average intake by default** (Android + web): the coach now knows your average calories and macros over the last 7 and the last 30 logged days without needing to look them up, so questions like "how am I doing" get an immediate, grounded answer. With day types on, it weighs single days against that day's target and the week against the weekly average.
- Closes Codeberg [#60](https://codeberg.org/fitguy/Chompass/issues/60) by [@Ir0nhid3](https://codeberg.org/Ir0nhid3).

### Changed

- **Add Food sheet is less wordy** (Android): the extra section titles (More ways to log, Water, Caffeine, Nicotine, Fasting) are gone. The rows still show their icons. Recent and frequent chips sit under voice, barcode, and search, above water and the other trackers.
- **Photo review no longer asks to confirm the portion** (Android): size chips and the extra exact-weight row are removed. Correct the portion with the existing tip note, or edit grams on the serving card. The Settings toggle is gone too.
- **Smoother back gesture on Android 14 and 15**: the system back preview is on.

### Fixed

- **Caffeine and nicotine limit sheets stay on screen** (Android): the daily-limit sheets no longer sit under the status bar, and Save stays visible when the keyboard is open.
- **Delete leftover serving units** (Android): leftover prefix units from older versions can be removed from an entry. Select the unit, tap the pencil, then the trash next to the checkmark. Grams stays; nutrition is unchanged. Follow-up to Codeberg [#59](https://codeberg.org/fitguy/Chompass/issues/59).
- **Logged weight stays in history when Health Connect or WebDAV is catching up** (Android): if you logged today's weight while the app was still pulling from Health Connect or WebDAV, the new row could vanish from Weight History a moment later. The pull now adds remote rows without replacing the one you just saved. Codeberg [#63](https://codeberg.org/fitguy/Chompass/issues/63) by [@tuxMode](https://codeberg.org/tuxMode).
- **What if? shows the advice, not the JSON** (Android): when the model wraps its suggestion in JSON, the dialog shows the advice text.

## [3.24.0] - 2026-08-24

### Added

- **Intermittent fasting timer** (Android + web): an optional, local-only fasting timer. Start or stop a fast from Home or the Add food sheet, and set your own fasting and eating windows freely, with quick picks for the common protocols (12:12, 14:10, 16:8, 18:6, 20:4, 23:1). The popular 16:8 is the default, so enabling the tracker gives you a working cycle. Off by default under Settings → Trackers & Reminders → Fasting.
- **Fasting bar and Auto fast windows** (Android + web): the Home bar shows how long until you can eat while fasting, and how long until the next fast starts while your eating window is open, including the time spent eating so far. Auto fast windows runs the cycle for you: your fast starts at the fast start time you choose (default 20:00) and ends at the goal, with no buttons on Home for auto fasts (you can still start one manually). Auto fast windows is on by default, turn it off in Settings → Trackers & Reminders → Fasting.
- **Fasting reminders** (Android): a start nudge before the fast begins and a break-fast nudge before it reaches the goal, both on by default with a lead time you choose from 0 to 120 minutes. The start nudge works with auto windows too.
- **Fasting stays on your device** (Android): the timer survives restarts, and nothing is synced, exported, or included in shared meals. Coach knows how long you have been fasting when you ask about eating timing. While the timer is on, long-press the app icon for a Start or stop fasting shortcut.
- **Optional nicotine tracking** (Android + web): log cigarettes, vapes, pouches and more with quick +1 chips or a custom count (with optional nicotine in mg), see today's total against a daily limit you set, and edit or delete any entry from a per-day history. Everything stays on your device and in your WebDAV sync; nothing is sent to an AI provider. Off by default under Settings → Trackers & Reminders → Nicotine.
- **Caffeine tracking** (Android + web): coffee, tea, and energy drinks now carry their caffeine, with a daily total and an adjustable daily max (default 400 mg) in Goals & Nutrition. Caffeine from photo, text, barcode, and manual logging is kept, can be edited, and is included in diary exports, shared meals, sync, and Health Connect. Closes Codeberg [#55](https://codeberg.org/fitguy/Chompass/issues/55) by [@DontBlameMe](https://codeberg.org/DontBlameMe).
- **Caffeine tracker** (Android + web): a water-style tracker on top of the caffeine nutrient, off by default under Settings → Trackers & Reminders → Caffeine. Home shows today's caffeine, from tracker logs and food entries, against the daily max from Goals & Nutrition. The Add food sheet gains +1 chips for coffee, tea, and energy drinks, and each logged cup lands in a per-day history you can edit or delete.
- **Daily notes** (Android + web): each day can carry a short free-text note for your thoughts, progress, and observations. On Android the note lives on Home for the day you are viewing, and opens an editor with a 1000-character limit. On the web app the same note appears on the day page. Notes follow your selected day, so you can journal for past days too. They sync through WebDAV, come along in backup restore, and are included in the JSON and Markdown diary exports (a day with only a note still exports). Closes the daily-notes part of Codeberg [#58](https://codeberg.org/fitguy/Chompass/issues/58) by [@DonSync](https://codeberg.org/DonSync). The note card is off by default; enable it under Settings → Trackers & Reminders → Daily notes.
- **Water history editing** (Android): tap the water card to open the day's logged glasses with their times. Tap a glass to change its amount, or remove it with the delete button. Works for past days too, not just today. Closes the water-edit part of Codeberg [#58](https://codeberg.org/fitguy/Chompass/issues/58) by [@DonSync](https://codeberg.org/DonSync).
- **Analysis queue** (Android): Add Food now has an Analysis queue. Failed photo and text analyses are saved there automatically with their photos and description, so nothing is lost when the AI call fails. You can run any saved prompt again later, for example against a model on your home PC, and you can queue a meal from the photo sheet without analyzing it first. Each entry shows its note, photos, and target day, and you can edit them, run one item or all waiting items, and clear the finished history, which is kept for 7 days. Closes Codeberg [#53](https://codeberg.org/fitguy/Chompass/issues/53) by [@madarexxx](https://codeberg.org/madarexxx).
- **Failed analyses keep your photos and description** (Android): when an AI analysis fails, the photos and note are no longer lost. The error dialog offers Retry for every photo case, including multiple photos, and an Open queue shortcut. Even after you dismiss it, the prompt stays in the queue and history.
- **Prompt history in the note inputs** (Android): the analysis and note sheets gain a Prompt history button. It lists your saved prompts, newest first, and one tap fills the input in grey until you edit it. Prompts from the analysis queue and failed analyses appear there automatically.

### Changed

- **Settings reorganized for findability** (Android): Settings now groups look and feel under Display and the tracking tools, water, nicotine, caffeine, fasting, and reminders, under Trackers & Reminders. Personal Info gains a Units row with one Metric/Imperial toggle, and the hub has a Search settings box that finds any setting by name or group.

### Fixed

- **Fasting auto cycle starts on time** (Android + web): with Auto fast windows on, your fast now starts at the fast start time you set, even the first time you enable it. Before, a fresh cycle waited for the next day: at 20:00 with an 18:00 start time, Home kept showing the next fast starting tomorrow instead of the countdown to your next eating window. The fasting bar also shows the correct time spent eating while the eating window is open.
- **Settings search understands your language** (Android): the search field now also matches the localized section names, so a term like "Ziele" in German or "Poids" in French lands on the right setting even before a translation adds its own search words. Each app language can add its own search terms; until then English terms and universal units like kg, kcal and ml still work. Search matching is also the same in every language (accents and capital letters are handled consistently) and responds more quickly as you type.
- **On-device AI falls back to a smaller model when the chosen one cannot run** (Android): when the primary on-device model is too big for the free memory on your phone, photo analysis now switches to the fallback model you picked in Settings → AI & Speech, for example from Gemma 4 E4B to E2B, instead of failing with a memory error. Closes Codeberg [#54](https://codeberg.org/fitguy/Chompass/issues/54) by [@ARR8](https://codeberg.org/ARR8).
- **Review and save buttons stay readable on light wallpapers** (Android): with a light wallpaper, the white text on the accent-gradient buttons could vanish on phones whose accent color comes out near-white. The label now switches to a dark color automatically when the accent is too light. Follow-up on Codeberg [#44](https://codeberg.org/fitguy/Chompass/issues/44) by [@ionsingo](https://codeberg.org/ionsingo).
- **Custom serving names save as one unit, not one entry per letter** (Android): while renaming a serving unit (for example a homemade dish's "bowl" or "slice"), every letter you typed was added to the unit list as its own entry, so "spice" turned into s, sp, spi, spic and spice. The name now applies when you tap the checkmark, and only the finished unit is saved. Closes Codeberg [#59](https://codeberg.org/fitguy/Chompass/issues/59) by [@ionsingo](https://codeberg.org/ionsingo).
- **Wheel pickers save the value you see** (Android): if you turned a settings wheel, for example the fasting start time, the water goal, or a reminder lead time, and tapped Save while the wheel was still finishing its move, the old value was saved instead of the one shown. The selected value now follows the wheel as it moves, so saving always stores the row you see highlighted.
- **Moving a food to another meal no longer reshuffles the whole food list** (Android): with "Latest Meals First" sorting, changing an entry's meal type used to rebuild every section header below it, which could make the card disappear from the home screen on slower phones until the app was restarted. The list now keeps the unaffected sections in place. Codeberg [#56](https://codeberg.org/fitguy/Chompass/issues/56).
- **Web sync reads files from older versions** (web): a sync file saved by an earlier app version, without the daily-notes field, now loads normally instead of failing to open.

## [3.23.0] - 2026-08-22

### Added

- **Saturday as the week start** (Android + web): Settings → App & Display → Week Starts On now offers Sunday, Monday, or Saturday, and the diary, charts, and week summaries follow it. A Monday start chosen before stays Monday.
- **Locked/Auto toggle on calorie and macro goals** (Android + web): Settings → Goals & Nutrition shows Locked or Auto on each calorie and macro row. Tap the chip to pin the current number so Recalculate and Adaptive leave it alone, or set it back to Auto to let them adjust it. In the web app, typing a custom calorie or macro locks it when you save. Follow-up on Codeberg [#45](https://codeberg.org/fitguy/Chompass/issues/45) by [@Ludisc](https://codeberg.org/Ludisc).
- **Clear on-device AI leftovers** (Android): Settings → AI & Speech → On-device model can now wipe a finished model, a broken partial download, the compile cache, and the engine in memory. Leftover files also show up there when the provider is back on Gemini, so a stuck download is not hidden behind the On-Device picker.

### Changed

- **Recalculate explains its new targets** (Android): the one-line result is now a sheet with your new calorie and macro targets next to the previous ones, which AI provider and model set them (and which backup model answered when one failed), the formula chain behind the number, the data it used, and the model's reasoning. The explanation stays on the Goals screen (Recalculation details) and behind the home card's info icon, so you can reopen it anytime, including after Adaptive Goals adjusts your calories.
- **Recalculate uses your full history with cloud AI** (Android): cloud providers now get your raw weigh-in series and the last 14 days of logged intake and judge the trend themselves. The on-device model stays conservative until your history is dense enough, and the app falls back to the built-in formula or your measured Health Connect burn when the logs are too thin.
- **Cheap cloud models use the safe Recalculate path** (Android): lite, nano, haiku, mini, and free endpoint models now run the same conservative tier as on-device AI instead of judging your raw weigh-in series. A device run showed they can otherwise pin the goal to your resting burn on a thin history.
- **Logging food, water, and weight entries is faster on long histories** (Android): saves no longer rewrite your whole history in one file each time. Adding one entry now touches only that calendar month, so the app stays as quick after a year of logging as it is on day one. Your existing data moves over automatically on update; exports, backups, and sync are unchanged.
- **Progress stays smooth on a long diary** (Android): the Progress charts now read a compact daily summary of your meals instead of every logged entry, so switching to a longer range no longer loads your whole food history at once. Totals and averages are unchanged.

### Fixed

- **Recalculate no longer anchors on a short weight trend** (Android): with only a few weigh-ins, the AI sometimes set your calorie goal at your resting burn instead of your activity-based target, because early readings can look like a much lower or higher maintenance than you really have. It now only uses your logged trend once it is dense enough (at least 4 weigh-ins across 14 days with 4 logged food days), and otherwise keeps the built-in formula or your measured Health Connect burn. A trend below your resting burn is ignored instead of dragging the goal down.
- **Gemma download survives screen-off and time limits** (Android): the on-device model file no longer resets to zero when the screen locks or Android interrupts the download, and a clean cut is retried instead of deleting the file. Follow-up on Codeberg [#51](https://codeberg.org/fitguy/Chompass/issues/51) by [@samuraihelmet](https://codeberg.org/samuraihelmet).
- **On-device download no longer runs out of memory at 100%** (Android): the app used to re-read the whole multi-gigabyte model file to verify it, which could crash the app and hide the On-Device option. It now hashes the file while downloading, and Settings shows On-Device right away.
- **Onboarding no longer appears stuck with the on-device provider** (Android): with on-device AI selected, onboarding used to run the AI plan calculation behind the Setting up screen, which could look frozen for about a minute while the engine started. It now uses the built-in formula targets unless the engine is already warm.
- **Recalculate no longer hangs on a stalled read** (Android): a slow settings read could leave the goal rings spinning for minutes after the model had already answered. Failures and stalls now clear the spinner and show an error instead.
- **Settings opens without the lag** (Android): the settings screen used to finish its disk and Health Connect reads on the main thread, which could stall the tab switch and the first rows on a busy phone. Those reads now happen in the background, so the screen appears immediately.
- **On-device AI no longer slows down a low-memory phone** (Android): when the phone is low on free memory, the Gemma engine now refuses to load with a clear message instead of swapping and stalling every app while it starts.
- **Energy Burn Goals row no longer spins for nothing** (Android): the Recalculate busy indicator under Energy Burn Goals only shows when the recalculation actually reads Health Connect.

## [3.22.0] - 2026-08-21

### Added

- **Daily summary shows today's deficit or surplus** (Android): the evening notification now uses logged calories and measured burn (Health Connect when available) to say whether the day landed in deficit, surplus, or on target. Expand it to see eaten vs your calorie goal, and net carbs when keto is on. You can set the time under Settings → Notifications. Off by default.
- **Optional nutrient goals can be estimated with AI on demand** (Android): Settings → Other Nutrient Goals now has an Estimate with AI button that fills sugar, fiber, sodium, vitamins, and the other optional goals from your profile and calorie target. It only runs when you tap it. Recalculate leaves these values alone, so your manual edits stay as you set them.

### Changed

- **Lose goals stay at or above resting calories** (Android + web): formula, Recalculate, Adaptive, and onboarding never set a daily target below your estimated resting burn or 1,200 kcal. A faster weekly pace shrinks to fit that floor instead of writing a crash diet. You can still type a lower number after a confirm. Adaptive also raises a target that is already too low.
- **Adaptive Goals is a small weekly nudge** (Android): about once a week it now adjusts calories by at most 150 kcal from your recent weight trend, and only after about four weeks of weigh-ins (six readings). Locked calorie and macro rows stay as you set them. Recalculate still asks the AI for a full plan, and if calories are locked it plans protein, carbs and fat around that number instead of replacing it. Closes Codeberg [#45](https://codeberg.org/fitguy/Chompass/issues/45) by [@Ludisc](https://codeberg.org/Ludisc).

### Fixed

- **On-device AI opens up to 6 GB phones** (Android): phones with about 6 GB of RAM now see the on-device option in Settings → AI Provider. It used to hide because usable memory runs a bit below the marketed amount, so most 6 GB phones missed the cutoff. If the on-device engine fails to start, the app now retries once before showing an error.
- **On-device model downloads resume after an interruption** (Android): the Gemma model file no longer restarts from zero when the connection drops or the download stalls, which could loop forever on a multi-GB file. If storage runs out, you get a clear error instead. Closes Codeberg [#51](https://codeberg.org/fitguy/Chompass/issues/51) by [@samuraihelmet](https://codeberg.org/samuraihelmet).
- **Coach average matches the Progress chart** (Android + web): asking the coach for a full analysis now uses the average of days you actually logged, not empty days before you started. Progress Avg also skips today, which is still being logged. Closes Codeberg [#49](https://codeberg.org/fitguy/Chompass/issues/49) and [#50](https://codeberg.org/fitguy/Chompass/issues/50) by [@tuxMode](https://codeberg.org/tuxMode).
- **Recalculate returns as soon as calories and macros are ready** (Android): it no longer waits on a second AI call for optional nutrients, which could sit for minutes after Gemini hiccuped.

## [3.21.2] - 2026-08-20

### Fixed

- **Opening the app no longer crashes on some Huawei phones** (Android): on older Huawei EMUI builds based on Android 10, tapping the icon could close the app before Home appeared when the language follows the system. Closes Codeberg [#43](https://codeberg.org/fitguy/Chompass/issues/43) by [@tuxMode](https://codeberg.org/tuxMode).
- **German water and serving labels** (Android + web): the next-drink line now says Nächste, and the water widget shows only the amount and time so the line is not cut off. Cup, tablespoon and teaspoon now use the German unit names Cup, EL and TL. The serving card labels the unit column Einheit. Follow-up on Codeberg [#3](https://codeberg.org/fitguy/Chompass/issues/3) by [@1260er](https://codeberg.org/1260er).
- **Switching to maintain updates calories right away** (Android): changing the goal from lose or gain to maintain now applies the formula targets immediately, so the old deficit does not stay on screen until Recalculate. Follow-up on Codeberg [#3](https://codeberg.org/fitguy/Chompass/issues/3) by [@1260er](https://codeberg.org/1260er).

## [3.21.1] - 2026-08-19

### Changed

- **Faster Home, Progress, and Settings on a long diary** (Android): those screens no longer load the whole food and body history just to show today or the selected range.
- **Add Food and Progress stay smooth on a long diary** (Android): Add Food opens without scanning a year of meals first. Progress range chips only read the months in view, and long calorie charts roll up to weekly bars. Logging a meal no longer forces Add Food to rescan recents.
- **Faster cold start** (Android): Home appears after one settings read instead of a chain of them. Background cleanup and widget refresh wait until the first screen is up.
- **Smaller Codeberg download** (Android): the Obtainium and Codeberg APK no longer includes Intel libraries that phones do not use. Phones are unchanged. Emulators need an ARM system image.

### Fixed

- **Deleted weigh-ins stay gone after WebDAV sync** (Android): deleting a weight and then tapping Sync Now no longer brings the old row back from the file. That used to happen when Auto-sync was off. Extra rows already in the file still need to be deleted once, then synced. Follow-up on Codeberg [#39](https://codeberg.org/fitguy/Chompass/issues/39) by [@tuxMode](https://codeberg.org/tuxMode).
- **Serving unit wheel stays open and every row is reachable** (Android): on Review Food, quantity and unit sit in one row, the unit list stays on screen, and a short list turns one row at a time so the last unit can be selected. Closes Codeberg [#42](https://codeberg.org/fitguy/Chompass/issues/42) by [@tuxMode](https://codeberg.org/tuxMode).
- **Coach reads streamed replies from local endpoints** (Android): a greeting from OmniRoute or a similar OpenAI-compatible proxy that streams its reply now shows in the coach instead of an error. Follow-up on Codeberg [#8](https://codeberg.org/fitguy/Chompass/issues/8) by [@darkxylese](https://codeberg.org/darkxylese).

## [3.21.0] - 2026-08-19

### Added

- **Local AI endpoints over plain HTTP** (Android): an Allow insecure HTTP switch in Settings → AI & Speech lets custom and Ollama endpoints use http:// URLs in release builds, for example on a home network. Off by default. Cloud providers use https. Follow-up on Codeberg [#8](https://codeberg.org/fitguy/Chompass/issues/8) by [@ARR8](https://codeberg.org/ARR8).
- **Ollama endpoints trust certificates you install** (Android): custom endpoints already did. Remote Ollama servers with your own CA now connect the same way.
- **Clearer local-endpoint errors** (Android): blocked plain HTTP and untrusted certificates now explain what to do (turn on Allow insecure HTTP, or install the CA on the phone) instead of showing a raw network error.
- **Polish and Turkish language packs** (Android + web): Chompass now speaks 18 languages. Polish and Turkish cover the full Android app, from onboarding through the diary, coach, widgets, and safety notices, plus the web app's core screens. Choose them in Settings → App & Display → Language.
- **Full Android translations** (Android): Russian, Ukrainian, Italian, Dutch, Brazilian Portuguese, Romanian, Azerbaijani, Hindi, Japanese, Korean, Simplified Chinese, and Arabic now cover the whole app, not just the main screens. French, Spanish, and German pick up the last missing strings.

### Changed

- **Log again shows recent and frequent foods** (Android + web): Add Food now has two scrollable rows under Log again, recent on top and frequent below. The extra hint under the heading is gone.
- **Add Food tiles, nutrition badges, and goal wheels** (Android + web): Add Food tiles share one height. The weight-chart legend is the shorter "Weigh-in". On Android, letter badges in the nutrition sheet sit on center, and goal wheels no longer repeat the nutrient name as a title.

## [3.20.0] - 2026-08-19

### Added

- **OLED appearance mode** (Android): a new OLED option in Settings → App & Display → Appearance turns the app and all four home-screen widgets true black, so OLED screens can switch pixels off. Text and accent colors stay the same as Dark mode. Choosing Light or Dark explicitly now also applies to the widgets, not just the app.
- **Per-app language selector** (Android): a Language row in Settings → App & Display switches the whole interface to any of the 16 supported languages, independent of the system language. The choice is saved and survives restarts; System follows the device language.
- **Wheel pickers for entering values** (Android): calories, macros, serving size, meal times, water, manual activity, AI settings, and photo notes now use scrollable wheel pickers instead of typed fields. Time wheels follow the 12 or 24 hour format of the app language.
- **Buy fitguy a yogurt** (Android + web): a Ko-fi donation link now appears in the app's About screen, the website footer and download page, the PWA About screen, and the README. The F-Droid listing's donation link points to Ko-fi instead of the repository. Closes Codeberg [#36](https://codeberg.org/fitguy/Chompass/issues/36) by [@tpapastylianou](https://codeberg.org/tpapastylianou).

## [3.19.0] - 2026-08-18

### Fixed

- **Numbers, dates and decimals now follow the app locale** (Android + web): diary entries, recipes, results, wheel pickers, birthday age and coach dialogs all use locale-aware formatting instead of US defaults. Medium dates are now locale-natural.
- **TalkBack reads week strip, onboarding pickers, swipe rows and the hero arc correctly** (Android): accessibility semantics added so screen readers announce these elements properly.
- **Empty macro cards render as bare tracks** (Android): the calorie ring no longer shows broken layout when a macro has no data.
- **Picker sheets use unified primary button styling** (Android): the iOS-style capsule button now appears consistently across picker dialogs.
- **Calorie accent color unified with the theme primary** (Android): the ring and accent elements now match the Material3 color scheme.
- **Wheel picker follows external selection, icons honor iconSize, chips have semantics** (Android): the wheel no longer fights programmatic selection, icons respect sizing, and chips are accessible.
- **Coach preview uses the production send button** (Android): visual consistency between preview and live coach.
- **Photo memory usage capped to the stored-image limit** (Android): in-memory decodes no longer exceed the on-disk cap, reducing OOM risk on large images.
- **Update Available row explains F-Droid delivery** (Android): the about screen now clarifies that updates come via F-Droid, not the Play Store.

## [3.18.0] - 2026-08-18

### Added

- **Macro goals rise with the active-burn target** (Android + web): on an active day the calorie ring can show a higher goal than the stored budget, and the protein, carbs and fat cards now scale up with it so they agree with the ring. Keto mode keeps the base split. Closes Codeberg [#38](https://codeberg.org/fitguy/Chompass/issues/38) by [@neshamon](https://codeberg.org/neshamon).

- **The on-device model joins the AI fallback options** (Android): when a provider call fails, the app can now use the on-device model as the fallback target. It appears in the fallback list under Settings → AI & Speech once a model is downloaded. Closes Codeberg [#37](https://codeberg.org/fitguy/Chompass/issues/37) by [@ARR8](https://codeberg.org/ARR8).

### Fixed

- **Typed entries no longer close by accident** (Android): a downward drag on the note sheet, or on an entry sheet while typing, no longer dismisses the sheet mid-entry. Dismissal stays on the handle, the scrim and the cancel button. Backing out of an Add food tool returns to the grid. Closes Codeberg [#30](https://codeberg.org/fitguy/Chompass/issues/30) by [@dpile103](https://codeberg.org/dpile103).

- **The onboarding pace line keeps its spacing** (Android): the estimated-days line on the goal-pace screen no longer runs the number into the word before it ("in30 Tagen" in German).

- **Duplicate weight rows no longer appear after WebDAV sync** (Android + web): rows with the same date and weight, written by the app or the web app, now merge into one row instead of piling up with every sync. Closes Codeberg [#39](https://codeberg.org/fitguy/Chompass/issues/39) by [@tuxMode](https://codeberg.org/tuxMode).

- **The last sync time shows in your local time zone** (Android): the sync screen used to show the raw UTC timestamp. It now shows the local time. Closes Codeberg [#40](https://codeberg.org/fitguy/Chompass/issues/40) by [@tuxMode](https://codeberg.org/tuxMode).

- **Edit Food Entry shows no AI controls when AI is off** (Android): with AI features turned off, the edit sheet now only shows a plain note field and a Save button. The note on a logged entry stays editable, and no AI call can be started from anywhere in the app.

## [3.17.2] - 2026-08-17

### Fixed

- **WebDAV sync and sync-file import no longer drop food photos and emoji** (Android + web): after a successful sync or import, food photos were replaced by the default picture and emoji were lost. Photos are still kept out of the backup file on purpose, but they now stay in place locally, and emoji are part of the backup again. Fixes Codeberg [#34](https://codeberg.org/fitguy/Chompass/issues/34) by [@tuxMode](https://codeberg.org/tuxMode).

## [3.17.1] - 2026-08-17

### Fixed

- **Settings values line up on the right again** (Android): values and their arrows sit at the right edge of each row again. Long labels still wrap instead of stacking letter by letter.

## [3.17.0] - 2026-08-17

### Added

- **Turn off all AI features** (Android + web): a new switch under Settings → AI &amp; Speech (Android) and AI &amp; Speech (web) turns off everything that sends data to an AI provider: the coach, photo/text/voice food logging, "What if?" and AI goal suggestions. The app keeps working without AI: barcode scanning, food search, manual entry, saved meals, water, sync and Health Connect all stay on, and goal calculations fall back to the built-in formulas. Part 2 of Codeberg [#20](https://codeberg.org/fitguy/Chompass/issues/20) by [@HattDroid](https://codeberg.org/HattDroid).

- **The app is now fully translated into Spanish and French** (Android): the whole Spanish and French packs are complete, from onboarding and settings to analysis and AI error messages, in one consistent voice: informal Spanish, formal French.

### Fixed

- **Long settings labels no longer break apart** (Android): a settings row with a long label and a long value, for example the food log sort order in German, used to squeeze the label until every letter sat on its own line. Labels now wrap naturally and values end with an ellipsis.

- **The result sheet's side-by-side actions fit** (Android): when both the tip and the add-photo action are shown with long labels, the second button no longer collapses letter by letter; both truncate cleanly.

- **"Add next ingredient" is translated** (Android): the add-to-meal action on the result sheet now shows in Arabic, Azerbaijani, Hindi, Italian, Japanese, Korean, Dutch, Brazilian Portuguese, Romanian, Russian, Ukrainian and Chinese instead of English.

- **The photo entry sheet no longer shakes when swiping** (Android): the sheet that shows a photo before analysis stays steady while scrolling and with the keyboard open.

- **A photo with a barcode no longer fails when AI cannot run** (Android): when food analysis is unavailable, the app now shows the product from the barcode or QR code in the photo instead of an error. The product data was already being read for the AI; it now doubles as the fallback.

- **Log Weight and Log Body Fat popups scroll when full** (Android): the Advanced date and time section and the Save button stay reachable on small screens and at large font sizes. Closes Codeberg [#29](https://codeberg.org/fitguy/Chompass/issues/29) by [@tuxMode](https://codeberg.org/tuxMode).

- **Adding a search result no longer fails on some products** (Android): some products found in the food search could not be added because their code does not pass the standard check digit, even though Open Food Facts lists them. They now add normally. Follow-up to Codeberg [#26](https://codeberg.org/fitguy/Chompass/issues/26) by [@felixbrucker](https://codeberg.org/felixbrucker).

- **Barcode scanning prefers the retail barcode on mixed labels** (Android + PWA): when a label carries both a barcode and a QR or Data Matrix code, the scan now uses the barcode, which is the code the food database lists. Follow-up to Codeberg [#24](https://codeberg.org/fitguy/Chompass/issues/24) by [@felixbrucker](https://codeberg.org/felixbrucker).

- **The serving unit on barcode and AI entries is translated** (Android + PWA): products added from a barcode or food analysis used to show the unit "serving" in English even in other languages. It now shows in the app language.

- **App-generated messages are translated** (Android): the adaptive-goals update message, the goal update message, and the chart loading label now show in the app language instead of English.

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

- **Home ring spans your whole expected day** (Android): with _Add Active_ on, the calorie ring now runs from zero to your projected daily burn, sedentary goal plus your usual active burn, instead of starting at the base goal in the morning. The goal line reads against that expected total, and the caption shows how much of your usual active burn you have covered so far, turning a different color once you burn more than usual. The calorie widget shows the same expected goal and remaining.

- **Reasoning effort for OpenRouter models** (Android + PWA): a new _Reasoning effort_ option under Settings → AI & Speech controls the thinking budget of reasoning-capable models. Auto keeps the app default; Low to High trade speed and token cost for accuracy on hard photos or logs. Closes upstream [#194](https://github.com/aopv/fud-ai/issues/194).

- **A separate model for photos** (Android + PWA): _Vision model_ under Settings → AI & Speech lets photos use a different model than text, per provider. Leave it on _same as Model_ for today's behavior, or set a vision-capable model when your main model is text-only. Closes upstream [#195](https://github.com/aopv/fud-ai/issues/195).

- **Fixed launcher icon option** (Android): a new switch in Settings, next to Theme Color, keeps the launcher icon teal and stops it from following the theme color or wallpaper. Useful on launchers that briefly hide or close the app when the icon changes.

### Fixed

- **Morning budget no longer includes a guessed active burn** (Android): with _Add Active_ on, the home ring and widget now use the active calories actually recorded so far, zero until your first workout or sync of the day, instead of substituting a whole-day estimate that inflated the budget and then dropped when the first measurement landed. The estimate still applies when no activity source is connected at all.

- **Active calories match the day you're viewing** (Android): with Health Connect connected and _Add Active_ on, the home ring's active burn and budget no longer show a previous day's value after switching days or reopening the app. Closes Codeberg [#22](https://codeberg.org/fitguy/Chompass/issues/22) by [@HattDroid](https://codeberg.org/HattDroid).

- **Changing the theme no longer closes the app on some devices** (Android): on some Xiaomi and Samsung launchers, switching dark or light mode or the theme color could drop you back to the home screen when the launcher icon color changed. The icon now updates only while the app is in the background, and a light wallpaper no longer turns the launcher icon gray. Closes Codeberg [#13](https://codeberg.org/fitguy/Chompass/issues/13) by [@armishinwn](https://codeberg.org/armishinwn) and [#21](https://codeberg.org/fitguy/Chompass/issues/21) by [@HattDroid](https://codeberg.org/HattDroid).

## [3.13.0] - 2026-08-14

### Added

- **New default AI model**: photo and text food analysis now uses Gemini 3.7 Flash by default, with better accuracy for the same free-tier cost. Your saved model choice is untouched, and the free fallback stays the high-quota Flash-Lite model.
- **AI model availability hints**: the model picker now flags paid-only models (Pro models need billing on your AI Studio project) and notes when free-tier availability varies by account and region. The same note appears during onboarding.
- **Clearer AI error messages**: when a provider can't find the model you picked, the app now explains that it may be paid-only, region-restricted, or a wrong endpoint, and points to the fix, instead of showing raw provider text. The rate-limit message now points free-tier users at the Flash-Lite model.

- **Custom serving sizes for any food** (Android): tap the pencil next to the serving unit in any food review or edit flow to rename it and set its weight (a 120 g pizza slice can become "big slice" at 180 g). Works from plain grams too: a dish without a suggested unit (a homemade curry, a stew) can get a named serving like "bowl" at 300 g, and the serving survives later name edits. The entry remembers the custom serving, and quantity changes scale from it like before. Available in manual entry, AI photo/text results, saved entries, and ingredient rows.
- **More nutrients in manual entry** (Android): the manual food dialog now records the optional nutrients, not just fiber. Tap _More Nutrition_ to expand the full list (sugars, fat types, cholesterol, sodium, potassium, vitamins, minerals, omega-3) and fill in the values you know; they land on the entry like any other logged nutrient.

### Fixed

- **Open food-entry dialogs survive rotation** (Android): rotating the phone no longer closes the in-progress text, voice, camera, or manual entry flow and discards what you typed. Captured photos and the photo review sheet already survived; now the open-sheet state and typed drafts do too.
- **Manual entry dialog scrolls when full** (Android): with _More Nutrition_ expanded, the dialog content scrolls within the screen, so Save stays reachable on small screens and at large font sizes.

## [3.12.0] - 2026-08-13

### Added

- **Weather input for the dynamic water goal** (Android): the expected-high temperature behind the +4 %/°C factor can come from two sources: the manual °C wheel (default, unchanged) or Open-Meteo, which finds today's forecast high for a searched city. No account, no key, no location permission; attribution is shown in Settings. When no fresh forecast exists, the manual value is the fallback, so the goal, reminders, and widgets keep working.
- **Body-measurement trend plots** (Android): the Progress tab can now chart how each body measurement changes over time, with one card per site: current value, net change over the selected range, and a trend line. Off by default: enable sites in _Settings → App & Display → Customize progress_, which also hosts the Progress default range. Data comes from Personal Info → Body measurements. Plots follow the 1W to All range chips and the cm/in unit setting. Closes Codeberg [#18](https://codeberg.org/fitguy/Chompass/issues/18) by [@dorian-grosch](https://codeberg.org/dorian-grosch).

### Fixed

- **"What if?" popup scrolls when the suggestion is long** (Android): the dialog now scrolls within a fixed height, so long AI suggestions stay readable on small screens and at large font sizes. Closes Codeberg [#17](https://codeberg.org/fitguy/Chompass/issues/17) by [@dorian-grosch](https://codeberg.org/dorian-grosch).

## [3.11.0] - 2026-08-13

### Added

- **Manual entry with serving quantity** (Android): the manual food dialog now has a _Serving_ card with quantity and unit, and the unit is suggested from the food name (pizza suggests slice, bread suggests slice, coffee suggests ml, the same heuristics the AI paths use). The serving is saved on the entry, so the edit sheet reopens with the right quantity, and later quantity changes scale from what you logged. Macros stay exactly what you typed. Untouched entries keep the old shape.
- **Copy and paste diary entries** (Android): long-press one or more food rows to enter selection mode, tap Copy, then tap the Paste chip to add the entries to any day you view. Pasted entries land as new rows in the current meal slot, batched into one write with Health Connect mirroring in the background, same as Copy from Day. The clipboard stays in memory until replaced or dismissed, and you can paste as often as you like. The chip shows a brief _Pasting…_ state while the write runs.
- **Copy from Day lets you pick the destination day** (Android): the sheet now shows a _Copy To_ row next to _Copy From_, so you can choose where the entries land without leaving the sheet (it still defaults to the day you're viewing). Handy for filling an old day with today's foods, or fixing a misfiled entry.

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

- **Arithmetic quantity entry** (Android + PWA): serving-quantity fields accept small math: `50×2`, `200−30`, `100÷4` resolve as absolute expressions (`× ÷` bind tighter than `+ −`), while `+20`/`-10` stay relative edits on the current amount. The serving card gains a `+ − × ÷` calculator row with a live `= result` preview; expressions commit to their resolved number on blur/unit change/save (Android collapses deltas immediately, as before). Locale-aware: comma decimals and whitespace parse in both implementations. Closes upstream [#171](https://github.com/aopv/fud-ai/issues/171).
- **Custom optional-nutrient goals clamp + vitamin D hint** (Android + PWA): free-form goal values are capped per nutrient (sanity guard; the wheel stays the quick pick), and vitamin D shows a live `mcg ≈ IU` conversion (1 mcg = 40 IU) while entering a custom value, so a 250 mcg / 10,000 IU goal is legible. Closes upstream [#173](https://github.com/aopv/fud-ai/issues/173).
- **Water log syncs to Health Connect** (Android): every logged drink is written out as a Health Connect `HydrationRecord` (tagged with the entry's UUID) when water tracking is enabled and the write permission is granted; deleting an entry deletes its record, and reconnecting backfills the whole log. After a reinstall or a new phone, the records Chompass itself wrote are read back (730-day window) and rebuilt into the local water log, recovering the original UUIDs so future deletes still match. The Progress wellness card keeps reading all hydration Health Connect holds: your own records included. Closes Codeberg [#9](https://codeberg.org/fitguy/Chompass/issues/9).
- **Water reminders say how much to drink** (Android): the reminder planner now computes the next-drink amount (_remaining goal ÷ cup size ÷ remaining window_) and the notification tells you: "Drink 300 ml · next in ~90 min". The Home water ring and the water widget show the next planned drink ("Next 18:20 · 300 ml"), and the reminder interval preview in Settings shows the per-cup quantity ("≈ every 90 min · 5 cups · 300 ml each"). The quantity rule is documented in the water register ([`CALCULATION_METHODS.md`](CALCULATION_METHODS.md)).
- **Ukrainian (uk) locale** (Android + PWA): complete 347-key catalog: Chompass's **16th language** (Android `values-uk` + PWA `uk.js`, both sides of the shared locale contract).

### Changed

- **Settings reorganized into per-domain screens** (Android): new **Food & Entry**, **Water**, **Notifications** and **Sync** sub-screens; App & Display shrinks from up to 24 inline rows to 7 static ones that deep-link to the domains; AI & Speech keeps provider wiring only. Settings become a **connected graph**: read-only cross-link rows navigate between related screens (Goals ↔ Water, Water ↔ Notifications, AI → Food & Entry, Data → Sync) with a `from` nav argument so the back label retraces the path; dependencies are shown disabled with a link instead of hidden (the water-reminder toggle stays visible). A new **Suggestions** card on the hub shows up to 3 dismissible nudges (water tracking, reminders, Adaptive Goals, Health Connect, notifications, WebDAV backup) gated by install age: rows only navigate, nothing is auto-enabled. Consistency pass: _Auto_ chip replaces the lock glyph, shared footnote and related-links composables, danger-zone separation for Clear Food Log / Delete All Data. **No behavior, storage, or formula changes to existing settings keys.** Plan and execution log: [`archive/SETTINGS_OVERHAUL_PLAN.md`](archive/SETTINGS_OVERHAUL_PLAN.md).
- **Localization hygiene** (Android + PWA): 1,551 verbatim English copies removed from the 15 locale packs (Russian copies translated, neutral formats and brands dropped: the Android string check now fails on phrase-level EN-identical values); hardcoded UI text moved to `strings.xml` (native speech errors, camera flash labels, WebDAV/sync messages, on-device download failure, AI provider errors); PWA catalogs gained 63 keys and the i18n test now asserts no EN copies. The onboarding tagline was rewritten to plain language ("Track meals, stay balanced") and re-translated.

### Fixed

- **Fallback AI provider keeps its own base URL and API key** (Android): main and fallback slots no longer share one stored URL/key per provider: a fallback that reuses the primary provider (e.g. a second OpenAI-compatible endpoint with a different model) keeps its own endpoint and key instead of silently inheriting the primary's after restart. Fallback-slot values live under separate keys (`customBaseURL_fallback_*`, `apikey_fallback_*`); existing primary values are untouched. Users with a same-provider main+fallback should re-enter the fallback URL/key once.

## [3.9.0] - 2026-08-11

### Added

- **Dynamic water goal + adaptive reminders** (Android, **Beta**, opt-in, default off): the daily water goal adapts to body weight (35 ml/kg), the expected high temperature (manual entry, no location permission), profile activity level, and optionally subtracts water from food (coarse 60 % of diary grams, capped 1 L). The water reminder becomes a **drinking window + cup size plan** (defaults 08:00–21:00, 300 ml): reminders fire at an interval of _remaining goal ÷ cup ÷ remaining window_ (e.g. 2,500 ml ÷ 300 ml over 13 h → about every 90 min), **recalculated after every entry** so logging a glass immediately re-paces the next reminder; goal reached or past the window end silences the chain until the next morning. Home water ring shows the computed goal with a tappable _auto_ badge (jumps to Settings); the widget goal follows the calculator. Shipped with a **medical disclaimer** (estimate, not advice; consult a doctor if you have a health condition, and always drink enough even when the app does not remind you) in Settings next to the feature, in the Safety &amp; Medical notices, and in the onboarding safety card. Docs: WATER-DYN-A/B/C in [`CALCULATION_METHODS.md`](CALCULATION_METHODS.md) (science-backed audit trail: EFSA 2010 / IOM 2004 AIs, ACSM 2007 exercise sweat rates, 19–30 % food-moisture share). Closes Codeberg [#3](https://codeberg.org/fitguy/Chompass/issues/3) by [@1260er](https://codeberg.org/1260er).
- **Pick the emoji or photo shown for a food entry** (Android): tap the hero icon in the edit-food sheet to open a picker: 44 food emojis in a grid, a _Set photo_ action (system photo picker), and _Remove photo_; photos are stored under the entry's own filename so re-picking overwrites in place without orphans. Closes Codeberg [#5](https://codeberg.org/fitguy/Chompass/issues/5) by [@armishinwn](https://codeberg.org/armishinwn).
- **Custom AI endpoints trust phone-installed CA certificates** (Android): self-hosted OpenAI-compatible servers are reachable again in release builds: cleartext LAN (`Cleartext is not allowed`) and self-signed HTTPS with the root CA installed on the phone (`Trust anchor for certification path not found`) both work. Cloud providers, OLLAMA loopback, WebDAV and STT keep the platform default, so a user-installed CA can never intercept cloud AI traffic. Closes Codeberg [#8](https://codeberg.org/fitguy/Chompass/issues/8) by [@darkxylese](https://codeberg.org/darkxylese).
- **Auto-capitalized AI food names** (Android): AI-generated ingredient and meal names are now title-cased ("grilled chicken breast with rice" → _Grilled Chicken Breast with Rice_, "hähnchen mit reis" → _Hähnchen mit Reis_) so they look consistent with manually entered foods; connector words (with/and/mit…) stay lowercase, and acronyms, brands and numbers are untouched. Closes Codeberg [#7](https://codeberg.org/fitguy/Chompass/issues/7) by [@Professional-Human](https://codeberg.org/Professional-Human).

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

Initial public release of Chompass - an ad-free, privacy-focused Android fork of [Fud AI](https://github.com/aopv/fud-ai).

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

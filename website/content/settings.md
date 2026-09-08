---
title: Settings guide
description: Every Chompass setting explained: profile, goals and macros, food entry, trackers and reminders, AI providers, Health Connect, sync, and data export on Android and the PWA.
layout: single
---

Every setting lives on your device. On Android the Settings hub has a search field. This page follows that app's group order and marks PWA-only and Android-only rows. PWA hub names differ slightly: trackers sit under App & Display → Home display, and there are no reminders.

## Personal Info

Profile used to estimate energy needs. All of these exist on both the Android app and the PWA unless marked.

| Setting | What it does |
| --- | --- |
| Units | Switches height and weight together between metric and imperial. On the PWA, weight and height units are separate under App & Display → Units & schedule. |
| Gender | Used for BMR. The PWA labels this Sex. |
| Birthday | Sets age for BMR. The PWA asks for Age instead. |
| Height | Body height in the unit you chose. |
| Weight | Current body weight. |
| Body Fat | Optional body-fat %. Unlocks Katch-McArdle BMR and a goal-fat row. |
| Use Body Fat for BMR | On uses lean mass (Katch-McArdle). Off uses Mifflin-St Jeor from height, weight, and age. Turn off if the reading is stale. |
| Goal Body Fat (Android) | Target body-fat % once a current value is set. |
| Body measurements | Tape circumferences for Navy/RFM estimates, AI goal calc, and Coach. They never edit BMR or the body-fat field. |

## Goals & Nutrition

Daily calorie and macro targets, and what drives them. All of these exist on both the Android app and the PWA unless marked.

| Setting | What it does |
| --- | --- |
| Weight Goal | Lose, maintain, or gain. |
| Diet Mode | Standard or Keto. Switching to Keto pauses and hides the Day Types plan (Day Types is Standard-diet only). |
| Keto carb mode | Shown in Keto. How carbs are counted on a keto plan. |
| Keto net carbs | Shown in Keto. Daily net-carb target. |
| Activity Level | Multiplier on resting burn. On the PWA this row lives under Personal Info. |
| Weekly Change | Shown when the goal is not maintain. Pace of weight change. The PWA calls this Pace. |
| Goal Weight | Shown when the goal is not maintain. |
| Adaptive Goals | Weekly calorie correction from your weight trend (at most 150 kcal). In-app ⓘ dialog. On the PWA this toggle lives under Home display. |
| Energy Burn Goals (Android) | Budget uses measured Health Connect burn instead of the formula estimate. In-app ⓘ dialog. |
| Calories / Protein / Carbs / Fat | Daily macros. Locked rows survive Recalculate Goals and Adaptive Goals. At most two macros can be locked, so one stays free to balance. On the PWA, filling a custom protein, carbs, or fat value locks that macro. |
| Custom calories (PWA) | Override the formula calorie target. Confirms if you go below the safety floor. |
| Protein target (PWA) | Grams per day, g per kg body weight, or g per kg lean mass. |
| Clear custom targets (PWA) | Drops custom macros and returns unlocked values to the formula. |
| Recalculate Goals | Rebuilds unlocked calories and macros. Uses your AI provider when a key is set; otherwise the formula. Android shows a "Tap to update" cue when inputs have changed. |
| Recalc Details (Android) | Reopens the last AI or Adaptive explanation. |

### Other Nutrient Goals

Daily goals for fiber and micros. The PWA calls this Optional nutrients. All of these exist on both clients unless marked.

| Setting | What it does |
| --- | --- |
| Per-nutrient goals | Daily targets used by Home nutrient cards. Vitamin D can be entered in mcg; 1 mcg = 40 IU. |
| Estimate with AI | Fills these goals from your profile and calorie target. Needs the AI master toggle on. |
| Caffeine goal | Same stored value as the caffeine daily limit under Trackers. Edit it on either screen. |

### Day Types

Carb-cycling profiles for Standard diet only. Hidden (paused) in Keto.

| Setting | What it does |
| --- | --- |
| Day Types | Master switch for named calorie/macro profiles (for example Training and Rest). |
| Profiles | Name plus kcal / protein / carbs / fat. Reorder or delete; names must be unique. |
| Schedule | Manual, weekdays, or a repeating cycle, with a default profile. |
| Per-date overrides | Pin a profile to a specific date. Android and the PWA both show a short preview of the coming days. |

## Food & Entry

How logging behaves. Android has this as its own hub group. On the PWA, meal times live under Units & schedule; meal breakdown and serving-unit mode live under AI & Speech.

| Setting | What it does |
| --- | --- |
| Default to Grams (Android) | New review sheets open on grams. You can still switch unit before logging. In-app ⓘ dialog. |
| Food log sort (Android) | Group by meal, or latest first. |
| Meal times | On Android, a toggle plus breakfast, lunch, dinner, and snack start times. Those boundaries drive automatic meal grouping. On the PWA the four start times are always under Units & schedule. |
| Ask for a photo note (Android) | Highlights the note field before Analyze. You can continue without a note. |
| Meal ingredient breakdown | Asks the AI to split a meal into editable ingredients. Off for on-device Gemma (needs a stronger model). |
| Serving unit inference | Grams only, heuristic from the food name, or an extra AI call when the model omits a unit. |
| Serving Unit Heuristics (Android) | Shown in heuristic mode. Rules used to guess a non-gram unit. |

## Display

Appearance, language, Home, and Progress. Android labels this Display. The PWA labels it App & Display and also puts trackers and Install app here. All of these exist on both clients unless marked.

| Setting | What it does |
| --- | --- |
| Appearance | System, light, dark, or OLED (Android). The PWA offers system, light, and dark. |
| Language | System default plus 18 locales. The PWA can follow the browser language. |
| Theme Color | Accent for buttons, charts, and progress. The PWA calls this Accent color (nine options). |
| Fixed Launcher Icon (Android) | Keeps the launcher icon teal instead of following theme color or wallpaper. |
| System date and time pickers (Android) | Uses the phone's date and time dialogs when editing a meal. |

### Home Display

Android: Settings → Display → Home Display. PWA: App & Display → Home display (trackers on that PWA screen are listed under Trackers & Reminders below).

| Setting | What it does |
| --- | --- |
| Nutrient cards | Which micros sit under the calorie ring, and how many cards to show. |
| Show step counter on home (Android) | Optional steps card. Needs Health Connect. |
| Daily step goal (Android) | Target for that card. |
| Show Active Calories (Android) | Optional active-burn card when the gauge is in static mode. |
| Calorie gauge | Static keeps the daily goal fixed. Add Active grows the ring with measured burn (Health Connect on Android, or an estimate). |
| Food log macros | Which macro chips appear on each food-log row. |

### Customize Progress

Android: Settings → Display → Customize progress. PWA: App & Display → Units & schedule.

| Setting | What it does |
| --- | --- |
| Week Starts On | Monday, Sunday, or Saturday. |
| Progress default range | Opening range on the Progress tab (1W to All). |
| Nutrient averages | Average selected nutrients over the Progress range. Confirms before enabling. |
| Nutrient average picks | Which nutrients are averaged. |
| Body measurement plots | Per-site circumference trends on Progress. Off by default. Data comes from Personal Info → Body measurements. |

## Trackers & Reminders

Optional Home cards. Android groups them here with Notifications. The PWA puts the tracker toggles under App & Display → Home display and has no reminders. All of these exist on both clients unless marked.

| Setting | What it does |
| --- | --- |
| Nicotine tracking | Home nicotine card. Logs stay on the device and in WebDAV; not Health Connect, not shared meals, not sent to AI. |
| Daily limit (nicotine) | 0 means no limit. |
| Quick log kinds (nicotine) | Which +1 chips appear on Add food. |
| Caffeine tracking | Home caffeine card. Today's total includes caffeine from food entries. |
| Daily limit (caffeine) | Milligrams. Same value as the caffeine goal under Other Nutrient Goals. |
| Daily notes | Home note card. Notes persist when you turn the card off. They sync and export with the diary; they are never sent to AI. |
| Fasting timer | Local-only timer. Fasting data is never exported or synced on either client. |

### Water

Android: Settings → Trackers & Reminders → Water. PWA: Home display (tracking toggle and goal only).

| Setting | What it does |
| --- | --- |
| Water tracking | Home water card and log. |
| Daily water goal | Fixed millilitre target when Dynamic goal is off. |
| Quick-log presets (Android) | Snap points on the Add food water slider. |
| Dynamic goal (Android) | Adapts to weight, weather, and profile activity. No location permission. |
| Base goal (Android) | Body weight (35 ml/kg) or a fixed amount. |
| Weather source (Android) | Open-Meteo city forecast, or a manual expected high. |
| City (Android) | City for Open-Meteo. Only the name and coordinates are sent. No account, no key. |
| Use profile activity level (Android) | Folds Activity Level into the dynamic target. |
| Subtract water from food (Android) | Coarse estimate from logged food weight, capped at 1 L. |
| Drinking reminders (Android) | Link to the Notifications drinking-window plan. |

### Notifications (Android)

Master toggle gates every reminder. The water reminder stays visible (disabled) until water tracking is on.

| Setting | What it does |
| --- | --- |
| Notifications | Master switch. On Android 13 and later this also requests the notification permission. |
| Food / streak reminders | Nudges to log food and keep a streak. |
| Daily summary | End-of-day recap at a time you pick. |
| Weight log reminder | Prompt to log weight. |
| Body fat reminder | Prompt to log body fat. |
| Water reminder | Drinking nudges. Disabled until Water tracking is on. |
| Reminder plan | Drinking window start/end and cup size. Interval is window ÷ cups needed for the goal. |
| Goal alerts | Hits on calorie or tracker limits. |
| App updates | Notice when a new version is ready. |
| Battery Optimization | Opens system settings so reminders can fire while the app is in the background. |
| Goal reached / Break-fast reminder / Start reminder | Fasting-cycle nudges and lead times, on the Fasting screen. |

## AI & Speech

Providers, models, and speech. All of these exist on both clients unless marked. A free [Google AI Studio](https://aistudio.google.com/apikey) key is enough for casual use.

| Setting | What it does |
| --- | --- |
| AI features | Master toggle. Off: no request goes to any LLM, and AI entry points hide. Barcode, search, and manual logging keep working. |
| Show the coach tab (Android) | Hides Coach from the bottom bar without turning AI off. |
| Provider | Android: 14 choices, including Ollama (local) and On-Device (Private) Gemma 4. PWA: Gemini, OpenAI, Anthropic, and OpenAI-compatible. |
| Model | Chat/analysis model for that provider. Custom model id where the provider allows it. |
| Vision model | Separate photo model. Android: OpenAI-compatible providers. PWA: OpenAI-compatible. |
| Reasoning effort | Android: OpenRouter. PWA: OpenAI-compatible. |
| API key | Masked. Required for every provider except Ollama and On-Device. Encrypted at rest (Android Keystore / PWA Web Crypto), like the WebDAV password. |
| Test key (PWA) | Quick Gemini-only check. Other providers are verified the first time you Analyze or open Coach. |
| Base URL / Server URL | Custom and Ollama endpoints. |
| Allow Insecure HTTP (Android) | The only cleartext path in release builds. For Ollama or custom endpoints on a LAN. |
| On-Device (Private) (Android) | Gemma 4 Edge (E2B or E4B), downloaded once. No API key and no server upload for food text or photo analysis. Cloud AI stays more accurate. |
| On-device leftover cleanup (Android) | Removes a leftover partial download. |
| Max response length (Android) | Token cap. Hidden for Gemini and on-device. |
| AI read timeout (Android) | How long to wait on a cloud reply. Hidden for on-device. |
| Google Search (Android) | Gemini-only grounding with Google Search. |
| Speech-to-Text (Android) | Native on-device, Gemini Audio, OpenAI Whisper, Groq Whisper, Deepgram, or AssemblyAI, plus a key if that engine needs one. |
| Speech language | Locale for dictation. The PWA uses the browser's on-device recognizer (Chrome/Edge work best). Cloud speech engines are Android-only. |
| Custom AI Instructions | Free-text preferences sent with analysis and Coach prompts. |
| Enable Fallback | Retries in the cloud when the primary provider fails. After on-device inference fails, the first fallback use reloads the engine (short delay). |
| Fallback provider / model / key | Cloud provider used for that retry. |

## Health & Data

Export, import, Health Connect, and sync. All of these exist on both clients unless marked.

| Setting | What it does |
| --- | --- |
| Health Connect (Android) | Two-way sync with Gadgetbridge, openScale, Samsung Health, and other Health Connect apps. |
| Manage Health Connect access (Android) | Opens system Health Connect permissions. |
| Background sync (Android) | Reads Health Connect every few hours while the app is closed. Off by default. Device-dependent. |
| Export Food Diary | JSON, CSV, or Markdown, with a date range. |
| Export Weight & Body Data | Weight, body-fat, and measurement history. |
| Import Food Diary JSON | Chompass or Fud AI diary JSON. |
| Import Weight & Body Data | Chompass JSON/CSV, openScale CSV, and common weight CSVs. |
| Clear Food Log (Android) | Deletes the diary after confirm. Profile and metrics stay. |
| Delete All Data | Wipes local diary, metrics, profile, chat, and keys, then returns to onboarding. Health Connect records already written stay in Health Connect. The PWA calls this Clear all local data. |

## Sync (WebDAV)

Optional user-hosted sync. Point Android and the PWA at the same WebDAV file (for example Nextcloud). Chompass does not run a sync server. API keys and food photos are not included. Fasting data is local-only and never synced.

| Setting | What it does |
| --- | --- |
| WebDAV file URL | HTTPS address of the sync document. |
| Username | WebDAV user. |
| Password | Stored encrypted (Android Keystore / PWA Web Crypto), like API keys. |
| Auto sync | Android: sync in the background when enabled. PWA: Sync on open, at most once a day, off by default. |
| Export / import sync JSON | File copy of the sync document (`Chompass-sync.json`) when you do not want WebDAV. |
| Sync now | Push and pull immediately. |

## About

App info. Rows differ by client.

| Setting | What it does |
| --- | --- |
| Check for updates (Android) | Looks up the latest Codeberg release. |
| Share the app (Android) | Share link to Chompass. |
| Open source | License and source. |
| Credits (Android) | Third-party notices. |
| Report issue (Android) | Opens the Codeberg issue tracker. |
| Privacy | Privacy policy. |
| Donate | Ko-fi ("buy fitguy a yogurt"). |
| Calculation methods (PWA) | Short register of BMR, TDEE, macros, forecast, Adaptive Goals, and body-fat formulas, with a link to the full notes. |

## PWA only: Install app

Under App & Display → Install. Hidden when the PWA is already running standalone.

| Setting | What it does |
| --- | --- |
| Install app | Browser install prompt when the engine offers one, plus how-to cards for iOS Safari, Android Chromium, Firefox, DuckDuckGo, and desktop. |

## Where the numbers come from

Formulas for BMR, TDEE, calorie targets, macros, and water are in the [calculation methods](https://codeberg.org/fitguy/chompass/src/branch/main/docs/CALCULATION_METHODS.md) notes. The Android app also has in-app ⓘ dialogs and a Calculation Methods screen.

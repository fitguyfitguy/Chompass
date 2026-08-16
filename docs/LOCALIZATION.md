# Localization (Android + PWA)

Shared multilingual contract for Chompass. Android remains the product of record for
resource keys; the PWA mirrors the same locale set for core daily-driver copy.

## Locale contract

Canonical list: [`testdata/parity/locales.json`](../testdata/parity/locales.json).

| Item | Rule |
|------|------|
| Supported UI locales | 16 tags: `en`, `ar`, `az`, `de`, `es`, `fr`, `hi`, `it`, `ja`, `ko`, `nl`, `pt-BR`, `ro`, `ru`, `uk`, `zh-CN` |
| Fallback | Always English (`en` / `values/strings.xml`) |
| RTL | Arabic (`ar`) only in the current set; set `dir="rtl"` / Compose RTL |
| Detection | Android: system + Android 13+ per-app language. PWA: `prefs.uiLang` or browser `navigator.language` |
| Persistence | Android: OS. PWA: IndexedDB `prefs.uiLang` (`""` = auto) |
| Speech vs UI | Independent. Speech settings do not change UI catalogs |

## Android

- Source of truth: [`android/app/src/main/res/values/strings.xml`](../android/app/src/main/res/values/strings.xml)
- Translations live in `values-<qual>/strings.xml` matching `androidValues` in the locale fixture
- User-facing dates/numbers use the app locale; export/protocol formats stay `Locale.US`
- Widgets and status fallbacks must use `R.string`, not hardcoded English
- AI response language follows `Locale.getDefault()` (includes per-app language)

Validate resource completeness:

```bash
uv run python scripts/check_android_strings.py
```

The checker fails on **verbatim EN copies**: a translated pack entry whose value
is identical to English (formats, URLs, and bare units/loanwords are exempt).
Copies silently duplicate the EN fallback, hide the real gap from the
missing-key report, and block translators, so they must be translated or
deleted rather than kept.

## Compact labels (fixed-width UI)

Translations of **compact UI labels** (chips, tabs, status lines, buttons, meal
slots, bottom-nav labels, widget cards) are systematically longer than English
in several locales, and fixed-width elements truncate or overflow. The registry
[`testdata/parity/compact_strings.json`](../testdata/parity/compact_strings.json)
is the source of truth (see [`docs/local/PLAN_UI_STRING_FIT.md`](local/PLAN_UI_STRING_FIT.md)):

- **Budget:** value ≤ 12 Latin chars; CJK glyphs are narrow and count at 0.5
  (pure-CJK strings cap at 8 chars). `perKeyOverrides` widen the budget for
  elements with more room: macro status lines get 20 (they include the value),
  bottom-nav labels fit 14, full-width sheet rows fit 16.
- **Placeholder position:** `valueFirst` keys (macro status lines) must put the
  first placeholder before the first word — EN "10,7g over" → DE "10,7g drüber",
  NOT "drüber 10,7g". The Russian prefix form ("Превышение 10,7g") was a bug of
  this class.
- **Rendered worst-case (warning):** for placeholder keys, the gate substitutes
  a worst-case value ("91,5" + "g") and warns when value + suffix exceed the
  status-line budget at max font scale (ru "91,5g осталось" = 14 > 13). The
  MacroCard status line auto-shrinks (`TextAutoSize`, 8–11 sp) so the UI still
  fits; the warning is a translator tripwire to prefer shorter suffixes
  ("übrig" over "осталось").
- **Guidance:** a short colloquial form beats the full word where the element is
  tight: drüber, 1W, Start, Mittag, Erneut, Eigene. Prose keys are simply not
  in the registry.

The gate lives in `check_android_strings.py` (warnings by default;
`--strict-compact` fails) and the PWA i18n test suite
(`web/app/src/lib/__tests__/i18n.test.js` → "compact labels"). New or changed
translations of registry keys must pass:

```bash
uv run python scripts/check_android_strings.py --strict-compact
```

The release checklist (`release:check-metadata`) runs the gate in strict mode.
Locale-fit screenshot references (de + ru variants of the compact screens —
Home over-goal macros, Progress range chips, Saved Meals tabs, Settings hub,
plus a ru Home at `fontScale = 1.3` with a long macro status line) live under
`android/app/src/screenshotTestDebug/reference/`; regenerate with
`./gradlew :app:updateDebugScreenshotTest` after intentional label changes, and
run `validateDebugScreenshotTest` in CI to catch regressions.

## PWA

- Catalog API: [`web/app/src/lib/i18n/`](../web/app/src/lib/i18n/)
- English catalog is complete for **core surfaces** (nav, onboarding, diary, progress, settings hub/app/language, dialogs, errors, a11y)
- Other locale catalogs override English; missing keys fall back to English
- Settings → App & Display → Language
- `document.documentElement.lang` / `dir` update when locale changes

```bash
node --test web/app/src/lib/__tests__/i18n.test.js
```

## Contributor workflow

1. Add English keys first (Android `values/strings.xml` and/or PWA `catalogs/en.js`)
2. Translate core surfaces for every locale in the fixture (or leave explicit EN fallback for out-of-scope keys)
3. Keep export JSON field names and machine IDs in English
4. Run `devenv tasks run release:check-parity` (includes locale fixture + PWA i18n tests)
5. Update [`PARITY.md`](PARITY.md) if the shared/android-only status of i18n changes

**Full-pack contributions** (e.g. a community translation merge) additionally
need a **UI-fit pass** before merging: render the compact screens (Home macro
cards, Progress range chips, Saved Meals tabs, Settings hub, bottom nav) at max
font scale in the target locale and confirm nothing truncates or overflows, then
run `check_android_strings.py --strict-compact` and regenerate the de/ru
screenshot references if the labels changed.

## Locale review status (second phase)

| Locale | PWA core catalog | Android resource pack |
|--------|------------------|------------------------|
| en | complete | complete (source) |
| ru | complete | most complete non-EN (1,044 / 1,420 present) |
| uk | complete (new, translated from scratch) | 673 keys translated; EN fallback for the rest |
| de, es, fr | complete | **de: complete (1,494 / 1,494 + 2 plurals, community pack by 1260er, style-reviewed 2026-08-16)**; es, fr: partial (~600 / 1,420 present) |
| ar, az, hi, it, ja, ko, nl, pt-BR, ro, zh-CN | complete | partial (~560–590 / 1,420 present); EN fallback for missing keys |

Second phase: all locale files were swept for verbatim EN copies: they were
translated (ru) or removed so they fall back to EN honestly (all other packs),
and `check_android_strings.py` now enforces zero copies (with a curated
per-locale exemption list for loanwords/proper nouns in
`testdata/parity/copy_exemptions.json`). ru additionally
received translations for the settings/water/safety/import keys that were
previously English-only. German joined ru at full coverage 2026-08-16 (a
community full-pack translation, normalized to the app's informal du voice
and the shared Protein term, reviewed against `UI_COPY_STYLE.md`). All hardcoded Kotlin user-facing strings (speech
errors, camera flash labels, sync messages, AI provider errors) now live in
`values/strings.xml`; `AiError` carries a `@StringRes` resolved at the UI
layer, so provider errors show in the app language without threading a
Context through the AI stack.

Core surfaces: shell nav, **full onboarding flow** (steps, choices, AI setup, plan-ready), diary empty states, progress title/ranges, settings hub/app/language, dialogs/errors/a11y, plus onboarding health + accuracy disclaimers.

## Out of scope / deferred

- Full 1:1 port of every Android string into the PWA (entry AI flows, grounded entry WIP, etc.)
- Human review of machine-assisted translations beyond core surfaces
- Store listing metadata beyond `metadata/en-US/`

## Remaining hardcoded English

- PWA install-guide note for iOS non-Safari (dropped as redundant with the tip)
- `RetryPolicy` fallback literal `"Request failed"` (unreachable; every attempt
  overwrites it before the loop exits)
- `AiError.connectionFailureMessage` (unused/dead)
- `friendlyMessage()` English text stays as the log/raw fallback; the localized
  counterpart is `friendlyMessageRes()` attached to the thrown `AiError.Api`


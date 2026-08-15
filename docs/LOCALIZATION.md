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

## Locale review status (second phase)

| Locale | PWA core catalog | Android resource pack |
|--------|------------------|------------------------|
| en | complete | complete (source) |
| ru | complete | most complete non-EN (1,044 / 1,420 present) |
| uk | complete (new, translated from scratch) | 673 keys translated; EN fallback for the rest |
| de, es, fr | complete | partial (~600 / 1,420 present) |
| ar, az, hi, it, ja, ko, nl, pt-BR, ro, zh-CN | complete | partial (~560–590 / 1,420 present); EN fallback for missing keys |

Second phase: all locale files were swept for verbatim EN copies: they were
translated (ru) or removed so they fall back to EN honestly (all other packs),
and `check_android_strings.py` now enforces zero copies. ru additionally
received translations for the settings/water/safety/import keys that were
previously English-only. All hardcoded Kotlin user-facing strings (speech
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


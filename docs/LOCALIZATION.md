# Localization (Android + PWA)

Shared multilingual contract for Chompass. Android remains the product of record for
resource keys; the PWA mirrors the same locale set for core daily-driver copy.

## Locale contract

Canonical list: [`testdata/parity/locales.json`](../testdata/parity/locales.json).

| Item | Rule |
|------|------|
| Supported UI locales | 15 tags: `en`, `ar`, `az`, `de`, `es`, `fr`, `hi`, `it`, `ja`, `ko`, `nl`, `pt-BR`, `ro`, `ru`, `zh-CN` |
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

## Locale review status (first phase)

| Locale | PWA core catalog | Android resource pack |
|--------|------------------|------------------------|
| en | complete | complete (source) |
| de, es, fr | complete | partial (~half of EN; new phase-1 keys translated) |
| ru | complete | most complete non-EN (~87%) |
| ar, az, hi, it, ja, ko, nl, pt-BR, ro, zh-CN | complete | partial (~half); EN fallback for missing keys |

Core surfaces: shell nav, **full onboarding flow** (steps, choices, AI setup, plan-ready), diary empty states, progress title/ranges, settings hub/app/language, dialogs/errors/a11y, plus onboarding health + accuracy disclaimers.

## Out of scope / deferred

- Full 1:1 port of every Android string into the PWA (entry AI flows, grounded entry WIP, etc.)
- Human review of machine-assisted translations beyond core surfaces
- Store listing metadata beyond `metadata/en-US/`


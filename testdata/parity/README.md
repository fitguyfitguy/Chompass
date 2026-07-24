# Cross-app parity fixtures

Committed golden inputs/expected outputs for Android and PWA drift checks.

| File | Purpose |
|------|---------|
| `formulas-expected.json` | Shared formula / FCAST intake golden scenarios |
| `diary-sample.json` | Anonymized diary export `format_version` 1.1 |
| `body-metrics-sample.json` | Synthetic body-metrics export `format_version` 1.0 |
| `meal-share-sample.json` | Meal-share payload `v` 1 (decoded JSON) |
| `ai-provider-defaults.json` | BYOK defaults for Gemini / Anthropic / OpenAI (PWA `openai_compatible`) |
| `goal-formula-prompt-fragments.json` | AI goal-prompt formula line strings |
| `pref-defaults.json` | Shared semantic preference defaults (not a portable prefs export) |

Do not commit personal diary exports here. Keep samples small and synthetic/anonymized.
Schemas live in `contracts/` (wire formats only).

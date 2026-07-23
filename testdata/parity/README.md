# Cross-app parity fixtures

Committed golden inputs/expected outputs for Android and PWA drift checks.

| File | Purpose |
|------|---------|
| `formulas-expected.json` | Shared formula / FCAST intake golden scenarios |
| `diary-sample.json` | Anonymized diary export `format_version` 1.1 |
| `body-metrics-sample.json` | Synthetic body-metrics export `format_version` 1.0 |
| `meal-share-sample.json` | Meal-share payload `v` 1 (decoded JSON) |

Do not commit personal diary exports here. Keep samples small and synthetic/anonymized.
Schemas live in `contracts/`.

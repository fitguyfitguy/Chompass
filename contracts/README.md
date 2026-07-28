# Wire-format contracts (Android ↔ PWA)

Versioned JSON Schemas for cross-app interchange. Fixtures in
[`testdata/parity/`](../testdata/parity/) must validate against these.

| Schema | Format | Consumers |
|--------|--------|-----------|
| [`diary-1.1.schema.json`](diary-1.1.schema.json) | Diary export `format_version` **1.1** | `DiaryExporter` / `DiaryImporter`, `web/.../diary-format.js` |
| [`body-metrics-1.0.schema.json`](body-metrics-1.0.schema.json) | Body metrics `kind=body_metrics` **1.0** | `BodyMetricsExporter` / `BodyMetricsImporter`, `body-metrics-format.js` |
| [`meal-share-v1.schema.json`](meal-share-v1.schema.json) | Meal share payload `v` **1** | `MealShare.kt`, `web/.../meal-share.js` |
| [`sync-1.0.schema.json`](sync-1.0.schema.json) | User-hosted sync `kind=sync` **1.0** | `SyncDocument` / `SyncRepository`, `web/.../sync-format.js` |

Schemas are a **structural / version** guard (`format_version` / `v` consts, required roots).
Root `additionalProperties: true` is intentional: clients may carry non-interchange
fields without breaking the other app. Field completeness is enforced by **strict
importers** plus **goldens** under `testdata/parity/`, not by locking every property
in the schema.

Bump the version constant **and** add a new schema file when the wire shape
changes. Run `devenv tasks run release:check-parity` (or
`./scripts/check_parity.sh`) before releasing.
